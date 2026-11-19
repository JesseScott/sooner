# Sooner

An Android app for Metro Vancouver transit: photograph a bus stop sign, and it
reads the stop number and shows how many minutes until each bus arrives.

Status: planning. See [docs/PLAN.md](docs/PLAN.md) for the research and design.

## API key

Real-time data uses TransLink's GTFS-realtime API, which needs a free key from
TransLink's developer resources. Put it in `local.properties` (git-ignored):

```
translink.apiKey=YOUR_KEY
```

Never commit the key.
