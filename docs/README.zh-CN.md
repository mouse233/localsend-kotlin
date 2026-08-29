# LocalSend Kotlin

[English](../README.md) | [简体中文](README.zh-CN.md)

一个使用 Kotlin 编写的非官方 Android LocalSend 客户端。本项目与原版 LocalSend 项目没有隶属、赞助或官方授权关系，目标是与原版客户端互操作，并支持在局域网内安全地发送和接收文件。

> 当前版本：`v0.7.5-alpha`
>
> 基本功能已经可以使用，但跨 Android 版本、不同厂商后台策略和长时间传输仍需要更多实机验证。

<a href="https://github.com/mouse233/localsend-kotlin/releases/latest"><img src="https://raw.githubusercontent.com/rubenpgrady/get-it-on-github/main/get-it-on-github.png" alt="Get it on GitHub" height="60"></a>&nbsp;&nbsp;<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22io.github.mouse233.localsendkotlin%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fmouse233%2Flocalsend-kotlin%22%2C%22author%22%3A%22mouse233%22%2C%22name%22%3A%22LocalSend%20Kotlin%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22versionDetection%5C%22%3Atrue%7D%22%7D"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="60"></a>

## 已实现功能

- 局域网设备发现
  - LocalSend v2 多播 UDP 发现，多播失败时回退到 HTTPS 扫描；支持选择网络接口和 Android 5.0（API 21）兼容
- 文件传输
  - 支持文件、文件夹、媒体、文本和剪贴板内容的发送与接收，包括多文件队列、进度、取消、可配置接收行为和历史记录
- 安全传输
  - 支持 HTTPS、基于证书指纹的设备身份校验、可选 PIN 验证和 SHA-256 校验和
- 界面与设置
  - 支持系统默认、简体中文和 English，以及主题色、深色模式、设备与收藏设备管理和网络/传输设置
- 后台传输
  - 使用前台服务支持锁屏传输、通知操作和可选保持屏幕唤醒
- 快速访问
  - Android 7.0（API 24）及以上支持名为 LocalSend Kotlin 的系统快捷设置磁贴，可切换后台服务；后台服务运行或应用处于前台时，磁贴会亮起
- Android 系统分享
  - 已注册为系统分享入口，支持分享单个文件、多个文件和文本内容

## 未实现功能

- 通过浏览器链接接收文件。
- 多线程传输。
- 大文件性能优化。
- 实验性 Android 4.x 支持。

## 技术栈

- Android Gradle Plugin 9.3.2、Gradle 9.7.1，以及 AGP 内置 Kotlin（2.2.x）
- Android SDK API 36；AndroidX Core KTX 1.17.0、RecyclerView 1.4.0
- Android Views/XML 布局
- Android Activity、RecyclerView、系统文件选择器
- Gradle Kotlin DSL
- OkHttp 5.4：HTTPS 请求和文件上传
- NanoHTTPD：本地 HTTP/HTTPS 服务
- Gson 2.14：LocalSend 协议 JSON 编解码
- Bouncy Castle：TLS 证书和加密支持
- UDP Multicast、IPv4 局域网扫描：设备发现
- MediaStore：Android 10 及以上的公共下载目录存储

## 兼容性

| 项目 | 当前设置 |
| --- | --- |
| 最低 Android 版本 | Android 5.0（API 21） |
| 编译 SDK | Android 16（API 36） |
| Target SDK | API 33 |
| Java/Kotlin JVM | Java 8 语言级别；构建必须使用 JDK 17 |
| 默认端口 | TCP/UDP `53317` |
| 协议 | LocalSend Protocol v2.2 / version `2.0` |

系统快捷设置磁贴支持 Android 7.0（API 24）及以上版本。Android 5.0–6.0（API 21–23）仍可正常使用应用，只是这些系统没有提供快捷设置磁贴 API。

两台设备需要连接到同一个局域网。部分路由器、访客网络、AP 隔离或厂商防火墙可能会阻止多播或设备之间的 TCP 连接。

## 构建项目

要求：

- Android Studio（建议使用较新的稳定版本）
- Android SDK Platform 36
- JDK 17

在项目根目录执行：

```bash
./gradlew assembleDebug
```

提交修改前建议运行完整验证：

```bash
./gradlew lintDebug test assembleDebug
```

当前构建已无 lint 错误，剩余少量非阻塞警告主要来自旧版 Android 兼容、AndroidManifest 属性、Bouncy Castle 第三方依赖和根布局背景检查。

`Android CI` 会为 PR 运行 lint、单元测试和调试构建；需要在 Android 5.0 模拟器上验证时，可手动触发独立的 `API 21 Compatibility` workflow。

生成的调试 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

也可以直接使用 Android Studio 打开项目并运行 `app` 配置。

## 使用方法

1. 在两台设备上安装并打开应用。
2. 确认两台设备连接到同一个 Wi-Fi/局域网，并允许应用访问网络。
3. 点击“选择文件”，选择要发送的文件。
4. 在“附近设备”中点击目标设备，或点击“手动发送”并输入目标 `IP:端口`。
5. 接收端确认弹窗后，文件开始传输。
6. 传输过程中可以点击“取消”。接收完成的文件会出现在“历史”页面中。

主页的“刷新”按钮会先清空当前设备列表，再重新发送一次设备多播公告，用于设备刚连接网络或需要重新查找附近设备时使用。

## 文件保存和权限

- Android 10（API 29）及以上：默认通过 MediaStore 保存到公共下载目录，不需要申请传统存储权限；用户可选择其他接收目录。
- Android 5.0 至 Android 9（API 21–28）：默认保存到公共 `Download/LocalSend Kotlin` 目录，需要申请 `WRITE_EXTERNAL_STORAGE` 权限。

## 协议与安全说明

项目实现了 LocalSend v2 的设备注册、准备上传、文件上传和取消接口。HTTPS 使用本地生成的设备证书，并对主动连接的设备固定校验证书指纹；服务端也会在 LocalSend 协议层校验客户端指纹。因此应用不依赖公共 CA 证书，也不应把局域网 HTTPS 地址当作普通公网 HTTPS 服务使用。

协议参考文件：

- [LocalSend-Protocol-v2.2.md](LocalSend-Protocol-v2.2.md)
- [LocalSend-Kotlin-PROJECT.md](LocalSend-Kotlin-PROJECT.md)

## 许可证

LocalSend Kotlin 使用 [Apache License 2.0](../LICENSE) 发布。第三方依赖仍遵循各自的许可证，概要说明见 [NOTICE](../NOTICE)。

## 当前限制

- 当前主要验证环境为 Android 16 实机；旧版 Android 和更多厂商 ROM 仍需要进一步测试。
- 当前不支持 Android 4.4（API 19）。未来可以将其作为实验性目标，但由于旧系统在 TLS、证书、存储和网络兼容性方面存在更大风险，目前仍将 API 21 作为最低版本。
- 正常切到后台或锁屏时，前台服务会继续传输；从最近任务中划掉应用会按设计停止服务和当前传输。
- 尚未实现断点续传。
- 如果网络启用了 AP 隔离、组播过滤或设备间客户端隔离，可能需要切换到允许设备互访的网络。

## 项目状态

这是一个可供早期用户试用的 Alpha 项目。欢迎通过实际设备测试发现问题，并提供 Android 版本、设备型号、双方客户端版本和复现步骤。

## Contributors

- [@FXDaily](https://github.com/FXDaily) — TLS 证书指纹处理以及 Android 5.1 文件接收修复。
