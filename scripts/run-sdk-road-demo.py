#!/usr/bin/env python3
"""One command: real road GPS replay through Pleos SDK on the dedicated emulator."""
import os, shutil
import argparse, datetime, pathlib, subprocess, sys
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--output',help='New directory for SDK/input logs and screenshots')
p.add_argument('--speed-mps', type=float, default=22.5, help='GPS replay speed, default 22.5m/s (1.5x previous demo)')
args=p.parse_args()
if not 2 <= args.speed_mps <= 30:p.error('--speed-mps must be between 2 and 30')
app=pathlib.Path(__file__).resolve().parent.parent
workspace=app.parent.parent
sdk=os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
adb=os.environ.get('ADB') or (str(pathlib.Path(sdk)/'platform-tools/adb') if sdk else None) or shutil.which('adb')
if not adb:
 candidate=workspace/'.test-runtime/android-sdk/platform-tools/adb'
 if candidate.is_file():adb=str(candidate)
 else:raise SystemExit('Install Android platform-tools and set ANDROID_HOME or put adb on PATH')
name=subprocess.run([str(adb),'-s','emulator-5556','emu','avd','name'],capture_output=True,text=True,check=True).stdout
if name.splitlines()[0]!='RoadContext':raise SystemExit('Only AVD RoadContext / emulator-5556 is allowed')
discovery=None
for ini in (pathlib.Path.home()/'Library/Caches/TemporaryItems/avd/running').glob('pid_*.ini'):
 values=dict(line.strip().split('=',1) for line in ini.read_text().splitlines() if '=' in line)
 if values.get('avd.id')=='RoadContext' and values.get('port.serial')=='5556' and values.get('grpc.port')=='8561':discovery=ini
if discovery is None:raise SystemExit('Start RoadContext with scripts/start-emulator.sh --window --gps-replay before replaying.')
out=pathlib.Path(args.output) if args.output else app/'verification/runs'/('sdk-road-demo-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
subprocess.run([sys.executable,str(app/'scripts/replay-sdk-road.py'),'--launch','--adb',str(adb),'--grpc-discovery',str(discovery),'--output',str(out),'--speed-mps',str(args.speed_mps)],check=True)
subprocess.run([sys.executable,str(app/'scripts/check-sdk-road-replay.py'),str(out)],check=True)
print('Evidence:',out)
