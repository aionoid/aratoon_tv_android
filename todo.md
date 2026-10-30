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
- Status: DONE — committed + release built (v0.10.0).
  Additional site-side change (2nd fix): the site now wraps Uqload/Mixdrop/Lulustream
  in an `iimag.top/embed2/?id=...` JS form-submit stub. The `StreamResolver` now
  extracts the real server URL from the `id=` param, fetches it directly, and adds
  Uqload + Lulustream decode handlers (bypassing the unreliable WebView fallback).
  Verified on TV: Mixdrop resolves + plays (MP4); Uqload/Lulustream fall back to
  WebView.

## TODO (user list — one commit + release per item)

-
  2. [ ] Top bar nav buttons: add padding between the inside content ("icon +
         text") and the edge of the button border.
-
  3. [ ] On the search page: replace the language filter dropdown with the same
         pagination used on the catalog pages (for "movies and series").
-
  4. [ ] Bug: the search results show some episodes, and clicking an episode
         takes you to a different series, not that episode.
-
  5. [ ] The search input on the search page needs to be rounded.
-
  6. [ ] the TV app lean back don't show app icon like on normal android.
-
  7. [ ] Add unit tests (TDD pattern) so we don't get in this kind of errors
         later and can reduce manual testing / save time. Cover: StreamResolver
         (iimag.top proxy extraction, server decoders), EvalDecoder (base-36
         decode, Mixdrop identity decode, cleanJwPlayerUrl), and Models parsing.
