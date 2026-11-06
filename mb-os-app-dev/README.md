# 灵逸 MB（Lingyi MB）

一款为 **micro:bit V2** 打造的轻量第三方控制台 App（Android）。
用一台安卓手机即可完成：刷写 MicroPython 固件 → USB / 蓝牙连接 → 实时控制与读数。

由 **10Y-灵逸云科技** 出品。

## 功能特性

- **免电脑刷机**：OTG 数据线直连手机，一键把内置 MicroPython 固件写入 micro:bit；也支持导出固件手动刷。
- **双连接方式**：USB 串口（CDC，直连最稳）/ 蓝牙 BLE（需另刷带蓝牙固件）。
- **实时读数（预览模式）**：温度、光线、加速度 X/Y/Z、指南针朝向、手势、按键 A/B 每秒轮询刷新。
- **丰富控制指令**：滚动文字、LED 5×5 画板、单点绘制、表情图标、音调、内置旋律、显示数字、屏幕亮度、指南针、清屏等。
- **一键演示**：按顺序自动播放文字 / 图案 / 旋律，快速验证整条链路。
- **自定义刷入**：写自己的 MicroPython 代码、导入 `.py` / `.hex`，或一键刷入内置小游戏（飞机大战 / 贪吃蛇 / 打砖块）。
- **真实检查更新**：启动时联网拉取线上 `version.json` 与本地版本号比较，仅在新版本发布时提示。
- **编程模块**：直达 MakeCode 官网自己编程（积木 / Python），下载 `.hex` 后一键刷入 micro:bit。
- **陀螺仪指针（无线触摸笔）**：独家功能——通过 micro:bit 陀螺仪把开发板变成无线鼠标：倾斜移动指针，按钮 A 单击，按钮 B 按住拖动（再按抬起）；灵敏度 0.4×–3.0× 可调，支持一键以当前姿态重新校准。
- **新固件发布提示**：连接 micro:bit 后自动回读板载固件版本（INFO 指令的 `MB-OS v` 字段），与 App 内置版本比对；检测到旧版时引导页刷机卡自动切换为更新提示，一键重新刷入；刷入自定义内容（游戏 / 自有代码）后不再误提示。
- **报错日志系统**：全 App 统一日志（分级记录、按天滚动、单文件 512KB / 总量 2MB 自动清理 + 内存缓冲 400 行）；未捕获异常在杀进程前把完整现场（异常链 + 堆栈 + 崩溃前日志尾部 + 设备信息）落盘到 `crashes/`；下次启动引导页弹出「上次发生了崩溃」提醒，可一键反馈。
- **一键反馈到邮箱**：关于页「错误日志与反馈」——打包 zip（附 device-info 清单）自动唤起邮件客户端发送到 `lyy10y_log@126.com`（收件人已预填），也可分享到微信 / QQ / 蓝牙等；日志只含排错信息，不含任何个人身份信息。
- **开发者调试模式**：引导页 / 关于页连按版本号 7 次解锁；未连接也可进入，集中调试指令协议、传感器数据模拟、设备诊断、协议自检、模拟连接状态、页面直达跳转等。
- **micro:bit V2 模拟器（开发者模式）**：开发者模式开启系统悬浮窗模拟器——实时 LED 点阵 + A/B 按键 + 摇晃 + 四向虚拟方向键 + 手势注入，完整支持固件全指令协议与周期上报（可开关）；真机连接自动让位，不接真板即可调试 App 全部功能。
- **主题自定义**：青绿 / 鎏金 / 黛蓝 / 绯红 / 松林五套配色 + 「安卓原生 Material 3 官方取色」（默认，Google M3 官方基线色板）；Android 12+ 支持 Material You 动态取色（跟随壁纸）；浅色 / 深色 / 跟随系统三模式即切即换。
- **平板与折叠屏适配**：内容宽度 840dp 封顶（宽屏居中、两侧留白），sw840dp expanded 尺寸档（更大 LED 点阵 / 控制台图 / 边距）；页面边距、大图高度、LED 格子按屏宽分档（≥600dp）；功能列表宽屏（含折叠屏内屏 / 分屏）自动双列；已声明 `resizeableActivity`，折叠 / 分屏 / 自由窗口均正常。
- **全界面 Material 3 + 过渡动画**：控件一律 M3 规范（统一圆角、状态层、涟漪）；页面转场 / 控件变化 / 悬浮窗 / LED 面板均有平滑过渡动画，设置中可一键开关；连接等待、检查更新、读数刷新、刷机进度全部使用 M3 加载指示器。
- **设置页**（控制台菜单 →「设置」）：外观（主题 / 动画效果）、连接（默认连接方式、读取间隔、屏幕常亮）、指针（默认灵敏度）、通用（触感反馈、检查更新、恢复默认）四节。

