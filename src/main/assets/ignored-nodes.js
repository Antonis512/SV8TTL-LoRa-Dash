'use strict';
let ignoredNodesTimer=null,ignoredNodesSignature='';
function openIgnoredNodes(){
  openSettingsWindow('ignored');
  if(!el('settings-window-ignored').open)return;
  ignoredNodesSignature='';el('ignored-status').textContent='Loading ignored nodes…';
  clear(el('ignored-list'));AndroidBridge.getIgnoredNodes();
  clearInterval(ignoredNodesTimer);
  ignoredNodesTimer=setInterval(()=>{if(el('settings-window-ignored').open)AndroidBridge.getIgnoredNodes();},2000);
}
el('settings-window-ignored').addEventListener('close',()=>{clearInterval(ignoredNodesTimer);ignoredNodesTimer=null;});
window.onIgnoredNodes=function(data){
  if(!el('settings-window-ignored').open)return;
  if(data.message)el('ignored-status').textContent=data.message;
  else if(el('ignored-status').textContent==='Loading ignored nodes…')el('ignored-status').textContent='';
  const signature=JSON.stringify(data.radios||[]);if(signature===ignoredNodesSignature)return;ignoredNodesSignature=signature;
  const list=el('ignored-list'),scroll=el('settings-window-ignored').querySelector('.settings-window-body').scrollTop;clear(list);
  for(const radio of data.radios||[]){
    const section=node('section','radio-card');section.appendChild(node('strong','',(radio.name||radio.id)+' · '+radio.id));
    section.appendChild(node('p','help',radio.ready?'Connected':radio.loading?'Loading radio — showing last reported ignored nodes':'Disconnected — showing last reported ignored nodes. Connect this radio to unignore.'));
    for(const entry of radio.nodes||[]){
      const row=node('div','actions');row.appendChild(node('span','',(entry.name||entry.id)+(entry.name&&entry.name!==entry.id?' · '+entry.id:'')));
      const button=node('button','','Unignore');button.type='button';button.disabled=!radio.ready||radio.busy;
      button.onclick=()=>{button.disabled=true;el('ignored-status').textContent='Unignoring node… waiting for radio confirmation.';AndroidBridge.setNodeIgnored(radio.id,entry.id,false);};
      row.appendChild(button);section.appendChild(row);
    }
    if(!(radio.nodes||[]).length)section.appendChild(node('p','help',radio.ready?'No ignored nodes on this radio.':'No cached ignored nodes.'));
    list.appendChild(section);
  }
  if(!(data.radios||[]).length)list.appendChild(node('p','help','Connect a Meshtastic radio to load its ignored nodes.'));
  el('settings-window-ignored').querySelector('.settings-window-body').scrollTop=scroll;
};
