# Changelog

## [v0.6.0-alpha] - 2026-08-25

### Added

- English interface support with an in-app language selector for System Default, Simplified Chinese, and English.
- Device verification using matching text codes or Material icon sequences before sending or accepting files.
- Sequential multi-file sending queue with per-file progress and a session-wide cancel action.
- Device name and bind address on the main screen for easier nearby-device identification.

### Changed

- Added clear Material icons to settings options, changelog, checksum controls, and verified-device entries.
- Receive history now shows the actual saved location and opens the configured receive directory.
- CI now cancels superseded runs, validates the Gradle Wrapper, uses job timeouts, and retains pull-request debug APKs for seven days.

### Fixed

- Completed English translations for the verification screen.
- Kept nearby-device rows at their original height while vertically centering the verification badge.
- Improved transfer queue cancellation labels and removed progress-row flickering during active transfers.

## 中文说明

### v0.6.0-alpha（2026-08-25）

#### 新增

- 支持英文界面；可在应用内选择系统默认、简体中文或 English。
- 新增设备验证：发送或接收前可通过文本安全码或 Material 图标序列核对对端设备。
- 支持多文件按队列依次发送，显示单文件进度，并可一键取消整个会话。
- 主页显示设备名称和绑定地址，便于识别附近设备。

#### 调整

- 为设置项、更新日志、校验和控制和已验证设备补充清晰的 Material 图标。
- 接收历史显示实际保存位置，“打开目录”会进入已配置的接收目录。
- CI 会取消过期运行、验证 Gradle Wrapper、限制任务时长，并将 PR 的调试 APK 保留 7 天。

#### 修复

- 补全验证页面的英文翻译。
- 附近设备条目保持原有高度，验证盾牌垂直居中。
- 优化传输队列的取消文案，并消除传输时进度条目闪烁。

## [v0.5.0-alpha] - 2026-08-24

### Added

- A dedicated settings screen for device identity, receiving behavior, sending checksums, and network discovery.
- Configurable receive directory, automatic saving, receive history, and checksum verification for incoming files.
- Server, port, encryption, and multicast-address controls for local network discovery.
- An About section with version, changelog, source code, feedback, license, and third-party license entries.

### Changed

- Reworked the main toolbar actions into history and settings icon buttons.
- Refined settings-page hierarchy, iconography, spacing, and native editing dialogs.

### Fixed

- Avoided a crash on some Android document providers after selecting a receive directory.
- Kept discovery broadcasts on the manufacturer name instead of the full device model.
- Moved network restarts off the settings UI thread to keep server and encryption switches responsive.

## 中文说明

### v0.5.0-alpha（2026-08-24）

#### 新增

- 新增独立设置页面，涵盖设备身份、接收行为、发送校验和与网络发现。
- 支持配置接收目录、自动保存、接收历史和接收文件校验和验证。
- 支持配置局域网发现服务器、端口、加密和多播地址。
- 新增“关于”分段，提供版本、更新日志、源代码、问题反馈、许可证和第三方许可证入口。

#### 调整

- 主页工具栏改用历史和设置图标按钮。
- 优化设置页的层级、图标、间距和原生编辑对话框。

#### 修复

- 修复部分 Android 文档提供方在选择接收目录后导致的崩溃。
- 发现广播恢复为使用设备厂商名，而非完整设备型号。
- 将网络重启移出设置页主线程，服务器和加密开关不再卡顿。

## [v0.4.0-alpha] - 2026-08-24

### Added

- Receive history for completed incoming files, including the sender, time, size, and saved file reference.
- A history page with direct file opening, per-item actions, file details, history clearing, and a shortcut to the receive directory.

### Changed

- Replaced the main-screen received-file list with the dedicated history entry point.
- Refined the history list density, system navigation controls, overflow menu, and file-detail layout.

### Fixed

- History entries now report “File does not exist” when the stored file has been deleted.
- Directory opening now targets `Download/LocalSend Kotlin` instead of the Downloads root.

## 中文说明

### v0.4.0-alpha（2026-08-24）

#### 新增

- 为已完成接收的文件增加历史记录，保存发送设备、时间、大小和文件引用。
- 新增历史页面，支持打开文件、单项操作、查看文件详情、清空历史和打开接收目录。

#### 调整

- 移除主页的“已接收文件”列表，改为独立的历史入口。
- 优化历史列表密度、系统返回控件、更多菜单和文件详情布局。

#### 修复

