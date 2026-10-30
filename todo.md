# Stardima TV App — Task List

## DONE

### 1. Fix playback regression (no servers working on TV) — v0.10.0

The site changed the episode URL format. The bare `/play/{id}` path now returns
a generic "error" page with no embed; the slug-prefixed
`/tvshow/{slug}/play/{id}` path is now required.

- Root cause: `SeriesScreen.playEpisode` passed `/play/${ep.id}`; the site no
  longer serves that path (returns an error page).
- Fix: `SeriesScreen` builds `/tvshow/$slug/play/${ep.id}` (falls back to
  `/play/${ep.id}` only if no slug is available).
- Verified (manual pipeline): slug-prefixed page -> `og:video` (hyperwatching
  embed) -> `data-page` JSON -> `props.video.servers` (5 servers: Uqload, etc.).
  The `StreamResolver` chain is unchanged and now receives a working URL.
- Status: DONE — committed + release built (v0.10.0). Additional site-side
  change (2nd fix): the site now wraps Uqload/Mixdrop/Lulustream in an
  `iimag.top/embed2/?id=...` JS form-submit stub. The `StreamResolver` now
  extracts the real server URL from the `id=` param, fetches it directly, and
  adds Uqload + Lulustream decode handlers (bypassing the unreliable WebView
  fallback). Verified on TV: Mixdrop resolves + plays (MP4); Uqload/Lulustream
  fall back to WebView.

## TODO (user list — one commit + release per item)

2. [x] Top bar nav buttons: add padding between the inside content ("icon +
       text") and the edge of the button border.
   - Increased internal padding from 14dp/5dp to 16dp/6dp and added a 128dp
     minimum width on the selected "Home" pill so the icon and label sit
     comfortably inside the gold border instead of hugging the edge.
   - Status: DONE — committed (v0.10.3).

3. [x] On the search page: replace the language filter dropdown with the same
       pagination used on the catalog pages (for "movies and series").
    - Replaced the `PageDropdown` pagination in SearchScreen with the
      `CatalogPageBar` (glass pill bar with page numbers + prev/next arrows),
      the same pattern used by CatalogScreen.
    - Status: DONE — committed.

4. [x] Bug: the search results show some episodes, and clicking an episode takes
    you to a different series, not that episode.
   - Root cause: StarDima API returns `is_series: true` for BOTH series detail
     pages and episode pages. The click handler used `item.isSeries` to decide,
     then extracted the slug with `substringAfterLast("/")` — for an episode
     URL `/tvshow/{slug}/play/{id}` this gave the numeric episode ID, so
     SeriesScreen loaded the wrong `/tvshow/{id}`.
   - Fix: new `VideoItemRouting.isSeriesPage(item)` — true only when
     `isSeries` AND the URL does NOT contain `/play/`. Applied to the click
     handlers in SearchScreen and CatalogScreen (3 spots). Episode pages now
     route to `Routes.servers(...)` (server selection), matching the Kodi
     addon's behavior (`actionResolve`).
   - TDD: 5 new unit tests in `VideoItemRoutingTest.kt` (34/34 pass).
   - Status: DONE — committed + release built.

5. [x] The search input on the search page needs to be rounded.
   - The search field used Material 3's near-square default shape. Matched the
     Flutter reference's `BorderRadius.circular(14)` with a 14dp corner radius
     plus a subtle white 0.06 fill for the glass border.
   - Status: DONE — committed + release built.

6. [x] the TV app lean back don't show app icon like on normal android.
   - Root cause: Android TV requires a 16:9 `android:banner` in addition to the
     launcher icon. The manifest was missing it, so the TV launcher showed a
     gray rectangle instead of the app icon.
   - Fix: added `ic_banner.png` (16:9, logo centered) at all densities
     (mdpi-xxxhdpi) and set `android:banner="@mipmap/ic_banner"` in
     AndroidManifest.xml.
   - Status: DONE — committed.

7. [x] Add unit tests (TDD pattern) so we don't get in this kind of errors later
       and can reduce manual testing / save time.
   - Added JUnit 4 + kotlinx-coroutines-test + Truth test dependencies.
   - `EvalDecoderTest` (16 tests): toBase36, decodeBase36, decodeMixdrop,
     extractMixdropUrl, extractJwPlayerUrl, extractStreamHgUrl,
     extractLulustreamUrl, extractGoodstreamUrl, cleanJwPlayerUrl (both
     formats), extractJwPlayerCookies.
   - `StreamResolverTest` (13 tests): queryParam (basic, null cases, multiple
     params, strema.top), origin (https, http, port, no-path, invalid),
     iimag.top proxy (mixdrop, uqload, lulustream).
   - **Bug found by TDD:** `cleanJwPlayerUrl` Format 1 used `groupValues[3]`
     (t2) instead of `groupValues[4]` (t3) — the wrong quality value was
     substituted. Fixed to `groupValues[4]`.
   - Status: DONE — committed.

8. [x] the background image on the catalogs "Movies/Series" is pixilated, blure
       it and make it smooth. and apply Gradient Scrim so the top is dark and
       the bottom is dark and the middle is visible
   - The full-screen catalog backdrop used a 24px blur that still left the
     low-res TMDB poster texture showing, plus a flat 0.35 black overlay (no
     gradient). Bumped the blur radius to 40px for a smooth, uniform backdrop
     and replaced the flat overlay with a vertical gradient scrim (dark ~0.85 at
     top, light ~0.12 in the middle, dark ~0.85 at the bottom) so the nav bar
     and bottom edges darken for legibility while the centre stays visible for
     the spotlight/list content.
   - Status: DONE — committed + release built.
