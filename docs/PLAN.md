# Sooner: research and plan

Point the phone at a Metro Vancouver bus stop sign, read the stop number from
the photo, and show how many minutes until each bus arrives.

Status: planning. Nothing is built yet. Findings below were checked in
November 2026 unless marked as unconfirmed.

## Pipeline

1. **Capture.** CameraX photo, or pick one from the gallery.
2. **Read the stop number.** On-device OCR with ML Kit Text Recognition v2.
   Take the 5-digit number nearest the "Bus Stop #" label, then let the user
   confirm or edit it. Use the bundled Latin model (about 4 MB) so it works
   offline straight after install.
3. **Look up the stop.** A table shipped inside the app maps the sign number
   to TransLink's internal stop ID, the stop name and the routes that serve
   it. No network call.
4. **Fetch arrivals.** One of the two sources below.
5. **Show.** Route, destination, headsign and minutes to arrival, soonest
   first, with a live/scheduled marker. Refresh only while on screen.

## Stop number to stop ID (confirmed)

TransLink's static GTFS data:
`https://gtfs-static.translink.ca/gtfs/google_transit.zip` (no key, about 16 MB
zipped).

- The number on the sign is `stop_code` in `stops.txt`; the APIs use `stop_id`.
  Example: sign **51238** is `stop_id` **1248**, "Southbound Commercial Dr @
  William St".
- 8,744 of 8,930 stops have a `stop_code`.
- Routes for a stop come from `stop_times.txt` joined to `trips.txt`. Stop 1248
  is served by `route_id` 6625 (route `020`, shown as 20) and 6835 (N20).
- A trimmed table of codes, IDs, names and routes per stop is about 540 KB raw,
  about 135 KB gzipped. Small enough to bundle.
- TransLink republishes this data when schedules change, so the bundled table
  needs refreshing from time to time.

## Arrival data: two sources

### A. Official GTFS-realtime V3 feed (recommended)

- `https://gtfsapi.translink.ca/v3/gtfsrealtime?apikey=KEY` (trip updates).
  Also `gtfsposition` (vehicle positions) and `gtfsalerts`.
- Free key from TransLink's developer resources:
  https://www.translink.ca/about-us/doing-business-with-translink/app-developer-resources/register
  Terms of use are on the same site.
- The key goes in the URL, not a header. Without a valid key the API returns a
  bare 403.
- One response was 393,734 bytes of binary protobuf covering the whole system.
  The app filters it for one `stop_id`. Refreshing every 30 seconds would be
  about 47 MB an hour while the app is open, so refresh about once a minute and
  only while visible.
- The old RTTI API (per-stop "next bus" calls) was retired on 3 December 2024.
  Tutorials and libraries that use it no longer work.

### B. The website's internal endpoint (works, undocumented)

What translink.ca's stop pages call, about every 30 seconds:

```
https://getaway.translink.ca/api/gtfs/stop/{stop_code}/route/{route}/realtimeschedules?querySize={n}
```

- No key. Returns small JSON (a few KB per route).
- Needs a route as well as a stop, which the bundled table provides.
- Not a published API: it can change or be blocked without notice, and there is
  no stated permission to use it. Fine for a prototype; check the terms before
  relying on it.

Response fields seen for stop 51238, route 20:

| Field | Meaning |
|---|---|
| `id`, `sc`, `sn` | stop ID (1248), stop code (51238), stop name |
| `r[]` | groups of trips, one per route and headsign |
| `rs`, `dn`, `hs` | route number, direction name ("SE Marine Drive"), headsign ("Victoria/to 41st Ave") |
| `t[].ut` | arrival as Unix time; use this for minutes to arrival |
| `t[].dt` | service-day clock time; can exceed 24:00 ("24:36" = 12:36 am), so do not use |
| `t[].rt` | true for a live prediction, false for scheduled |
| `t[].vi` | vehicle number, only on live trips |
| `t[].dl` | probably delay in seconds, negative = early (unconfirmed) |
| `po`, `do`, `tc`, `cs` | probably pickup-only, drop-off-only, trip cancelled, stop cancelled (unconfirmed) |

Route 20 comes back as two groups because short-turns ("to 41st Ave") and full
trips ("To SE Marine Dr") have different headsigns. Merge, sort by `ut`, and
keep the headsign visible. Usually only the next one or two trips are live.

## Design decisions so far

- Keep the arrivals source behind one interface so A and B are swappable.
- The API key lives in `local.properties` (git-ignored) and is injected at
  build time. Anyone with the APK can extract it; acceptable for personal use,
  not for a public release without a small proxy server.
- Stack: Kotlin, Jetpack Compose, CameraX, ML Kit Text Recognition v2.

## Open questions

- Decode a real feed with `tools/decode_gtfsrt.py` and check: does stop 1248
  appear, do predictions carry absolute times or only delays, is `route_id`
  filled in, and does each trip list all remaining stops or only the next few.
- Compare feed predictions with source B's for the same stop.
- Does the feed server support gzip? That would cut the 394 KB a lot.
- Confirm the unconfirmed field meanings above.