- 历史文件被删除后，打开时明确提示“文件不存在”。
- “打开目录”改为定位至 `Download/LocalSend Kotlin`，不再仅打开下载根目录。

## [v0.3.0-alpha] - 2026-08-24

### Added

- Multi-file selection and sequential send queues.
- Multi-file receive sessions with independent per-file progress rows.
- Individual-file cancellation as well as whole-session cancellation while receiving.

### Changed

- Refresh now clears the existing nearby-device list before sending a fresh discovery announcement.
- Receiving status uses aggregate session progress instead of repeatedly switching between file names.

## 中文说明

### v0.3.0-alpha（2026-08-24）

#### 新增

- 支持多选文件并按队列依次发送。
- 支持多文件接收会话，并为每个文件显示独立进度。
- 接收时支持取消单个文件或整个会话。

#### 调整

- 刷新设备时先清空现有附近设备列表，再发送新的发现广播。
- 接收状态改为显示整个会话的总进度，不再在多个文件名之间反复切换。

## [v0.2.0-alpha] - 2026-08-23

### Added

- Background file sending and receiving through a foreground service.
- Notification actions for accepting, rejecting, and cancelling transfers.
- Transfer notifications with percentage, completed size, transfer speed, and ETA.
- Service and transfer state restoration when the Activity returns to the foreground.
- Reliable cancellation handling without treating cancelled uploads as completed transfers.

### Fixed

- Prevented progress callbacks from flooding the main thread and causing background-transfer ANRs.
- Kept the receiving service available after cancelling a transfer.

## 中文说明

### v0.2.0-alpha（2026-08-23）

#### 新增

- 通过前台服务支持后台发送和接收文件。
- 通知栏支持接受、拒绝和取消传输。
- 传输通知显示百分比、已完成大小、传输速度和预计剩余时间。
- Activity 回到前台时恢复服务和传输状态。
- 完善取消处理，避免已取消的上传被误判为已完成。

#### 修复

- 限制进度回调频率，避免后台传输时主线程消息堆积导致 ANR。
- 取消传输后保留接收服务，仍可继续接收下一次发送。

## [v0.1.1-alpha] - 2026-08-22

### Fixed

- Fixed file receiving on Android 5.1 by capturing the peer TLS certificate fingerprint after the TLS handshake completes. Thanks to [**@FXDaily**](https://github.com/FXDaily).
- Added a deterministic TLS fingerprint regression test for the NanoHTTPD/SSL handshake flow.

## 中文说明

### v0.1.1-alpha（2026-08-22）

#### 修复

- 修复 Android 5.1 上接收文件时因 TLS 握手完成监听时序导致请求被错误拒绝的问题。
- 增加稳定的 TLS 指纹回归测试，覆盖 NanoHTTPD/SSL 握手流程。
- 感谢 [**@FXDaily**](https://github.com/FXDaily) 贡献 TLS 证书指纹处理和 Android 5.1 文件接收修复。

## [v0.1.0-alpha] - 2026-08-22

### Added

- LocalSend v2 LAN discovery with UDP multicast and HTTPS scan fallback.
- File sending and receiving with streaming I/O and progress display.
- Receive confirmation dialog and sender/receiver cancellation.
- TLS certificate fingerprint verification for peer connections.
- Saving received files to `Download/LocalSend Kotlin`.
- Received-file list with an Open action.
- API 21-compatible network binding and legacy storage handling.
- Apache-2.0 licensing, bilingual documentation, and GitHub Actions CI.

### Known limitations

- Foreground Activity use is the primary supported mode.
- Multi-file queues, resumable transfers, and transfer history are not implemented.
- Older Android versions and additional vendor ROMs need more real-device testing.
- Android 4.4 (API 19) is not currently supported.

## 中文说明

### v0.1.0-alpha（2026-08-22）

#### 新增

- LocalSend v2 局域网发现，支持 UDP 多播和 HTTPS 扫描回退。
- 基于流式 I/O 的文件发送、接收和进度显示。
- 接收确认对话框，以及发送端/接收端取消传输。
- 连接对端证书指纹校验。
- 将接收文件保存到 `Download/LocalSend Kotlin`。
- 已接收文件列表和打开按钮。
- API 21 网络绑定兼容和旧版存储处理。
- Apache-2.0 许可证、双语文档和 GitHub Actions CI。

#### 已知限制

- 当前主要支持前台 Activity 使用。
- 尚未实现多文件队列、断点续传和传输历史。
- 旧版 Android 和更多厂商 ROM 仍需要更多实机测试。
- 当前不支持 Android 4.4（API 19）。
