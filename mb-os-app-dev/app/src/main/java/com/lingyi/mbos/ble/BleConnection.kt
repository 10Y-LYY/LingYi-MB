/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * 蓝牙 BLE 连接。
 * 关键设计：
 *  1. 优先从系统「已配对设备」直连 micro:bit，不依赖重新扫描（已配对的随机地址设备在 Android 扫描里常被隐藏）。
 *  2. 提供 connectByAddress()：由 UI 列出系统已配对设备，用户手动选中 micro:bit 直连，彻底绕开“搜不到”。
 *  3. 名字匹配去除冒号/短横/空格，兼容 "BBC micro:bit" / "micro:bit" / "micro:bit XXXX"。
 *  4. 服务发现需要时自动触发系统 PIN 配对，并在配对完成后重连服务。
 *  5. 全程带连接超时与清晰中文状态提示。
 */
class BleConnection : MicrobitConnection {

    override val type: String = "ble"

    private var ctx: Context? = null
    private var listener: MicrobitConnection.Listener? = null
    private var adapter: BluetoothAdapter? = null
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var rxChar: BluetoothGattCharacteristic? = null
    private var txChar: BluetoothGattCharacteristic? = null
    private val handler = Handler(Looper.getMainLooper())
    private var settled = false
    private var connectingAddr: String? = null
    private var serviceRetry = 0

    /** 当前连接目标地址（供「静默重连上次设备」记忆，见 ConnectionHub）。 */
    override val address: String?
        get() = connectingAddr

    /**
     * BLE 行缓冲：ATT MTU 默认仅 23 字节（载荷 20），固件的 INFO / READ 长响应
     * 会被拆成多个通知到达。不重组则版本识别、传感器读数、MOUSE 数据全部失效。
     */
    private val lineBuffer = LineBuffer()

    /** CCCD 写入完成后才认为「串口就绪」，避免一次性响应（如 INFO）在通知尚未使能时被丢。 */
    private var notifyReady = false

    companion object {
        private val NUS_SERVICE = uuid("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        private val NUS_RX = uuid("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        private val NUS_TX = uuid("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        private val MB_SERVICE = uuid("E95D93B0-251D-470A-A062-FA1922DFA9A8")
        private val MB_RX = uuid("E95D93B2-251D-470A-A062-FA1922DFA9A8")
        private val MB_TX = uuid("E95D93B1-251D-470A-A062-FA1922DFA9A8")
        private val CCC = uuid("00002902-0000-1000-8000-00805f9b34fb")
        private fun uuid(s: String) = java.util.UUID.fromString(s)

        /** 兼容 "BBC micro:bit" / "micro:bit" / "micro:bit XXXX" 等写法。 */
        private fun isMicrobit(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            val n = name.replace(":", "").replace("-", "").replace(" ", "").uppercase()
            return n.contains("MICROBIT")
        }

        /** 列出系统已配对设备（供 UI 手动选择，绕过扫描发现）。需要 BLUETOOTH_CONNECT 权限。 */
        fun bondedDevices(context: Context): List<BluetoothDevice> {
            if (Build.VERSION.SDK_INT >= 31 &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) return emptyList()
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val a = bm?.adapter ?: return emptyList()
            if (!a.isEnabled) return emptyList()
            return runCatching { a.bondedDevices?.toList() ?: emptyList() }.getOrDefault(emptyList())
        }
    }

    /** 监听系统配对状态：配对完成后重新发现服务。 */
    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            @Suppress("DEPRECATION")
            val dev = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
            if (dev.address != connectingAddr) return
            when (state) {
                BluetoothDevice.BOND_BONDED -> {
                    handler.removeCallbacks(connectTimeout)
                    listener?.onState(ConnState(false, "配对成功，正在建立串口…"))
                    handler.postDelayed({ runCatching { gatt?.discoverServices() } }, 300)
                }
                BluetoothDevice.BOND_NONE -> {
                    fail("配对失败或被取消。请在系统蓝牙中手动“取消配对”micro:bit 后重试，或确认 PIN 输入正确")
                }
            }
        }
    }

    override fun connect(context: Context, listener: MicrobitConnection.Listener) {
        this.ctx = context.applicationContext
        this.listener = listener
        this.settled = false
        this.serviceRetry = 0
        this.connectingAddr = null
        if (!prepare(context)) return

        // 1) 优先直连已配对设备（你的场景：系统里已经配对过）
        val bonded = runCatching { adapter?.bondedDevices?.filter { isMicrobit(it.name) } }.getOrNull()
        if (!bonded.isNullOrEmpty()) {
            listener.onState(ConnState(false, "已配对设备「${bonded.first().name ?: "micro:bit"}」连接中…"))
            connectGatt(bonded.first(), true)
            return
        }

        // 2) 否则扫描
        listener.onState(ConnState(false, "扫描 micro:bit 中…（也可从已配对列表手动选择）"))
        startScan()
    }

