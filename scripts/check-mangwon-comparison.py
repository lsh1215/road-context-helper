#!/usr/bin/env python3
"""Check real callback evidence and exact archived GPS sequence for the two road profiles."""
import argparse, json, pathlib
APP=pathlib.Path(__file__).resolve().parent.parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('run')
parser.add_argument('--cases', nargs='+', choices=['b','c'], default=['c','b'])
args=parser.parse_args()
ROOT=pathlib.Path(args.run).resolve()
results={}
for case,oldrun in [('c','ko1'),('b','ko2')]:
 if case not in args.cases:continue
 out=ROOT/case
 source=APP/f'verification/runs/kakao-context-03/case-{case}-{oldrun}/gps.jsonl'
 original=[json.loads(x) for x in source.read_text().splitlines()]
 sent=[json.loads(x) for x in (out/'inputs.jsonl').read_text().splitlines()]
 assert len(original)==len(sent)
 for a,b in zip(original,sent):
  assert a['gps']==b['gps'] and a['alongMeters']==b['alongMeters']
  for key in ['latitude','longitude','bearing','speed']:assert abs(b['readback'][key]-b['gps'][key])<1e-8
 events=[]
 for line in (out/'sdk-logcat.txt').read_text().splitlines():
  if 'RoadContext: {' in line:events.append(json.loads(line.split('RoadContext: ',1)[1]))
 decisions=[e for e in events if e['event']=='gps_replay_decision' and e['data']['route']==f'mangwon-{case}']
 sdk=[e for e in decisions if e['data']['trigger']=='sdk']
 assert len(sdk)>len(sent)*.6, 'SDK callbacks must actually arrive'
 first=next(e for e in decisions if e['data']['visible'])
 passed=next(e for e in decisions if e['data']['state']=='PASSED')
 route=json.loads((APP/f'data/reference/mangwon-gps-replay/mangwon-{case}.json').read_text())
 assert first['data']['alongMeters']<route['junctionAlongMeters']-600
 assert passed['data']['alongMeters']>=route['endAlongMeters']+20
 assert all(not e['data']['visible'] for e in decisions if e['atEpochMs']>=passed['atEpochMs'])
 reference=json.loads((APP/f'verification/runs/kakao-context-03/case-{case}-route.json').read_text())
 assert route['points']==reference['points']
 # Capture delivery timing separately from intended playback timing.
 offset=sent[0]['sentEpochMs']-original[0]['epochMs']
 lateness=[b['sentEpochMs']-a['epochMs']-offset for a,b in zip(original,sent)]
 result=dict(status='PASS',samples=len(sent),sdkDecisions=len(sdk),allGpsInputsAndReadbacksMatch=True,referencePointsUnchanged=True,firstGuidanceBeforeGraphMergeMeters=route['junctionAlongMeters']-first['data']['alongMeters'],firstGuidanceEpochMs=first['atEpochMs'],passedAlongMeters=passed['data']['alongMeters'],passedEpochMs=passed['atEpochMs'],maxRelativeSchedulingErrorMs=max(abs(x) for x in lateness),finalState=decisions[-1]['data']['state'])
 results[case]=result
(ROOT/'acceptance-result.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(results,ensure_ascii=False,indent=2))
