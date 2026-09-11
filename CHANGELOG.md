🌐 [English](CHANGELOG.md) · [Português (Brasil)](CHANGELOG.pt-BR.md)

# Changelog

This project follows milestone-style pre-release versioning while core protocol
and audio behavior is still being validated.

## [0.9.0-m8] - 2026-08-10

### Added

- Expandable channel rows with direct member rosters.
- Single-tap expansion and double-tap channel joining.
- Password prompt for protected channels reached by double tap.
- Public-repository license, notices, privacy and security policies,
  contribution guidance, templates, and CI configuration.

### Changed

- The current channel expands automatically after connection or a channel move.
- Channel member rows expose talking, microphone-muted, output-muted, and own
  client states.

## [0.8.0-m7] - 2026-08-09

### Added

- Per-user mute and 0-200% playback gain keyed by stable TeamSpeak identity.

## Earlier milestones

M0-M6 established ts3j protocol connectivity, foreground lifecycle,
bidirectional Opus audio, RNNoise denoising, microphone modes, audio routing,
reconnection, and channel switching.
