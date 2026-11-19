# Sooner

An Android app for Metro Vancouver transit: photograph a bus stop sign, and it
reads the stop number and shows how many minutes until each bus arrives.

Status: early scaffold. The app builds to a placeholder screen. See
[docs/PLAN.md](docs/PLAN.md) for the research and design.

## Build

Requires the Android SDK (API 35) and JDK 17. Open the project in Android
Studio, or from the command line:

```
./gradlew assembleDebug        # build the debug APK
./gradlew testDebugUnitTest    # run unit tests
```

## Layout

- `app/` Android app (Kotlin, Jetpack Compose, CameraX, ML Kit).
- `app/src/main/java/tt/co/jesses/sooner/arrivals/` the `ArrivalsSource`
  interface that the GTFS-realtime and translink.ca sources will implement.
- `app/src/main/assets/transit/` stop, route and headsign tables generated
  from TransLink's static GTFS. Do not edit by hand.
- `tools/` helper scripts, such as the GTFS-realtime decoder.
- `docs/` research and plan.

## API key

Real-time data uses TransLink's GTFS-realtime API, which needs a free key from
TransLink's developer resources. Put it in `local.properties` (git-ignored):

```
translink.apiKey=YOUR_KEY
```

The build reads it into `BuildConfig.TRANSLINK_API_KEY`. Never commit the key.

## Refreshing the transit tables

TransLink republishes its static GTFS when schedules change. To refresh the
bundled tables (Python 3, no packages needed; downloads about 16 MB):

```
python3 tools/build_transit_data.py
```

Commit the changed files under `app/src/main/assets/transit/`. `feed.tsv`
records which GTFS version they came from.
