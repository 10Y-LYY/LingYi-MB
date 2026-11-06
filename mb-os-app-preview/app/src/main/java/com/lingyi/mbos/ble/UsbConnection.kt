/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ble

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import com.lingyi.mbos.util.AppLog
import java.util.concurrent.atomic.AtomicBoolean
/**
 * USB OTG 直连（CDC-ACM 虚拟串口）。
 * micro:bit V2 的 DAPLink 接口布局（VID 0x0D28 / PID 0x0204）：
 *   接口 0 = MSC（MICROBIT 盘，class 8，系统自动挂载，无需处理）
 *   接口 1 = CDC 控制（class 2，Communication）
 *   接口 2 = CDC 数据（class 10，CDC_DATA，批量 IN/OUT 端点在这）
 * CDC-ACM 在 Android 上必须把“控制接口”和“数据接口”都认领，批量端点才真正激活；
 * 且 SET_LINE_CODING / SET_CONTROL_LINE_STATE 的 wIndex 必须指向 CDC 控制接口号（此处为 1）。
 * 全程异常兜底：任何失败都转化为 ConnState 提示，绝不抛异常导致 App 闪退。
 *
 * 读线程的断开判定见 [evaluateRead]：串口空闲（bulkTransfer 超时）绝不能判为断开——
 * 固件只在收到指令或指针增量时才输出，静止的板子会数秒不吐一个字节，误判会让
 * 「连接状态」在进入陀螺仪模式约 3.5 秒后被刷新成「未连接」，而板子其实一直插着。
 */
class UsbConnection : MicrobitConnection {

    override val type: String = "usb"

    private var ctx: Context? = null
    private var listener: MicrobitConnection.Listener? = null
    private var manager: UsbManager? = null
    private var device: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private var inEp: UsbEndpoint? = null
    private var outEp: UsbEndpoint? = null
    private val running = AtomicBoolean(false)
    private var connected = false
    private var registered = false
    private var detachRegistered = false

