#!/usr/bin/env python3
"""Replay OSM road points into emulator GPS. Never calls an app callback or sets UI state.

Use --launch to prime GPS then select the debug SDK-road mode. Only emulator-5556.
Correct NMEA checksums are required by this Pleos image's GPS parser.
"""
import argparse
import datetime
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import subprocess
import time

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent

def distance(a, b):
    lat1, lat2 = map(math.radians, (a[1], b[1]))
    dlat, dlon = lat2-lat1, math.radians(b[0]-a[0])
    h = math.sin(dlat/2)**2+math.cos(lat1)*math.cos(lat2)*math.sin(dlon/2)**2
    return 6371008.8*2*math.atan2(math.sqrt(h), math.sqrt(max(0, 1-h)))

def heading(a, b):
    lat1,lat2=map(math.radians,(a[1],b[1])); dlon=math.radians(b[0]-a[0])
    return math.degrees(math.atan2(math.sin(dlon)*math.cos(lat2),math.cos(lat1)*math.sin(lat2)-math.sin(lat1)*math.cos(lat2)*math.cos(dlon)))%360

def point_at(points, along):
    remaining=along
    for a,b in zip(points,points[1:]):
        length=distance(a,b)
        if remaining<=length:
            t=max(0,remaining/length)
            return [a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t],heading(a,b)
        remaining-=length
    return points[-1],heading(points[-2],points[-1])

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--adb',required=True)
    p.add_argument('--output',required=True)
    p.add_argument('--grpc-discovery',help='Official emulator discovery INI for local port 8561')
    p.add_argument('--speed-mps',type=float,default=15)
    p.add_argument('--interval-seconds',type=float,default=1,
                   help='GPS input cadence; use 2 when emulator SDK callbacks are slower than 1Hz')
    p.add_argument('--scenario',choices=['forward','offroad','reverse'],default='forward')
    p.add_argument('--start-meters',type=float,default=0)
    p.add_argument('--end-meters',type=float,default=None)
    p.add_argument('--hold-seconds',type=int,default=5)
    p.add_argument('--dry-run',action='store_true')
    p.add_argument('--launch',action='store_true',help='Prime GPS before opening the explicit debug SDK mode')
    args=p.parse_args()
    if not math.isfinite(args.speed_mps) or not 2<=args.speed_mps<=30: p.error('speed 2..30m/s')
    if not math.isfinite(args.interval_seconds) or not .5<=args.interval_seconds<=3:p.error('interval .5..3seconds')
    if not 0<=args.hold_seconds<=10: p.error('hold 0..10seconds')
    if not args.dry_run and not args.grpc_discovery: p.error('--grpc-discovery is required for location and bearing input')
    route_path=ROOT/'data/reference/yanghwa-gps-replay/route.json'
    route=json.loads(route_path.read_text());points=route['points']
    total=sum(distance(a,b) for a,b in zip(points,points[1:]))
    end=total if args.end_meters is None else args.end_meters
    if not 0<=args.start_meters<=end<=total+.01:p.error('invalid road interval')
    out=Path(args.output)
    if out.exists() and any(out.iterdir()):p.error('use new output directory')
    out.mkdir(parents=True,exist_ok=True)
    spec=importlib.util.spec_from_file_location('encoder',HERE/'gps-corrected-nmea.py')
    encoder=importlib.util.module_from_spec(spec);spec.loader.exec_module(encoder)
    def adb(arguments,**kwargs):
        return subprocess.run([args.adb,'-s','emulator-5556']+arguments,check=True,timeout=15,**kwargs)
    hold_count=math.ceil(args.hold_seconds/args.interval_seconds)
    step_meters=args.speed_mps*args.interval_seconds
    inputs=[args.start_meters]*hold_count
    travel=math.ceil((end-args.start_meters)/step_meters)
    inputs += [min(end,args.start_meters+i*step_meters) for i in range(travel+1)]
    inputs += [end]*hold_count
    meta={'routeId':route['id'],'inputOrigin':'EMULATOR_GRPC_SETGPS_AND_CHECKSUMMED_NMEA',
          'routeSha256':hashlib.sha256(route_path.read_bytes()).hexdigest(),
          'sdkCallbacksManufactured':False,'scenario':args.scenario,'speedMetersPerSecond':args.speed_mps,
          'inputIntervalSeconds':args.interval_seconds,
          'serial':'emulator-5556','pointCount':len(inputs),'startEpochMs':int(time.time()*1000)}
    (out/'environment.json').write_text(json.dumps(meta,indent=2))
    log=None; log_file=None; gps=None
    try:
        if not args.dry_run:
            adb(['get-state'],capture_output=True)
            log_file=(out/'sdk-logcat.txt').open('w')
            log=subprocess.Popen([args.adb,'-s','emulator-5556','logcat','-v','epoch','-T','1','RoadContext:I','*:S'],stdout=log_file,stderr=subprocess.STDOUT)
        if args.launch and not args.dry_run:
            # Prime before subscribing: a previous run's fresh final fix must not latch PASSED.
            adb(['shell','am','force-stop','--user','10','dev.pleos.roadcontext'],capture_output=True)
            initial_along=end if args.scenario=='reverse' else args.start_meters
            initial,bearing=point_at(points,initial_along)
            if args.scenario=='offroad':initial=[initial[0],initial[1]+.002]
            if args.scenario=='reverse':bearing=(bearing+180)%360
            gps={'passiveUpdate':True,'latitude':initial[1],'longitude':initial[0],'bearing':bearing,'speed':args.speed_mps,'altitude':0,'satellites':8}
            subprocess.run(['node',str(HERE/'gps-replay-control.mjs'),args.grpc_discovery,json.dumps(gps)],capture_output=True,text=True,check=True,timeout=10)
            time.sleep(2)
            adb(['shell','am','start','--user','10','-W','-n','dev.pleos.roadcontext/.MainActivity','--ez','sdk_road_replay','true'],capture_output=True)
        start=time.monotonic(); captured=set()
        with (out/'inputs.jsonl').open('w') as stream:
            for i,along in enumerate(inputs):
                if not args.dry_run:time.sleep(max(0,start+i*args.interval_seconds-time.monotonic()))
                if args.scenario=='reverse':along=end-(along-args.start_meters)
                point,bearing=point_at(points,along)
                if args.scenario=='offroad':point=[point[0],point[1]+.002]
                if args.scenario=='reverse':bearing=(bearing+180)%360
                lon,lat=point;now=datetime.datetime.now(datetime.timezone.utc)
                stamp=now.strftime('%H%M%S');ll=encoder.coordinate(lat,2);lo=encoder.coordinate(lon,3)
                gga=encoder.sentence(f'GPGGA,{stamp},{ll},N,{lo},E,1,8,,0.0,M,0.,M,,,')
                rmc=encoder.sentence(f'GPRMC,{stamp},A,{ll},N,{lo},E,{args.speed_mps/0.514444:.2f},{bearing:.2f},{now:%d%m%y},0.0,W')
                commands=[['emu','geo','nmea',gga],['emu','geo','nmea',rmc]]
                replies=[];gps_readback=None
                if not args.dry_run:
                    gps={'passiveUpdate':True,'latitude':lat,'longitude':lon,'bearing':bearing,'speed':args.speed_mps,'altitude':0,'satellites':8}
                    control=subprocess.run(['node',str(HERE/'gps-replay-control.mjs'),args.grpc_discovery,json.dumps(gps)],capture_output=True,text=True,check=True,timeout=10)
                    gps_readback=json.loads(control.stdout)
                    if abs(gps_readback['bearing']-bearing)>.001:raise RuntimeError('Emulator bearing readback differs')
                    for command in commands:
                        reply=adb(command,capture_output=True,text=True)
                        replies.append(reply.stdout.strip())
                        if 'OK' not in reply.stdout:raise RuntimeError('GPS injection rejected')
                row={'index':i,'alongMeters':along,'latitude':lat,'longitude':lon,'heading':bearing,
                     'hostEpochMs':int(time.time()*1000),'gga':gga,'rmc':rmc,'responses':replies,'emulatorGpsReadback':gps_readback}
                stream.write(json.dumps(row)+'\n');stream.flush()
                if i%5==0:print(f"{args.scenario} GPS {along:.0f}m point {i}/{len(inputs)}",flush=True)
                for label,threshold in [('before',100),('guidance',350),('passed',600)]:
                    if label not in captured and along>=threshold and not args.dry_run:
                        with (out/f'{label}.png').open('wb') as image:adb(['exec-out','screencap','-p'],stdout=image)
                        captured.add(label)
            if not args.dry_run:
                time.sleep(2)
                with (out/'final.png').open('wb') as image:adb(['exec-out','screencap','-p'],stdout=image)
    finally:
        if gps is not None and not args.dry_run:
            gps['speed']=0
            subprocess.run(['node',str(HERE/'gps-replay-control.mjs'),args.grpc_discovery,json.dumps(gps)],capture_output=True,text=True,timeout=10)
        if log:
            log.terminate();log.wait(timeout=5);log_file.close()
        meta['endEpochMs']=int(time.time()*1000)
        (out/'environment.json').write_text(json.dumps(meta,indent=2))

if __name__=='__main__':main()
