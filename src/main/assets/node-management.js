'use strict';
// Extend this per-radio menu when owner and preset management are implemented.
const nodeManagementSections=[{id:'channels',title:'Channels',description:'Add or remove channels on this radio'}];
let managedNode=null,managedNodeInfo=null,managedNodeTimer=null;
function openNodeManagement(item){
  if(state?.memberConfig?.enabled)return;
  managedNode={radio:item.radio,id:item.id,name:item.name||item.id};managedNodeInfo=null;
  el('node-management-identity').textContent=managedNode.name+' · '+item.radio.toUpperCase()+' · '+item.id;
  el('node-channel-name').value='';el('node-channel-key').value='';el('node-key-show').checked=false;el('node-channel-key').type='password';
  const menu=el('node-management-menu');menu.replaceChildren();
  for(const section of nodeManagementSections){const button=node('button','');button.type='button';button.append(node('strong','',section.title),node('span','',section.description));button.onclick=()=>showNodeManagementSection(section.id);menu.append(button);}
  const copy=node('button','');copy.id='node-channel-copy';copy.type='button';copy.disabled=true;
  copy.append(node('strong','',tr('Copy channels between radios')),node('span','',tr('Copy from this radio to a connected radio of the same network · empty slots only')));
  copy.onclick=()=>{if(managedNode&&managedNodeInfo?.ready&&!managedNodeInfo.blocked)AndroidBridge.copyRadioChannels(managedNode.radio,managedNode.id);};menu.append(copy);
  const modes=el('node-channel-mode');modes.replaceChildren();
  const choices=item.radio==='mc'?[['hashtag',tr('Public hashtag (#name)')],['private',tr('Private key')]]:[['private',tr('Private key')],['default',tr('Public default key')],['none',tr('No encryption')]];
  for(const [value,label] of choices)modes.add(new Option(label,value));
  el('node-channel-help').textContent=item.radio==='mc'?tr('Name: up to 31 UTF-8 bytes. Hashtag channels are public and use the exact #name. Private channels need the same key on each radio.'):tr('Name: up to 11 UTF-8 bytes. Added as a secondary channel; the primary channel and radio preset stay unchanged. Use the same name and key on each radio.');
  el('node-channel-key-label').textContent=item.radio==='mc'?tr('Key (32 hexadecimal characters)'):tr('Key (Base64, 16 or 32 bytes)');
  changeNodeChannelMode();showNodeManagementSection('home');
  el('node-channel-status').textContent=tr('Loading radio channels…');el('node-channel-add').disabled=true;
  openSettingsWindow('node-management');refreshNodeManagement();
  clearInterval(managedNodeTimer);managedNodeTimer=setInterval(refreshNodeManagement,2000);
}
function showNodeManagementSection(id){el('node-management-menu').hidden=id!=='home';el('node-management-channels').hidden=id!=='channels';}
function refreshNodeManagement(){if(managedNode&&el('settings-window-node-management').open)AndroidBridge.getNodeManagement(managedNode.radio,managedNode.id);}
function changeNodeChannelMode(){
  const privateKey=el('node-channel-mode').value==='private';el('node-channel-key-fields').hidden=!privateKey;el('node-channel-key').required=privateKey;
  el('node-channel-name').placeholder=el('node-channel-mode').value==='hashtag'?'#local':tr('Channel name');
}
function generateNodeChannelKey(){if(managedNode)el('node-channel-key').value=AndroidBridge.generateChannelKey(managedNode.radio==='mc');}
function addNodeChannel(event){
  event.preventDefault();if(!managedNode||!managedNodeInfo?.ready||managedNodeInfo.blocked||managedNodeInfo.freeSlot<0)return;
  const name=el('node-channel-name').value.trim(),mode=el('node-channel-mode').value,key=el('node-channel-key').value.trim();
  const size=new TextEncoder().encode(name).length,limit=managedNode.radio==='mc'?31:11;
  if(!size||size>limit||name.includes('\0')){el('node-channel-status').textContent=tr('Name must be 1–')+limit+tr(' UTF-8 bytes.');return;}
  if(mode==='hashtag'&&(!name.startsWith('#')||name.length<2)){el('node-channel-status').textContent=tr('Enter a public channel name beginning with #.');return;}
  managedNodeInfo.blocked=true;el('node-channel-add').disabled=true;el('node-channel-status').textContent=tr('Adding channel… waiting for radio verification.');
  AndroidBridge.addRadioChannel(managedNode.radio,managedNode.id,name,mode,key);
}
window.onNodeManagement=function(info){
  if(!managedNode||info.radio!==managedNode.radio||info.id!==managedNode.id||!el('settings-window-node-management').open)return;
  managedNodeInfo=info;const list=el('node-channel-list');list.replaceChildren();
  el('node-channel-copy').disabled=!info.ready||info.blocked||!info.channels.length;
  for(const channel of info.channels){
    const row=node('div','node-channel-row',channel.name+' '),remove=node('button','',tr('Remove'));remove.type='button';
    remove.disabled=!info.ready||info.blocked||channel.index===0;
    remove.title=channel.index===0?tr('Primary/default channel is protected'):'';
    remove.onclick=()=>{if(!confirm(tr('Remove ')+channel.name+' from '+managedNode.name+tr('? This changes the radio.')))return;
      remove.disabled=true;AndroidBridge.removeRadioChannel(managedNode.radio,managedNode.id,channel.index,channel.name);};
    row.append(remove);list.append(row);
  }
  if(!info.channels.length)list.append(node('p','help',info.ready?tr('No active channels reported.'):tr('Connect this radio and wait for its channels.')));
  el('node-channel-slot').textContent=!info.ready?tr('Channel slots unavailable while disconnected or loading.'):info.freeSlot<0?tr('No empty slots reported.'):tr('An empty slot reported by this radio will be used.');
  el('node-channel-add').disabled=!info.ready||info.blocked||info.freeSlot<0;
  for(const control of el('node-channel-form').querySelectorAll('input,select,button:not([type="submit"])'))control.disabled=info.busy;
  if(info.message)el('node-channel-status').textContent=info.message;
};
el('settings-window-node-management').addEventListener('close',()=>{clearInterval(managedNodeTimer);managedNodeTimer=null;managedNode=null;managedNodeInfo=null;el('node-channel-key').value='';});
