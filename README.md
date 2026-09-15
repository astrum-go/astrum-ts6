🌐 [English](README.md) · [Português (Brasil)](README.pt-BR.md)

# Astrum TS6

**Astrum TS6** (Astrum - Client for TS6) is an open-source Android client for TeamSpeak,
built on the full client protocol provided by
[Manevolent/ts3j](https://github.com/Manevolent/ts3j) with WebRTC video and screen sharing support for TeamSpeak 6.

This is an unofficial community project. It is not affiliated with, endorsed
by, or sponsored by TeamSpeak Systems GmbH. TeamSpeak and related names and
marks are the property of their respective owners.

---

## 💡 Project Motivation

This project was developed in response to blocks and connectivity disruptions affecting Discord in Brazil, which prompted the search for reliable, independent communication alternatives. The primary goal was to create a legitimate, high-performance mobile solution for screen sharing and video streaming — whether to watch media together or broadcast game sessions with my girlfriend — using the decentralized and self-hostable infrastructure of TeamSpeak 6.

---

## 🏛️ Origins and Credits (Project Foundation)

This project was built and evolved upon the foundation originally established by [monet4070/ts3-mobile](https://github.com/monet4070/ts3-mobile), which laid the essential mobile groundwork, initial Android architecture, and baseline TeamSpeak 3 client protocol integration via [Manevolent/ts3j](https://github.com/Manevolent/ts3j).

### ✨ What Was Built and Evolved in This Community Edition:
- **TeamSpeak 6 WebRTC Video & Camera Streaming**: Implementation of a complete WebRTC signaling and media layer compatible with TeamSpeak 6, allowing mobile camera broadcasting (with upright physical orientation and dynamic portrait/landscape adaptation) and multi-stream viewing with pinch-to-zoom and fullscreen overlays.
- **Mobile Screen Sharing with System Audio**: Low-latency screen capture via Android's `MediaProjection`, featuring internal audio playback capture (Android 10+) to broadcast game/media sound directly alongside video, complete with customizable resolution, FPS, and bitrate presets.
- **Neural Noise Suppression Pipeline (Astrum Clarity)**: Integration of transient suppression, neural AI denoising via DeepFilterNet and RNNoise v0.2, VoiceGate expander, and individual speaker gain controls.
- **Complete Visual Re-engineering**: Total UI overhaul built with modern Jetpack Compose dark styling, bookmarked servers manager with quick-connect, new Astrum launcher icon branding, and intuitive mobile ergonomics.

---

## Current milestone: M8 channel roster

The current build provides:

- TeamSpeak identity generation and AES-GCM storage backed by Android Keystore
- foreground UDP connection service with a notification disconnect action
- server address, port, password, and nickname input
- connection, cancellation, disconnection, and error states
- hierarchical channel view with direct channel-member rosters
- single-tap channel expansion and double-tap channel joining
- public and password-protected channel switching with current-channel state
- OPUS_VOICE and OPUS_MUSIC receive support through official libopus
- per-speaker jitter buffers, packet-loss concealment, and PCM mixing
- foreground playback with a speaker mute control
- always-off, push-to-talk, and continuous microphone modes
- 48 kHz mono, 20 ms, 64 kbps Opus voice encoding and ts3j voice transmission
- constrained VBR, fullband Opus, acoustic echo cancellation, and RNNoise v0.2
- always-on 10 ms neural denoising with Android automatic gain control disabled
- bounded capture and encoded-frame queues to prevent latency growth
- microphone foreground-service activation only while transmitting
- communication-device selection for earpiece, speaker, wired, Bluetooth, and USB
- automatic fallback to system routing when a selected device is disconnected
- cancellable automatic reconnection with network-aware 1-30 second backoff
- stale-session callback rejection and in-memory restoration of the last channel
- per-user mute and 0-200% playback gain keyed by stable TeamSpeak identity

Whisper transmission, automatic voice activation, text chat, file transfer,
permissions administration, bookmarks, and multi-server tabs are not
implemented. Treat the app as pre-release software and keep another client
available when testing on important servers.

## Privacy

The project includes no analytics, advertising, telemetry, crash-reporting SDK,
or project-operated backend. The app connects directly to servers selected by
the user. The TeamSpeak identity is encrypted locally using Android Keystore.
Connection details are kept in app/service memory while needed by the
connection workflow; they are not deliberately persisted. A password used to
restore a channel remains in the foreground service's memory only.

See [PRIVACY.md](PRIVACY.md) for the complete data-handling statement.

## Modules

- `app`: Compose UI, Android lifecycle, foreground service, and identity vault
- `ts6-protocol`: JVM-only ts3j facade, models, session generation, and channel ordering
- `audio-opus`: JNI libopus codec, capture, denoising, jitter buffering, mixing,
  and Android audio I/O

The compatibility layer pins ts3j commit
`db57d60c989e399626aa16d921390f5033e6cdeb` through JitPack. A maintained fork
and dependency locking are still required before a stable product release.

The app uses the BSD-licensed Xiph libopus 1.3.1 Prefab package and vendors the
official Xiph RNNoise v0.2 model at commit `904a876d`. License texts and exact
provenance are documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Build

Requirements:

- JDK 17
- Android SDK Platform 35
- Android Build Tools 35.0.0
- Android NDK 27.0.12077973
- CMake 3.22.1

Set `sdk.dir` in an untracked `local.properties`, then run:

```powershell
.\gradlew.bat :ts6-protocol:test :audio-opus:testDebugUnitTest :audio-opus:lintDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

With a physical Android device connected, run the native codec test with:

```powershell
.\gradlew.bat :audio-opus:connectedDebugAndroidTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
GitHub Actions runs the JVM/unit tests, lint, and debug build for every pull
request and push to `main`.

### Optional Rust core APK integration

The app does not activate `AstrumCoreSessionClient` by default. When a clean
`astrum-core` checkout at revision `abfd54d` is available, Cargo can build the
two packaged ABIs and the opt-in UniFFI Kotlin bindings without committing
native binaries or generated sources:

```bash
./gradlew :app:assembleDebug \
  -PastrumCoreDir=/path/to/clean/astrum-core \
  -PastrumCoreRuntime=true
./gradlew :ts6-protocol:generateAstrumCoreKotlin \
  -PastrumCoreDir=/path/to/clean/astrum-core \
  -PastrumCoreRuntime=true
./gradlew :app:inspectAstrumCoreApk \
  -PastrumCoreDir=/path/to/clean/astrum-core \
  -PastrumCoreRuntime=true
```

The task uses `ANDROID_NDK_ROOT`, or
`ANDROID_HOME/ndk/27.0.12077973` as a fallback, and writes generated libraries
under `app/build/generated/cargo/jniLibs/`. The UniFFI task uses the
`uniffi-bindgen` 0.32.1 executable and writes Kotlin under
`ts6-protocol/build/generated/astrumCore/kotlin/`. It refuses to run if the supplied
checkout has a different `HEAD` or any tracked/untracked changes; in
particular, a dirty checkout must not be used for this build. The inspection
task verifies that the debug APK contains only the expected `astrum_core`
library entries for `arm64-v8a` and `x86_64`, and compares their SHA-256 hashes
with the generated outputs. The opt-in `AstrumCoreMobileSession.sendVoiceFrame`
API accepts Opus Voice codec `4` or Opus Music codec `5` and forwards the
non-empty payload unchanged; the default production path continues to use the
existing `AstrumCoreSessionClient` JNI integration.

## Contributing and security

Read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a change and follow
the [Code of Conduct](CODE_OF_CONDUCT.md). Do not open a public issue for a
suspected vulnerability; use the process in [SECURITY.md](SECURITY.md).

## License

Astrum TS6 is licensed under the [GNU Affero General Public License v3.0](LICENSE). Components from
other projects remain under their respective licenses; see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Next gate

Verify long-running duplex audio, simultaneous speakers, packet loss, audio
focus, Bluetooth duplex behavior, route hot-plugging, subjective echo and voice
quality, and minimum-API behavior on more physical devices. Input-level
diagnostics and automatic voice activation remain the next functional targets.
