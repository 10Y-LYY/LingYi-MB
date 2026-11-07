<div align="center">

<h1>灵逸 MB · LingYi-MB</h1>

<p><strong>一台安卓手机，玩转 micro:bit V2</strong></p>

<p>
<a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-36685D.svg" alt="License: MIT"></a>
<a href="#硬件准备"><img src="https://img.shields.io/badge/Platform-Android%207.0%2B-28554B.svg" alt="Platform"></a>
<a href="#技术栈"><img src="https://img.shields.io/badge/Kotlin-100%25-7F52FF.svg" alt="Kotlin"></a>
<a href="#从源码构建"><img src="https://img.shields.io/badge/minSdk-24-5E6E68.svg" alt="minSdk"></a>
<a href="#从源码构建"><img src="https://img.shields.io/badge/targetSdk-34-5E6E68.svg" alt="targetSdk"></a>
<img src="https://img.shields.io/badge/Repo-10Y--LYY%2FLingYi--MB-1E2A26.svg" alt="Repo">
</p>

<p>
<a href="#稳定版-stable"><img src="https://img.shields.io/badge/Stable-4.2.7%20%7C%20code%2053-36685D.svg" alt="Stable"></a>
<a href="#预览版-preview"><img src="https://img.shields.io/badge/Preview-4.2.7--preview%20%7C%20code%2060-D2E7E0.svg" alt="Preview"></a>
<a href="#开发者测试版-devtest"><img src="https://img.shields.io/badge/DevTest-4.2.7--dev%20%7C%20code%2053-EDF2EE.svg" alt="DevTest"></a>
</p>

<p>
<a href="#下载与安装">下载与安装</a> ·
<a href="#三个版本说明">三个版本说明</a> ·
<a href="#快速开始">快速开始</a> ·
<a href="#从源码构建">从源码构建</a> ·
<a href="#常见问题">常见问题</a>
</p>

</div>

---

灵逸 MB 是一款为 **micro:bit V2** 打造的轻量第三方控制台 App（Android）。用一台安卓手机即可完成 **刷写 MicroPython 固件 → USB / 蓝牙连接 → 实时控制与读数**，全程不需要电脑。

由 **10Y-灵逸云科技** 出品，源代码以 [MIT License](LICENSE) 开源。

> **本项目与 micro:bit Educational Foundation 无隶属关系。**「micro:bit」名称与标识为其所有方商标，仅用于说明兼容性。

---

## 目录

