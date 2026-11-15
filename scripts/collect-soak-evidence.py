#!/usr/bin/env python3
"""Read-only companion for the explicitly launched 30-minute instrumentation test."""
import argparse
import json
from pathlib import Path
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument("--adb", required=True)
parser.add_argument("--run-dir", required=True)
args = parser.parse_args()
run = Path(args.run_dir).resolve()
run.mkdir(parents=True, exist_ok=True)
base = [args.adb, "-s", "emulator-5556"]
started = time.monotonic()
with (run / "runtime.log").open("w") as log:
    collector = subprocess.Popen(base + ["logcat", "-T", "1", "-v", "epoch",
        "RoadContext:I", "RoadContextUx:I", "AndroidRuntime:E", "ActivityManager:I", "*:S"],
        stdout=log, stderr=subprocess.STDOUT)
    try:
        while time.monotonic() - started < 1900:
            elapsed = round(time.monotonic() - started)
            for name, command in {
                "memory": ["shell", "dumpsys", "meminfo", "dev.pleos.roadcontext"],
                "checkpoint": ["shell", "run-as", "dev.pleos.roadcontext", "--user", "10", "cat",
                    "files/verification-device/endurance-1800.json"],
            }.items():
                result = subprocess.run(base + command, capture_output=True, text=True, timeout=15)
                (run / f"{name}-{elapsed:04d}.txt").write_text(result.stdout + result.stderr)
            output = (run / "instrumentation.txt").read_text()
            if "OK (" in output or "FAILURES!!!" in output or "INSTRUMENTATION_FAILED" in output:
                break
            time.sleep(60)
    finally:
        collector.terminate()
        try:
            collector.wait(timeout=5)
        except subprocess.TimeoutExpired:
            collector.kill()
            collector.wait()
        (run / "collector.json").write_text(json.dumps({
            "elapsedSeconds": time.monotonic() - started,
            "serial": "emulator-5556", "readOnly": True,
            "scope": "Periodic checkpoints and process memory; collector begins after instrumentation launch. Final acceptance requires complete instrumentation and diagnostic decision records."
        }, indent=2) + "\n")
