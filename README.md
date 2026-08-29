# LocalSend Kotlin

[English](README.md) | [简体中文](docs/README.zh-CN.md)

An unofficial, lightweight native Android LocalSend client written in Kotlin. This project is not affiliated with or endorsed by the official LocalSend project. It aims to interoperate with the official LocalSend clients and provide secure file transfer on local networks, including older Android devices.

> Current version: `v0.7.5-alpha`
>
> The core workflow is usable, but compatibility across Android versions, vendor-specific background policies, and long-running transfers still needs more real-device validation.

<a href="https://github.com/mouse233/localsend-kotlin/releases/latest"><img src="https://raw.githubusercontent.com/rubenpgrady/get-it-on-github/main/get-it-on-github.png" alt="Get it on GitHub" height="60"></a>&nbsp;&nbsp;<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22io.github.mouse233.localsendkotlin%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fmouse233%2Flocalsend-kotlin%22%2C%22author%22%3A%22mouse233%22%2C%22name%22%3A%22LocalSend%20Kotlin%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22versionDetection%5C%22%3Atrue%7D%22%7D"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="60"></a>

## Implemented

- LAN device discovery
  - LocalSend v2 UDP multicast with an HTTPS scanning fallback, selectable network interfaces, and Android 5.0 (API 21) compatibility
- File transfer
  - Send and receive files, folders, media, text, and clipboard content with multi-file queues, progress, cancellation, configurable receive behavior, and history
- Secure transfer
  - HTTPS, certificate-fingerprint identity verification, optional PIN authentication, and SHA-256 checksums
- Interface and settings
  - System Default, Simplified Chinese, and English, plus theme colors, dark mode, device and favorite management, and network/transfer settings
- Background transfer
  - Foreground-service transfers with lock-screen support, notifications, and an optional keep-screen-awake mode
- Quick access
  - Android Quick Settings tile named LocalSend Kotlin that toggles the background service on Android 7.0 (API 24) and later; it is active while the service or the app is in the foreground

## Not implemented yet

- Registration as an Android system share target.
- Receiving files through browser links.
- Multi-threaded transfers.
- Large-file performance optimizations.
- Experimental Android 4.x support.

## Technology stack

- Android Gradle Plugin 9.3.2, Gradle 9.7.1, and AGP built-in Kotlin (2.2.x)
- Android SDK API 36; AndroidX Core KTX 1.17.0 and RecyclerView 1.4.0
- Classic Android Views/XML layouts
- Activity, RecyclerView, and the system file picker
- Gradle Kotlin DSL
- OkHttp 5.4: HTTPS requests and streaming uploads
- NanoHTTPD: embedded HTTP/HTTPS server
- Gson 2.14: LocalSend JSON encoding and decoding
- Bouncy Castle: TLS certificates and cryptography
- UDP multicast and IPv4 LAN scanning: device discovery
- MediaStore: public Downloads storage on Android 10 and later

## Compatibility

| Item | Current setting |
| --- | --- |
| Minimum Android version | Android 5.0 (API 21) |
| Compile SDK | Android 16 (API 36) |
| Target SDK | API 33 |
| Java/Kotlin JVM | Java 8 language level; JDK 17 required for builds |
| Default port | TCP/UDP `53317` |
| Protocol | LocalSend Protocol v2.2 / version `2.0` |

The Quick Settings tile is available on Android 7.0 (API 24) and later. On Android 5.0–6.0 (API 21–23), the app continues to work normally without the system tile because those versions do not provide the platform API.

Both devices must be on the same local network. Guest networks, AP/client isolation, multicast filtering, or vendor firewalls may prevent discovery or the TCP connection.

## Build

Requirements:

- Android Studio (a recent stable release is recommended)
- Android SDK Platform 36
- JDK 17

From the project root:

```bash
./gradlew assembleDebug
```

Before submitting changes, run the validation suite:

```bash
./gradlew lintDebug test assembleDebug
```

The build currently completes without lint errors. A small number of non-blocking warnings remain for legacy Android compatibility, AndroidManifest attributes, the Bouncy Castle dependency, and a root-layout overdraw check.

`Android CI` runs lint, unit tests, and a debug build for pull requests. The separate `API 21 Compatibility` workflow is manually triggered when an Android 5.0 emulator verification is needed.

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The project can also be opened in Android Studio and run with the `app` configuration.

## Usage

1. Install and open the app on both devices.
2. Connect both devices to the same Wi-Fi/LAN and allow network access.
3. Tap **Select file** and choose a file.
4. Tap the target device under **Nearby devices**, or tap **Manual send** and enter the target `IP:port`.
5. Accept the confirmation dialog on the receiving device.
6. Monitor the progress and tap **Cancel** if needed. Completed received files appear in **History**.

The **Refresh** button clears the current device list and sends another device multicast announcement. It is useful after connecting to Wi-Fi or when you want a fresh nearby-device scan.

## File storage and permissions

- Android 10 (API 29) and later: MediaStore saves files to the public Downloads directory by default without the legacy storage permission; users can choose another receive directory.
- Android 5.0 through Android 9 (API 21–28): files are written to `Download/LocalSend Kotlin` by default and `WRITE_EXTERNAL_STORAGE` is required.

## Protocol and security

The implementation covers LocalSend v2 device registration, prepare-upload, upload, receive, and cancellation endpoints. HTTPS uses a locally generated device certificate and pins the peer certificate fingerprint for outbound device communication; the server also validates the client fingerprint at the LocalSend layer. It does not depend on a public CA and is intended for local-network use, not as a public HTTPS service.

Protocol references:

- [LocalSend-Kotlin-PROJECT.md](docs/LocalSend-Kotlin-PROJECT.md)
- [LocalSend-Protocol-v2.2.md](docs/LocalSend-Protocol-v2.2.md)

## License

LocalSend Kotlin is licensed under the [Apache License 2.0](LICENSE). Third-party dependencies retain their respective licenses; see [NOTICE](NOTICE) for a summary.

## Current limitations

- The main validation environment is Android 16 real devices; older Android versions and additional vendor ROMs still need testing.
- Android 4.4 (API 19) is not currently supported. It may be considered as an experimental target later, but API 21 remains the current minimum because older systems have greater TLS, certificate, storage, and network-compatibility risks.
- Transfers continue through normal backgrounding and lock-screen use while the foreground service is running. Swiping the app task away intentionally stops the service and active transfers.
- Resumable transfers are not implemented.
- AP isolation, multicast filtering, or client isolation can prevent devices from communicating.

## Project status

This is an Alpha project suitable for early-user testing. When reporting an issue, include the Android version, device model, both client versions, and reproducible steps.

## Contributors

- [@FXDaily](https://github.com/FXDaily) — TLS certificate fingerprint handling and Android 5.1 file-receiving fix.
