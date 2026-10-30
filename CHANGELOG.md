# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.10.7] - 2026-10-30

### Changed

- **Solid black tab bar on the catalogs (task 8 follow-up):** the full-bleed
  blurred backdrop behind the Movies/Series catalogs bled up behind the shared
  top tab bar, so the nav row showed a poster smear instead of the solid black
  used on the home screen. Gave the `TabBar` its own opaque black background
  (drawn above the content via `zIndex`) so the nav row is now a clean black
  band on every screen. Home was already black behind the bar, so it is
  visually unchanged.

## [0.10.6] - 2026-10-30

### Changed

- **Smooth catalog background + gradient scrim (task 8):** the full-screen
  backdrop behind the Movies/Series catalogs used a 24px blur that still left
  the low-res TMDB poster texture showing, plus a flat 0.35 black overlay with
  no gradient. Bumped the blur radius to 40px so the backdrop is smooth and
  uniform, and replaced the flat overlay with a vertical gradient scrim (dark
  ~0.85 at the top, light ~0.12 in the middle, dark ~0.85 at the bottom) so
  the nav bar and bottom edges darken for legibility while the centre stays
  visible for the spotlight/list content.
  Verified on Waydroid: Movies + Series catalogs now show a smooth blurred
  backdrop with the dark-top / light-middle / dark-bottom scrim and legible
  featured text.

## [0.10.5] - 2026-10-30

### Changed

