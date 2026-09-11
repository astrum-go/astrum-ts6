🌐 [English](PRIVACY.md) · [Português (Brasil)](PRIVACY.pt-BR.md)

# Privacy

Last updated: 2026-08-10

Astrum TS6 is an unofficial client that connects directly from the Android
device to TeamSpeak servers selected by the user. The maintainers do not
operate an intermediary service for those connections.

## Data handled on the device

- Server address, port, nickname, and server password remain in app/service
  memory while needed for the active connection workflow. The project does not
  deliberately persist them to app storage.
- The generated TeamSpeak identity is encrypted locally with AES-GCM using a key
  protected by Android Keystore.
- Server and channel passwords are used only for the requested connection or
  channel operation. A channel password needed for reconnection is retained in
  foreground-service memory and is discarded when that service process ends.
- Microphone audio is captured only while the selected microphone mode is
  transmitting. It is encoded and sent directly to the connected server.
- Incoming voice traffic is decoded and played locally. The app does not record
  or archive voice traffic.
- Per-user mute and volume preferences are stored locally and keyed by the
  participant's stable TeamSpeak identity when available.

## Data not collected by this project

The app contains no analytics, advertising, telemetry, project-operated crash
reporting, or project-operated backend. The maintainers therefore do not
receive connection details, identities, audio, or usage events from the app.

The TeamSpeak server operator, network provider, Android platform, device
vendor, and any services used to obtain a build may process data under their own
terms. Review the policies of the server and distribution channel you use.

## Permissions

The app requests network access to connect to servers, microphone access for
voice transmission, Bluetooth/audio-device permissions for route selection,
notification access for the foreground connection service, and the Android
permissions required to keep that service active. Denying optional permissions
may disable the related feature.

## Deletion

Uninstalling the app removes its app-private per-user audio preferences and
encrypted identity under normal Android behavior. Clearing the app's storage
has the same effect. Data already received by a TeamSpeak server or other
participants is outside the project's control.

Privacy concerns that are not sensitive may be filed as repository issues.
For sensitive reports, use the private process described in [SECURITY.md](SECURITY.md).
