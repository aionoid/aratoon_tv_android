# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.7.0] - 2026-10-27

### Added

- Player: **fast-forward / rewind (±10 s)** buttons in the control bar
  (D-pad left/right already seeked; now visible as buttons too)
- Player: **subtitle selection** — a Subtitles button (top bar) with a
  dialog offering each available text track plus an **Off** option.
  Shown only when the stream carries subtitle tracks
- Player: **Arabic is the default** for both audio and subtitles when an
  Arabic track exists (user preference); the existing language selector
  still lets you switch at any time

### Changed

- Player controls now **auto-hide** a few seconds after playback starts
  or the last interaction, and resurface on any tap, D-pad center, seek,
  or UP — the video is unobstructed by default

## [0.6.0] - 2026-10-27

### Changed

- Home hero reworked to match the Flutter reference exactly: the card now
  shows a **sharp** hero image (never blurred) — full-bleed cover for
  backdrops (`/b/`), or the whole poster sharp in a 2:3 box on the right —
  with a surface card, 2dp white border, the left→right dark gradient
  (0.62 → 0.30 → 0.05) and a 140px bottom fade. Posters are up-scaled to
  `w780` (TMDB width token) so the hero stays sharp on TV
- Hero text block moved to the top-left (clear of the poster), title is
  extra-bold, description is white (3 lines)
- Hero "Watch now" now reads **"Watch episode"** for series; the gold
  button and the glass circle both open the item (Flutter `_openItem`)

### Fixed

- Hero "Watch now" did not respond to **touch** on tablets/phones: the
  `tv-material` `Button` only handled D-pad ENTER. Swapped to
  `material3.Button`, which handles both D-pad and touch
- Hero always routed to the server screen, dead-ending for series
  ("No servers available"). Series now open the **Series screen**
  (Flutter `_openItem` behaviour); movies still open servers
- Player: D-pad keys (incl. DOWN) could bubble to the nav host and pop
  the player while the stream was still resolving or in the error state.
  D-pad keys are now always consumed; only BACK exits
- `VideoItem` now carries `backgroundUrl` (API `background_url`) and the
  hero uses it when present

## [0.5.0] - 2026-10-25

### Added

- Full UI recreation matching the Flutter reference app
  (stardima_tv_flutter): home layout, side navigation, hero carousel,
  content rails, spotlight cards, focus states and glass effects
- Home: hero carousel (blurred backdrop + gradient + title/description +
  gold "Watch now" + add button + pagination dots) over a full-screen
  blurred backdrop, with a "Popular" poster rail and episode / recently
  watched rails with chevron scroll buttons. Tapping a poster re-selects
  the hero (Netflix-style) — backdrop, text and Watch-now target follow
  the selection, and the selected poster is outlined in gold
- Catalog: tappable page bar (jump to any page) plus a List/Grid view
  toggle
- Player: "ended" overlay with a gold Replay pill, D-pad chrome control
  (UP shows / DOWN hides the chrome), bottom info row
- Dedicated Recently Watched screen (grid of watched items)
- Left sidebar navigation (Home / Movies / Series / Recently Watched /
  Search / Settings) with a two-tone AraToons logo
- Gold/amber accent theme (Material 3) across the app; English/Arabic
  strings for all new screens
- Quality (e.g. "1080p") parsed from the API and shown on the hero
- Redesigned poster cards: cropped image with a dark gradient scrim
  fading into a text panel (title + year, short description, quality);
  thin light border (gold when focused) and a soft drop shadow

### Fixed

- Blurred background never rendered: the hero bitmap was drawn at its
  native size while positioned for the scaled size, landing off-screen —
  now drawn as a scaled destination rect (ContentScale.Crop)
- Compose 1.7.3 API drift: `animateContentSize` (androidx.compose.
  animation), `KeyboardActions` (foundation.text), `key`/`type`
  extensions on Compose KeyEvent, and `IntOffset`/`IntSize` moved to
  androidx.compose.ui.unit

## [0.4.0] - 2026-10-20

### Added

- Explicit pagination for catalogs (movies/series) and search: Previous /
  Next buttons plus a "Page X / Y" dropdown to jump to any page (the server
  serves 15 items per page, e.g. 108 pages of movies)
- Changing the language filter (All / Subtitled / Dubbed) returns to page 1
- App now locks to landscape orientation (TV-first: phones match the TV
  screen ratio and the full home navigation row is visible)

### Fixed

- Catalogs and search only ever showed the first 15 titles: the infinite
  scroll never re-triggered on scroll (stable grid-state identity) and its
  derived state captured the initial empty list

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
