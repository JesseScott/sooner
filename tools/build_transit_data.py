#!/usr/bin/env python3
"""Build the transit tables bundled in the app from TransLink's static GTFS.

Usage:
    python3 tools/build_transit_data.py [GTFS_ZIP] [OUT_DIR]

GTFS_ZIP defaults to downloading
https://gtfs-static.translink.ca/gtfs/google_transit.zip (no key needed).
OUT_DIR defaults to app/src/main/assets/transit.

Writes tab-separated UTF-8 files, one record per line, no header:

  stops.tsv   stop_code, stop_id, stop_name, route_ids (space-separated)
              Only stops that have a stop_code (the number on the sign).
  routes.tsv  route_id, display name ("020" becomes "20"), route_long_name
  headsigns.tsv  one headsign per line; line N (from 0) is headsign N
  trips.tsv   first_trip_id, last_trip_id, headsign index
              Runs of consecutive numeric trip IDs that share a headsign, in
              ascending order. Lets the app label a live trip from the
              GTFS-realtime feed, which carries trip_id and route_id but no
              headsign. (63,000 trips fit in about 6,000 runs.)
  feed.tsv    key, value: feed_version and the dates the schedule covers.

Uses only the standard library. TransLink republishes the GTFS when schedules
change; rerun this script and commit the result to refresh the app's data.
"""
import csv
import io
import os
import re
import sys
import tempfile
import urllib.request
import zipfile
from collections import defaultdict

GTFS_URL = "https://gtfs-static.translink.ca/gtfs/google_transit.zip"
DEFAULT_OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                           "..", "app", "src", "main", "assets", "transit")


def rows(zf, name):
    """Yield each row of a GTFS file in the zip as a dict, streaming."""
    with zf.open(name) as raw:
        text = io.TextIOWrapper(raw, encoding="utf-8-sig", newline="")
        yield from csv.DictReader(text)


def clean(value):
    """Tabs and newlines would break the TSV format; GTFS text shouldn't have them."""
    return " ".join((value or "").split())


def display_route(short_name, long_name):
    short = (short_name or "").strip()
    if short:
        # TransLink pads bus numbers: "020" -> "20", "002" -> "2". Leaves "N20", "R4".
        return re.sub(r"^0+(?=\d)", "", short)
    return clean(long_name)


def route_sort_key(display):
    """Natural order: 2, 20, 99, then N8, N9, N35, then R4, then names."""
    m = re.match(r"^([A-Za-z]*)(\d+)(.*)$", display)
    if not m:
        return (2, "", 0, display)
    prefix, number, rest = m.groups()
    return (1 if prefix else 0, prefix, int(number), rest)


def trip_runs(trip_headsign, headsign_index):
    """Collapse trips into (first_id, last_id, headsign_index) runs of consecutive IDs."""
    bad = [t for t in trip_headsign if not t.isdigit()]
    if bad:
        raise SystemExit(f"non-numeric trip_id values, the run format needs numbers: {bad[:10]}")
    runs = []
    for trip_id, headsign in sorted(((int(t), h) for t, h in trip_headsign.items())):
        idx = headsign_index[headsign]
        if runs and runs[-1][1] == trip_id - 1 and runs[-1][2] == idx:
            runs[-1][1] = trip_id
        else:
            runs.append([trip_id, trip_id, idx])
    return [[str(v) for v in run] for run in runs]


def build(zip_path, out_dir):
    with zipfile.ZipFile(zip_path) as zf:
        routes = {}
        for r in rows(zf, "routes.txt"):
            routes[r["route_id"]] = (
                display_route(r.get("route_short_name"), r.get("route_long_name")),
                clean(r.get("route_long_name")),
            )

        trip_route, trip_headsign = {}, {}
        for t in rows(zf, "trips.txt"):
            trip_route[t["trip_id"]] = t["route_id"]
            trip_headsign[t["trip_id"]] = clean(t.get("trip_headsign"))

        stop_routes = defaultdict(set)
        for st in rows(zf, "stop_times.txt"):
            route_id = trip_route.get(st["trip_id"])
            if route_id is not None:
                stop_routes[st["stop_id"]].add(route_id)

        stops = []
        for s in rows(zf, "stops.txt"):
            code = (s.get("stop_code") or "").strip()
            if not code:
                continue
            served = sorted(stop_routes.get(s["stop_id"], ()),
                            key=lambda rid: route_sort_key(routes.get(rid, (rid,))[0]))
            stops.append((code, s["stop_id"], clean(s.get("stop_name")), " ".join(served)))

        feed = {}
        if "feed_info.txt" in zf.namelist():
            for f in rows(zf, "feed_info.txt"):
                for key in ("feed_version", "feed_start_date", "feed_end_date"):
                    if f.get(key):
                        feed[key] = f[key]
                break

    stops.sort(key=lambda s: s[0])
    codes = [s[0] for s in stops]
    if len(codes) != len(set(codes)):
        dupes = sorted({c for c in codes if codes.count(c) > 1})
        raise SystemExit(f"duplicate stop_code values: {dupes[:10]}")

    os.makedirs(out_dir, exist_ok=True)

    def write(name, records):
        path = os.path.join(out_dir, name)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            for rec in records:
                f.write("\t".join(rec) + "\n")
        return path

    write("stops.tsv", stops)
    write("routes.tsv", sorted(((rid, d, ln) for rid, (d, ln) in routes.items()),
                               key=lambda r: route_sort_key(r[1])))
    headsigns = sorted(set(trip_headsign.values()))
    write("headsigns.tsv", ([h] for h in headsigns))
    write("trips.tsv", trip_runs(trip_headsign, {h: i for i, h in enumerate(headsigns)}))
    write("feed.tsv", sorted(feed.items()))

    print(f"stops with a code: {len(stops):,}  "
          f"without routes: {sum(1 for s in stops if not s[3]):,}")
    print(f"routes: {len(routes):,}  trips: {len(trip_headsign):,}")
    for name in ("stops.tsv", "routes.tsv", "headsigns.tsv", "trips.tsv", "feed.tsv"):
        print(f"  {name}: {os.path.getsize(os.path.join(out_dir, name)):,} bytes")


def main():
    zip_path = sys.argv[1] if len(sys.argv) > 1 else None
    out_dir = sys.argv[2] if len(sys.argv) > 2 else DEFAULT_OUT
    if zip_path:
        build(zip_path, out_dir)
        return
    with tempfile.TemporaryDirectory() as tmp:
        zip_path = os.path.join(tmp, "google_transit.zip")
        print(f"downloading {GTFS_URL}")
        urllib.request.urlretrieve(GTFS_URL, zip_path)
        build(zip_path, out_dir)


if __name__ == "__main__":
    main()