- **Rounded search input (task 5):** the search field previously used
  Material 3's near-square default shape. Gave it a 14dp corner radius
  (matching the Flutter reference's `BorderRadius.circular(14)`) plus a
  subtle white 0.06 fill so the glass border reads like the reference.
  Verified on Waydroid: the Search-tab input now shows rounded corners.


## [0.10.4] - 2026-10-30

### Fixed

- **Search episode opened the wrong series (task 4):** the Stardima API
  returns `is_series: true` for both a series detail page
  (`/tvshow/{slug}`) and an episode page
  (`/tvshow/{slug}/play/{id}`). Clicking a search/catalog episode used
  `substringAfterLast("/")` as the series slug, which on an episode URL is
  just the numeric episode **id** — so the Series screen loaded an unrelated
  `/tvshow/{id}`.
- New `data.VideoItemRouting.isSeriesPage(item)` now gates the Series
  navigation: an item only opens the Series screen when it is series-flagged
  **and** the URL has no `/play/`. Episode pages route to the server
  selection screen instead, matching the Kodi addon's `actionResolve` path.
- Applied in `SearchScreen` and `CatalogScreen` (grid + spotlight).
- TDD: 5 new unit tests in `VideoItemRoutingTest.kt` (34/34 green).
  Verified on Waydroid: searching an episode title lands on the server
  list (Mixdrop / Uqload / Lulustream / Savefiles), not a wrong series.

### Changed
- **Top bar tab pills (task 2):** More breathing room inside the tab buttons.
  Internal padding increased to 16dp horizontal / 6dp vertical and a 128dp
  minimum width on the selected "Home" pill so the icon and label are set
  back from the gold edge instead of hugging it.

## [0.10.2] - 2026-10-30

### Added

- **TV banner icon** (Android TV requirement): the app launcher now shows
  the proper AraToons icon instead of a gray rectangle. Added 16:9
  `ic_banner.png` at all densities and set `android:banner` in the
  manifest.

- **TDD unit test suite** (29 tests): JUnit tests for `StreamResolver`
  and `EvalDecoder` — catches decoding and proxy-extraction regressions
  early without manual testing. Tests cover:
  - `EvalDecoder`: base-36 decode, Mixdrop identity decode, per-server
    extractors (Mixdrop, Uqload, Lulustream, StreamHG, Goodstream),
    `cleanJwPlayerUrl` (both formats), cookie extraction.
  - `StreamResolver`: queryParam, origin, iimag.top proxy extraction
    (Mixdrop, Uqload, Lulustream).

### Fixed

- **Bug found by TDD** — `EvalDecoder.cleanJwPlayerUrl` Format 1 used
  `groupValues[3]` (the second quality value, `t2`) instead of
  `groupValues[4]` (the last, `t3`). The wrong quality was substituted
  into the m3u8 URL for Uqload/Goodstream. Fixed to `groupValues[4]`.


## [0.10.1] - 2026-10-30

### Fixed

- **Playback — 2nd site-side change (iimag.top proxy)**: the site now wraps
  Uqload / Mixdrop / Lulustream in an `iimag.top/embed2/?id=...` JS form-submit
  stub. Previously only `strema.top` was handled. `StreamResolver` now extracts
  the real server URL from the `id=` param, fetches it directly, and applies
  the per-server decoders:
  - **Mixdrop** — direct MP4 via identity `eval` decode.
  - **Uqload** — base-36 `eval` decode (new handler).
  - **Lulustream** — katana `eval` decode (new handler).
  - StreamHG / Earnvids / Filemoon / Goodstream — existing handlers (unchanged).
  Servers whose direct decode fails still fall back to the headless WebView.
- Verified on the real TV: Mixdrop resolves + plays (MP4) end-to-end.


## [0.10.0] - 2026-10-30

### Changed

- Home preview is now full-bleed (edge-to-edge) and flush with the top of
  the content area — it starts immediately under the tab bar with no gap
  (immersive-list anatomy). The 20dp inset now applies to the rails only

### Added

- **Two-step episode list for series**: the series screen is now a two-panel
  layout — a persistent left column with the series header (poster, title,
  description) and a right panel that transitions (animated fade/slide) between
  the season list (step 1) and that season's episode list (step 2). Selecting
  a season animates to its episodes; selecting an episode opens the server list.
  BACK from the episode list returns to the season list; BACK from there goes
  home.
- Episode rows show a number badge (from the API `episode_number`) instead of
  a per-episode thumbnail — the site returns the same series-level image for
  every episode.

### Fixed

- **Series crash on back**: the series screen held non-`Saveable` state
  (`SeasonInfo`) in `rememberSaveable`, throwing `IllegalStateException` when
  leaving the screen. All series state is now plain `remember`; the data is
  re-fetched on composition.
- **Smart back navigation**: `onKeyEvent` on a Box can't catch system BACK.
  Replaced with `BackHandler(enabled = selectedSeason != null)` so BACK from the
  episode list returns to the season list, and BACK from the season list pops
  to home (via the nav host).
- **Playback regression (no servers)**: the site changed the episode URL format.
  The bare `/play/{id}` path now returns a generic "error" page with no embed;
  the slug-prefixed `/tvshow/{slug}/play/{id}` path is required. Series now
  navigate with the slug-prefixed URL, so the server list resolves again.

## [0.9.0] - 2026-10-29

### Added

- **New UI shell**: the left side-nav overlay is replaced by a horizontal
  tab bar (Home / Movies / Series / Recently Watched / Search / Settings)
  with the gold selected pill. A new top-level `AraTooonsShell` owns focus
  and hosts the tab bar + the selected tab's content; item-level
  navigation (server select, player) still pushes on top of the shell
- **Immersive-list home**: the home screen is now the Android TV
  immersive-list — a full-width cinematic hero preview at the top with content
  rails stacked below (Popular, Movies, Series, Recently Watched). The hero
  background is scaled up and shifted so the subject lands in the top-right
  corner (the Android TV "Composition" guideline), with the content block
  (title + description) over a legible scrim. The hero Play/Add buttons and
  carousel dots are gone — the hero is a single tappable/focusable preview
  area. Tapping or focusing a rail card sets the hero. The preview is set
  back from the tab bar and clipped to its bounds, so the over-cropped
  background (scaled per the "Composition" guideline) can never bleed into
  it
- **Rail focus wiring**: `ContentRail` routes LEFT/RIGHT to cycle cards,
  UP from the top rail to the preview, DOWN from the bottom rail to the
  tab bar, and crosses rails at the same card index (clamped)
- Catalog / Recently Watched / Search now accept `focusEntry` and stop
  auto-requesting focus on their own, so the shell owns focus

### Changed

- `AraTooonsShell` renders the HOME route in `NavGraph` (item-level nav
  pushes above it)
- Removed the old chrome composables: `SideNavEntry`, `SideNavItem`,
  `TopBarOverlay`, `MenuCard`, `HeroDots`
- The old home "side-nav + hero + 3 rails" layout is gone; the preview is
  now full-width at the top (immersive-list) instead of a top-left block
- **Centered icon tabs**: the top tab bar is now centered; each tab is an
  icon-only button that expands to icon + text only when selected (gold pill).
  The expand/collapse is animated, and the tabs use generous internal padding
  so the icon and label never stick to the pill edge

### Added

- **Server selection**: when an item opens, all available streaming
  servers are now listed by name (e.g. Mixdrop, Uqload, Lulustream,
  Streamhg, Savefiles) as focusable pills, with a hint that speed,
  languages and quality can differ per server. You pick the server
  explicitly before playback instead of auto-landing on the first one
- **Movies rail** on the home screen, alongside Recently Watched,
  Popular and Series
- **Isolated WebView extraction**: the headless WebView that decodes
  JS-protected server embeds now runs in its own `:wv` process
  (`WebViewExtractService`). If the vendor System WebView FATALs during
  init on a TV box, only that process dies and extraction falls back
  gracefully instead of killing the app
- **Hero navigation**: ← / → on the featured item switches it (and the
  full-screen background); ← at the first hero goes to the side nav,
  → at the last hero goes to the search field. The hero also
  auto-advances every 7 s like a carousel
- **Full D-pad topology**: DOWN from the hero lands on the first rail
  card; UP/DOWN between rails crosses to the same card index of the
  neighbouring rail (clamped); LEFT at the leftmost card of the top
  rail goes back to the hero; RIGHT at the rightmost card wraps around
  to the leftmost

### Changed

- `EvalDecoder` now handles both the classic and the rotated
  `eval()`/jsdeflate builds used by the Mixdrop-family servers (fixed
  a build that changed the call marker and broke MP4 extraction)
- `StreamResolver` resolves the **full server list** from the embed
  page's `data-page` JSON (was: single-server heuristic) and logs the
  whole resolution chain under the `Resolver` tag for debugging
- The headless extractor is now an orchestrator: `WebViewStreamExtractor`
  talks to the `:wv` service over a broadcast and returns null on
  timeout instead of hanging

### Fixed

- **Focus tracking broken app-wide**: `onFocusChanged` was attached
  *after* `clickable()`/`focusable()` in the modifier chain, but it
  only aggregates focus targets that follow it in the chain — so the
  card zoom/glow, side-nav highlight, page-number highlight, pill
  highlight and the hero's "a card has focus" state never turned on.
  Moved before the focusable node in all 8 affected composables
- LEFT/DOWN/RIGHT key presses aimed at the hero could be swallowed by a
  nearby card, making the hero appear to ignore the D-pad
- `getEmbedUrl` / `fetchEmbedInfo` no longer crash the resolver on a
  network error — the failure is logged and the item falls back to the
  next resolution path

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

### Fixed

- D-pad keys (notably UP) could stop working after the controls
  auto-hid: the player surface silently lost Compose focus when the
  overlay left the composition. Focus is now re-granted automatically
  and a root-view key backstop covers the in-between window
- Tap on the video now reliably resurfaces the controls (touch events
  are claimed by the player view so Compose forwards them)
- The unused built-in PlayerView control bar can no longer steal
  Android-level focus from the D-pad layer

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
