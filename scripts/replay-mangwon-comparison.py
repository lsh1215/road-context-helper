#!/usr/bin/env python3
"""Replay the exact archived Kakao GPS samples through Pleos Fused Location. Dedicated AVD only."""
import os, shutil
import argparse, concurrent.futures, datetime, hashlib, importlib.util, json, pathlib, signal, subprocess, time
APP=pathlib.Path(__file__).resolve().parent.parent
ROOT=APP.parent.parent
sdk=os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
ADB=os.environ.get('ADB') or (str(pathlib.Path(sdk)/'platform-tools/adb') if sdk else None) or shutil.which('adb')
if not ADB:
 candidate=ROOT/'.test-runtime/android-sdk/platform-tools/adb'
 if candidate.is_file():ADB=str(candidate)
 else:raise SystemExit('Install Android platform-tools and set ANDROID_HOME or put adb on PATH')
SRC=APP/'verification/runs/kakao-context-03'
p=argparse.ArgumentParser();p.add_argument('case',choices=['b','c']);p.add_argument('--output',required=True);a=p.parse_args()
out=pathlib.Path(a.output).resolve();out.mkdir(parents=True,exist_ok=False)
serial='emulator-5556'
def adb(*args,**kw):return subprocess.run([str(ADB),'-s',serial,*args],check=True,timeout=30,**kw)
assert adb('emu','avd','name',capture_output=True,text=True).stdout.splitlines()[0]=='RoadContext'
for f in (pathlib.Path.home()/'Library/Caches/TemporaryItems/avd/running').glob('pid_*.ini'):
 v=dict(x.split('=',1) for x in f.read_text().splitlines() if '=' in x)
 if v.get('avd.id')=='RoadContext' and v.get('port.serial')=='5556' and v.get('grpc.port')=='8561': discovery=f;break
else:raise RuntimeError('RoadContext gRPC8561 unavailable')
source=SRC/f"case-{a.case}-{'ko2' if a.case=='b' else 'ko1'}/gps.jsonl"
rows=[json.loads(x) for x in source.read_text().splitlines()]
spec=importlib.util.spec_from_file_location('nmea',APP/'scripts/gps-corrected-nmea.py');n=importlib.util.module_from_spec(spec);spec.loader.exec_module(n)
def inject(g):
 r=subprocess.run(['node',str(APP/'scripts/gps-replay-control.mjs'),str(discovery),json.dumps(g)],capture_output=True,text=True,check=True,timeout=12)
 now=datetime.datetime.now(datetime.timezone.utc);tm=now.strftime('%H%M%S');ll=n.coordinate(g['latitude'],2);lo=n.coordinate(g['longitude'],3)
 for msg in [f'GPGGA,{tm},{ll},N,{lo},E,1,8,,0.0,M,0.,M,,,',f"GPRMC,{tm},A,{ll},N,{lo},E,{g['speed']/0.514444:.2f},{g['bearing']:.2f},{now:%d%m%y},0.0,W"]:
  adb('emu','geo','nmea',n.sentence(msg),capture_output=True)
 return json.loads(r.stdout)
meta={'case':a.case,'sourceGps':str(source.relative_to(APP)),'sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'serial':serial,'inputMethod':'setGps + checksummed NMEA → actual Pleos Fused callback','sameOrderedCoordinatesAndBearings':True,'sourceEpochOffsetMs':rows[0]['epochMs'],'profile':f'mangwon-{a.case}'}
adb('shell','am','force-stop','--user','10','dev.pleos.roadcontext',capture_output=True)
inject(rows[0]['gps']);time.sleep(2)
logfile=(out/'sdk-logcat.txt').open('w');log=subprocess.Popen([str(ADB),'-s',serial,'logcat','-v','epoch','-T','1','RoadContext:I','*:S'],stdout=logfile)
adb('shell','am','start','--user','10','-W','-n','dev.pleos.roadcontext/.MainActivity','--ez','sdk_road_replay','true','--es','road_profile',f'mangwon-{a.case}',capture_output=True)
time.sleep(3)
remote=f'/sdcard/mangwon-{a.case}-{int(time.time())}.mp4'
meta['recordRequestedEpochMs']=int(time.time()*1000)
rec=subprocess.Popen([str(ADB),'-s',serial,'shell','screenrecord','--size','1920x1080','--bit-rate','6000000','--time-limit','175',remote],stdout=subprocess.DEVNULL,stderr=(out/'recording-stderr.txt').open('w'))
time.sleep(2)
start=time.monotonic();meta['replayStartEpochMs']=int(time.time()*1000)
pool=concurrent.futures.ThreadPoolExecutor(max_workers=1);futures=[];done=set()
def capture(label):
 begin=int(time.time()*1000)
 with (out/f'{label}.png').open('wb') as f:adb('exec-out','screencap','-p',stdout=f)
 return dict(label=label,requestEpochMs=begin,completeEpochMs=int(time.time()*1000))
try:
 with (out/'inputs.jsonl').open('w') as stream:
  for i,row in enumerate(rows):
   target=(row['epochMs']-rows[0]['epochMs'])/1000
   time.sleep(max(0,start+target-time.monotonic()))
   sent=int(time.time()*1000);actual=inject(row['gps'])
   record=dict(index=i,sourceEpochMs=row['epochMs'],sentEpochMs=sent,completeEpochMs=int(time.time()*1000),alongMeters=row['alongMeters'],beforeMergeMeters=row['beforeMergeMeters'],gps=row['gps'],readback=actual)
   stream.write(json.dumps(record)+'\n');stream.flush()
   for label,threshold in [('before-1000m',1000),('before-600m',600),('before-200m',200),('merge',0),('after-100m',-100),('after-600m',-600)]:
    if row['beforeMergeMeters']<=threshold and label not in done:
     done.add(label);futures.append(pool.submit(capture,label))
   if i%15==0:print(f"{a.case}: {i}/{len(rows)}, merge remaining {row['beforeMergeMeters']:.0f}m",flush=True)
 time.sleep(3);futures.append(pool.submit(capture,'final'))
finally:
 g=dict(rows[-1]['gps']);g['speed']=0;inject(g)
 pool.shutdown(wait=True)
 adb('shell','pkill','-2','screenrecord',capture_output=True);rec.wait(timeout=15)
 adb('pull',remote,str(out/'our-app.mp4'),capture_output=True)
 log.terminate();log.wait(timeout=10);logfile.close()
 (out/'captures.json').write_text(json.dumps([f.result() for f in futures],indent=2))
 meta['finishedEpochMs']=int(time.time()*1000);meta['samples']=len(rows)
 (out/'environment.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n')
print('DONE '+str(out),flush=True)