- [项目简介](#项目简介)
- [核心特性](#核心特性)
- [界面与适配](#界面与适配)
  - [二级页过渡与排版统一](#二级页过渡与排版统一code-60)
  - [设置里切换界面风格](#设置里切换界面风格)
- [三个版本说明](#三个版本说明)
  - [稳定版 stable](#稳定版-stable)
  - [预览版 preview](#预览版-preview)
  - [开发者测试版 devtest](#开发者测试版-devtest)
  - [并装与数据隔离](#并装与数据隔离)
- [下载与安装](#下载与安装)
- [硬件准备](#硬件准备)
- [快速开始](#快速开始)
- [内置固件](#内置固件)
- [指令协议](#指令协议)
- [从源码构建](#从源码构建)
  - [发版流程（每下次都这样）](#发版流程每下次都这样)
- [目录结构](#目录结构)
- [权限说明](#权限说明)
- [开发者接口](#开发者接口)
- [自动更新机制](#自动更新机制)
- [技术栈](#技术栈)
- [常见问题](#常见问题)
- [贡献指南](#贡献指南)
- [开源许可与鸣谢](#开源许可与鸣谢)
- [免责声明](#免责声明)
- [联系方式](#联系方式)
- [开发计划](#开发计划)

---

## 项目简介

micro:bit 官方流程通常需要一台电脑：在 MakeCode 里编程、拖拽 `.hex` 到 `MICROBIT` 盘符、再用串口工具调试。灵逸 MB 把这条链路整个搬到了手机上：

| 环节 | 传统方式 | 灵逸 MB |
| :--- | :--- | :--- |
| 刷写固件 | 电脑 + 数据线 + 拖拽文件 | 手机 OTG 直连，App 内一键写入 |
| 编程 | 电脑浏览器打开 MakeCode | App 内直达 MakeCode，下载 `.hex` 后一键刷入 |
| 连接 | 电脑串口工具 | USB CDC 串口 或 蓝牙 BLE |
| 控制与读数 | 手写脚本 | 图形化工具台，实时读数 |
| 供电 | 电脑 USB | 手机 OTG / 板载电池 |

App 内置 **MB-OS v4.0.0** MicroPython 合并固件，烧录后开发板即可理解 App 下发的文本指令协议。工具台提供 **7 大分类、77 个登记工具条目**（见 [`FeatureCatalog.kt`](app/src/main/java/com/lingyi/mbos/ui/FeatureCatalog.kt)），加上连接管理、设置、开发者工具箱等系统级入口，App 内可用功能入口约 **88 项**。[^1]

---

## 核心特性

### 连接与刷写

- **免电脑刷机**：OTG 数据线直连手机，一键把内置 MicroPython 固件写入 micro:bit；也支持导出固件手动刷。
- **双连接方式**：USB 串口（CDC-ACM，直连最稳）/ 蓝牙 BLE（需另刷带蓝牙固件）。
- **编程猫 BLE 兼容**：内置固件 v4.0.0 起支持标准 micro:bit BLE 服务，可与编程猫 App 双向通信。
- **自动重连与版本回读**：连接后自动回读板载固件版本（`INFO` / `VERSION` 指令），检测到旧固件时引导页直接提示一键更新，且刷入自定义内容后不再误报。

### 控制与读数

- **实时读数**：温度、光线、加速度 X/Y/Z、指南针朝向、手势、按键 A/B 每秒轮询刷新。
- **显示与声音**：滚动文字、LED 5×5 画板、单点绘制、表情图标、内置旋律、音调、数字显示、亮度调节、清屏。
- **一键演示**：按顺序自动播放文字 / 图案 / 旋律，快速验证整条链路。
- **自定义刷入**：写自己的 MicroPython 代码、导入 `.py` / `.hex`，或一键刷入内置小游戏（飞机大战 / 贪吃蛇 / 打砖块）。
- **陀螺仪指针（无线触摸笔）**：倾斜开发板移动指针，按钮 A 单击、按钮 B 按住拖动；灵敏度 0.4×–3.0× 可调，支持以当前姿态重新校准。基于无障碍服务的手势模拟实现。[^2]

### 系统与可维护性

- **报错日志系统**：分级记录、按天滚动、单文件 512KB / 总量 2MB 自动清理 + 内存缓冲 400 行；未捕获异常在杀进程前把完整现场落盘到 `crashes/`，下次启动提示并可一键反馈。
- **一键反馈**：关于页打包日志 zip（附 device-info 清单）唤起邮件客户端；日志仅含排错信息，不含个人身份信息。
- **主题自定义**：青绿 / 鎏金 / 黛蓝 / 绯红 / 松林五套配色 + Material 3 官方取色；Android 12+ 支持 Material You 动态取色；浅色 / 深色 / 跟随系统。
- **真实检查更新**：启动时按渠道拉取对应 `version*.json`，与本地 `versionCode` 比较，仅在新版本发布时提示。
- **平板 / 折叠屏适配**：内容宽度 840dp 封顶、`sw840dp` 尺寸档、宽屏双列、已声明 `resizeableActivity`。
- **隐私**：无账号、无广告、无埋点，**不收集、不上传任何个人信息**；本地仅存设置项与调试日志。

---

## 界面与适配

三个版本共用同一套业务代码与全部功能，界面层按渠道隔离：

| 版本 | 界面风格 | 说明 |
| :--- | :--- | :--- |
| 稳定版 / 开发版 | **Material 3 浓系原生风格** | 全部颜色走 M3 令牌，控件为 `MaterialCardView` / `MaterialButton` / `MaterialSwitch` / `Slider` / `Chip` 等，浅深双主题 |
| 预览版 | **Voyage 风格**（设置里可切回 M3） | 深青绿主色、几何 M 字标、开屏动效、骨架屏错落淡入、圆形揭示转场；二级页、弹窗与 XML 页面经统一主题覆盖层同步换肤 |

### 二级页过渡与排版统一（code 60）

所有二级页（设置、关于、开发者工具箱、功能分组、各类弹窗）共用一套排版语言，一处改动全量生效：

- **卡片浮起**：纯白卡片 + 26dp 大圆角 + 无描边，浮在浅灰绿页面底上，靠色阶而不是描边分层；
- **分组与选择器**：更大更醒目的分区标题、分段选择器选中项为深青绿实色胶囊 + 白字（与主界面分类选中态一致）；
- **平滑转场**：300ms 微缩放 + 轻滑的窗口转场，页面内分区标题与卡片按顺序错落淡入上滑；
- **XML 页面同步**：设置页等 XML 布局通过 `ThemeOverlay.MBOS.Voyage` 与运行时圆角统一一并换肤，不再出现「主界面换了、设置还是原样式」。

### 设置里切换界面风格

预览版「设置 → 外观 → 界面风格」提供分段选择器，可在 **稳定版 M3** 与 **Voyage 风格**之间切换。开关写入偏好并 bump 主题纪元：主界面皮肤的挂载 / 卸载、二级页换肤与排版、窗口与内容过渡一起重建，返回栈里的页面由 `BaseActivity` 自动重建保证一致。稳定版 / 开发版不显示此开关、界面零变化。

手机自适应（`smallestScreenWidthDp < 600`，预览版）：

- 主导航由左侧常驻栏改为**底部导航栏**，横向空间全部留给内容；
- 工具台分类由左侧栏改为**顶部横向滚动胶囊**，网格全宽铺开、列数随宽度自适应；
- 顶栏隐藏冗余菜单按钮，系统栏与窗口底色与页面统一，按状态栏 / 导航栏 / 挖孔 inset 整体内收留白。

> 设计规范自 v2.9 起强制执行：控件一律采用原生 Material 3 风格，颜色与字号不硬编码在布局中，列表行高 ≥ 72dp、图标 ≥ 40dp，强调用字重与字阶而非另造颜色。Voyage 风格在此基础上仅替换色板与形状令牌。

---

## 三个版本说明

三个渠道由 `app/build.gradle.kts` 的 `productFlavors` 一次产出，**共用同一份源码仓库**，差异仅在 flavor 配置与运行时门控 `BuildConfig.DEFAULT_CHANNEL`。

| 对比项 | 稳定版 | 预览版 | 开发者测试版 |
| :--- | :--- | :--- | :--- |
| **flavor** | `stable` | `preview` | `devtest` |
| **versionName** | `4.2.7` | `4.2.7-preview` | `4.2.7-dev` |
| **versionCode** [^3] | `53` | `60` | `53` |
| **包名** | `com.lingyi.mbos` | `com.lingyi.mbos.preview` | `com.lingyi.mbos.dev` |
| **应用名** | 灵逸 MB | 灵逸 MB 预览 | 灵逸 MB 开发 |
| **界面** | Material 3 原生 | **Voyage 全新界面** | Material 3 原生 |
| **开发者模式** | 连点版本号 7 次解锁 | 默认开启，关于页直入 | 默认开启，关于页直入 |
| **对外开发者接口** | 受开关保护 | 可用 | 可用 |
| **更新通道** | `version.json` | `version-beta.json` | `version-dev.json` |
| **发布节奏** | 功能验证完成后才升号 | 高频迭代，每次改动独立升号 | 跟随稳定版 |
| **适用人群** | 日常使用、教学 | 想尝鲜新界面、参与反馈 | 调试、二次开发、接口联调 |

### 稳定版 stable

正式发布通道，面向日常使用与课堂教学。功能与预览版保持对齐，但**只接收经过验证的改动**；界面保持 Material 3 原生风格，不加载 Voyage 皮肤。自动更新读取 `version.json`，仅当线上 `versionCode` 高于本地时提示。

> 若你只装一个版本，装这个。

### 预览版 preview

新界面、新交互、新适配的**首发通道**，当前承载 Voyage 风格界面与手机自适应。仅预览通道独立升构建号（稳定版 / 开发版保持 `53`），因此预览版的更新**不会误触发**另外两个渠道的更新提示。

预览版已包含：

- 全新开屏动画（几何 M 字标缩放淡入、标题浮现、圆形描边绘制、圆形揭示揭入主页）；
- 首页动态问候与日期、全屏工具搜索、渐变横幅卡、彩色图标功能网格、设备连接状态条；
- 手机底部导航 + 工具台横向分类胶囊 + 自适应网格；
- 分类点击**实色选中态**与 180ms 颜色渐变过渡；
- 「我的固件」页、关于页正式文档（用户协议 / 隐私政策 / 权限申请清单各自独立）；
- 开发者工具箱、设置、关于等所有二级页统一深青绿 Voyage 配色；
- **二级页排版统一升级**（白卡浮起 / 大圆角分组 / 实色分段选择器 / 300ms 柔和转场与错落入场）；
- **设置里可切换「稳定版 M3」与「Voyage 风格」**，切换后全 App 界面同步重建；
- 全部 88 项功能、连接管理、自动更新自检、开发者模式完整保留。

> 预览版可能与稳定版**同机并装**，方便对比。遇到界面问题欢迎通过反馈入口提交。

### 开发者测试版 devtest

仅供调试与二次开发，**不建议作为日常使用**。界面与稳定版一致（Material 3 原生），但默认开启开发者调试：组件检视悬浮窗、本地 HTTP 接口、对外 ContentProvider、开发者工具箱（Hero 主卡 + 三枚实时状态胶囊 + 分组图标行 + 终端风操作日志）、崩溃与 ANR 诊断工具等。

在线文档：<https://af8c03ac486ac1f4a.app.workbuddy.host/dev-docs.html>

### 并装与数据隔离

三个 flavor 的 `applicationId` 不同，因此**可以在同一台手机上同时安装、互不覆盖**；各自的 SharedPreferences、日志目录、崩溃记录也相互独立。对外接口 authority 随包名变化，分别为 `<包名>.external`。

---

## 下载与安装

**官方下载页**：<https://af8c03ac486ac1f4a.app.workbuddy.host/>

**开发者站**：<https://af8c03ac486ac1f4a.app.workbuddy.host/dev/> —— 独立的构建 / 源码 / 文档发布点，每次发版的新 APK、三份源码包与 README 都提交在这里，并提供 README 在线阅读。

| 渠道 | 直链 | 源码包 |
| :--- | :--- | :--- |
| 稳定版 | [`lingyi-mbos-os-debug.apk`](https://af8c03ac486ac1f4a.app.workbuddy.host/lingyi-mbos-os-debug.apk) | [`lingyi-mbos-os-source-stable.apk`](https://af8c03ac486ac1f4a.app.workbuddy.host/lingyi-mbos-os-source-stable.apk) |
| 预览版 | [`lingyi-mbos-os-beta.apk`](https://af8c03ac486ac1f4a.app.workbuddy.host/lingyi-mbos-os-beta.apk) | [`lingyi-mbos-os-source-beta.apk`](https://af8c03ac486ac1f4a.app.workbuddy.host/lingyi-mbos-os-source-beta.apk) |
| 开发版 | [`lingyi-mbos-os-dev.apk`](https://af8c03ac486ac1f4a.app.workbuddy.host/lingyi-mbos-os-dev.apk) | [`lingyi-mbos-os-source-dev.apk`](https://af8c03ac486ac1f4a.app.workbuddy.host/lingyi-mbos-os-source-dev.apk) |

安装须知：

1. **签名一致**：三个渠道均使用同一调试证书签名，同渠道可**直接覆盖升级，无需卸载旧版**。
2. **源码包扩展名**：源码压缩包含 `.apk` 后缀是因分发服务拦截 `.zip`，**下载后把扩展名改为 `.zip` 再解压**即可，它不是可安装的 APK。
3. **第三方安装器**：部分「静默安装 / 安装器」类工具在覆盖安装时可能报 `Binder invocation to an incorrect interface` 之类的系统接口错误，与 APK 本身无关。改用系统「文件管理 / 下载」直接点开 APK，或用 `adb install -r <file>` 即可。
4. **未知来源**：首次安装需允许该来源的安装权限。

<details>
<summary>命令行安装示例</summary>

```bash
adb install -r lingyi-mbos-os-beta.apk

# 若设备上有多版本并装，指定包名卸载单个渠道
adb uninstall com.lingyi.mbos.preview
```

</details>

---

## 硬件准备

| 项目 | 要求 |
| :--- | :--- |
| 开发板 | **micro:bit V2**（V1 不在支持范围） |
| 手机 | Android 7.0（API 24）及以上 |
| USB 连接 | 支持 OTG 的手机 + 数据线（不能用仅充电线） |
| 蓝牙连接 | Android 12+ 需授予附近设备权限；需刷入带 BLE 支持的固件 |
| 指针功能 | 需在系统「设置 → 无障碍」启用「灵逸 MB 指针服务」 |

---

## 快速开始

1. **安装 App**：从[下载页](#下载与安装)选择渠道并安装。
2. **刷写固件**：用 OTG 数据线把手机与 micro:bit 相连，打开 App，在引导页点「一键刷入内置固件」。完成后开发板会自动重启。
3. **建立连接**：在引导页选择 USB 串口或蓝牙 BLE；连接成功后状态条变为已连接，并回读固件版本。
4. **开始使用**：进入控制台 / 工具台，任选功能下发指令；点「预览模式」查看实时传感器读数。
5. **（可选）自己编程**：在「编程与刷写」分类打开 MakeCode，下载 `.hex` 后回到 App 一键刷入；也可直接写 MicroPython 代码导入。
6. **（可选）无线指针**：进入「陀螺仪指针」，按提示开启无障碍服务，倾斜板子即可控制指针。

<details>
<summary>连接失败时的排查顺序</summary>

- [ ] 换一根**支持数据传输**的 USB 线，确认手机支持 OTG
- [ ] 确认刷入的是本项目内置固件（板载需理解 App 的指令协议）
- [ ] USB 方式下重新插拔，检查系统是否弹出 USB 设备授权
- [ ] 蓝牙方式下确认固件带 BLE 支持，且附近设备权限已授予
- [ ] 在「设置 → 连接」切换默认连接方式后重试
- [ ] 通过关于页「错误日志与反馈」导出日志定位

</details>

---

## 内置固件

仓库另含固件工程 `mb-os-firmware`（MicroPython 侧），核心是 `main.py`，构建产物为 `micropython-microbit-v2.hex`（合并固件）。App 侧把最终 HEX 放在 `app/src/main/assets/firmware/`，与 `ui/FirmwareScript.kt` 中的 `main.py` 保持一致。

当前内置固件 **MB-OS v4.0.0**，主要能力：

- 解析文本指令并驱动 LED / 喇叭 / 传感器；
- 以约 50Hz 上报 `MOUSE:dx,dy,a,b`（指针模式的倾斜增量与按键）；
- `INFO` / `VERSION` 指令回报固件版本，供 App 做新旧比对；
- 连接开机动画；
- 标准 micro:bit BLE 服务，兼容编程猫 App 双向蓝牙通信。

重新生成固件：

```bash
cd mb-os-firmware
node generate_hex.js        # 生成合并固件 micropython-microbit-v2.hex
node build_games.js         # 生成三款小游戏 HEX 到 games/
# 将产物复制到 mb-os-app/app/src/main/assets/firmware/
```

---

## 指令协议

通过串口（Nordic UART / USB CDC）逐行发送文本指令，**每行以 `\n` 结尾**：

| 指令 | 作用 |
| :--- | :--- |
| `SCROLL:<text>` | LED 滚动显示文字 |
| `SHOW:<pattern>` / `PLOT:<x>,<y>,<on>` | 显示图案 / 单点绘制 |
| `TONE:<freq>,<ms>` / `MELODY:<name>` | 播放音调 / 内置旋律 |
| `NUMBER:<n>` / `BRIGHT:<0-9>` | 显示数字 / 调节亮度 |
| `COMPASS` / `CLEAR` / `READ` / `INFO` | 指南针 / 清屏 / 读数 / 版本信息 |
| `MOUSE:ON` / `MOUSE:OFF` / `MOUSE:CAL` | 指针模式开关与校准 |

示例：

```text
SCROLL:Hello micro:bit
SHOW:09090:90009:90009:09990:00900
MOUSE:ON
```

实现见 [`ui/Protocol.kt`](app/src/main/java/com/lingyi/mbos/ui/Protocol.kt)，板载脚本见 [`ui/FirmwareScript.kt`](app/src/main/java/com/lingyi/mbos/ui/FirmwareScript.kt)。

---

## 从源码构建

### 环境要求

| 项 | 版本 |
| :--- | :--- |
| JDK | 17+ |
| Android SDK | `compileSdk 34` |
| Gradle | 8.x |
| Kotlin | 由 AGP Kotlin 插件提供 |
| 最低系统 | Android 7.0（API 24） |

在 `local.properties` 中配置 `sdk.dir`，或设置环境变量 `ANDROID_HOME`。

### 三个 flavor 一次产出

```bash
git clone https://github.com/10Y-LYY/LingYi-MB.git
cd LingYi-MB

# 分别构建
gradle :app:assembleStableDebug
gradle :app:assemblePreviewDebug
gradle :app:assembleDevtestDebug

# 或一次构建全部
gradle :app:assembleDebug

# 单元测试
gradle :app:testStableDebugUnitTest
```

产物路径：`app/build/outputs/apk/<flavor>/debug/app-<flavor>-debug.apk`

| flavor | `DEFAULT_CHANNEL` | `IS_DEVTEST` | 输出 |
| :--- | :--- | :--- | :--- |
| `stable` | `"stable"` | `false` | 稳定版 |
| `preview` | `"preview"` | `true` | 预览版（Voyage 皮肤） |
| `devtest` | `"dev"` | `true` | 开发者测试版 |

> Voyage 风格以 `Context.voyageStyle()` 为**唯一判定点**（`DEFAULT_CHANNEL == "preview"` 且设置里的界面风格开关开启）：主界面皮肤挂载、主题覆盖层、`themeColor()` 换肤、M3Kit 排版、窗口与内容过渡全部走它。因此改预览版界面不会影响稳定版 / 开发版，也是三渠道共用一份源码的原因。

### 发版流程（每下次都这样）

每次发版只需一条命令，新 APK、三份源码包与 README 会自动提交到[开发者站](https://af8c03ac486ac1f4a.app.workbuddy.host/dev/)：

```bash
# 1) 改 app/build.gradle.kts 里 preview 的 versionCode（仅预览版升号）
# 2) 运行（标题可省略）：
bash /workspace/publish-dev-site.sh <新版本号> "本次更新说明" "日志标题"
```

脚本依次完成：升版本号 → 编译预览版 → 重生成三份源码包 → 同步正式站与开发者站文件 → 更新 `version*.json`、首页日志、README 在线版与更新日志 → 发布站点 → 线上校验。稳定版 / 开发版 `versionCode` 恒为 `53`，不触发误更新。

---

## 目录结构

```text
mb-os-app/
├── app/src/main/java/com/lingyi/mbos/
│   ├── GuideActivity.kt             # 入口：引导页 / 首次刷机 / 连接 / 更新提示
│   ├── MainActivity.kt              # 控制台：88 项功能分派、连接管理
│   ├── PreviewActivity.kt           # 实时传感器读数
│   ├── CustomFlashActivity.kt       # 自定义代码 / .hex / 小游戏刷入
│   ├── MakeCodeActivity.kt          # MakeCode 编程模块
│   ├── PointerModeActivity.kt       # 陀螺仪指针
│   ├── PointerAccessibilityService.kt
│   ├── SettingsActivity.kt / AboutActivity.kt / LicenseActivity.kt
│   ├── DeveloperActivity.kt / dev/ # 开发者调试、工具箱、组件检视、本地 HTTP
│   ├── ble/                         # USB CDC / BLE / 模拟器 / 连接单例 / 对外 Provider
│   ├── ui/
│   │   ├── VoyageSkin.kt            # 预览版专属 Voyage 界面与自适应布局
│   │   ├── M3Kit.kt                 # 程序化 Material 3 组件库
│   │   ├── FeatureCatalog.kt        # 7 大类 77 项功能目录
│   │   ├── Protocol.kt / FirmwareScript.kt
│   │   └── ...                      # LED、旋律、手势、游戏、CSV 等
│   └── util/                        # 主题、更新检查、日志、崩溃捕获、偏好设置
├── app/src/main/assets/firmware/    # 内置 MicroPython 合并 HEX + 三款小游戏
├── app/build.gradle.kts             # productFlavors：stable / preview / devtest
├── LICENSE                          # MIT
└── NOTICE.md                        # 第三方组件与素材鸣谢

mb-os-firmware/                      # micro:bit 侧 MicroPython 固件工程
mbos-dist/                           # 静态下载站（index.html + 三渠道 version*.json + APK）
```

---

## 权限说明

| 权限 | 用途 |
| :--- | :--- |
| `INTERNET` | 检查更新、访问 MakeCode 网页，不上传个人信息 |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` | 兼容旧版本 Android 的蓝牙连接（带 `maxSdkVersion` 限制） |
| `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` | Android 12+ 扫描与连接 BLE 设备，`SCAN` 声明 `neverForLocation` |
| `FOREGROUND_SERVICE` 及 `connectedDevice` / `dataSync` 型 | 保持长连接与日志服务在前台运行 |
| `SYSTEM_ALERT_WINDOW` | 开发者模式的组件检视 / 模拟器悬浮窗 |
| `DYNAMIC_RECEIVER_NOT_EXPORTED` | Android 13+ 广播接收器安全声明 |

**不申请**：位置、相机、麦克风、通讯录、短信、电话、外部存储读写等权限。完整清单以 [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) 为准，App 内「关于 → 权限申请清单」也可查看。

---

## 开发者接口

预览版与开发版提供两类对外接口，**默认受开发者模式开关保护**，仅用于本机调试与自动化：

**1. ContentProvider**（authority 为 `<包名>.external`）

```text
content://com.lingyi.mbos.preview.external/send?cmd=SCROLL:hello
content://com.lingyi.mbos.dev.external/send?cmd=INFO
```

也支持 `call("send", "SCROLL:hello", null)` 形式，见 [`ble/DeveloperApiProvider.kt`](app/src/main/java/com/lingyi/mbos/ble/DeveloperApiProvider.kt)。

**2. 本地 HTTP 服务**（仅监听回环地址 `127.0.0.1:8765`）

```bash
curl "http://127.0.0.1:8765/send?cmd=SCROLL:hello"
curl "http://127.0.0.1:8765/status"
```

> 本地 HTTP 服务不监听外网端口，不提供任何远程访问能力。完整接口文档见 [开发者文档](https://af8c03ac486ac1f4a.app.workbuddy.host/dev-docs.html)。

---

## 自动更新机制

App 启动进入主控制台时按渠道拉取对应 JSON，与本地 `versionCode` 比较，高于本地才弹窗，且同一次启动对同一版本只提醒一次。

| 渠道 | 更新文件 | 当前 `versionCode` |
| :--- | :--- | :--- |
| 稳定版 | [`version.json`](https://af8c03ac486ac1f4a.app.workbuddy.host/version.json) | `53` |
| 预览版 | [`version-beta.json`](https://af8c03ac486ac1f4a.app.workbuddy.host/version-beta.json) | `60` |
| 开发版 | [`version-dev.json`](https://af8c03ac486ac1f4a.app.workbuddy.host/version-dev.json) | `53` |

发布新版本的约定：**仅预览通道独立升 `versionCode`**，稳定版与开发版保持 `53`，避免预览版发版时另外两个渠道误收到更新提示。

---

## 技术栈

| 层次 | 选型 |
| :--- | :--- |
| 语言 | Kotlin 100% |
| 构建 | Android Gradle Plugin + Kotlin DSL、Gradle 8.x、JDK 17 |
| UI | 纯代码 View 体系 + Material Components for Android（M3 令牌） |
| 异步 / 连接 | USB CDC-ACM、Android BLE GATT、前台服务、进程级连接单例 |
| 数据 | SharedPreferences、本地日志与 CSV、按天滚动文件 |
| 固件 | MicroPython（micro:bit V2）、JavaScript 合并脚本 |
| 分发 | 静态站 + 三渠道 `version*.json` |
| 测试 | JUnit + Robolectric |

---

## 常见问题

<details>
<summary><strong>三个版本可以同时安装吗？会互相覆盖数据吗？</strong></summary>

可以。三者 `applicationId` 不同，属于系统眼里的三个独立应用，数据与日志目录相互隔离。同渠道覆盖升级也无需卸载。

</details>

<details>
<summary><strong>为什么预览版版本号一直变，稳定版不动？</strong></summary>

预览版是新界面的高频迭代通道，每次改动独立升 `versionCode`；稳定版只在功能验证完成后才升号。这是为了不让预览版发版误触发稳定版 / 开发版的更新弹窗。

</details>

<details>
<summary><strong>源码包下载后打不开 / 提示不是 APK？</strong></summary>

源码包实际是 zip，因分发服务拦截 `.zip` 才使用 `.apk` 扩展名。把后缀改成 `.zip` 再解压即可，不要当 APK 安装。

</details>

<details>
<summary><strong>用第三方安装器提示「未知错误」或 Binder 报错？</strong></summary>

这类报错来自安装器自身调用的隐藏系统接口与设备系统版本不匹配，与 APK 无关。APK 的签名、版本号、包名均可被正常解析。改用系统文件管理器直接打开，或 `adb install -r` 安装。

</details>

<details>
<summary><strong>连接不上 / 指令没反应？</strong></summary>

按顺序排查：换数据线并确认 OTG → 确认已刷入本项目内置固件 → 重新插拔并接受 USB 授权 → 蓝牙方式确认固件带 BLE 且权限已授予 → 在设置里切换默认连接方式 → 导出错误日志定位。详见[快速开始](#快速开始)。

</details>

<details>
<summary><strong>陀螺仪指针为什么需要无障碍权限？</strong></summary>

Android 不允许第三方 App 直接控制系统鼠标指针，因此采用无障碍服务的手势模拟实现（API 30+ 使用连续手势保证拖动平滑，低版本自动降级），对点击、滑动、拖拽、绘图等效。

</details>

<details>
<summary><strong>支持 micro:bit V1 吗？</strong></summary>

不支持，仅支持 **micro:bit V2**。V1 的内存与蓝牙能力不足以运行内置 MicroPython 合并固件。

</details>

---

## 贡献指南

欢迎提交 Issue 与 Pull Request。

1. Fork 本仓库并基于 `main` 建立特性分支，命名建议 `feat/xxx` 或 `fix/xxx`。
2. 遵循现有规范：Kotlin 代码风格、界面走 M3 令牌、颜色与字号不硬编码在布局中。
3. 改动涉及界面时，**确认三个 flavor 都能编译通过**：
   ```bash
   gradle :app:assembleStableDebug :app:assemblePreviewDebug :app:assembleDevtestDebug
   ```
4. 若修改预览版专属界面，请只改 `preview` 门控下的代码（`BuildConfig.DEFAULT_CHANNEL`），保证稳定版 / 开发版零影响。
5. 提交信息用中文或英文均可，建议格式 `feat: 简述改动`；附截图或复现步骤更容易被合并。
6. 提交前确认不含有本机路径、密钥（`*.jks` / `local.properties`）、构建产物 `app/build/` 等，参见 [`.gitignore`](.gitignore)。

Bug 反馈请附：机型与 Android 版本、渠道与 `versionCode`、micro:bit 连接方式、复现步骤、App 内导出的错误日志。

---

## 开源许可与鸣谢

本项目**源代码**采用 [MIT License](LICENSE) 开源。

```text
MIT License
Copyright (c) 2026 10Y-灵逸云科技
```

第三方组件与素材**不属于 MIT 覆盖范围**，按各自许可证使用，详见 [NOTICE.md](NOTICE.md)：MicroPython（MIT）、AndroidX / Jetpack / Material Components（Apache-2.0）、micro:bit 官方相关资料等。App 内也可通过「关于 → 开源许可证」查看。

<details>
<summary>署名</summary>

Powered by 闫冰峰 · © 10Y-灵逸云开发团队 · 10Y-灵逸云科技 出品

</details>

---

## 免责声明

- 本工具仅用于**你本人拥有与控制的设备**，请在合法合规范围内使用。
- 刷写固件会覆盖开发板原有内容，操作前请自行备份；因刷写、外接设备、OTG 供电等造成的设备损坏或数据丢失，作者不承担责任。
- 「micro:bit」名称与标识为 micro:bit Educational Foundation 的商标，本项目与其**无隶属、无背书关系**。
- 预览版与开发者测试版可能包含未充分验证的改动，日常使用请优先选择稳定版。
- 软件按「现状」提供，不附带任何明示或默示担保，详见 [LICENSE](LICENSE)。

---

## 联系方式

| 用途 | 渠道 |
| :--- | :--- |
| 下载与更新 | <https://af8c03ac486ac1f4a.app.workbuddy.host/> |
| 开发者站（新 APK / 源码包 / README） | <https://af8c03ac486ac1f4a.app.workbuddy.host/dev/> |
| 开发者接口文档 | <https://af8c03ac486ac1f4a.app.workbuddy.host/dev-docs.html> |
| 代码与 Issue | <https://github.com/10Y-LYY/LingYi-MB> |
| 错误日志反馈 | `lyy10y_log@126.com`（App 内「关于 → 错误日志与反馈」可一键打包发送） |

---

## 开发计划

- [x] 三渠道 flavor 隔离与并装
- [x] USB CDC / BLE 双连接与自动重连
- [x] 内置 MB-OS v4.0.0 固件与编程猫 BLE 兼容
- [x] 88 项功能入口与 7 大分类工具台
- [x] 报错日志系统、崩溃现场落盘、一键反馈
- [x] Material 3 全界面与平板 / 折叠屏适配
- [x] 预览版 Voyage 风格界面与几何 M 字标
- [x] 预览版手机自适应（底部导航 + 分类胶囊 + 边缘留白）
- [x] 用户协议 / 隐私政策 / 权限清单正式文档
- [x] 二级页过渡与排版统一升级（白卡浮起 / 分段选择器 / 柔和转场与错落入场）
- [x] 设置里切换稳定版 M3 与 Voyage 风格
- [x] 独立开发者站与一键发版流程
- [ ] 稳定版合入 Voyage 界面（待预览版验证收敛）
- [ ] 更多板型与固件版本适配

---

<div align="center">

<p><strong>如果这个项目对你有帮助，欢迎点个 Star。</strong></p>

<p><a href="#readme">回到顶部</a></p>

</div>

[^1]: 工具目录条目数来自 `FeatureCatalog.FEATURES`；系统级入口（连接管理、设置、关于、开发者工具箱等）单独计数，合计约 88 项。

[^2]: Android 不允许第三方 App 直接控制系统鼠标指针，故采用无障碍服务手势模拟；API 30+ 使用 `willContinue` 连续手势，低版本自动降级为离散滑动。

[^3]: 版本号截至 **2026-11-07**，以各渠道 `version*.json` 为准。预览版独立升号，稳定版 / 开发版保持 `53`。
