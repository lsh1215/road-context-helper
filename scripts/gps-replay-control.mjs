#!/usr/bin/env node
// Public installed emulator_controller.proto only; synthetic emulator GPS input.
// Does not change application code or manufacture NaviHelper callbacks.
import fs from 'node:fs';
import http2 from 'node:http2';
const [discovery, requestJson] = process.argv.slice(2);
if (!discovery || !requestJson) throw new Error('Usage: gps-replay-control.mjs discovery.ini request.json');
const settings = Object.fromEntries(fs.readFileSync(discovery, 'utf8').split('\n').filter(x=>x.includes('=')).map(x=>{const i=x.indexOf('=');return [x.slice(0,i).trim(),x.slice(i+1).trim()];}));
const port=Number(settings['grpc.port']);
if(port!==8561) throw new Error('Only dedicated RoadContext gRPC port 8561 allowed');
const client=http2.connect(`http://127.0.0.1:${port}`);

function encode(s){
 const chunks=[Buffer.from([8,s.passiveUpdate?1:0])];
 for(const [id,key] of [[2,'latitude'],[3,'longitude'],[4,'speed'],[5,'bearing'],[6,'altitude']]) { const b=Buffer.alloc(9); b[0]=(id<<3)|1;b.writeDoubleLE(s[key]??0,1);chunks.push(b); }
 chunks.push(Buffer.from([56,s.satellites??0]));return Buffer.concat(chunks);
}
function decode(b){
 const s={passiveUpdate:false,latitude:0,longitude:0,speed:0,bearing:0,altitude:0,satellites:0};
 const keys={2:'latitude',3:'longitude',4:'speed',5:'bearing',6:'altitude'};
 for(let i=0;i<b.length;){ const tag=b[i++],wire=tag&7,id=tag>>>3;
 if(wire===0){let v=0,m=1;while(true){const n=b[i++];v+=(n&127)*m;if(!(n&128))break;m*=128;}if(id===1)s.passiveUpdate=!!v;if(id===7)s.satellites=v;}
 else if(wire===1){if(keys[id])s[keys[id]]=b.readDoubleLE(i);i+=8;}else throw new Error('Unexpected GPS protobuf wire');
 }return s;
}
function rpc(method,payload=Buffer.alloc(0)){
 return new Promise((resolve,reject)=>{
 const headers={':method':'POST',':path':`/android.emulation.control.EmulatorController/${method}`,'content-type':'application/grpc',te:'trailers'};
 if(settings['grpc.token'])headers.authorization=`Bearer ${settings['grpc.token']}`;
 const req=client.request(headers),chunks=[];let status='0';
 const timer=setTimeout(()=>{req.close();reject(new Error('gRPC timeout'));},5000);
 req.on('response',h=>{if(h[':status']!==200)status=String(h[':status']);if(h['grpc-status'])status=String(h['grpc-status']);});
 req.on('trailers',h=>{if(h['grpc-status'])status=String(h['grpc-status']);});
 req.on('data',b=>chunks.push(b));req.on('error',()=>{clearTimeout(timer);reject(new Error('gRPC transport error'));});
 req.on('end',()=>{clearTimeout(timer);if(status!=='0')return reject(new Error(`gRPC status ${status}`));const data=Buffer.concat(chunks);if(!data.length)return resolve(Buffer.alloc(0));if(data.length<5||data[0]!==0||data.readUInt32BE(1)!==data.length-5)return reject(new Error('Invalid unary gRPC frame'));resolve(data.subarray(5));});
 const frame=Buffer.alloc(5);frame.writeUInt32BE(payload.length,1);req.end(Buffer.concat([frame,payload]));
 });
}
try {
 const state=JSON.parse(requestJson);
 if (!state || !Number.isFinite(state.latitude) || !Number.isFinite(state.longitude) || Math.abs(state.latitude)>90 || Math.abs(state.longitude)>180 || !Number.isFinite(state.bearing) || state.bearing<0 || state.bearing>=360 || !Number.isFinite(state.speed) || state.speed<0 || state.speed>30 || !Number.isInteger(state.satellites) || state.satellites<0 || state.satellites>12 || typeof state.passiveUpdate !== 'boolean') throw new Error('Invalid GPS input');
 await rpc('setGps',encode(state));
 const actual=decode(await rpc('getGps'));
 console.log(JSON.stringify(actual));
} finally { client.close(); }
