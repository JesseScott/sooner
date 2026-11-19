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
- One response was 393,734 bytes of binary protobuf covering the whole system
  (405,490 bytes on 19 Nov 2026). The app filters it for one `stop_id`.
  Refreshing every 30 seconds would be about 47 MB an hour while the app is
  open, so refresh about once a minute (about 24 MB an hour) and only while
  visible.
- The server does not compress: with `Accept-Encoding: gzip` or `br` it still
  returns the same 405,490 uncompressed bytes and no `Content-Encoding` header.
  (The same request to source B did come back gzipped, so the proxy was not
  stripping it.) gzip would have cut it to about 117 KB.
- No `ETag` or `Last-Modified` header, so conditional requests are not an
  option either.
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
| `t[].dl` | delay in seconds, negative = early; matches the feed's `delay` exactly (confirmed) |
| `t[].cs` | stop cancelled for this trip (confirmed: true at a stop the feed marks SKIPPED, with `vi` absent) |
| `po`, `do`, `tc` | probably pickup-only, drop-off-only, trip cancelled (unconfirmed; a cancelled trip was simply left out rather than shown with `tc` true) |
| `lu` | equals the GTFS-RT feed header timestamp, so this endpoint is built on the same feed |

Route 20 comes back as two groups because short-turns ("to 41st Ave") and full
trips ("To SE Marine Dr") have different headsigns. Merge, sort by `ut`, and
keep the headsign visible. Usually only the next one or two trips are live.

## Design decisions so far

- Keep the arrivals source behind one interface so A and B are swappable.
- The API key lives in `local.properties` (git-ignored) and is injected at
  build time. Anyone with the APK can extract it; acceptable for personal use,
  not for a public release without a small proxy server.
- Stack: Kotlin, Jetpack Compose, CameraX, ML Kit Text Recognition v2.

## Live feed check (19 Nov 2026, about 10 pm Vancouver time)

One trip-updates response from source A, decoded with `tools/decode_gtfsrt.py`
and compared with static GTFS and with source B for stop 51238 / route 20,
fetched within seconds of each other.

**Feed shape**

- 448 trip updates, 8,761 stop-time updates, 150 routes, 4,853 stops. Only
  `TripUpdate` entities; no vehicle positions or alerts mixed in.
- Only trips that have a vehicle assigned are present (445 of 448 carry a
  vehicle). Predictions reach at most about 90 minutes ahead. **Trips that
  have not started yet are not in the feed**, so it cannot fill an
  arrivals list on its own: anything beyond the next one or two buses per route
  has to come from the static schedule (or from source B, which merges both).
- `route_id` and `direction_id` are filled in on every trip. `start_time`, the
  trip-level `timestamp` and the trip-level `delay` are never set.
- Every stop update that is not skipped carries both an absolute
  `arrival.time` (Unix seconds) and `arrival.delay`, and the same for
  `departure`. Use `arrival.time` directly; no schedule lookup needed.

**Which stops each trip lists**

- Each trip lists its remaining stops, not just the next few: passed stops are
  dropped from the front. The final stop of the trip is never included (0 of
  448 trips reach their last `stop_sequence`), so a stop that is only ever the
  last stop of a trip will never get a prediction from this feed.
- 124 stop updates are `SKIPPED` (schedule_relationship 1) and have no times
  (a detour on route 20 at stop 980, for example). Hide those.
- 4 trips were `CANCELED` (trip schedule_relationship 3), including one on
  route 20. Their stop updates are all SKIPPED. Hide them, or show them as
  cancelled; do not fall back to the scheduled time.

**Stop 51238 (`stop_id` 1248) against source B**

| | Source A (GTFS-RT) | Source B (getaway) |
|---|---|---|
| Live trips | 15529226 and 15529227, route_id 6625 | the same two, `rt: true` |
| Arrival | 1795064731 and 1795065606 | `ut` identical |
| Delay | -123 s and -88 s | `dl` identical |
| Vehicle | 2554 and 2532 | `vi` identical |
| Later trips | none | scheduled trips with `rt: false`, up to `querySize` |

The two sources agree exactly for live trips. B adds scheduled trips and
headsigns, and drops cancelled trips for you.

**Time zone**

Trip 15529226 is scheduled at 22:07:34 in static `stop_times.txt`. With its
-123 s delay that is 22:05:31, which matches B's `dt` of 22:05, while the Unix
time was 05:05:31 UTC. So local service time was UTC-7 in November, not
UTC-8. Never apply a fixed offset: compute minutes as
`(arrival.time − now) / 60` from Unix times, and if scheduled times from
static GTFS are needed, convert them with `America/Vancouver` from the
device's time zone database.

## What this means for the app

- With source A alone, show live predictions for trips in the feed, and fill
  the rest of the list from a static schedule. That means bundling
  `stop_times` for each stop (much bigger than the current 135 KB table) or
  accepting "live buses only".
- Source B already merges live and scheduled trips per route, so it is the
  quicker path for a first version. Keep A behind the same interface.
- Filter A by `stop_id`, drop SKIPPED stop updates and CANCELED trips, group by
  `route_id`, sort by `arrival.time`.

## Open questions

- Confirm `po`, `do` and `tc` in source B.
- How long source B keeps working, and whether its use is allowed.
