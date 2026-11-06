# 第三方组件与素材鸣谢（NOTICE）

本项目**源代码**采用 [MIT License](LICENSE) 开源。
以下第三方组件与素材**不属于** MIT 覆盖范围，按各自许可证使用：

## 1. MicroPython — MIT License

App 内置 `assets/firmware/` 目录下的合并固件基于 MicroPython 构建，
配套助手脚本（见 `app/src/main/java/com/lingyi/mbos/ui/FirmwareScript.kt`）
运行于 MicroPython 之上。

- Copyright (c) 2013–2023 Damien P. George
- https://micropython.org

## 2. AndroidX / Jetpack / Material Components for Android — Apache License 2.0

界面与基础库依赖：

- `androidx.core:core-ktx`
- `androidx.appcompat:appcompat`
- `com.google.android.material:material`
- `androidx.constraintlayout` / `androidx.recyclerview` / `androidx.viewpager2`
- `androidx.lifecycle:lifecycle-*`

- Copyright (c) The Android Open Source Project
- https://developer.android.com/jetpack

## 3. Kotlin 标准库 — Apache License 2.0

- Copyright (c) JetBrains s.r.o.
- https://kotlinlang.org

## 4. 测试依赖

- JUnit 4 — Eclipse Public License 1.0
- Robolectric — MIT License
- Mockito — MIT License

## 5. micro:bit V2 官方插图 — CC BY-NC-SA 4.0

`app/src/main/res/drawable-nodpi/microbit_v2.png` 为 micro:bit 教育基金会
官方绘制图，采用 **CC BY-NC-SA 4.0**（署名-非商业性使用-相同方式共享）许可。

- Copyright (c) micro:bit Educational Foundation
- 该图片**不属于** MIT 覆盖范围：再分发时须保留署名，且**不得用于商业用途**。
- 如需完全 MIT 化的分发，请自行绘制并替换该图片。

## 商标说明

「micro:bit」名称与标识为 micro:bit Educational Foundation 的商标。
本项目为其第三方客户端，与 micro:bit Educational Foundation **无隶属关系**。
