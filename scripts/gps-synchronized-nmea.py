#!/usr/bin/env python3
"""Finite emulator input experiment; does not manufacture or replace SDK callbacks.

Tests two unresolved hypotheses together: a large Seoul position jump may be
filtered by navigation, and passive NMEA may restore the last geo-fix position.
Uses the existing corrected NMEA encoder, first aligning geo-fix with each point.
Only emulator-5556 is permitted. --dry-run performs no device or socket access.
"""
import argparse
import datetime
import importlib.util
import json
import math
import subprocess
import time
from pathlib import Path


def encoder():
    path = Path(__file__).with_name('gps-corrected-nmea.py')
    spec = importlib.util.spec_from_file_location('corrected_nmea', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.coordinate, module.sentence


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--serial', default='emulator-5556')
    parser.add_argument('--output-dir', required=True)
    parser.add_argument('--latitude', type=float, default=37.4127212)
    parser.add_argument('--longitude', type=float, default=127.094681)
    parser.add_argument('--latitude-step', type=float, default=0.00015)
    parser.add_argument('--count', type=int, default=21)
    parser.add_argument('--interval', type=float, default=3)
    parser.add_argument('--dry-run', action='store_true')
    parser.add_argument('--public-log-only', action='store_true',
                        help='Collect public application logcat only; never read app-private files.')
    args = parser.parse_args()
    if args.serial != 'emulator-5556':
        parser.error('Only dedicated emulator-5556 is permitted.')
    if not 2 <= args.count <= 100 or not 0.2 <= args.interval <= 5:
        parser.error('Use a bounded trace: 2..100 points, interval 0.2..5 seconds.')
    values = [args.latitude, args.longitude, args.latitude_step, args.interval]
    if not all(math.isfinite(v) for v in values):
        parser.error('Coordinates and interval must be finite.')
    end = args.latitude + (args.count - 1) * args.latitude_step
    if not -90 <= min(args.latitude, end) <= max(args.latitude, end) <= 90 or not -180 <= args.longitude <= 180:
        parser.error('Trace coordinates must be valid latitude/longitude.')
    return args


def main():
    args = parse_args()
    coordinate, sentence = encoder()
    out = Path(args.output_dir)
    if out.exists() and any(out.iterdir()):
        raise SystemExit('Output directory must be empty to preserve prior evidence.')
    out.mkdir(parents=True, exist_ok=True)
    command_prefix = [args.adb, '-s', args.serial]
    events = []

    def adb(arguments, check=True):
        result = subprocess.run(command_prefix + arguments, capture_output=True,
                                text=True, timeout=15)
        if check and result.returncode:
            raise RuntimeError(f'ADB returned {result.returncode}: {result.stderr.strip()}')
        return result

    def clock_samples():
        samples = []
        for _ in range(3):
            before = time.time_ns() // 1_000_000
            result = adb(['shell', 'date', '+%s%3N'], check=False)
            after = time.time_ns() // 1_000_000
            raw = result.stdout.strip()
            valid = result.returncode == 0 and raw.isdigit() and len(raw) == 13
            device = int(raw) if valid else None
            samples.append(dict(hostBeforeEpochMs=before, hostAfterEpochMs=after,
                                deviceEpochMs=device, raw=raw, error=result.stderr,
                                midpointOffsetMs=None if device is None else device - (before + after) / 2,
                                roundTripMs=after - before))
        return samples

    def snapshot(name):
        # Raw files are copied without edits. Missing/permission errors are separate evidence.
        result = adb(['shell', 'run-as', 'dev.pleos.roadcontext', '--user', '10',
                      'cat', 'files/diagnostics.jsonl'], check=False)
        (out / f'diagnostics-{name}.jsonl').write_text(result.stdout)
        (out / f'diagnostics-{name}.status.json').write_text(json.dumps({
            'returnCode': result.returncode, 'stderr': result.stderr,
            'hostEpochMs': time.time_ns() // 1_000_000}, indent=2) + '\n')

    metadata = dict(serial=args.serial, applicationId='dev.pleos.roadcontext',
                    foregroundUser=10, dryRun=args.dry_run, origin=[args.latitude, args.longitude],
                    latitudeStep=args.latitude_step, count=args.count, intervalSeconds=args.interval,
                    plannedSpanSeconds=(args.count - 1) * args.interval,
                    privateFilesRead=not args.public_log_only and not args.dry_run,
                    callbackCapture='public_app_logcat' if args.public_log_only else 'private_diagnostics_and_logcat',
                    hypothesis='near-default origin and same-point passive geo fix alignment',
                    runtimePrerequisite='Real-input MainActivity foreground with runtime permission granted; root owns APK provenance',
                    limitations=['geo fix still emits its own potentially invalid-checksum NMEA',
                                 'no guarantee of Navi provider acceptance or route association',
                                 'input and callback equality must be analyzed independently'])
    (out / 'environment.json').write_text(json.dumps(metadata, indent=2) + '\n')
    log_process = None
    log_file = None
    error = None
    try:
        if not args.dry_run:
            if adb(['get-state']).stdout.strip() != 'device':
                raise RuntimeError('Dedicated emulator is not online.')
            metadata['clockBefore'] = clock_samples()
            if not args.public_log_only:
                snapshot('before')
            log_file = (out / 'logcat-raw.txt').open('w')
            # No log clearing. Capture SDK/NMEA context without assuming vendor tag names.
            filters = ['RoadContext:I', 'RoadContextUx:I', 'NaviProbe:I', '*:S'] if args.public_log_only else []
            log_process = subprocess.Popen(command_prefix + ['logcat', '-v', 'epoch', '-T', '1'] + filters,
                                           stdout=log_file, stderr=subprocess.STDOUT)
        start = time.monotonic()
        with (out / 'inputs.jsonl').open('w') as stream:
            for index in range(args.count):
                remaining = start + index * args.interval - time.monotonic()
                if remaining > 0 and not args.dry_run:
                    time.sleep(remaining)
                lat = args.latitude + index * args.latitude_step
                lon = args.longitude
                now = datetime.datetime.now(datetime.timezone.utc)
                stamp = now.strftime('%H%M%S')
                ll, lo = coordinate(lat, 2), coordinate(lon, 3)
                ns, ew = ('N' if lat >= 0 else 'S'), ('E' if lon >= 0 else 'W')
                # Same integer HHMMSS + numeric magnetic variation encoder as nmea-01/02.
                gga = sentence(f'GPGGA,{stamp},{ll},{ns},{lo},{ew},1,6,,0.0,M,0.,M,,,')
                rmc = sentence(f'GPRMC,{stamp},A,{ll},{ns},{lo},{ew},10.00,0.00,{now:%d%m%y},0.0,W')
                commands = [
                    ['emu', 'geo', 'fix', f'{lon:.9f}', f'{lat:.9f}', '0', '6', '10'],
                    ['emu', 'geo', 'nmea', gga],
                    ['emu', 'geo', 'nmea', rmc],
                ]
                row = dict(index=index, latitude=lat, longitude=lon, gga=gga, rmc=rmc,
                           hostBeforeEpochMs=time.time_ns() // 1_000_000,
                           plannedElapsedSeconds=index * args.interval,
                           source='DRY_RUN' if args.dry_run else 'EMULATOR_GEO_FIX_THEN_CORRECTED_NMEA',
                           commandResults=[])
                for command in commands:
                    result = dict(arguments=command_prefix + command,
                                  hostBeforeEpochMs=time.time_ns() // 1_000_000)
                    if not args.dry_run:
                        response = adb(command, check=False)
                        result.update(returnCode=response.returncode, stdout=response.stdout,
                                      stderr=response.stderr, hostAfterEpochMs=time.time_ns() // 1_000_000)
                    row['commandResults'].append(result)
                row.update(hostAfterEpochMs=time.time_ns() // 1_000_000,
                           elapsedSeconds=time.monotonic() - start)
                stream.write(json.dumps(row) + '\n')
                stream.flush()
                events.append(row)
                if any(r.get('returnCode', 0) != 0 or (not args.dry_run and 'OK' not in r.get('stdout', ''))
                       for r in row['commandResults']):
                    raise RuntimeError(f'Input command failed at index {index}; inspect raw input record.')
                if index % 5 == 0:
                    print(f'{row["source"]} point={index} elapsed={row["elapsedSeconds"]:.2f}s', flush=True)
        if not args.dry_run:
            # Drain final callbacks; input span remains the recorded first-to-last command span.
            time.sleep(3)
            metadata['clockAfter'] = clock_samples()
            if not args.public_log_only:
                snapshot('after')
    except BaseException as exc:
        error = dict(type=type(exc).__name__, message=str(exc))
        raise
    finally:
        if log_process is not None:
            log_process.terminate()
            try:
                log_process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                log_process.kill()
                log_process.wait(timeout=5)
        if log_file is not None:
            log_file.close()
        metadata.update(completedPoints=len(events), error=error)
        (out / 'environment.json').write_text(json.dumps(metadata, indent=2) + '\n')


if __name__ == '__main__':
    main()