    /** 监听 USB 拔出：micro:bit 被拔掉 / 断电时必须通知 App，否则界面一直显示“已连接」。 */
    private val detachReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            // 广播回调同样可能运行在非常规线程，异常即崩进程，总体兜底
            runCatching {
                if (intent?.action != UsbManager.ACTION_USB_DEVICE_DETACHED) return
                val d = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                else
                    @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                if (d == device) {
                    notifyDisconnected("micro:bit 已断开连接（USB 拔出）")
                }
            }
        }
    }

    private val permReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            runCatching {
                if (intent?.action != ACTION_USB_PERMISSION) return
                val d = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                else
                    @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                if (d == device) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        openDevice()
                    } else {
                        fail("USB 权限被拒绝")
                    }
                }
            }
        }
    }

    override fun connect(context: Context, listener: MicrobitConnection.Listener) {
        try {
            this.ctx = context.applicationContext
            this.listener = listener
            manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            if (manager == null) return fail("设备不支持 USB 主机模式（请确认已开启 OTG）")

            device = findMicrobit(manager!!)
            if (device == null) return fail("未找到 micro:bit，请先用 OTG 数据线连接，并确认已开启手机 OTG 开关")

            if (manager!!.hasPermission(device)) {
                openDevice()
            } else {
                runCatching {
                    listener.onState(ConnState(false, "请求 USB 权限…"))
                    // Android 12+ 必须显式声明 PendingIntent 可变性；Android 14（target 34）禁止隐式 Intent
                    // 使用 FLAG_IMMUTABLE，故改用 FLAG_IMMUTABLE 并用 setPackage 使其成为定向 Intent。
                    val permIntent = Intent(ACTION_USB_PERMISSION).apply {
                        setPackage(context.packageName)
                    }
                    val pi = PendingIntent.getBroadcast(
                        context, 0, permIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    val permFilter = IntentFilter(ACTION_USB_PERMISSION)
                    // Android 13+ 要求注册广播时显式指定是否导出；USB 权限广播为本 App 定向接收。
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.registerReceiver(permReceiver, permFilter, Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        context.registerReceiver(permReceiver, permFilter)
                    }
                    registered = true
                    manager!!.requestPermission(device, pi)
                }.onFailure { fail("无法请求 USB 权限：${it.message}") }
            }
        } catch (t: Throwable) {
            fail("USB 连接异常：${t.message}")
        }
    }

    private fun findMicrobit(m: UsbManager): UsbDevice? {
        // 优先精确匹配 micro:bit V2（VID 0x0D28 / PID 0x0204）
        for (d in m.deviceList.values) {
            if (d.vendorId == 0x0D28 && d.productId == 0x0204) return d
        }
        // 兜底：任意 CDC-ACM（数据类）设备
        for (d in m.deviceList.values) {
            for (i in 0 until d.interfaceCount) {
                val itf = runCatching { d.getInterface(i) }.getOrNull() ?: continue
                if (itf.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) return d
            }
        }
        return null
    }

    private fun openDevice() {
        val d = device ?: return fail("设备丢失")
        val m = manager ?: return fail("USB 管理器丢失")
        try {
            connection = m.openDevice(d)
            if (connection == null) return fail("无法打开 USB 设备（可能正被其他应用占用，请关闭文件管理器等）")

            // 找出 CDC 控制接口（class 2）与 CDC 数据接口（class 10）。
            var controlIf: UsbInterface? = null
            var dataIf: UsbInterface? = null
            for (i in 0 until d.interfaceCount) {
                val itf = runCatching { d.getInterface(i) }.getOrNull() ?: continue
                when (itf.interfaceClass) {
                    UsbConstants.USB_CLASS_COMM -> controlIf = itf
                    UsbConstants.USB_CLASS_CDC_DATA -> dataIf = itf
                }
            }
            // 兜底：标准 class 找不到时，取含批量端点、且非 MSC 的接口作为数据接口。
            if (dataIf == null) {
                for (i in 0 until d.interfaceCount) {
                    val itf = runCatching { d.getInterface(i) }.getOrNull() ?: continue
                    if (itf.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) continue
                    val hasBulk = (0 until itf.endpointCount).any { ei ->
                        runCatching { itf.getEndpoint(ei) }.getOrNull()?.type == UsbConstants.USB_ENDPOINT_XFER_BULK
                    }
                    if (hasBulk) { dataIf = itf; break }
                }
            }

            if (dataIf == null) {
                runCatching { connection?.close() }
                connection = null
                return fail("未找到 CDC 串口端点（请确认连接的是 micro:bit V2 数据口）")
            }

            // 认领控制接口（尽量认领；失败不影响数据接口独立工作）。
            if (controlIf != null) {
                runCatching { connection?.claimInterface(controlIf, true) }
            }
            val dataClaimed = runCatching { connection?.claimInterface(dataIf, true) ?: false }.getOrDefault(false)
            if (!dataClaimed) {
                runCatching { connection?.close() }
                connection = null
                return fail("无法认领 CDC 串口接口（可能正被系统或其他应用占用）")
            }

            // 从数据接口取批量 IN/OUT 端点。
            for (j in 0 until dataIf.endpointCount) {
                val ep = runCatching { dataIf.getEndpoint(j) }.getOrNull() ?: continue
                when {
                    ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                        ep.direction == UsbConstants.USB_DIR_IN -> inEp = ep
                    ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                        ep.direction == UsbConstants.USB_DIR_OUT -> outEp = ep
                }
            }
            if (inEp == null || outEp == null) {
                runCatching { connection?.close() }
                connection = null
                return fail("CDC 端点不完整（缺少批量 IN 或 OUT）")
            }

            // 设置波特率（115200 / 8 / N / 1）与 DTR+RTS。
            // wIndex 必须指向 CDC 控制接口号（micro:bit V2 上是 1，不是 0），否则 DTR 设错接口，串口不激活。
            val ctrlIndex = controlIf?.id ?: 0
            val baud = byteArrayOf(0x00, 0xC2.toByte(), 0x01, 0x00, 0x00, 0x00, 0x08, 0x00)
            runCatching {
                connection?.controlTransfer(0x21, 0x20, 0, ctrlIndex, baud, baud.size, 0) // SET_LINE_CODING
                connection?.controlTransfer(0x21, 0x22, 0x0003, ctrlIndex, null, 0, 0)   // SET_CONTROL_LINE_STATE: DTR+RTS
            }

            connected = true
            // 监听拔出（Android 8+ 需 RECEIVER_NOT_EXPORTED；本广播为系统定向）
            runCatching {
                val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ctx?.registerReceiver(detachReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    ctx?.registerReceiver(detachReceiver, filter)
                }
                detachRegistered = true
            }
            listener?.onState(ConnState(true, "已连接（USB）"))
            // 连上即触发固件开机动画（显示图案→蓝牙图标→开机音）；老固件回 ERR，向后兼容。
            runCatching { send("BOOT\n") }
            startRead()
        } catch (t: Throwable) {
            runCatching { connection?.close() }
            connection = null
            fail("打开 USB 串口失败：${t.message}")
        }
    }

    private fun startRead() {
        running.set(true)
        Thread {
            val buf = ByteArray(512)
            val lineBuffer = LineBuffer()
            var failStreak = 0
            while (running.get()) {
                val ep = inEp ?: break
                val r = runCatching { connection?.bulkTransfer(ep, buf, buf.size, 500) }.getOrNull() ?: -1
                if (r > 0) {
                    failStreak = 0
                    runCatching {
                        val lines = lineBuffer.append(String(buf, 0, r, Charsets.UTF_8))
                        lines.forEach { listener?.onData(it) }
                    }
                    continue
                }
                // 非正值必须区分「串口空闲」与「设备掉线」，判定逻辑见 evaluateRead
                val verdict = evaluateRead(r, failStreak)
                failStreak = verdict.failStreak
                if (verdict.disconnected) {
                    notifyDisconnected("micro:bit 已断开连接（USB 数据中断）")
                    break
                }
                // 节流：空闲时 bulkTransfer 本身已阻塞满 500ms，这里的睡眠只是兜底防忙等自旋
                runCatching { Thread.sleep(if (verdict.idle) 20 else 80) }
            }
        }.start()
    }

    override fun send(line: String): Boolean {
        val ep = outEp ?: return false
        if (!connected) return false
        val bytes = line.toByteArray(Charsets.UTF_8)
        // 分片发送（CDC 批量包上限 64 字节），确保长指令完整到达 micro:bit。
        var offset = 0
        while (offset < bytes.size) {
            val n = minOf(64, bytes.size - offset)
            val sent = runCatching {
                connection?.bulkTransfer(ep, bytes, offset, n, 500) ?: -1
            }.getOrDefault(-1)
            if (sent <= 0) return false
            offset += sent
        }
        return true
    }

    override fun isConnected(): Boolean = connected

    override fun disconnect() {
        val wasConnected = connected
        running.set(false)
        if (registered) {
            runCatching { ctx?.unregisterReceiver(permReceiver) }
            registered = false
        }
        if (detachRegistered) {
            runCatching { ctx?.unregisterReceiver(detachReceiver) }
            detachRegistered = false
        }
        runCatching { connection?.close() }
        connection = null
        inEp = null
        outEp = null
        connected = false
        if (wasConnected) notifyDisconnected("已断开连接")
    }

    /** 通知监听器连接已断开（幂等：仅此前处于已连接状态时才发）。 */
    private fun notifyDisconnected(msg: String) {
        if (!connected) return
        connected = false
        running.set(false)
        runCatching { listener?.onState(ConnState(false, msg)) }
    }

    private fun fail(msg: String) {
        if (connected) return
        listener?.onState(ConnState(false, msg))
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.lingyi.mbos.USB_PERMISSION"

        // ---------- bulkTransfer 非正值分类 ----------
        // UsbDeviceConnection.bulkTransfer 的返回值直接透传 Linux usbfs ioctl（USBDEVFS_BULK）
        // 的结果，即「读到的字节数」或「取负的 errno」：
        //   > 0      本次读到的字节数
        //   -110     500ms 内无数据（ETIMEDOUT）——设备仍在线，只是此刻没说话
        //   -19 等   设备已被移除（ENODEV / ESHUTDOWN / ENOENT / ENOTCONN）
        //   其它负值  端点停滞 / 总线抖动 / 参数错误（-EPIPE / -EPROTO / -EILSEQ / -EIO / -1…）
        //
        // 为什么「超时」与「掉线」必须分开：串口空闲是常态——固件只在收到指令或指针增量时
        // 才输出，静止的板子（死区内不上报 MOUSE）和没刷 MB-OS 助手脚本的裸 MicroPython
        // 板子（不识别 MOUSE:ON）都会数秒不吐一个字节。早期把任何非正值都当「读失败」，
        // 连续 6 次（约 3.5 秒）就把活着的连接误判成已断开：进入陀螺仪模式后约 3.5 秒，
        // 连接状态被刷新成「未连接」，而板子其实一直插着、连着（用户报障实锤）。
        // 内核文档明确：设备拔除时返回 -ENODEV 而非超时——「超时」恰是「设备仍在线」的
        // 正证据，因此空闲永不触发断开判定；真正的拔出由 ACTION_USB_DEVICE_DETACHED
        // 广播负责，这里的错误分类只是广播漏收时的兜底。

        internal const val READ_IDLE = 0
        internal const val READ_DEVICE_GONE = 1
        internal const val READ_TRANSIENT = 2

        /** 连续多少次「设备已移除」类错误才判定断开（拔出后内核立即返回，2 次足够且防抖）。 */
        internal const val DEVICE_GONE_LIMIT = 2

        /** 连续多少次其它错误才判定断开（拔出瞬间的总线抖动可能先报 -EPIPE 等，给足恢复余地）。 */
        internal const val TRANSIENT_FAIL_LIMIT = 20

        /** 把 bulkTransfer 的非正值归类：空闲超时 / 设备已移除 / 其它瞬时错误（>0 由调用方先行处理）。 */
        internal fun classifyReadResult(result: Int): Int = when (result) {
            0, -4, -11, -110 -> READ_IDLE            // 0 / EINTR / EAGAIN / ETIMEDOUT：本次无数据
            -2, -19, -107, -108 -> READ_DEVICE_GONE  // ENOENT / ENODEV / ENOTCONN / ESHUTDOWN：设备没了
            else -> READ_TRANSIENT                   // EPIPE / EPROTO / EILSEQ / EIO / EINVAL / -1…：保守累计
        }

        /**
         * 读线程单次判定：更新连续失败计数，并决定是否应断开。
         * 纯函数——单测据此锁定「空闲永不误判断开、掉线快速判定」。
         */
        internal fun evaluateRead(result: Int, failStreak: Int): ReadVerdict {
            if (result > 0) return ReadVerdict(0, idle = true, disconnected = false)
            val cls = classifyReadResult(result)
            if (cls == READ_IDLE) return ReadVerdict(0, idle = true, disconnected = false)
            val next = failStreak + 1
            val limit = if (cls == READ_DEVICE_GONE) DEVICE_GONE_LIMIT else TRANSIENT_FAIL_LIMIT
            return ReadVerdict(next, idle = false, disconnected = next >= limit)
        }
    }
}

/**
 * USB 读线程单次 bulkTransfer 结果的判定（见 [UsbConnection.evaluateRead]）。
 */
internal data class ReadVerdict(
    /** 更新后的连续失败计数（读到数据 / 空闲超时均归零）。 */
    val failStreak: Int,
    /** 本次是否为「串口空闲」——仅用于节流睡眠时长，不影响连接状态。 */
    val idle: Boolean,
    /** 是否应判定连接已断开。 */
    val disconnected: Boolean
)