## 设计规范（自 v2.9 起强制执行）

所有界面控件一律采用**浓系安卓原生 Material 3** 风格，今后所有更新必须沿用，禁止回退：

- **主题**：`Theme.Material3.Light/Dark.NoActionBar` 动态深浅双主题（`values-night`），全部颜色走 M3 令牌
  （`colorPrimary` / `colorSurface` / `colorSurfaceContainerLow/High/Highest/Bright/Dim` / `colorOutlineVariant` 等），
  禁止在布局里硬编码颜色与字号。
- **组件**：`MaterialToolbar`、`MaterialCardView`（outlined 描边 + tonal 背景）、
  `MaterialButton`（filled / tonal / outlined / text）、`MaterialSwitch`、`Slider`、`Chip`、`Snackbar`。
- **可读性**：描述文字用 `textAppearanceBodyMedium` 及以上；列表行高 ≥ 72dp、图标 ≥ 40dp；
  强调用字重与字阶，而非另造颜色。
- **图标**：启动图标用自适应图标（`mipmap-anydpi-v26` 双层 + PNG 兜底）；页内展示用透明底 `ic_app_logo`。

> 因 Android 不允许第三方 App 直接控制系统鼠标指针，陀螺仪指针采用无障碍服务「手势模拟」方案实现
> （API 30+ 使用 `willContinue` 连续手势保证拖动连续，低版本自动降级为离散滑动），
> 对点击、滑动、拖拽、绘图等场景完全等效。首次使用需在系统设置 → 无障碍中启用「灵逸 MB 指针服务」。

## 构建

```bash
# 环境要求：JDK 17+、Android SDK（compileSdk 34）、Gradle 8.x
gradle assembleDebug          # 输出 app/build/outputs/apk/debug/app-debug.apk
gradle testDebugUnitTest      # 运行单元测试（Robolectric）
```

最低支持 Android 7.0（API 24），目标 Android 14（API 34）。

## 目录结构

```
app/src/main/java/com/lingyi/mbos/
├── GuideActivity.kt        # 入口：引导页（首次刷机引导 / 连接 / 更新提示）
├── MainActivity.kt         # 控制台（功能列表、指令下发）
├── PreviewActivity.kt      # 预览模式（实时读数）
├── CustomFlashActivity.kt  # 自定义刷入（代码 / .hex / 小游戏）
├── MakeCodeActivity.kt     # 编程模块（MakeCode 官网 + .hex 一键刷入）
├── PointerModeActivity.kt  # 陀螺仪指针（无线触摸笔）模式
├── PointerAccessibilityService.kt  # 指针的无障碍手势服务
├── AboutActivity.kt        # 关于页（检查更新、开源许可证）
├── DeveloperActivity.kt    # 开发者调试
├── LicenseActivity.kt      # 开源许可证与第三方鸣谢
├── ble/                    # 连接层：USB（CDC-ACM）/ 蓝牙 BLE + 进程级连接单例
├── ui/                     # 指令协议、固件脚本、小游戏脚本、开发板绘制 View
└── util/                   # SharedPreferences、主题、更新检查
app/src/main/assets/firmware/   # 内置 MicroPython 合并固件与三款小游戏 HEX
```

## 指令协议

通过串口（Nordic UART / USB CDC）逐行发送文本指令，每行以 `\n` 结尾：
`SCROLL:` / `SHOW:` / `PLOT:` / `TONE:` / `MELODY:` / `NUMBER:` / `BRIGHT:` /
`COMPASS` / `CLEAR` / `READ` / `INFO` / `MOUSE:ON|OFF|CAL`。
固件在指针模式下以约 50Hz 上报 `MOUSE:dx,dy,a,b`（倾斜增量 + 两个按键）。
实现见 `ui/Protocol.kt`，开发板端配套脚本见 `ui/FirmwareScript.kt`（与内置 HEX 中的 main.py 一致）。

## 许可证

本项目源代码采用 [MIT License](LICENSE) 开源。
第三方组件与素材（MicroPython、AndroidX、Material Components、micro:bit 官方插图等）
按其各自许可证使用，详见 [NOTICE.md](NOTICE.md)。

App 内查看路径：**关于 → 开源许可证**。

## 免责声明

本工具仅用于你本人拥有与控制的设备。「micro:bit」名称与标识为
micro:bit Educational Foundation 的商标，本项目与其无隶属关系。
