#!/usr/bin/env python3
"""Check captured OS inputs against actual SDK callbacks and gate decisions, without rewriting evidence."""
import argparse, collections, hashlib, json, math
from pathlib import Path

def distance(a,b):
 lat1,lat2=map(math.radians,(a['latitude'],b['latitude']))
 dlat=lat2-lat1;dlon=math.radians(b['longitude']-a['longitude'])
 h=math.sin(dlat/2)**2+math.cos(lat1)*math.cos(lat2)*math.sin(dlon/2)**2
 return 12742017.6*math.atan2(math.sqrt(h),math.sqrt(max(0,1-h)))

def main():
 p=argparse.ArgumentParser();p.add_argument('run');args=p.parse_args();folder=Path(args.run)
 meta=json.loads((folder/'environment.json').read_text())
 inputs=[json.loads(l) for l in (folder/'inputs.jsonl').read_text().splitlines()]
 rows=[]
 for line in (folder/'sdk-logcat.txt').read_text().splitlines():
  try:
   row=json.loads(line[line.index('{'):])
   if meta['startEpochMs']<=row.get('atEpochMs',0)<=meta['endEpochMs']:rows.append(row)
  except (ValueError,KeyError):pass
 locations=[r for r in rows if r['event']=='sdk' and r['data'].get('provider')=='FUSED_LOCATION' and r['data'].get('selected')]
 decisions=[r for r in rows if r['event']=='gps_replay_decision']
 matches=[]
 for sample in inputs:
  near=[r for r in locations if -1500<=r['atEpochMs']-sample['hostEpochMs']<=3500]
  best=min(near,key=lambda r:distance(sample,r['data']),default=None)
  matches.append({'inputIndex':sample['index'],'distanceMeters':distance(sample,best['data']) if best else None,
    'bearingErrorDegrees':abs((best['data']['heading']-sample['heading']+540)%360-180) if best else None,
    'sdkEpochMs':best['atEpochMs'] if best else None})
 ordered=[]
 for r in decisions:
  if not ordered or ordered[-1]['state']!=r['data']['state']:ordered.append({'state':r['data']['state'],'epochMs':r['atEpochMs'],'alongMeters':r['data'].get('alongMeters')})
 good=[m for m in matches if m['distanceMeters'] is not None and m['distanceMeters']<5]
 unique_coords=len({(r['data']['latitude'],r['data']['longitude']) for r in locations})
 states=[r['state'] for r in ordered]
 if meta['scenario']=='forward':
  behavior=all(s in states for s in ['WAITING','GUIDANCE','PASSED']) and states.index('WAITING')<states.index('GUIDANCE')<states.index('PASSED')
  stable=[r for r in decisions if r['atEpochMs']>=meta['startEpochMs']+5000]
  behavior=behavior and all(r['data']['state'] in ['WAITING','GUIDANCE','PASSED'] for r in stable)
  guidance=[r for r in decisions if r['data']['state']=='GUIDANCE']
  passed_rows=[r for r in decisions if r['data']['state']=='PASSED']
  behavior=behavior and len(guidance)>=10 and bool(passed_rows)
  if guidance and passed_rows:
   behavior=behavior and 224<=guidance[0]['data'].get('alongMeters',-1)<=270 and 494<=passed_rows[0]['data'].get('alongMeters',-1)<=540
  behavior=behavior and all(m['bearingErrorDegrees']<2 for m in good)
 else:
  required='OUTSIDE' if meta['scenario']=='offroad' else 'WRONG_DIRECTION'
  behavior=required in states and not any(r['data']['visible'] for r in decisions)
 passed=behavior and len(good)>=max(min(10,len(inputs)),math.ceil(len(inputs)*.9)) and unique_coords>=min(5,len(inputs))
 result={'status':'PASS' if passed else 'FAIL','scenario':meta['scenario'],'source':'REAL_PLEOS_FUSED_CALLBACKS_FROM_EMULATOR_GPS',
  'inputCount':len(inputs),'sdkCallbackCount':len(locations),'uniqueSdkCoordinates':unique_coords,
  'inputsMatchedWithin5m':len(good),'maxMatchedDistanceMeters':max((m['distanceMeters'] for m in good),default=None),
  'maxMatchedBearingErrorDegrees':max((m['bearingErrorDegrees'] for m in good),default=None),
  'decisionCounts':dict(collections.Counter(r['data']['state'] for r in decisions)),
  'stateTransitions':ordered,'behaviorPassed':behavior,'coordinateMatches':matches,
  'scope':'Selected-road GPS integration test. No production road catalog approval, native navigation sync, real vehicle or current-lane validation.',
  'sha256':{name:hashlib.sha256((folder/name).read_bytes()).hexdigest() for name in ['inputs.jsonl','sdk-logcat.txt','environment.json']}}
 (folder/'summary.json').write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n')
 print(json.dumps({k:v for k,v in result.items() if k not in ['coordinateMatches','sha256']},indent=2,ensure_ascii=False))
 raise SystemExit(0 if passed else 1)
if __name__=='__main__':main()
