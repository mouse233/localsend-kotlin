# Changelog

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
