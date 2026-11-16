'use strict';

let usageInitialized=false;
let usageDefaultEnd='';
function usageLocalMinute(time) {
  const date=new Date(time);
  return new Date(time-date.getTimezoneOffset()*60000).toISOString().slice(0,16);
}
function openUsageHistory() {
  const now=Date.now();
  if(!usageInitialized)el('usage-start').value=usageLocalMinute(now-86400000);
  if(!usageInitialized || el('usage-end').value===usageDefaultEnd) {
    usageDefaultEnd=usageLocalMinute(now);
    el('usage-end').value=usageDefaultEnd;
  }
  el('usage-record').checked=AndroidBridge.usageRecording();
  const devices=new Map();
  for(const d of state?.myNodes||[])if(d.radio==='mt')devices.set(d.id,(d.name && d.name!==d.id)?d.name+' · '+d.id:d.id);
  for(const id of JSON.parse(AndroidBridge.usageDevices()))if(!devices.has(id))devices.set(id,id);
  const list=el('usage-devices'),target=el('usage-delete-device');
  const selected=new Set([...list.querySelectorAll('input:checked')].map(input=>input.value));
  const previousTarget=target.value, first=!usageInitialized || !list.querySelector('input');
  list.replaceChildren();target.replaceChildren();
  for(const [id,name] of devices) {
    const label=document.createElement('label'),input=document.createElement('input');
    input.type='checkbox';input.value=id;input.checked=first || selected.has(id);
    input.onchange=refreshUsage;
    label.append(input,' '+name);list.append(label);
    target.add(new Option(name,id));
  }
  if(devices.has(previousTarget))target.value=previousTarget;
  if(!devices.size)list.textContent='Connect a Meshtastic radio to start recording.';
  usageInitialized=true;refreshUsage();
}
function usageRange() {
  const start=new Date(el('usage-start').value).getTime(),end=new Date(el('usage-end').value).getTime()+59999;
  return Number.isFinite(start)&&Number.isFinite(end)&&start>=0&&end>=start?[start,end]:null;
}
function refreshUsage() {
  const result=el('usage-results'),range=usageRange();
  result.replaceChildren();
  if(!range){result.textContent='Choose a valid start and end time.';return;}
  const inputs=[...el('usage-devices').querySelectorAll('input:checked')];
  if(!inputs.length){result.textContent='Select a device to view its records.';return;}
  if(el('usage-mode').value==='graph')drawUsageGraph(result,inputs,range);
  else for(const input of inputs){
    const section=document.createElement('section');result.append(section);
    drawUsageTable(section,input,range,0);
  }
}
function drawUsageTable(section,input,range,page) {
  const rows=JSON.parse(AndroidBridge.usageHistory(input.value,...range,false,page));
  section.replaceChildren(node('h3','',input.parentElement.textContent));
  if(!rows.length){section.append('No records in this period.');return;}
  const table=document.createElement('table'),header=table.createTHead().insertRow();
  for(const title of ['Recorded','AirUtil TX','ChUtil'])header.append(node('th','',title));
  const body=table.createTBody();
  for(const [time,air,channel] of rows.slice(0,100)){
    const row=body.insertRow();
    for(const value of [new Date(time).toLocaleString(),air,channel])
      row.insertCell().textContent=typeof value==='number'?value.toFixed(2)+'%':value??'—';
  }
  section.append(table);
  for(const [label,next,enabled] of [['Previous',page-1,page>0],['Next',page+1,rows.length>100]]){
    const button=node('button','',label);button.disabled=!enabled;
    button.onclick=()=>drawUsageTable(section,input,range,next);section.append(button);
  }
}
function drawUsageGraph(result,inputs,[start,end]) {
  const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');
  svg.setAttribute('viewBox','0 0 480 185');svg.setAttribute('role','img');
  svg.setAttribute('aria-label','Meshtastic utilization by time, from 0 to 100 percent');
  const add=(tag,attrs,label)=>{
    const item=document.createElementNS(svg.namespaceURI,tag);
    for(const [key,value] of Object.entries(attrs))item.setAttribute(key,value);
    if(label)item.textContent=label;
    svg.append(item);
  };
  result.append(svg,node('p','help','Solid line: AirUtil TX · Dashed line: ChUtil. Points show averages within each time interval.'));
  const x=time=>40+430*(time-start)/(end-start),y=value=>145-1.35*value;
  for(const level of [0,25,50,75,100]){
    add('line',{x1:40,x2:470,y1:y(level),y2:y(level),stroke:'currentColor',opacity:.2});
    add('text',{x:36,y:y(level)+4,'text-anchor':'end','font-size':12,fill:'currentColor'},level+'%');
  }
  for(const [time,anchor] of [[start,'start'],[end,'end']])
    add('text',{x:x(time),y:168,'text-anchor':anchor,'font-size':12,fill:'currentColor'},new Date(time).toLocaleString());
  inputs.forEach((input,index)=>{
    const rows=JSON.parse(AndroidBridge.usageHistory(input.value,start,end,true,0));
    const color='hsl('+((175+index*137.5)%360)+' 72% 44%)';
    const legend=node('div','',input.parentElement.textContent+(rows.length?'':' · No records'));
    legend.style.borderLeft='5px solid '+color;legend.style.paddingLeft='0.5em';result.append(legend);
    for(const metric of [1,2]){
      let path='',previous=false;
      for(const row of rows){
        if(row[metric]===null){previous=false;continue;}
        const cx=x(row[0]),cy=y(row[metric]);
        path+=(previous?' L':' M')+cx+' '+cy;previous=true;
        add('circle',{cx,cy,r:metric===1?2:1.3,fill:color});
      }
      add('path',{d:path,stroke:color,fill:'none','stroke-width':2,'stroke-dasharray':metric===2?'5 4':'none'});
    }
  });
}
function deleteUsageRecords(all) {
  const target=el('usage-delete-device'),range=all?[0,Number.MAX_SAFE_INTEGER]:usageRange();
  if(!target.value || !range)return;
  const period=all?'all recorded times':el('usage-start').value.replace('T',' ')+' through '+el('usage-end').value.replace('T',' ');
  if(!confirm('Delete utilization records for '+target.selectedOptions[0].text+' for '+period+'?'))return;
  AndroidBridge.deleteUsage(target.value,...range);
}
function usageDeleted(count) {
  if(!el('settings-window-usage').open)return;
  openUsageHistory();
  el('usage-results').prepend(node('p','help',count+' utilization records deleted.'));
}