    /** 按系统蓝牙地址直连（手动从已配对列表选择时使用）。 */
    fun connectByAddress(context: Context, address: String, listener: MicrobitConnection.Listener) {
        this.ctx = context.applicationContext
        this.listener = listener
        this.settled = false
        this.serviceRetry = 0
        this.connectingAddr = null
        if (!prepare(context)) return
        val dev = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
            ?: return fail("找不到该蓝牙设备（$address）")
        listener.onState(ConnState(false, "正在连接所选设备…"))
        connectGatt(dev, true)
    }

    /** 初始化 adapter / 权限 / 配对监听，返回是否就绪。 */
    private fun prepare(context: Context): Boolean {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        adapter = bm?.adapter
        if (adapter == null) { fail("设备不支持蓝牙"); return false }
        if (!adapter!!.isEnabled) { fail("请先开启手机蓝牙"); return false }
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) { fail("缺少蓝牙连接权限，请在系统设置中授予「附近的设备」权限"); return false }
        runCatching {
            // 用 applicationContext 注册：与 disconnect()/fail() 的反注册上下文一致，
            // 避免 Activity 销毁后反注册静默失败导致 bondReceiver 持有 Activity 泄漏（v4.2）
            ContextCompat.registerReceiver(
                context.applicationContext, bondReceiver,
                IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        return true
    }

    private fun startScan() {
        scanner = adapter?.bluetoothLeScanner ?: return fail("无法启动蓝牙扫描")
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner?.startScan(emptyList(), settings, scanCallback)
        handler.postDelayed({
            if (!settled) {
                stopScan()
                // 扫描没找到：已配对设备常被 Android 隐藏，自动回退到已配对直连
                val bonded = runCatching { adapter?.bondedDevices?.filter { isMicrobit(it.name) } }.getOrNull()
                if (!bonded.isNullOrEmpty()) {
                    listener?.onState(ConnState(false, "扫描无果，改从已配对设备直连…"))
                    connectGatt(bonded.first(), true)
                } else {
                    fail("15 秒内未扫描到 micro:bit。已配对设备常被 Android 隐藏，请改用「从已配对设备选择」按钮直连；并确认 micro:bit 已刷 MicroPython 固件且在范围内")
                }
            }
        }, 15000)
    }

    private fun stopScan() {
        runCatching { scanner?.stopScan(scanCallback) }
        scanner = null
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val name = result.scanRecord?.deviceName ?: device.name ?: ""
            if (isMicrobit(name)) {
                stopScan()
                connectGatt(device, false)
            }
        }
    }

    private fun connectGatt(device: BluetoothDevice, autoConnect: Boolean) {
        connectingAddr = device.address
        listener?.onState(ConnState(false, "正在连接 micro:bit…"))
        handler.removeCallbacks(connectTimeout)
        handler.postDelayed(connectTimeout, 20000)
        runCatching {
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(ctx, autoConnect, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(ctx, autoConnect, gattCallback)
            }
        }.onFailure { fail("连接失败：${it.message}") }
    }

