#!/usr/bin/env python3
"""Summarise a GTFS-realtime trip-updates feed using only the standard library.

Usage: python3 decode_gtfsrt.py FEED.pb [STOP_ID]

Prints feed totals, a sample trip update, and (if STOP_ID is given)
every predicted arrival at that stop, soonest first.
Field numbers follow gtfs-realtime.proto.
"""
import sys
import time
from collections import Counter


def varint(buf, i):
    shift = result = 0
    while True:
        b = buf[i]
        i += 1
        result |= (b & 0x7F) << shift
        if not b & 0x80:
            return result, i
        shift += 7


def fields(buf):
    """Yield (field_number, wire_type, value) for one message."""
    i, n = 0, len(buf)
    while i < n:
        key, i = varint(buf, i)
        num, wt = key >> 3, key & 7
        if wt == 0:
            val, i = varint(buf, i)
        elif wt == 1:
            val, i = buf[i:i + 8], i + 8
        elif wt == 2:
            ln, i = varint(buf, i)
            val, i = buf[i:i + ln], i + ln
        elif wt == 5:
            val, i = buf[i:i + 4], i + 4
        else:
            raise ValueError(f"unsupported wire type {wt}")
        yield num, wt, val


def signed64(v):
    return v - (1 << 64) if v >= 1 << 63 else v


def parse_event(buf):  # StopTimeEvent: delay=1, time=2
    ev = {}
    for num, _, val in fields(buf):
        if num == 1:
            ev["delay"] = signed64(val)
        elif num == 2:
            ev["time"] = signed64(val)
    return ev


def parse_stu(buf):  # StopTimeUpdate
    stu = {}
    for num, _, val in fields(buf):
        if num == 1:
            stu["stop_sequence"] = val
        elif num == 2:
            stu["arrival"] = parse_event(val)
        elif num == 3:
            stu["departure"] = parse_event(val)
        elif num == 4:
            stu["stop_id"] = val.decode()
        elif num == 5:
            stu["schedule_relationship"] = val
    return stu


def parse_trip_update(buf):
    tu = {"stop_time_updates": []}
    for num, _, val in fields(buf):
        if num == 1:  # TripDescriptor
            trip = {}
            for n2, _, v2 in fields(val):
                key = {1: "trip_id", 2: "start_time", 3: "start_date",
                       4: "schedule_relationship", 5: "route_id",
                       6: "direction_id"}.get(n2)
                if key:
                    trip[key] = v2.decode() if isinstance(v2, bytes) else v2
            tu["trip"] = trip
        elif num == 2:
            tu["stop_time_updates"].append(parse_stu(val))
        elif num == 3:  # VehicleDescriptor
            veh = {}
            for n2, _, v2 in fields(val):
                if n2 in (1, 2):
                    veh[{1: "id", 2: "label"}[n2]] = v2.decode()
            tu["vehicle"] = veh
        elif num == 4:
            tu["timestamp"] = val
        elif num == 5:
            tu["delay"] = signed64(val)
    return tu


def parse_feed(data):
    header, updates, other = {}, [], Counter()
    for num, _, val in fields(data):
        if num == 1:
            for n2, _, v2 in fields(val):
                if n2 == 1:
                    header["version"] = v2.decode()
                elif n2 == 3:
                    header["timestamp"] = v2
        elif num == 2:
            for n2, _, v2 in fields(val):
                if n2 == 3:
                    updates.append(parse_trip_update(v2))
                elif n2 in (4, 5):
                    other["vehicle" if n2 == 4 else "alert"] += 1
    return header, updates, other


def main():
    path = sys.argv[1]
    stop = sys.argv[2] if len(sys.argv) > 2 else None
    with open(path, "rb") as f:
        data = f.read()
    header, updates, other = parse_feed(data)
    now = header.get("timestamp") or int(time.time())

    stus = [s for u in updates for s in u["stop_time_updates"]]
    print(f"bytes: {len(data):,}")
    print(f"header: {header}  ({time.strftime('%Y-%m-%d %H:%M:%S UTC', time.gmtime(now))})")
    print(f"trip updates: {len(updates):,}   other entities: {dict(other) or 'none'}")
    print(f"stop time updates: {len(stus):,}  "
          f"(avg {len(stus) / max(len(updates), 1):.1f} per trip)")
    print(f"distinct routes: {len({u['trip'].get('route_id') for u in updates})}  "
          f"distinct stops: {len({s.get('stop_id') for s in stus})}")
    print(f"updates with route_id: {sum('route_id' in u['trip'] for u in updates):,}  "
          f"with vehicle: {sum('vehicle' in u for u in updates):,}")
    print(f"stop updates with arrival.time: "
          f"{sum('time' in s.get('arrival', {}) for s in stus):,}  "
          f"arrival.delay only: "
          f"{sum('delay' in s.get('arrival', {}) and 'time' not in s['arrival'] for s in stus):,}")
    if updates:
        sample = updates[0]
        print("\nsample trip update:")
        print(f"  trip: {sample['trip']}  vehicle: {sample.get('vehicle')}")
        for s in sample["stop_time_updates"][:3]:
            print(f"  {s}")
        if len(sample["stop_time_updates"]) > 3:
            print(f"  ... {len(sample['stop_time_updates']) - 3} more")

    if stop:
        hits = []
        for u in updates:
            for s in u["stop_time_updates"]:
                if s.get("stop_id") == stop:
                    ev = s.get("arrival") or s.get("departure") or {}
                    hits.append((ev.get("time"), ev.get("delay"), u, s))
        hits.sort(key=lambda h: h[0] or 0)
        print(f"\npredictions for stop_id {stop}: {len(hits)}")
        for t, delay, u, s in hits:
            mins = f"{(t - now) / 60:5.1f} min" if t else "   no time"
            print(f"  route {u['trip'].get('route_id', '?'):>5}  trip {u['trip'].get('trip_id')}  "
                  f"{mins}  delay {delay}  vehicle {u.get('vehicle', {}).get('label', '-')}")


if __name__ == "__main__":
    main()
