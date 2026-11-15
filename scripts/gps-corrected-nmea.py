#!/usr/bin/env python3
"""Finite, checksummed, goldfish-compatible NMEA input; never changes app callbacks.

The emulator serial is explicit and defaults to the dedicated RoadContext AVD.
The uncorrected automatic passive stream may still occur between these inputs.
"""
import argparse
import datetime
import json
import subprocess
import time
from pathlib import Path


def coordinate(value, width):
    v = abs(value)
    return f'{int(v):0{width}d}{(v-int(v))*60:07.4f}'

def sentence(body):
    checksum = 0
    for ch in body:
        checksum ^= ord(ch)
    return f'${body}*{checksum:02X}'


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--adb', required=True)
    p.add_argument('--serial', default='emulator-5556')
    p.add_argument('--output', required=True)
    p.add_argument('--latitude', type=float, default=37.52989)
    p.add_argument('--longitude', type=float, default=126.964775)
    p.add_argument('--count', type=int, default=21)
    p.add_argument('--interval', type=float, default=3)
    args = p.parse_args()
    if args.serial != 'emulator-5556':
        p.error('This experiment is restricted to the dedicated emulator-5556.')
    if not 2 <= args.count <= 100 or not 0.2 <= args.interval <= 5:
        p.error('Use a bounded trace: 2..100 points, interval 0.2..5 seconds.')

    def send(nmea):
        r = subprocess.run([args.adb, '-s', args.serial, 'emu', 'geo', 'nmea', nmea],
                           capture_output=True, text=True, check=True)
        return r.stdout.strip()

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    start = time.monotonic()
    with out.open('w') as f:
        for i in range(args.count):
            remaining = start + i * args.interval - time.monotonic()
            if remaining > 0:
                time.sleep(remaining)
            lat = args.latitude + i * 0.00015
            lon = args.longitude
            now = datetime.datetime.now(datetime.timezone.utc)
            # Goldfish sscanf expects integer HHMMSS and numeric magnetic variation.
            stamp = now.strftime('%H%M%S')
            ll = coordinate(lat, 2)
            lo = coordinate(lon, 3)
            ns, ew = ('N' if lat >= 0 else 'S'), ('E' if lon >= 0 else 'W')
            rmc = sentence(f'GPRMC,{stamp},A,{ll},{ns},{lo},{ew},10.00,0.00,{now:%d%m%y},0.0,W')
            gga = sentence(f'GPGGA,{stamp},{ll},{ns},{lo},{ew},1,6,,0.0,M,0.,M,,,')
            before = time.time_ns() // 1_000_000
            # Altitude/constellation first, then a full time+location fix.
            responses = [send(gga), send(rmc)]
            row = dict(index=i, latitude=lat, longitude=lon, hostBeforeEpochMs=before,
                       hostAfterEpochMs=time.time_ns()//1_000_000,
                       elapsedSeconds=time.monotonic()-start, gga=gga, rmc=rmc,
                       responses=responses, source='EMULATOR_GEO_NMEA')
            f.write(json.dumps(row)+'\n')
            f.flush()
            if i % 5 == 0:
                print(f'sent {i}, elapsed={row["elapsedSeconds"]:.2f}s', flush=True)


if __name__ == "__main__":
    main()