    private val connectTimeout = Runnable {
        if (!settled) {
            runCatching { gatt?.close() }
            gatt = null
            fail("连接超时，micro:bit 未响应。请确认：① 已上电且在范围内；② 没被其他设备连接；③ 已刷 MicroPython 固件。也可在系统蓝牙「取消配对」后重试")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            // 本回调运行在 Binder 线程：任何未预期异常都会直接崩掉进程
            // （表现为连接后闪退回引导页），因此总体兜底。
            runCatching {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt = g
                    handler.removeCallbacks(connectTimeout)
                    // 请求更大 MTU（247）：默认 23 字节会把固件的 INFO / READ 长响应拆得粉碎
                    runCatching { g.requestMtu(247) }
                    g.discoverServices()
                } else {
                    val wasSettled = settled
                    if (!settled) fail("与 micro:bit 的连接已断开")
                    settled = false
                    notifyReady = false
                    lineBuffer.reset()
                    runCatching { g.close() }
                    gatt = null
                    // 已建立过的连接意外断开（走出范围 / 断电）：必须通知 App 更新状态，
                    // 否则界面永远显示「已连接」，后续指令全部静默丢失。
                    if (wasSettled) {
                        runCatching { listener?.onState(ConnState(false, "与 micro:bit 的连接已断开")) }
                    } else {
                        // 尚未建立过连接：fail() 已给出原因
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            runCatching {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    findChars(g)
                    if (rxChar != null && txChar != null) {
                        enableNotify(g)
                    } else if (serviceRetry < 3) {
                        serviceRetry++
                        listener?.onState(ConnState(false, "串口服务未就绪，重试中…"))
                        handler.postDelayed({ runCatching { g.discoverServices() } }, 500)
                    } else {
                        fail("未找到串口服务。多半是 micro:bit 还没刷 MicroPython 固件——请打开 App「帮助 / 固件与帮助」复制脚本，用 Mu 编辑器刷入后再连")
                    }
                    return@runCatching
                }
                // 非成功：多半是需要加密 / 需要配对
                val dev = g.device
                if (dev.bondState != BluetoothDevice.BOND_BONDED) {
                    listener?.onState(
                        ConnState(false, "需要配对：请在系统弹窗输入 micro:bit LED 上显示的 6 位 PIN 码")
                    )
                    runCatching { dev.createBond() }
                } else if (serviceRetry < 3) {
                    serviceRetry++
                    handler.postDelayed({ runCatching { g.discoverServices() } }, 600)
                } else {
                    fail("服务发现失败。请在系统蓝牙「取消配对」micro:bit 后重试，并确保已刷 MicroPython 固件")
                }
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == txChar?.uuid) {
                val text = characteristic.value?.toString(Charsets.UTF_8) ?: return
                // 必须兜底：本回调运行在 Binder 线程，未捕获异常会直接崩掉进程
                // （表现为连接后一点功能就闪退回引导页）。与 USB 读线程保持一致。
                runCatching {
                    // 按行重组：一个通知可能只是半行（MTU 限制），一行也可能跨多个通知
                    val lines = lineBuffer.append(text)
                    lines.forEach { listener?.onData(it) }
                }
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            // 无响应写通常忽略
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            // CCCD 写入回调：成功才算「通知真正使能」，此时才对外广播已连接，
            // 避免 App 紧接着发出的 INFO 等一次性请求的响应在通知使能前被丢弃。
            // 写入失败也照常置就绪（降级），避免连接流程卡死。
            if (descriptor.uuid != CCC || descriptor.characteristic?.uuid != txChar?.uuid) return
            notifyReady = status == BluetoothGatt.GATT_SUCCESS
            if (!settled) {
                settled = true
                runCatching { listener?.onState(ConnState(true, "已连接（蓝牙）")) }
                // 连上即触发生开机动画（显示图案→蓝牙图标→开机音）。
                // 老固件不识别 BOOT 只会回 ERR，不影响连接，向后兼容。
                runCatching { send("BOOT\n") }
            }
        }
    }

    private fun findChars(g: BluetoothGatt) {
        val services = runCatching { g.services }.getOrNull() ?: return
        for (svc in services) {
            when (svc.uuid) {
                NUS_SERVICE -> {
                    rxChar = svc.getCharacteristic(NUS_RX)
                    txChar = svc.getCharacteristic(NUS_TX)
                }
                MB_SERVICE -> {
                    rxChar = svc.getCharacteristic(MB_RX)
                    txChar = svc.getCharacteristic(MB_TX)
                }
            }
        }
    }

    private fun enableNotify(g: BluetoothGatt) {
        val tx = txChar ?: return fail("串口特征缺失")
        g.setCharacteristicNotification(tx, true)
        val desc = tx.getDescriptor(CCC)
        if (desc != null) {
            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val ok = runCatching { g.writeDescriptor(desc) }.getOrDefault(false)
            if (ok) {
                // 等 onDescriptorWrite 成功回调再置就绪（见该回调）
                return
            }
        }
        // 无 CCCD 描述符或写入失败：仍按就绪处理，避免卡住（老固件/特殊实现）
        settled = true
        notifyReady = true
        runCatching { listener?.onState(ConnState(true, "已连接（蓝牙）")) }
    }

    override fun send(line: String): Boolean {
        val g = gatt ?: return false
        val rx = rxChar ?: return false
        if (!settled) return false
        return runCatching {
            // 优先带响应的写：连续「无响应写」在部分机型/蓝牙栈上会被静默丢弃，
            // 导致 MOUSE:ON 等关键指令根本没发出去（指针“连上了但不动”的元凶之一）。
            rx.writeType = if (rx.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            rx.value = line.toByteArray(Charsets.UTF_8)
            g.writeCharacteristic(rx)
        }.getOrDefault(false)
    }

    override fun isConnected(): Boolean = settled && gatt != null

    override fun disconnect() {
        val wasSettled = settled
        stopScan()
        handler.removeCallbacks(connectTimeout)
        runCatching { ctx?.unregisterReceiver(bondReceiver) }
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        rxChar = null
        txChar = null
        settled = false
        notifyReady = false
        connectingAddr = null
        lineBuffer.reset()
        if (wasSettled) {
            runCatching { listener?.onState(ConnState(false, "已断开连接")) }
        }
    }

    private fun fail(msg: String) {
        if (settled) return
        settled = true
        runCatching { ctx?.unregisterReceiver(bondReceiver) }
        listener?.onState(ConnState(false, msg))
    }
}