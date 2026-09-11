🌐 [English](CONTRIBUTING.md) · [Português (Brasil)](CONTRIBUTING.pt-BR.md)

# Contributing

Thanks for helping improve Astrum TS6. This is an open-source client, so
changes should preserve connection stability, bounded audio latency, and user
control over microphone transmission.

## Before opening a change

- Search existing issues and pull requests.
- Use an issue for behavior changes that need design agreement.
- Never include server passwords, identity files, real server addresses,
  participant information, local SDK paths, APKs, keystores, or signing keys.
- Keep changes focused and follow the existing Kotlin and Compose style.
- New protocol and audio behavior should include focused tests where practical.

## Local checks

Use JDK 17 and the Android/NDK versions listed in the README. Before submitting
a pull request, run:

```powershell
.\gradlew.bat :ts6-protocol:test :audio-opus:testDebugUnitTest :audio-opus:lintDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Changes to native audio or device interaction should also be tested on a
physical device. Describe the tested device, Android version, audio route, and
result without exposing private server details.

## Pull requests

Explain the user-visible behavior, technical approach, tests performed, and
known limitations. By submitting a contribution, you represent that you have
the right to provide it under the repository's Apache License 2.0 and that any
third-party material is identified with its applicable license.

All contributors must follow [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
