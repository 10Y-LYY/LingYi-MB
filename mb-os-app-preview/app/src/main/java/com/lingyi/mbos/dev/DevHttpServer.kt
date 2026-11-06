/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.dev

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.lingyi.mbos.ble.ConnectionHub
import com.lingyi.mbos.ui.Protocol
import com.lingyi.mbos.util.Prefs
import java.net.ServerSocket
import java.net.Socket

/**
 * 开发者测试版专属：本地 HTTP 接口（127.0.0.1）。
 *
 * 作为对外开发者接口的「第二种调用方式」——除了 ContentProvider，开发者也可以
 * 用 `curl` 或任意语言通过 HTTP 调用，例如：
 *   curl "http://127.0.0.1:8765/send?cmd=SCROLL:hello"
 *   curl "http://127.0.0.1:8765/status"
 * 返回 JSON。同样受「允许外部应用控制开发板」开关保护。
 *
 * 仅 devtest / preview flavor 注册（manifest 中 android:enabled=@bool/devtest_only）。
 */
class DevHttpServer : Service() {

    @Volatile
    var port: Int = PORT
        private set

    @Volatile
    var isUp: Boolean = false
        private set

    private var server: ServerSocket? = null
    private var loop: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startServer()
        return START_STICKY
    }

    @Synchronized
    private fun startServer() {
        if (server != null) return
        loop = Thread({
            try {
                server = ServerSocket(port, 0, java.net.InetAddress.getByName("127.0.0.1"))
                isUp = true
                while (!Thread.currentThread().isInterrupted) {
                    val client = server?.accept() ?: break
                    handle(client)
                }
            } catch (e: Exception) {
                isUp = false
            } finally {
                isUp = false
            }
        }, "dev-http").also { it.start() }
    }

    private fun handle(socket: Socket) {
        try {
            val reader = socket.getInputStream().bufferedReader()
            val requestLine = reader.readLine() ?: return
            val rawPath = requestLine.split(" ").getOrNull(1) ?: "/status"
            val uri = android.net.Uri.parse(rawPath)
            val method = uri.lastPathSegment ?: "status"
            val cmd = uri.getQueryParameter("cmd") ?: ""
            val json = when (method) {
                "send" -> {
                    if (!Prefs.isExtApiEnabled(this)) """{"ok":false,"reason":"外部接口未开启"}"""
                    else if (!ConnectionHub.isConnected()) """{"ok":false,"reason":"未连接开发板"}"""
                    else {
                        val ok = ConnectionHub.send(Protocol.custom(cmd))
                        """{"ok":$ok,"sent":"${cmd.replace("\\", "\\\\").replace("\"", "\\\"")}"}"""
                    }
                }
                else -> """{"ok":true,"connected":${ConnectionHub.isConnected()},"channel":"dev"}"""
            }
            val body = json.toByteArray(Charsets.UTF_8)
            socket.getOutputStream().use { out ->
                out.write("HTTP/1.1 200 OK\r\n".toByteArray())
                out.write("Content-Type: application/json; charset=utf-8\r\n".toByteArray())
                out.write("Content-Length: ${body.size}\r\n".toByteArray())
                out.write("Connection: close\r\n\r\n".toByteArray())
                out.write(body)
                out.flush()
            }
        } catch (_: Exception) {
        } finally {
            runCatching { socket.close() }
        }
    }

    override fun onDestroy() {
        runCatching { server?.close() }
        server = null
        isUp = false
        super.onDestroy()
    }

    companion object {
        /** 本地 HTTP 接口监听端口（仅 127.0.0.1 回环，外部不可达）。 */
        const val PORT = 8765

        /** 当前是否已起服务（供 UI 反映状态）。 */
        @Volatile
        var running: Boolean = false
            private set

        fun start(ctx: Context) {
            if (running) return
            ctx.startService(Intent(ctx, DevHttpServer::class.java))
            running = true
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, DevHttpServer::class.java))
            running = false
        }
    }
}
