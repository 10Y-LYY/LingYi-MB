/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import com.lingyi.mbos.R

/**
 * 预设小游戏：运行在 micro:bit V2 的 5×5 LED 上的点阵游戏（MicroPython）。
 * 通过「自定义刷入」一键刷进开发板后自动运行，用按钮 A / B 操控。
 * 注意：这些是独立游戏脚本（自带循环），刷入后开发板进入游戏，
 * App 的读数模式不再响应，直到重新刷写固件/普通 main.py。
 */
data class GamePreset(
    val id: String,
    val name: String,
    val desc: String,
    val iconRes: Int,
    val code: String
)

object MicrobitGames {
    val presets: List<GamePreset> = listOf(
        GamePreset("plane", "飞机大战", "自动开火·A/B 移动", R.drawable.ic_plane, PLANE),
        GamePreset("snake", "贪吃蛇", "A 左转·B 右转", R.drawable.ic_snake, SNAKE),
        GamePreset("bricks", "打砖块", "A/B 移动接球", R.drawable.ic_bricks, BRICKS),
    )
}

/** 飞机大战：底部飞机随 A/B 左右移动并自动开火，击落顶部下落的敌机。 */
private val PLANE = """
# 飞机大战 —— 刷入 micro:bit 后自动运行
# 操作：按 A 向左移动，按 B 向右移动；飞机会自动开火。
# 目标：击落从顶部不断下落的敌机，别让它们撞到你（你停在底部）。
from microbit import *
import random

px = 2
bullets = []
enemies = []
score = 0
lives = 3
frame = 0
spawn = 0
cooldown = 0


def step():
    global px, score, lives, frame, spawn, cooldown
    frame += 1
    if button_a.was_pressed():
        px = max(0, px - 1)
    if button_b.was_pressed():
        px = min(4, px + 1)
    cooldown -= 1
    if cooldown <= 0:
        bullets.append([px, 3])
        cooldown = 2
    nb = []
    for b in bullets:
        b[1] -= 1
        if b[1] >= 0:
            nb.append(b)
    bullets = nb
    if frame % 3 == 0:
        ne = []
        for e in enemies:
            e[1] += 1
            if e[1] > 4:
                if e[0] == px:
                    lives -= 1
            else:
                ne.append(e)
        enemies = ne
        spawn += 1
        if spawn >= 2:
            enemies.append([random.randint(0, 4), 0])
            spawn = 0
    for e in enemies[:]:
        for b in bullets[:]:
            if e[0] == b[0] and e[1] == b[1]:
                enemies.remove(e)
                bullets.remove(b)
                score += 1
                break
    for e in enemies[:]:
        if e[1] == 4 and e[0] == px:
            enemies.remove(e)
            lives -= 1
    display.clear()
    display.set_pixel(px, 4, 9)
    for b in bullets:
        display.set_pixel(b[0], b[1], 5)
    for e in enemies:
        display.set_pixel(e[0], e[1], 9)
    sleep(120)


while lives > 0:
    step()
display.scroll("GAME OVER " + str(score))
""".trimIndent()

/** 贪吃蛇：A 向左转、B 向右转，吃亮点变长，撞墙或撞自己即结束。 */
private val SNAKE = """
# 贪吃蛇 —— 刷入 micro:bit 后自动运行
# 操作：按 A 向左转，按 B 向右转。吃到亮点会变长，撞墙或撞自己就结束。
from microbit import *
import random

snake = [[2, 2]]
dx, dy = 0, 1
food = [0, 0]
score = 0
alive = True


def new_food():
    while True:
        f = [random.randint(0, 4), random.randint(0, 4)]
        if f not in snake:
            return f


food = new_food()
while alive:
    if button_a.was_pressed():
        dx, dy = -dy, dx
    if button_b.was_pressed():
        dx, dy = dy, -dx
    head = [snake[0][0] + dx, snake[0][1] + dy]
    if head[0] < 0 or head[0] > 4 or head[1] < 0 or head[1] > 4 or head in snake:
        alive = False
        break
    snake.insert(0, head)
    if head == food:
        score += 1
        food = new_food()
    else:
        snake.pop()
    display.clear()
    for s in snake:
        display.set_pixel(s[0], s[1], 9)
    display.set_pixel(food[0], food[1], 5)
    sleep(400)
display.scroll("OVER " + str(score))
""".trimIndent()

/** 打砖块：A/B 移动底部挡板接住小球，打碎顶部所有砖块即过关。 */
private val BRICKS = """
# 打砖块 —— 刷入 micro:bit 后自动运行
# 操作：按 A 左移挡板，按 B 右移挡板。接住小球，打碎顶部所有砖块即过关。
from microbit import *

px = 2
ball = [2, 3]
vx, vy = 1, -1
bricks = [[x, 0] for x in range(5)]
lives = 3

while lives > 0 and bricks:
    if button_a.was_pressed():
        px = max(0, px - 1)
    if button_b.was_pressed():
        px = min(4, px + 1)
    ball[0] += vx
    ball[1] += vy
    if ball[0] < 0:
        ball[0] = 0
        vx = -vx
    if ball[0] > 4:
        ball[0] = 4
        vx = -vx
    if ball[1] < 0:
        ball[1] = 0
        vy = -vy
    if ball[1] >= 4:
        if abs(ball[0] - px) <= 1:
            ball[1] = 3
            vy = -vy
        else:
            lives -= 1
            ball = [2, 3]
            vx, vy = 1, -1
    for br in bricks[:]:
        if br[0] == ball[0] and br[1] == ball[1]:
            bricks.remove(br)
            vy = -vy
            break
    display.clear()
    display.set_pixel(px, 4, 9)
    if px - 1 >= 0:
        display.set_pixel(px - 1, 4, 3)
    if px + 1 <= 4:
        display.set_pixel(px + 1, 4, 3)
    for br in bricks:
        display.set_pixel(br[0], br[1], 7)
    display.set_pixel(ball[0], ball[1], 9)
    sleep(150)

if bricks:
    display.scroll("OVER " + str(lives))
else:
    display.scroll("WIN")
""".trimIndent()