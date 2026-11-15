#!/usr/bin/env python3
"""Compose separately recorded views; labels explicitly disclose approximate timeline alignment."""
import argparse,json,pathlib,subprocess,wave
from PIL import Image,ImageDraw,ImageFont
APP=pathlib.Path(__file__).resolve().parent.parent
p=argparse.ArgumentParser();p.add_argument('case',choices=['b','c']);p.add_argument('--run',required=True);a=p.parse_args()
root=pathlib.Path(a.run).resolve();old=APP/'verification/runs/kakao-context-03';run=root/a.case
font='/System/Library/Fonts/AppleSDGothicNeo.ttc'
def f(n):return ImageFont.truetype(font,n)
bg=Image.new('RGB',(1920,1080),'#10202d');d=ImageDraw.Draw(bg)
name='가까운 왼쪽 진출' if a.case=='b' else '진입 차로 소멸 · 본선 유지'
d.text((32,18),'망원 합류부  |  '+name,font=f(36),fill='#f1f7fb')
d.text((32,70),'동일 GPS 경로 · 각각 녹화 · 진행 시점 대략 정렬(±4초) · 반응속도 비교용 아님',font=f(23),fill='#b6c9d8')
d.text((32,122),'카카오내비 4.52.1 · 모바일',font=f(24),fill='#f1f7fb')
d.text((600,122),'초행길 · 실제 Pleos 위치 SDK · 앱 영역 확대',font=f(24),fill='#80e1c4')
d.text((32,1039),'음성: 기존 카카오내비 녹음  |  우리 앱: 연결 관계 도식, 실제 차량 차로·차로 수·주행 지시가 아님',font=f(22),fill='#b6c9d8')
background=root/f'{a.case}-comparison-background.png';bg.save(background)
oldrun='ko2' if a.case=='b' else 'ko1';gps=[json.loads(x) for x in (old/f'case-{a.case}-{oldrun}/gps.jsonl').read_text().splitlines()];base=gps[0]['epochMs']
start=10.;duration=132. if a.case=='b' else 105.
# Original utterance PCM is placed by its captured epoch timestamp, not concatenated-WAV offset.
rate=44100;audio=bytearray(round(duration*rate)*2);clips=[]
for u in json.loads((old/f'case-{a.case}-ko-audio-utterances.json').read_text()):
 offset=round(((u['startEpochMs']-base)/1000-start)*rate)
 with wave.open(str(old/u['clip']),'rb') as w:
  assert w.getnchannels()==1 and w.getsampwidth()==2 and w.getframerate()==rate
  data=w.readframes(w.getnframes())
 lo=max(0,offset);hi=min(len(audio)//2,offset+len(data)//2)
 if hi>lo:
  audio[lo*2:hi*2]=data[(lo-offset)*2:(hi-offset)*2];clips.append(dict(source=u['clip'],startRelativeReplaySeconds=(u['startEpochMs']-base)/1000))
audiofile=root/f'{a.case}-aligned-kakao-audio.wav'
with wave.open(str(audiofile),'wb') as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(rate);w.writeframes(audio)
# Old videos have timestamped screenshots: first-frame offsets inferred at several distinct UI states.
kakaoOffset=3.5 if a.case=='b' else -4.5
meta=json.loads((run/'environment.json').read_text());oursOffset=(meta['recordRequestedEpochMs']-meta['replayStartEpochMs'])/1000
info=dict(sourceWindowStartSeconds=start,durationSeconds=duration,kakaoVideoStartRelativeReplaySeconds=kakaoOffset,ourVideoStartRelativeReplaySeconds=oursOffset,timelineAccuracy='approximately ±4 seconds; not latency measurement',method='Kakao timestamped screenshot/frame alignment; our recorder host timestamp; same archived ordered GPS samples',audioClips=clips,crop='Our original1920x1080 recording: app region x640 y68 w1260 h906; no map drawn or screen state generated')
(root/f'{a.case}-edit-decisions.json').write_text(json.dumps(info,ensure_ascii=False,indent=2)+'\n')
filters='[1:v]scale=386:860,setsar=1[k];[2:v]crop=1260:906:640:68,scale=1196:860,setsar=1[o];[0:v][k]overlay=84:166[tmp];[tmp][o]overlay=624:166,format=yuv420p[v]'
subprocess.run(['ffmpeg','-v','error','-y','-loop','1','-framerate','30','-i',str(background),'-ss',str(start-kakaoOffset),'-i',str(old/f'case-{a.case}-ko-context.mp4'),'-ss',str(start-oursOffset),'-i',str(run/'our-app.mp4'),'-i',str(audiofile),'-filter_complex_threads','2','-filter_complex',filters,'-map','[v]','-map','3:a','-t',str(duration),'-r','30','-c:v','libx264','-threads','2','-preset','veryfast','-crf','20','-c:a','aac','-b:a','128k','-movflags','+faststart',str(root/f'case-{a.case}-comparison.mp4')],check=True)
print('Rendered',a.case,flush=True)
