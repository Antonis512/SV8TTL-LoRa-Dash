'use strict';
let signalView=null;
function openSignalHistory(key,peer) {
  signalView={key,peer,hours:24,message:''};
  const card=dialogCard('Signal Quality');card.id='signal-history';
  card.appendChild(node('p','','Loading saved readings…'));
  AndroidBridge.signalHistory(key,peer,false);
}
function signalViewOpen(key,peer) {
  return signalView?.key===key&&signalView.peer===peer&&!el('dm-context').hidden&&!!el('signal-history');
}
window.onSignalSampleSaved=(key,peer)=>{
  if(signalViewOpen(key,peer))AndroidBridge.signalHistory(key,peer,false);
};
window.onSignalRequestResult=(key,peer,message,pending=false)=>{
  if(!signalViewOpen(key,peer))return;
  signalView.message=message;signalView.pending=pending;if(signalView.data){signalView.data.requestStatus=message;signalView.data.requestPending=pending;renderSignalHistory();}AndroidBridge.signalHistory(key,peer,false);
};
function signalDate(time) {
  return new Date(time).toLocaleString(undefined,{year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'});
}
function signalGraph(rows,field,label,color) {
  const host=node('section','signal-plot');host.appendChild(node('h3','',label));
  const points=rows.filter(r=>Number.isFinite(r[field])).sort((a,b)=>a.time-b.time);
  if(!points.length){host.appendChild(node('p','help','No direct '+label+' readings in this period.'));return host;}
  const ns='http://www.w3.org/2000/svg';
  const svg=document.createElementNS(ns,'svg');svg.setAttribute('viewBox','0 0 600 160');svg.setAttribute('role','img');svg.setAttribute('aria-label',label+' over time, direct receptions only');
  const add=(tag,attrs,text)=>{const e=document.createElementNS(ns,tag);for(const [k,v]of Object.entries(attrs))e.setAttribute(k,String(v));if(text!==undefined)e.textContent=text;svg.appendChild(e);return e;};
  const low=Math.floor(Math.min(...points.map(r=>r[field])))-1,high=Math.ceil(Math.max(...points.map(r=>r[field])))+1;
  const start=points[0].time,end=points.at(-1).time;
  const x=t=>start===end?320:50+530*(t-start)/(end-start),y=v=>125-105*(v-low)/(high-low);
  for(const value of [low,(low+high)/2,high]){add('line',{x1:50,x2:580,y1:y(value),y2:y(value),stroke:'#39414b'});add('text',{x:44,y:y(value)+4,'text-anchor':'end',fill:'#aab2bd','font-size':12},Number(value.toFixed(1)));}
  add('polyline',{points:points.map(r=>x(r.time)+','+y(r[field])).join(' '),fill:'none',stroke:color,'stroke-width':2});
  for(const r of points){const dot=add('circle',{cx:x(r.time),cy:y(r[field]),r:3,fill:color});const title=document.createElementNS(ns,'title');title.textContent=signalDate(r.time)+' · '+r[field]+' '+(field==='snr'?'dB':'dBm');dot.appendChild(title);}
  add('text',{x:50,y:150,fill:'#aab2bd','font-size':11},signalDate(start));
  if(start!==end)add('text',{x:580,y:150,'text-anchor':'end',fill:'#aab2bd','font-size':11},signalDate(end));
  host.appendChild(svg);return host;
}
window.onSignalHistory=data=>{
  if(!signalViewOpen(data.key,data.peer))return;
  signalView.data=data;signalView.message=data.requestStatus??signalView.message;signalView.pending=!!data.requestPending;renderSignalHistory();
};
function renderSignalHistory() {
  const view=signalView,data=view.data,card=el('signal-history');if(!card||!data)return;
  const oldScroll=card.scrollTop;clear(card);
  card.appendChild(node('h2','','Signal Quality · '+data.name));
  card.appendChild(node('p','help','Receiving MT radio: '+(data.receiver||data.key)));
  const tools=node('div','signal-tools');
  const range=node('select','form-input');range.setAttribute('aria-label','Signal history period');
  for(const [hours,title]of [[24,'Last 24 hours'],[168,'Last 7 days'],[0,'All saved']]){const o=node('option','',title);o.value=hours;range.appendChild(o);}
  range.value=view.hours;range.onchange=()=>{view.hours=Number(range.value);renderSignalHistory();};tools.appendChild(range);
  const request=dialogAction(tools,'Request node statistics',()=>{view.message='Sending request to radio…';view.pending=true;renderSignalHistory();AndroidBridge.nodeAction(data.key,data.peer,'signal','');});request.disabled=!data.connected||!!view.pending;
  dialogAction(tools,'Refresh',()=>AndroidBridge.signalHistory(data.key,data.peer,false));card.appendChild(tools);
  const status=node('p','signal-request-status',view.message);status.setAttribute('role','status');card.appendChild(status);
  const cutoff=view.hours?Date.now()-view.hours*3600000:0;
  const rows=data.rows.filter(r=>r.time>=cutoff).sort((a,b)=>b.time-a.time);
  const direct=rows.filter(r=>r.hops===0&&!r.mqtt&&(Number.isFinite(r.snr)||Number.isFinite(r.rssi)));
  card.appendChild(node('p','help','Direct receptions only in these graphs. Readings accumulate automatically when this app receives packets from the connected radio.'));
  card.append(signalGraph(direct,'snr','SNR (dB)','#79d894'),signalGraph(direct,'rssi','RSSI (dBm)','#72b8ff'));
  if(direct.length){const summary=node('p','help',direct.length+' direct receptions. ');for(const [field,unit]of [['snr','dB'],['rssi','dBm']]){const values=direct.map(r=>r[field]).filter(Number.isFinite);if(values.length)summary.appendChild(document.createTextNode(field.toUpperCase()+': latest '+values[0]+' '+unit+', min '+Math.min(...values)+', max '+Math.max(...values)+', average '+(values.reduce((a,b)=>a+b,0)/values.length).toFixed(1)+'. '));}card.appendChild(summary);}
  card.appendChild(node('h3','','Node statistics'));
  card.appendChild(node('p','help','Statistics reported by the selected node, including its noise floor when available. These may arrive through relays and are separate from signal strength measured at your radio.'));
  const stats=rows.filter(r=>r.stats);
  if(!stats.length)card.appendChild(node('p','help','No saved node statistics in this period. Remote requests depend on firmware support.'));
  for(const r of stats){const item=node('details','signal-record');item.appendChild(node('summary','',signalDate(r.time)+' · '+(r.requested?'Requested':'Automatically received')+(r.phoneTime?' · phone receipt time':'')));item.appendChild(node('pre','',r.stats));card.appendChild(item);}
  card.appendChild(node('h3','','Reception details'));
  if(!rows.length)card.appendChild(node('p','help','No saved readings yet.'));
  for(const r of rows.filter(r=>Number.isFinite(r.snr)||Number.isFinite(r.rssi))){const item=node('div','signal-record');const direct=r.hops===0&&!r.mqtt;item.appendChild(node('strong','',signalDate(r.time)+(r.phoneTime?' · phone receipt time':'')));item.appendChild(node('p','',direct?'Direct · 0 hops':r.hops>0?'Last-hop reception · '+r.hops+' hops':'Unknown route · excluded from direct graph'));item.appendChild(node('p','','SNR: '+(Number.isFinite(r.snr)?r.snr+' dB':'not reported')+' · RSSI: '+(Number.isFinite(r.rssi)?r.rssi+' dBm':'not reported')));card.appendChild(item);}
  card.appendChild(node('p','help','Stored on this phone: newest 1,000 records per receiving radio and remote node. Signal history is not included in App Backup.'));
  dialogAction(card,'Clear this signal history',()=>{if(!card.querySelector('.signal-clear-confirm')){const confirm=node('div','signal-clear-confirm');confirm.appendChild(node('p','','Delete all saved signal readings and node statistics for this receiver/node pair?'));dialogAction(confirm,'Delete saved readings',()=>{view.message='History cleared.';AndroidBridge.signalHistory(data.key,data.peer,true);});dialogAction(confirm,'Keep history',()=>confirm.remove());card.appendChild(confirm);confirm.scrollIntoView({block:'nearest'});}});
  dialogAction(card,'Close',()=>{el('dm-context').hidden=true;signalView=null;});card.scrollTop=oldScroll;
}
