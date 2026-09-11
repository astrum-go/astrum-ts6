🌐 [English](README.md) · [Português (Brasil)](README.pt-BR.md)

# TS6 Mobile Community

TS6 Mobile Community is an open-source Android client for TeamSpeak,
built on the full client protocol provided by
[Manevolent/ts3j](https://github.com/Manevolent/ts3j) with WebRTC video and screen sharing support for TeamSpeak 6.

This is an unofficial community project. It is not affiliated with, endorsed
by, or sponsored by TeamSpeak Systems GmbH. TeamSpeak and related names and
marks are the property of their respective owners.

---

## 💡 Project Motivation

This project was developed in response to blocks and connectivity disruptions affecting Discord in Brazil, which prompted the search for reliable, independent communication alternatives. The primary goal was to create a legitimate, high-performance mobile solution for screen sharing and video streaming — whether to watch media together or broadcast game sessions with my girlfriend — using the decentralized and self-hostable infrastructure of TeamSpeak 6.

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

## Contributing and security

Read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a change and follow
the [Code of Conduct](CODE_OF_CONDUCT.md). Do not open a public issue for a
suspected vulnerability; use the process in [SECURITY.md](SECURITY.md).

## License

TS6 Mobile Community is licensed under the [GNU Affero General Public License v3.0](LICENSE). Components from
other projects remain under their respective licenses; see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Next gate

Verify long-running duplex audio, simultaneous speakers, packet loss, audio
focus, Bluetooth duplex behavior, route hot-plugging, subjective echo and voice
quality, and minimum-API behavior on more physical devices. Input-level
diagnostics and automatic voice activation remain the next functional targets.
