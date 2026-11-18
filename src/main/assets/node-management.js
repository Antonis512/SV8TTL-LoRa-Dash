'use strict';
// Extend this per-radio menu when owner and preset management are implemented.
const nodeManagementSections=[{id:'channels',title:'Channels',description:'Add a channel to this radio'}];
let managedNode=null,managedNodeInfo=null,managedNodeTimer=null;
function openNodeManagement(item){
  if(state?.memberConfig?.enabled)return;
  managedNode={radio:item.radio,id:item.id,name:item.name||item.id};managedNodeInfo=null;
  el('node-management-identity').textContent=managedNode.name+' · '+item.radio.toUpperCase()+' · '+item.id;
  el('node-channel-name').value='';el('node-channel-key').value='';el('node-key-show').checked=false;el('node-channel-key').type='password';
  const menu=el('node-management-menu');menu.replaceChildren();
  for(const section of nodeManagementSections){const button=node('button','');button.type='button';button.append(node('strong','',section.title),node('span','',section.description));button.onclick=()=>showNodeManagementSection(section.id);menu.append(button);}
  const modes=el('node-channel-mode');modes.replaceChildren();
  const choices=item.radio==='mc'?[['hashtag','Public hashtag (#name)'],['private','Private key']]:[['private','Private key'],['default','Public default key'],['none','No encryption']];
  for(const [value,label] of choices)modes.add(new Option(label,value));
  el('node-channel-help').textContent=item.radio==='mc'?'Name: up to 31 UTF-8 bytes. Hashtag channels are public and use the exact #name. Private channels need the same key on each radio.':'Name: up to 11 UTF-8 bytes. Added as a secondary channel; the primary channel and radio preset stay unchanged. Use the same name and key on each radio.';
  el('node-channel-key-label').textContent=item.radio==='mc'?'Key (32 hexadecimal characters)':'Key (Base64, 16 or 32 bytes)';
  changeNodeChannelMode();showNodeManagementSection('home');
  el('node-channel-status').textContent='Loading radio channels…';el('node-channel-add').disabled=true;
  openSettingsWindow('node-management');refreshNodeManagement();
  clearInterval(managedNodeTimer);managedNodeTimer=setInterval(refreshNodeManagement,2000);
}
function showNodeManagementSection(id){el('node-management-menu').hidden=id!=='home';el('node-management-channels').hidden=id!=='channels';}
function refreshNodeManagement(){if(managedNode&&el('settings-window-node-management').open)AndroidBridge.getNodeManagement(managedNode.radio,managedNode.id);}
function changeNodeChannelMode(){
  const privateKey=el('node-channel-mode').value==='private';el('node-channel-key-fields').hidden=!privateKey;el('node-channel-key').required=privateKey;
  el('node-channel-name').placeholder=el('node-channel-mode').value==='hashtag'?'#local':'Channel name';
}
function generateNodeChannelKey(){if(managedNode)el('node-channel-key').value=AndroidBridge.generateChannelKey(managedNode.radio==='mc');}
function addNodeChannel(event){
  event.preventDefault();if(!managedNode||!managedNodeInfo?.ready||managedNodeInfo.blocked||managedNodeInfo.freeSlot<0)return;
  const name=el('node-channel-name').value.trim(),mode=el('node-channel-mode').value,key=el('node-channel-key').value.trim();
  const size=new TextEncoder().encode(name).length,limit=managedNode.radio==='mc'?31:11;
  if(!size||size>limit||name.includes('\0')){el('node-channel-status').textContent='Name must be 1–'+limit+' UTF-8 bytes.';return;}
  if(mode==='hashtag'&&(!name.startsWith('#')||name.length<2)){el('node-channel-status').textContent='Enter a public channel name beginning with #.';return;}
  managedNodeInfo.blocked=true;el('node-channel-add').disabled=true;el('node-channel-status').textContent='Adding channel… waiting for radio verification.';
  AndroidBridge.addRadioChannel(managedNode.radio,managedNode.id,name,mode,key);
}
window.onNodeManagement=function(info){
  if(!managedNode||info.radio!==managedNode.radio||info.id!==managedNode.id||!el('settings-window-node-management').open)return;
  managedNodeInfo=info;const list=el('node-channel-list');list.replaceChildren();
  for(const channel of info.channels)list.append(node('div','node-channel-row',channel.name));
  if(!info.channels.length)list.append(node('p','help',info.ready?'No active channels reported.':'Connect this radio and wait for its channels.'));
  el('node-channel-slot').textContent=!info.ready?'Channel slots unavailable while disconnected or loading.':info.freeSlot<0?'No empty slots reported.':'An empty slot reported by this radio will be used.';
  el('node-channel-add').disabled=!info.ready||info.blocked||info.freeSlot<0;
  for(const control of el('node-channel-form').querySelectorAll('input,select,button:not([type="submit"])'))control.disabled=info.busy;
  if(info.message)el('node-channel-status').textContent=info.message;
};
el('settings-window-node-management').addEventListener('close',()=>{clearInterval(managedNodeTimer);managedNodeTimer=null;managedNode=null;managedNodeInfo=null;el('node-channel-key').value='';});
