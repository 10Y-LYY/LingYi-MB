/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

/**
 * 配套固件（MicroPython）源码，与 App 内置 assets/firmware 目录下合并 HEX 中的 main.py 一致。
 * 通过 USB 串口（uart）/ 蓝牙接收《灵逸 MB》的指令，驱动 LED / 喇叭 / 传感器，
 * 并支持 MOUSE 陀螺仪指针模式（把 micro:bit 变成无线触摸笔）。
 * 说明：此字符串仅用于「固件与帮助」展示与 PC 拷贝；真正刷入设备的是 App 内置合并固件。
 */
object FirmwareScript {

    /** MB-OS 助手固件版本号（与 main.py 中 MBOS_VERSION 保持一致）。 */
    const val VERSION = "4.0.0"

    val script: String = """
from microbit import *
import music
import os

# 《灵逸 MB》配套固件脚本（MicroPython）
# 刷入 micro:bit V2 后，App 即可通过 USB 串口 / 蓝牙下发指令。
# 兼容协议：SCROLL / NUMBER / SHOW / PLOT / TONE / MELODY / BRIGHT /
#           COMPASS / CLEAR / READ / INFO / VERSION / MOUSE（陀螺仪指针）
MBOS_VERSION = "4.0.0"

# Explicitly bring up the USB serial channel. On micro:bit V2 the uart module
# defaults to the internal USB-UART, but initialising it explicitly guarantees
# the console/serial path is ready regardless of firmware quirks.
try:
    uart.init(baudrate=115200)
except Exception:
    pass


def _melody(name):
    tune = getattr(music, name, None)
    if tune is None:
        uart.write("ERR:MELODY\n")
        return
    music.play(tune)
    uart.write("OK:MELODY:{}\n".format(name))


def _gesture():
    for g in ("shake", "tap", "logo up", "face down", "freefall"):
        if accelerometer.was_gesture(g):
            return g.replace(" ", "")
    return "none"


# ============ 陀螺仪指针（无线触摸笔）模式 ============
# App 发送 MOUSE:ON 后，固件以约 50Hz 持续上报倾斜增量与按键状态：
#   MOUSE:dx,dy,a,b   （dx/dy 为像素增量，a/b 为按键 A/B 是否按下）
# MOUSE:OFF 退出；MOUSE:CAL 以当前姿态为新的中心（防漂移）。
mouse_on = False
mouse_cx = 0
mouse_cy = 0
mouse_last_a = 0
mouse_last_b = 0
mouse_last_send = 0

# 死区（mg）：小于该幅度的倾斜视为静止抖动，不上报
MOUSE_DEADZONE = 60
# 缩放：倾斜多少 mg 折算成 1 像素增量
MOUSE_SCALE = 48
# 最小上报间隔（ms）
MOUSE_INTERVAL = 20


def _mouse_calibrate():
    global mouse_cx, mouse_cy
    mouse_cx = accelerometer.get_x()
    mouse_cy = accelerometer.get_y()


def _mouse_tick():
    global mouse_last_a, mouse_last_b, mouse_last_send
    if not mouse_on:
        return
    x = accelerometer.get_x()
    y = accelerometer.get_y()
    dx = x - mouse_cx
    dy = y - mouse_cy
    if -MOUSE_DEADZONE < dx < MOUSE_DEADZONE:
        dx = 0
    if -MOUSE_DEADZONE < dy < MOUSE_DEADZONE:
        dy = 0
    dx = int(dx / MOUSE_SCALE)
    dy = int(dy / MOUSE_SCALE)
    a = int(button_a.is_pressed())
    b = int(button_b.is_pressed())
    now = running_time()
    moved = dx != 0 or dy != 0
    btn_changed = a != mouse_last_a or b != mouse_last_b
    if (moved or btn_changed) and now - mouse_last_send >= MOUSE_INTERVAL:
        uart.write("MOUSE:{},{},{},{}\n".format(dx, dy, a, b))
        mouse_last_send = now
        mouse_last_a = a
        mouse_last_b = b


def parse(line):
    global mouse_on, mouse_last_a, mouse_last_b
    line = line.strip()
    if not line:
        return
    if line.startswith("SCROLL:"):
        display.scroll(line[7:])
    elif line.startswith("NUMBER:"):
        display.scroll(line[7:])
    elif line.startswith("SHOW:"):
        rows = line[5:].split(",")
        img = Image(5, 5)
        for y in range(5):
            r = rows[y] if y < len(rows) else "00000"
            for x in range(5):
                if x < len(r) and r[x] == "1":
                    img.set_pixel(x, y, 9)
        display.show(img)
    elif line.startswith("PLOT:"):
        _, rest = line.split(":", 1)
        x, y, s = rest.split(",")
        display.set_pixel(int(x), int(y), 9 if int(s) else 0)
    elif line.startswith("TONE:"):
        _, rest = line.split(":", 1)
        f, d = rest.split(",")
        music.pitch(int(f), int(d))
    elif line.startswith("MELODY:"):
        _melody(line[7:].strip().upper())
    elif line.startswith("BRIGHT:"):
        try:
            display.set_brightness(int(line[7:]))
            uart.write("OK:BRIGHT\n")
        except Exception:
            uart.write("ERR:BRIGHT\n")
    elif line == "CLEAR":
        display.clear()
    elif line == "READ":
        try:
            h = compass.heading()
        except Exception:
            h = -1
        uart.write("A:{}|B:{}|T:{}|L:{}|X:{}|Y:{}|Z:{}|C:{}|G:{}\n".format(
            int(button_a.is_pressed()),
            int(button_b.is_pressed()),
            temperature(),
            display.read_light_level(),
            accelerometer.get_x(),
            accelerometer.get_y(),
            accelerometer.get_z(),
            h,
            _gesture()))
    elif line == "INFO":
        try:
            rel = os.uname().release
        except Exception:
            rel = "unknown"
        uart.write("INFO:MicroPython {}|MB-OS v{}|micro:bit V2\n".format(rel, MBOS_VERSION))
    elif line == "MOUSE:ON":
        mouse_on = True
        _mouse_calibrate()
        mouse_last_a = 0
        mouse_last_b = 0
        uart.write("OK:MOUSE:ON\n")
    elif line == "MOUSE:OFF":
        mouse_on = False
        uart.write("OK:MOUSE:OFF\n")
    elif line == "MOUSE:CAL":
        _mouse_calibrate()
        uart.write("OK:MOUSE:CAL\n")
    else:
        uart.write("ERR:{}\n".format(line))


# Main loop: accumulate bytes in a buffer and parse line-by-line on '\n'.
# This avoids uart.readline() blocking when a line is not yet complete, and
# the try/except keeps the script alive if a stray byte (e.g. 0x03) raises
# an exception -- otherwise the whole helper would silently stop responding.
recv = b""

while True:
    try:
        if uart.any():
            chunk = uart.read()
            if chunk:
                recv += chunk
                while b"\n" in recv:
                    raw, recv = recv.split(b"\n", 1)
                    parse(raw.decode("utf-8", "ignore").strip())
        else:
            # 指针模式下持续上报倾斜增量；否则空闲节流
            _mouse_tick()
            sleep(10)
    except Exception:
        try:
            uart.write("ERR:LOOP\n")
        except Exception:
            pass
        recv = b""
        sleep(50)
""".trimIndent()
}
