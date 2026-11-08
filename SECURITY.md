# 安全政策 · Security Policy

> **灵逸 MB** · micro:bit V2 第三方 Android 客户端
> 维护者：[10Y-LYY/LingYi-MB](https://github.com/10Y-LYY/LingYi-MB)（10Y-灵逸云科技）
> 最后更新：**2026-11-08** · 适用预览版：**4.2.7-preview（code 61）**
> 本文件由发版脚本在**每次发布新版本时自动更新**（版本号、支持版本表、下载校验和）。

我们重视使用者的安全与隐私。如果你认为发现了安全问题，请按下方方式私下报告，不要在修复发布前公开细节。

## 1. 受支持的版本

| 渠道 | flavor | 包名 | 当前 `versionCode` | 安全更新 |
| :--- | :--- | :--- | :--- | :--- |
| 稳定版 | `stable` | `com.lingyi.mbos` | `53` | 修复验证后随稳定版发布 |
| 预览版 | `preview` | `com.lingyi.mbos.preview` | `61` | 优先修复，随每次迭代发版 |
| 开发版 | `devtest` | `com.lingyi.mbos.dev` | `53` | 跟随稳定版，不单独提供补丁 |

- 仅维护**各渠道最新发布版本**；已停止分发的旧版本不再提供安全补丁，请升级到当前版本。
- 预览版是高频迭代通道，安全修复通常先在预览版验证，再合入稳定版；严重问题会加急单独发版。
- 三个渠道包名不同、可同机并装，数据由 Android 沙箱相互隔离，单一渠道的问题不影响其它渠道数据。

## 2. 报告漏洞

**请勿在公开 Issue、讨论区、群聊或评论区发布未修复漏洞的细节。**

按优先级选择渠道：

1. [GitHub 私密安全通告](https://github.com/10Y-LYY/LingYi-MB/security/advisories/new)（仓库 → Security → Report a vulnerability），内容仅维护者可见。
2. 若无法使用该入口，请开一个**不含任何细节**的 Issue，标题写「安全问题，请私信」，我们会在 GitHub 上与你私信跟进。

为便于尽快定位，请尽量提供：

| 项目 | 说明 |
| :--- | :--- |
| 影响范围 | 渠道（stable / preview / devtest）、`versionCode`、Android 版本与机型 |
| 环境 | USB 串口 / 蓝牙 BLE、micro:bit 固件版本、是否连接开发板 |
| 复现步骤 | 可稳定复现的最小步骤 |
| 证据 | 日志片段、截图或 PoC（请先脱敏，勿包含他人信息） |
| 影响评估 | 可能造成的影响、你判断的严重程度 |
| 建议 | 可选的修复思路 |

**响应时限**（自收到完整报告起）：

| 阶段 | 目标 |
| :--- | :--- |
| 确认收到 | 72 小时内 |
| 初步评估与分级 | 7 个工作日内 |
| 修复发版 | 通常随最近一次预览版发版（1–2 周）；高危问题加急 |
| 状态同步 | 修复发布后主动告知报告者 |

## 3. 协调披露

- 默认采用**协调披露**：修复发布后 14 天、或报告后 90 天（以先到者为准）再公开细节，期限可与报告者协商调整。
- 修复发版时会在更新日志标注「安全修复」，但披露期结束前不公开攻击细节。
- 如需 CVE，维护者可协助申请；报告者可选择在致谢名单署名或保持匿名。
- 对已公开、已被广泛利用，或仅影响已停止支持版本的问题，可提前公开以便用户自保。

## 4. 安全范围

**在范围内**

- App 自身逻辑、设置与本地存储（SharedPreferences、日志导出）
- 自动更新机制（`version*.json` 拉取、版本比对、下载入口）
- USB 串口 / BLE 通信、指令协议与内置 MicroPython 固件脚本
- 权限使用、对外开发者接口（ContentProvider / 本地 HTTP，默认受开发者模式开关保护）
- 分发包的完整性与签名说明

**不在范围内**

- micro:bit 硬件、官方固件、MakeCode 编辑器及第三方浏览器
- Android 系统 / ROM / 第三方安装器（例如 InstallX 类工具）自身的问题
- 需要设备已 root、已解锁 Bootloader，或攻击者已能物理完全控制设备才能利用的问题
- 社会工程、需持续物理操作的拒绝服务、无安全影响的纯 UI 显示问题
- 用户自行导入的第三方固件、`.hex` 或 MicroPython 代码

## 5. 权限与数据

| 权限 | 用途 | 数据去向 |
| :--- | :--- | :--- |
| `INTERNET` | 检查更新、访问 MakeCode 网页、下载文档 | 仅访问本项目站点与 MakeCode，不上传个人信息 |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` | 兼容旧版 Android 的 BLE 连接（带 `maxSdkVersion`） | 仅本地设备 |
| `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` | Android 12+ 扫描与连接 BLE，`SCAN` 声明 `neverForLocation` | 仅本地设备 |
| `FOREGROUND_SERVICE`（`connectedDevice` / `dataSync`） | 保持长连接与日志服务在前台 | 仅本地 |
| `SYSTEM_ALERT_WINDOW` | 开发者模式的悬浮窗 / 组件检视 | 仅本地 |
| 无障碍服务（用户手动开启） | 陀螺仪指针的手势模拟 | 仅下发手势，不上传数据 |

- **不申请**位置、相机、麦克风、通讯录、短信、电话、外部存储读写权限；完整清单见 `AndroidManifest.xml` 与 App 内「关于 → 权限申请清单」。
- 无账号体系、无登录、无广告 SDK、无遥测上报；错误日志仅在用户主动导出时生成本地文件。

## 6. 已知限制与设计取舍

> 以下是**有意的设计取舍**，不是待修复漏洞；提交报告前请先确认不在此列。

- **本地链路明文**：USB 串口 / BLE 指令为明文本地通信，用于近距离课堂与个人控制，不承载凭据或敏感数据。请勿在不可信环境中配对或长期暴露可写接口。
- **更新不静默安装**：App 只通过 HTTPS 读取 `version*.json` 比对 `versionCode` 并弹窗提示，**不会自动下载或安装 APK**；请始终从官方站点获取安装包。
- **调试签名**：迭代期间公开分发的预览版 / 开发版使用 debug 签名以便快速安装验证，稳定版正式发布前切换为 release 签名。请通过第 7 节校验和确认文件未被篡改。
- **源码包扩展名**：源码包内容实为 zip，因分发服务拦截 `.zip` 才使用 `.apk` 扩展名，**不是可安装的 APK**，请勿当作应用安装。
- **开发者接口**：ContentProvider 与本地 HTTP 接口默认受开发者模式开关保护，仅用于本机调试；在不可信网络环境中请保持关闭。

## 7. 校验下载文件

开发者站与正式站根目录提供 `SHA256SUMS.txt`，每次发版随 APK 与三份源码包一同更新。

```bash
# Linux / macOS（在下载目录执行）
curl -O https://af8c03ac486ac1f4a.app.workbuddy.host/dev/SHA256SUMS.txt
sha256sum -c SHA256SUMS.txt
```

```powershell
# Windows PowerShell
Get-FileHash .\lingyi-mbos-os-beta.apk -Algorithm SHA256
```

若哈希不匹配，请**不要安装**，并通过第 2 节渠道告知我们。

## 8. 本文件的维护方式

本文件随每次发版由 `publish-dev-site.sh` → `render-security.py` 自动更新：

| 自动更新项 | 来源 |
| :--- | :--- |
| 最后更新日期、适用预览版 `versionCode` / `versionName` | 发版参数与 `app/build.gradle.kts` |
| 「受支持的版本」表 | `app/build.gradle.kts` 的 `productFlavors` |
| `SHA256SUMS.txt` | 本次编译的 APK 与三份源码包 |
| 在线阅读版 `security.html` | 本文件按 UTF-8 渲染 |

稳定版 / 开发版 `versionCode` 恒为 `53`，仅预览版独立升号。历史安全修复记录见[开发者站更新日志](https://af8c03ac486ac1f4a.app.workbuddy.host/dev/#changelog)。

## 9. 相关链接

- [开发者站（新 APK / 源码包 / 本文件）](https://af8c03ac486ac1f4a.app.workbuddy.host/dev/)
- [GitHub 仓库](https://github.com/10Y-LYY/LingYi-MB)
- [私密报告漏洞](https://github.com/10Y-LYY/LingYi-MB/security/advisories/new)
- [README 在线版](https://af8c03ac486ac1f4a.app.workbuddy.host/dev/readme.html)

---

本项目以 [MIT License](LICENSE) 开源。本安全政策与漏洞报告流程独立于开源许可，报告者不因报告行为丧失任何权利。
