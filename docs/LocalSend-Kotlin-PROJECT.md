# LocalSend-Kotlin

## Native Lightweight LocalSend Client for Legacy Android Devices

------------------------------------------------------------------------

# 1. Project Overview

LocalSend-Kotlin is a lightweight native Android implementation of the
LocalSend protocol.

The project aims to provide a non-Flutter LocalSend client for older
Android devices that cannot run the official Flutter client due to GPU,
OpenGL ES, memory, or system limitations.

Protocol reference:

https://github.com/localsend/protocol

------------------------------------------------------------------------

# 2. Project Goals

## Primary Goal

Create a native Android LocalSend client compatible with official
LocalSend clients:

-   Windows
-   macOS
-   Linux
-   iOS
-   Android

The first milestone is:

> Official LocalSend clients can discover this app and transfer files
> successfully.

------------------------------------------------------------------------

# 3. Target Environment

## Minimum Supported Version

Primary target:

-   Android 5.0 (API Level 21)

Experimental:

-   Android 4.4 (API Level 19)

Do not prioritize Android 4.0/4.1 compatibility in the first stage.

## Target Hardware

Examples:

-   Kindle Fire tablets
-   old Android tablets
-   old phones
-   embedded Android devices

Expected:

-   ARMv7 / ARM64 CPU
-   512MB - 2GB RAM

------------------------------------------------------------------------

# 4. Design Principles

## Compatibility First

Priority:

Protocol compatibility \> Device compatibility \> Performance \> UI
appearance

## Minimal Dependencies

Do not use:

-   Flutter
-   Jetpack Compose
-   Material3
-   heavy frameworks
-   unnecessary background libraries

------------------------------------------------------------------------

# 5. Technology Stack

## Language

Kotlin 1.6.x

Reason:

-   good Android compatibility
-   good AI coding support
-   modern but lightweight

## UI

Classic Android XML View system.

Use:

-   Activity
-   Fragment
-   RecyclerView
-   ProgressBar

Do not use:

-   Jetpack Compose

## Networking

HTTP Client:

-   OkHttp 3.12.x

HTTP Server:

-   NanoHTTPD

JSON:

-   Gson

## Concurrency

Use:

-   ExecutorService
-   Thread
-   Handler

Avoid:

-   Coroutine
-   Flow
-   RxJava

------------------------------------------------------------------------

# 6. Architecture

    +--------------------------------+
    |             UI Layer            |
    | Activity / Fragment / XML View  |
    +---------------+----------------+
                    |
    +---------------v----------------+
    |          Service Layer          |
    | Transfer management             |
    +---------------+----------------+
                    |
            +-------+-------+
            |               |
    +-------v------+ +------v--------+
    | Discovery    | | HTTP Transfer |
    | UDP          | | OkHttp        |
    +--------------+ +---------------+
                    |
    +---------------v----------------+
    |          Storage Layer          |
    | Stream file IO                  |
    +--------------------------------+

------------------------------------------------------------------------

# 7. Module Structure

    org.localsend.kotlin/

    ├── model/
    ├── discovery/
    ├── protocol/
    ├── client/
    ├── server/
    ├── transfer/
    ├── security/
    └── ui/

------------------------------------------------------------------------

# 8. LocalSend Protocol Implementation

Reference:

https://github.com/localsend/protocol

The protocol repository is the single source of truth.

Do not invent incompatible behavior.

Implement:

-   device discovery
-   device information
-   prepare-upload
-   upload
-   cancel
-   file receiving

------------------------------------------------------------------------

# 9. MVP Development Phases

## Phase 1: Device Discovery

Implement:

-   UDP discovery
-   device announcement
-   device list UI

Goal:

Devices can discover each other.

------------------------------------------------------------------------

## Phase 2: File Sending

Implement:

-   file picker
-   prepare-upload
-   upload
-   transfer progress

Requirements:

-   streaming upload
-   no loading entire file into RAM

Buffer:

32KB - 64KB

------------------------------------------------------------------------

## Phase 3: File Receiving

Implement:

-   embedded HTTP server
-   receive upload request
-   stream file to storage

Use:

    InputStream
          |
    FileOutputStream

Never allocate entire file in memory.

------------------------------------------------------------------------

## Phase 4: Security

Implement:

-   self-signed certificate
-   fingerprint verification

Certificate should be generated once and stored.

Do not regenerate on every startup.

------------------------------------------------------------------------

# 10. Performance Requirements

Target:

APK:

\<10MB

Memory:

\<100MB

Avoid:

-   android:largeHeap=true

Do not force:

-   android:hardwareAccelerated=false

unless testing proves it is required.

------------------------------------------------------------------------

# 11. Development Rules For Agent

1.  Read LocalSend protocol before coding.
2.  Keep the project buildable after each step.
3.  Compile frequently:

```{=html}
<!-- -->
```
    ./gradlew assembleDebug

4.  Prefer simple Android APIs.
5.  Explain compatibility impact before adding dependencies.
6.  Do not over-engineer before MVP works.

------------------------------------------------------------------------

# 12. Testing Strategy

Priority:

1.  Android Emulator API 21
2.  Real Android 5+ device
3.  Kindle Fire / old tablet

------------------------------------------------------------------------

# 13. Future Roadmap

Possible future:

-   Android 4.x support
-   background receiver service
-   trusted devices
-   multiple file transfer
-   transfer history
-   lightweight CLI core

------------------------------------------------------------------------

# 14. First Agent Task

Create the initial Kotlin Android project.

Requirements:

-   XML UI
-   No Compose
-   Minimal dependencies
-   Android 5.0 minimum
-   Architecture ready for LocalSend protocol

After project creation:

Implement device discovery only.
