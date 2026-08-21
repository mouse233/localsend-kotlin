# Changelog

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
