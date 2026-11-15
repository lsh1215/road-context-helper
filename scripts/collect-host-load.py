#!/usr/bin/env python3
"""Bounded aggregate-only macOS sampling alongside a device test, never per-process data."""
import argparse
import datetime
import json
from pathlib import Path
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument("--output", type=Path, required=True)
parser.add_argument("--stop-file", type=Path, required=True)
parser.add_argument("--seconds", type=int, default=900)
args = parser.parse_args()
if not 1 <= args.seconds <= 3600:
    parser.error("seconds must be between 1 and 3600")
if args.output.exists() or args.stop_file.exists():
    parser.error("Use a fresh output and stop marker; existing evidence is not overwritten")
args.output.parent.mkdir(parents=True, exist_ok=True)
started = time.monotonic()
commands = {
    "loadAverage": ["sysctl", "-n", "vm.loadavg"],
    "aggregateTop": ["top", "-l", "1", "-n", "0"],
}
count = 0
with args.output.open("x") as stream:
    while time.monotonic() - started < args.seconds and not args.stop_file.exists():
        sample = {"index": count, "hostUtc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                  "elapsedSeconds": time.monotonic() - started}
        acquisition = time.monotonic()
        for name, command in commands.items():
            try:
                result = subprocess.run(command, capture_output=True, text=True, timeout=5, check=False)
                output = result.stdout
                if name == "aggregateTop":
                    prefixes = ("Processes:", "Load Avg:", "CPU usage:", "PhysMem:", "VM:")
                    output = "\n".join(line for line in output.splitlines() if line.startswith(prefixes))
                sample[name] = {"exitCode": result.returncode, "stdout": output, "stderr": result.stderr}
            except (OSError, subprocess.TimeoutExpired) as error:
                sample[name] = {"error": str(error)}
        sample["acquisitionSeconds"] = time.monotonic() - acquisition
        stream.write(json.dumps(sample) + "\n")
        stream.flush()
        count += 1
        # Five-second sampling minimizes observer work; not precise CPU attribution.
        time.sleep(min(5, max(0, args.seconds - (time.monotonic() - started))))
print(json.dumps({"samples": count, "elapsedSeconds": time.monotonic() - started,
                  "reason": "stop-marker" if args.stop_file.exists() else "duration-limit"}))
