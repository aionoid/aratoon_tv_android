# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.0] - 2026-10-19

### Added

- Server selection screen between catalog and player: pick which playback
  server to use (servers differ in speed, availability, languages and
  quality)
- Single-server videos skip the list and play directly
- "Play (auto)" fallback when the server list cannot be loaded

### Fixed

- Selected server was ignored: navigation 2.8 stores optional route
  parameters under a key with the `?` suffix (`server?`), so the player
  always fell back to auto mode

## [0.2.0] - 2026-10-19

### Added

- End-to-end playback: hyperwatching stream chain + ExoPlayer (HLS/MP4) with
  Material 3 player controls (seek, play/pause, audio track selection)
- Headless WebView stream extractor as a generic fallback for obfuscated
  server pages (Mixdrop, Uqload, Lulustream, StreamHG, SaveFiles) — captures
  the real m3u8/mp4 the page's own JavaScript requests
- Audio track selection in the player (e.g. العربية / English), Arabic first
- Server fallback dialog on playback error (retry with a different server)

### Fixed

- WebView crashes: missing `com.google.android.gms.version` meta-data and
  WebView created off the main UI thread
- Black video on Waydroid: PlayerView now uses a TextureView surface
  (inflated from XML — no public setter in media3 1.4.1)
- Slow server pages (Waydroid right after boot): 35s extraction deadline with
  repeated play-button probes instead of a 12s one-shot timeout

## [0.1.0] - 2026-10-19

### Added

- Initial scaffold: Gradle project, Material 3 TV theme, EN/AR localization
- Home screen (Movies / Series / Latest / Search / Settings)
- Catalog browsing with All/Subtitled/Dubbed filters
- Series detail with seasons and episodes, full-text search
- Recently watched list on the home screen
