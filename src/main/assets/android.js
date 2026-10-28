'use strict';
function compareContacts(a,b) {
  const unnamed=x=>!x.name||x.name===x.id||/^!?[0-9a-f]{8}$/i.test(x.name);
  return Number(unnamed(a))-Number(unnamed(b))||(a.name||a.id).localeCompare(b.name||b.id);
}
let state = null;
let network = 'left';
let savedDmMode=false;
function sideId(slot){return slot==='left'?'mc':slot==='right'?'mt':slot;}
function radioType(slot){return state?.[slot]?.radio||slot;}
function radioLabel(slot){return state?.[slot]?.ownId?radioType(slot).toUpperCase():'';}
function chatSlot(radio){return radio==='mc'?'left':radio==='mt'?'right':radio;}
let channel = {left:0,right:0};
let conversation = {mc: 'dm', mt: 'dm'};
let dmMode = true;
let dmSelection = {radio: null, peer: null};
const dmDrafts=new Map();
function dmDraftKey(radio,peer){return JSON.stringify([radioType(radio),state[radio].ownId,peer]);}
function saveDmDraft(){if(dmSelection.peer)dmDrafts.set(dmDraftKey(dmSelection.radio,dmSelection.peer),el('dm-input').value);}
let nodeFilter = 'all';
let nodeSort='name';
function setNodeSort(value){nodeSort=['name','hops-asc','hops-desc','heard-asc','heard-desc'].includes(value)?value:'name';renderNodes();}
function compareNodeRows(a,b){
 if(nodeSort.startsWith('heard-')){
  const ah=Number(a.entry.lastHeard)||0,bh=Number(b.entry.lastHeard)||0;
  if((ah>0)!==(bh>0))return ah>0?-1:1;
  if(ah!==bh)return (ah-bh)*(nodeSort==='heard-desc'?-1:1);
 } else if(nodeSort!=='name'){
  const ah=Number.isInteger(a.entry.hops)&&a.entry.hops>=0,bh=Number.isInteger(b.entry.hops)&&b.entry.hops>=0;
  if(ah!==bh)return ah?-1:1;
  if(ah&&a.entry.hops!==b.entry.hops)return (a.entry.hops-b.entry.hops)*(nodeSort==='hops-desc'?-1:1);
 }
 return compareContacts(a.entry,b.entry)||a.id.localeCompare(b.id)||a.entry.id.localeCompare(b.entry.id);
}
let nodeKind='all', nodeRole='all', scrollToOpenedNode=false;
let favoritesOnly = false;
let openedNode = null;
let savedNode = null;
let savedChannel = null;
let pendingUnreadDevice=null;
let settingsPage='home';
const historyPages=new Map();
function historyPageKey(radio,peer,ch){return JSON.stringify([state[radio].key,peer,ch]);}
function addHistoryLoader(host,radio,peer,ch,entries){
 const key=historyPageKey(radio,peer,ch),status=historyPages.get(key);
 if(!entries.length||status?.done)return;
 const button=node('button','history-older',status?.loading?'Loading older messages…':'Load older messages');
 button.disabled=!!status?.loading;
 button.onclick=()=>{
  if(historyPages.get(key)?.loading)return;
  historyPages.set(key,{loading:true,host,height:host.scrollHeight,top:host.scrollTop});
  button.disabled=true;button.textContent='Loading older messages…';
  AndroidBridge.loadOlder(state[radio].key,peer,ch,entries[0].id);
 };
 host.appendChild(button);
 host.onscroll=()=>{if(host.scrollTop<24&&!button.disabled)button.click();};
}
window.onHistoryPage=(key,peer,ch,count)=>{
 const id=JSON.stringify([key,peer,ch]),saved=historyPages.get(id);
 const done=count>=0&&count<100;historyPages.set(id,{done});
 const current=peer?(dmSelection.peer===peer&&state?.[dmSelection.radio]?.key===key):(state?.[network]?.key===key&&channel[network]===ch);
 if(saved?.host&&current){if(count>=0)saved.host.scrollTop=saved.top+saved.host.scrollHeight-saved.height;
  const button=saved.host.querySelector('.history-older');if(button){button.disabled=false;button.textContent='Load older messages';if(done)button.remove();}}
};
let usersListVisible={left:true,right:true};
let chatFontStep=0, composerFontStep=0;
let sideFontSteps={left:0,right:0};
let activeTrace=null, pendingCompose=null;
let sideZoom=null;
const selectedKnown=new Set();
let activeTab='chat', restoringNavigation=false, navigationDepth=0;
const navigationStack=[];

function changeChatFont(delta) {
  const next=Math.max(0,Math.min(6,chatFontStep+delta));
  if(next!==chatFontStep) AndroidBridge.setChatFontStep(next);
}
function sideMagnifier(mode,radio) {
  const button=node('button','side-magnifier','🔍 Enlarge');
  button.type='button';
  button.setAttribute('aria-label','Enlarge '+radioLabel(radio)+' '+(mode==='dm'?'direct messages':'channels')+' list');
  button.onclick=()=>openSideZoom(mode,radio,button);
  return button;
}
function openSideZoom(mode,radio,trigger) { radio=chatSlot(radio);
  closeSideZoom();
  const side=el((mode==='dm'?'dm':'channel')+'-'+sideId(radio)+'-side');
  const overlay=el('side-zoom');
  sideZoom={side,parent:side.parentNode,next:side.nextSibling,trigger,radio,step:sideFontSteps[radio]};
  el('side-zoom-title').textContent=radioLabel(radio)+' '+(mode==='dm'?'DMs':'Channels');
  el('side-zoom-panel').style.setProperty('--side-zoom-extra',(sideZoom.step*2)+'px');
  overlay.classList.toggle('right',radio==='right');
  el('side-zoom-body').appendChild(side);
  overlay.hidden=false;
  el('side-zoom-smaller').disabled=sideZoom.step===0;
  el('side-zoom-larger').disabled=sideZoom.step===6;
  el('side-zoom-close').focus();
}
function changeSideZoom(delta) {
  if(!sideZoom) return;
  sideZoom.step=Math.max(0,Math.min(6,sideZoom.step+delta));
  el('side-zoom-panel').style.setProperty('--side-zoom-extra',(sideZoom.step*2)+'px');
  el('side-zoom-smaller').disabled=sideZoom.step===0;
  el('side-zoom-larger').disabled=sideZoom.step===6;
  sideFontSteps[sideZoom.radio]=sideZoom.step;
  AndroidBridge.setTextSize('side_'+sideId(sideZoom.radio),sideZoom.step);
}
function closeSideZoom() {
  if(!sideZoom) return;
  const {side,parent,next,trigger}=sideZoom;
  parent.insertBefore(side,next&&next.parentNode===parent?next:null);
  sideZoom=null;
  el('side-zoom').hidden=true;
  if(trigger.isConnected) trigger.focus();
}

function el(id) { return document.getElementById(id); }
function clear(node) { while (node.firstChild) node.removeChild(node.firstChild); }
function node(tag, className, text) {
  const item = document.createElement(tag);
  if (className) item.className = className;
  if (text !== undefined) item.textContent = String(text);
  return item;
}
function appendLinkedText(container, text) {
  const urls = /(?:https?:\/\/|www\.)[^\s<>"']+/gi;
  let offset = 0;
  let match;
  while ((match = urls.exec(text)) !== null) {
    const raw = match[0];
    const url = raw.replace(/[.,!?;:)\]]+$/, '');
    if (!url) continue;
    container.appendChild(document.createTextNode(text.slice(offset, match.index)));
    const link = node('a', 'chat-link', url);
    link.href = /^www\./i.test(url) ? 'https://' + url : url;
    container.appendChild(link);
    offset = match.index + url.length;
  }
  container.appendChild(document.createTextNode(text.slice(offset)));
}
function switchTab(tab) {
  activeTab=tab;
  for(const id of ['nodes-action-status','dm-chat-status']) el(id).textContent='';
  el('dm-context').hidden=true;
  closeSideZoom();
  for (const panel of document.querySelectorAll('.tab-panel')) panel.hidden = panel.id !== 'tab-' + tab;
  for (const button of document.querySelectorAll('.nav-btn')) button.classList.toggle('active', button.dataset.tab === tab);
  if (tab === 'chat') AndroidBridge.markRead(network);
}
function showSettingsPage(page) {
  settingsPage=['home','nodes','sounds','backup','channel-backup','connections','about','sending'].includes(page)?page:'home';
  el('settings-sending-page').hidden=settingsPage!=='sending';
  el('settings-home').hidden=settingsPage!=='home';
  el('settings-about-page').hidden=settingsPage!=='about';
  if(settingsPage==='about') AndroidBridge.about();
  el('settings-connections-page').hidden=settingsPage!=='connections';
  el('settings-my-nodes-page').hidden=settingsPage!=='nodes';
  el('settings-sounds-page').hidden=settingsPage!=='sounds';
  el('settings-backup-page').hidden=settingsPage!=='backup';
  el('settings-channel-backup-page').hidden=settingsPage!=='channel-backup';
  if(settingsPage==='channel-backup') renderBackupDevices();
  el('settings-content').scrollTop=0;
}
let pendingSavedView=null;
function openSettingsWindow(name) {
  const dialog=el('settings-window-'+name);
  if(dialog&&!dialog.open)dialog.showModal();
}
function closeSettingsWindow(name) {
  el('dm-context').hidden=true;
  if(name==='history')pendingSavedView=null;
  el('settings-window-'+name).close();
}
el('settings-window-history').addEventListener('cancel',()=>{pendingSavedView=null;});
function viewSavedMessages(radio,id) {
  pendingSavedView={radio,id};savedNode=null;
  clear(el('my-node-history'));el('my-node-history').appendChild(node('p','help','Loading saved messages…'));
  openSettingsWindow('history');AndroidBridge.viewSaved(radio,id);
}
function setNetwork(id) {
  id=chatSlot(id); network = id;
  dmMode=false;
  if(state && state[id].channels.length &&
      !state[id].channels.some(c=>c.index===channel[id])) channel[id]=state[id].channels[0].index;
  if(state && state[id].connected && state[id].channels.some(c=>c.index===channel[id]))
    AndroidBridge.markChannelRead(id,channel[id]);
  renderChat();
}
function chooseConversation(value) {
  if(value==='dm') openDmScreen();
  else selectChannel(network,Number(value.slice(3)));
}
function openChannelsScreen() {
  closeSideZoom();if(!state)return;
  const unread=['left','right'].find(id=>Object.keys(state[id].chats).some(ch=>conversationUnread(id,'',ch)));
  if(unread){const index=Object.keys(state[unread].chats).find(i=>conversationUnread(unread,'',i));selectChannel(unread,Number(index));}
  else setNetwork(network);
}
function selectChannel(radio,index) {
  radio=chatSlot(radio);network=radio; channel[radio]=index; dmMode=false;
  AndroidBridge.markChannelRead(radio,index);
  renderChat();
}
function renderDeviceSelector(slot) {
  const menu=el('chat-'+sideId(slot)+'-device');clear(menu);
  const empty=node('option','','No device');empty.value='';menu.appendChild(empty);
  const devices=[...state.devices];
  for(const known of state.myNodes)if(!devices.some(d=>d.radio===known.radio&&d.id===known.id))
    devices.push({...known,key:'saved:'+known.id});
  for(const item of devices){
    const option=node('option','',(item.unread?'● ':'')+(item.name||item.id)+' · '+item.radio.toUpperCase()+(item.connected===false||item.key.startsWith('saved:')?' · offline':''));
    option.value=item.radio+'|'+item.key;menu.appendChild(option);
  }
  const current=state[slot];
  const chosen=devices.find(d=>d.radio===current.radio&&(d.key===current.key||d.id===current.ownId));
  menu.value=chosen?chosen.radio+'|'+chosen.key:'';menu.disabled=false;
}
function selectChatDevice(slot,value) {
  const split=value.indexOf('|'),radio=split<0?'':value.slice(0,split),key=split<0?'':value.slice(split+1);
  AndroidBridge.selectChatDevice(slot,radio,key);
}
function toggleUsersList(radio) {
  radio=chatSlot(radio);usersListVisible[radio]=!usersListVisible[radio];
  renderChat();
}
function dmName(radio, peer) {
  const contact = state[radio].nodes.find(item => item.id === peer);
  return contact && contact.name ? contact.name : state[radio].peerNames?.[peer]||peer;
}
function unreadDmPeers(radio) {
  return Object.keys(state[radio].directs).filter(peer =>
    conversationUnread(radio,peer,-1))
    .sort((a,b) => dmName(radio,a).localeCompare(dmName(radio,b)));
}
function conversationUnread(radio,peer,ch){
 const device=state[radio];
 if(device.historyUnread)return !!device.historyUnread[peer?'d:'+peer:'c:'+ch];
 return (peer?device.directs[peer]||[]:device.chats[String(ch)]||[]).some(m=>m.unread);
}
function openDmScreen(saved=false) {
  closeSideZoom();if(!state)return;dmMode=true;savedDmMode=saved;
  if(saved)usersListVisible={left:true,right:true};
  renderChat();
}
function showConversationDelete(slot,peer) {
  const key=state[slot].key,card=dialogCard(dmName(slot,peer));
  dialogAction(card,'Delete this Conversation',()=>{
    dmDrafts.delete(dmDraftKey(slot,peer));
    el('dm-context').hidden=true;AndroidBridge.deleteConversation(key,peer);
    if(dmSelection.radio===slot&&dmSelection.peer===peer){dmSelection={radio:null,peer:null};el('dm-input').value='';renderChat();}
  });
  dialogAction(card,'Cancel',()=>el('dm-context').hidden=true);
}
function selectDmPeer(radio,peer) {
  radio=chatSlot(radio);dmMode=true;
  saveDmDraft();
  dmSelection={radio,peer};
  el('dm-input').value=dmDrafts.get(dmDraftKey(radio,peer))||'';
  resizeComposer(el('dm-input'));
  network=radio;
  AndroidBridge.markDirectRead(radio,peer);
  renderChat();
}
function replyDm() {
  if (!state || !dmSelection.peer || pendingCompose) return;
  const {radio,peer}=dmSelection;
  if (!state[radio].connected || !state[radio].nodes.some(item=>item.id===peer && nodeMessageable(radio,item))) return;
  const input=el('dm-input'), message=input.value.trim();
  if (!message) return;
  if (!validMessageBudget(input)) return;
  pendingCompose={key:state[radio].key,peer,text:message,draftKey:dmDraftKey(radio,peer)};
  AndroidBridge.nodeAction(state[radio].key,peer,'dm',message);

}
function showDmContext(radio,peer) {
  closeSideZoom();
  const overlay=el('dm-context');
  if(!overlay.hidden) return;
  clear(overlay); overlay.hidden=false;
  const card=node('div','dm-context-card');
  card.appendChild(node('strong','',dmName(radio,peer)+' · '+radioLabel(radio)));
  const contact=state[radio].nodes.find(item=>item.id===peer);
  if(contact) card.appendChild(nodeDetails(radio,contact));
  const clearButton=node('button','','Clear DM history');
  clearButton.onclick=()=>{overlay.hidden=true;AndroidBridge.clearDirect(radio,peer);};
  card.appendChild(clearButton);
  const line=node('div','','Erase messages older than');
  const days=node('input','form-input'); days.type='number'; days.min='1'; days.value='30';
  line.append(days,document.createTextNode(' days')); card.appendChild(line);
  const erase=node('button','','Erase older messages');
  erase.onclick=()=>{if(Number(days.value)>=1){overlay.hidden=true;AndroidBridge.eraseDirectOlder(radio,peer,Number(days.value));}};
  card.appendChild(erase);
  const exportButton=node('button','','Export chat as CSV');
  exportButton.onclick=()=>{overlay.hidden=true;AndroidBridge.exportDirectCsv(radio,peer);};
  card.appendChild(exportButton);
  const cancel=node('button','','Cancel'); cancel.onclick=()=>overlay.hidden=true;
  card.appendChild(cancel); overlay.appendChild(card);
  overlay.onclick=event=>{if(event.target===overlay) overlay.hidden=true;};
}
function bindDmContact(button,radio,peer,allowExport=true) {
  bindHold(button,()=>allowExport?showDmContext(radio,peer):showMessageNodeActions(radio,peer),()=>selectDmPeer(radio,peer));
}
function renderDmSide(radio) {
  const side=el('dm-'+sideId(radio)+'-side'); clear(side);
  side.appendChild(sideMagnifier('dm',radio));
  if(state[radio].loading) side.appendChild(node('div','dm-side-empty','Loading '+radioLabel(radio)+' messages…'));
  if(!state[radio].connected) {
    side.appendChild(node('div','dm-side-empty','Offline history · reconnect in Config to send'));
  }
  const directs=state[radio].directs;
  const received=Object.keys(directs).filter(peer=>directs[peer].some(m=>!m.reaction))
    .sort((a,b)=>dmName(radio,a).localeCompare(dmName(radio,b)));
  side.appendChild(node('div','dm-section-title','Saved conversations'));
  if(!received.length) side.appendChild(node('div','dm-side-empty',state[radio].loading?'Loading…':'None yet'));
  for(const peer of received) {
    const unread=conversationUnread(radio,peer,-1);
    const selected=dmSelection.radio===radio && dmSelection.peer===peer;
    const button=node('button','dm-contact'+(unread?' unread':'')+(selected?' active':''),dmName(radio,peer));
    button.title=dmName(radio,peer); bindHold(button,()=>showConversationDelete(radio,peer),()=>selectDmPeer(radio,peer)); side.appendChild(button);
  }
  if(savedDmMode)return;
  side.appendChild(node('div','dm-section-title','All contacts'));
  const contacts=state[radio].nodes.filter(item=>nodeMessageable(radio,item))
    .sort(compareContacts);
  if(!contacts.length) side.appendChild(node('div','dm-side-empty',state[radio].loading?'Loading…':'No contacts'));
  for(const contact of contacts) {
    const unread=conversationUnread(radio,contact.id,-1);
    const selected=dmSelection.radio===radio && dmSelection.peer===contact.id;
    const button=node('button','dm-contact'+(unread?' unread':'')+(selected?' active':''),contact.name||contact.id);
    button.title=(contact.name||contact.id)+' · '+contact.id;
    bindDmContact(button,radio,contact.id); side.appendChild(button);
  }
}
function renderDmScreen() {
  renderDmSide('left'); renderDmSide('right');
  const history=el('dm-chat-history'); clear(history);
  const {radio,peer}=dmSelection;
  if(!radio || !peer) {
    el('dm-chat-title').textContent='Choose a contact';
    history.appendChild(node('div','empty-panel','Select a name to view direct messages.'));
    el('dm-reply').disabled=true; el('dm-input').disabled=true;
    el('dm-input').placeholder='Choose a conversation';
    updateMessageBudget(el('dm-input'));
    return;
  }
  el('dm-chat-title').textContent=dmName(radio,peer)+' · '+radioLabel(radio);
  const entries=state[radio].directs[peer]||[];
  addHistoryLoader(history,radio,peer,-1,entries);
  for(const entry of entries.filter(item=>!item.reaction)) {
    const bubble=node('div','android-msg'+(entry.own?' own':''));
    appendMessageMeta(bubble,radio,entry); 
    const body=node('div','body'); appendLinkedText(body,entry.text); bubble.appendChild(body);
    appendMessageDelivery(bubble,radio,entry);
    if(Number.isFinite(entry.hops) && entry.hops>=0)
      bubble.appendChild(node('div','message-hops',entry.hops+' hops'));
    bindMessageActions(bubble,radio,entry,{peer,channel:-1});
    appendReactions(bubble,radio,entry,entries);
    history.appendChild(bubble);
  }
  if(!entries.length) history.appendChild(node('div','empty-panel',state[radio].loading?'Loading messages…':'No messages yet. Start a conversation below.'));
  const canReply=state[radio].connected&&state[radio].nodes.some(item=>item.id===peer && nodeMessageable(radio,item));
  el('dm-reply').disabled=!canReply; el('dm-input').disabled=false;
  updateMessageBudget(el('dm-input'));
  el('dm-input').placeholder=canReply?'Direct message…':!state[radio].connected?'Reconnect this radio in Config to send':'Wait for this contact to load';
  history.scrollTop=history.scrollHeight;
}
function setNodeFilter(id) {
  nodeFilter = id;
  for (const filter of ['all', 'mc', 'mt']) el('nodes-' + filter).classList.toggle('active', id === filter);
  renderNodes();
}
function toggleFavorites() {
  favoritesOnly = !favoritesOnly;
  el('nodes-favorites').classList.toggle('active', favoritesOnly);
  renderNodes();
}
function setNodeKind(value) { nodeKind=value; renderNodes(); }
function setNodeRole(value) { nodeRole=value; renderNodes(); }
function nodeInfrastructure(entry) {
  return /repeater|router|sensor|room server/i.test(entry.role||'');
}
function nodeMessageable(radio,entry) {
  if(!entry || entry.id===state[radio].ownId || !state[radio].connected) return false;
  if(radioType(radio)==='mc') return entry.role==='User' || entry.role==='Room server';
  return /^![0-9a-fA-F]{8}$/.test(entry.id) &&
    !/^!(00000000|ffffffff)$/i.test(entry.id);
}
function openDmForNode(radio,entry) {
  el('dm-context').hidden=true;
  if(!nodeMessageable(radio,entry)) return;
  if(radio==='mc'||radio==='mt'){const slot=chatSlot(radio);AndroidBridge.selectChatDevice(slot,radio,state[radio].key);state[slot]=state[radio];radio=slot;}
  savedDmMode=false;switchTab('chat');
  selectDmPeer(radio,entry.id);
  el('dm-input').focus();
}
function distanceKm(a, b, c, d) {
  const rad = Math.PI / 180;
  const deltaLat = (c - a) * rad, deltaLon = (d - b) * rad;
  const value = Math.sin(deltaLat / 2) ** 2 + Math.cos(a * rad) * Math.cos(c * rad) * Math.sin(deltaLon / 2) ** 2;
  return Math.round(12742 * Math.asin(Math.sqrt(value)) * 10) / 10;
}
function nodeButton(parent, label, action, radio, entry, handler) {
  const button = node('button', '', label);
  button.type = 'button';
  button.onclick = handler || (() => AndroidBridge.nodeAction(radio, entry.id, action, ''));
  parent.appendChild(button);
}
function nodeDetails(radio, entry) {
  const details = node('div', 'node-details');
  details.appendChild(node('div', 'node-facts', entry.role + (entry.model ? ' · ' + entry.model : '') + ' · ' + entry.id));
  nodeButton(details,'Enter Name','',radio,entry,()=>enterNodeName(radio,entry));
  if (state[radio].connected && !entry.savedOnly) {
    const actions = node('div', 'node-actions');
    nodeButton(actions, entry.favorite ? '★ Favorited' : '☆ Add to Favorites', 'favorite', radio, entry);
    if(nodeMessageable(radio,entry))
      nodeButton(actions,'DM','dm',radio,entry,()=>openDmForNode(radio,entry));
    if (radioType(radio) === 'mt') {
      nodeButton(actions, 'TR', 'tr', radio, entry);
      nodeButton(actions, 'Req info', 'req', radio, entry);
      nodeButton(actions, 'Req position', 'pos', radio, entry);
      nodeButton(actions, 'Signal Quality', 'signal', radio, entry);
    } else {
      if(['Repeater','Room server'].includes(entry.role))nodeButton(actions,'Ping','ping',radio,entry,()=>showPingOptions(radio,entry));
      nodeButton(actions, 'TR', 'tr', radio, entry);
      nodeButton(actions, 'Trace learned repeaters', 'trace-path', radio, entry);
      if (entry.role === 'Repeater' || entry.role === 'Sensor')
        nodeButton(actions, 'Req status', 'req', radio, entry);
    }
    if (Number.isFinite(entry.latitude) && Number.isFinite(entry.longitude))
      nodeButton(actions, 'Map', 'map', radio, entry);
    nodeButton(actions, 'Share', 'share', radio, entry);
    details.appendChild(actions);
  }
  if(state[radio].lastTrace?.target===entry.id) nodeButton(details,'Last trace','',radio,entry,()=>onTraceResult(state[radio].lastTrace));
  return details;
}
function localNodeName(radio,id) { return state?.nodeNames?.[radioType(radio)+':'+id]||''; }
function namedNode(radio,id,name) {const label=localNodeName(radio,id);return (name||id)+(label?' · '+label:'');}
function enterNodeName(radio,entry) {
  const card=dialogCard('Enter Name · '+(entry.name||entry.id));
  card.appendChild(node('p','help','A private name on this phone. Leave blank to remove it.'));
  const input=node('input','form-input');input.type='text';input.maxLength=80;input.value=localNodeName(radio,entry.id);input.setAttribute('aria-label','Node name');card.appendChild(input);
  dialogAction(card,'Save',()=>{AndroidBridge.setNodeName(radioType(radio),entry.id,input.value.trim());el('dm-context').hidden=true;});
  cancelDialog(card);input.focus();
}
function showMessageNodeActions(radio,id) {
  window.getSelection()?.removeAllRanges();
  closeSideZoom();
  const entry=state[radio].nodes.find(item=>item.id===id);
  if(!entry) return;
  const card=dialogCard(namedNode(radio,entry.id,entry.name));
  const overlay=el('dm-context');
  card.dataset.actionKey=state[radio].key;
  const detail=nodeDetails(radio,entry);
  card.appendChild(detail);
  const close=node('button','','Close'); close.onclick=()=>overlay.hidden=true;
  card.appendChild(close);
  card.tabIndex=-1; card.focus();
  overlay.onclick=event=>{if(event.target===overlay) overlay.hidden=true;};
}
function renderSoundConfig() {
  const mode=state.sound.mode;
  el('sound-mode').value=mode;
  const host=el('sound-choices'); clear(host);
  const disconnectHost=el('disconnect-sound-choice');clear(disconnectHost);
  el('disconnect-audio').checked=state.disconnectAudioEnabled!==false;
  for(const kind of [...(mode==='shared'?['shared']:['mc','mt','dm']),'disconnect']) {
    const row=node('div','sound-row');
    row.appendChild(node('strong','',kind==='disconnect'?'Device disconnected':kind==='shared'?'All messages':kind.toUpperCase()));
    const choose=node('button','',state.sound[kind]?.selected?'Change system sound':'Select system sound');
    choose.onclick=()=>AndroidBridge.pickSound(kind); row.appendChild(choose);
    const label=node('label','',' Play ');
    const repeats=node('select','form-input');
    for(let i=1;i<=3;i++){const option=node('option','',i+' time'+(i>1?'s':''));option.value=i;repeats.appendChild(option);}
    repeats.value=String(state.sound[kind]?.repeats||1);
    repeats.onchange=()=>AndroidBridge.setSoundRepeats(kind,Number(repeats.value));
    label.append(repeats); row.appendChild(label); (kind==='disconnect'?disconnectHost:host).appendChild(row);
  }
}
function renderChat() {
  el(dmMode?'dm-center':'channels-center').prepend(el('chat-font-bar'));
  if (!state) return;
  for(const side of ['left','right']) {
    const button=el('chat-advert-'+side);
    button.hidden=radioType(side)!=='mc'||!state[side].ownId;
    button.disabled=!state[side].connected;
    button.setAttribute('aria-label','Send advert from '+side+' device');
  }
  const listsVisible=Number(usersListVisible.left)+Number(usersListVisible.right);
  el('chat-font-size').textContent=(listsVisible===2?13:listsVisible===1?16:18)+chatFontStep+' px';
  el('chat-font-smaller').disabled=chatFontStep===0;
  el('chat-font-larger').disabled=chatFontStep===6;
  const radio=state[network];
  const sync=el('chat-sync');
  const loading=['left','right'].filter(id=>state[id].loading);
  sync.hidden=!loading.length;
  el('chat-sync-text').textContent='Loading '+loading.map(id=>radioLabel(id)).join(' and ')+' messages…';
  el('chat-dm-pill').classList.toggle('active',dmMode);
  el('chat-dm-pill').classList.toggle('unread',state.myNodes.some(item=>item.dmUnread));
  el('chat-channels-pill').classList.toggle('active',!dmMode);
  el('chat-channels-pill').classList.toggle('unread',state.myNodes.some(item=>item.channelUnread) ||
    ['left','right'].some(id=>Object.values(state[id].chats).some(history=>history.some(entry=>entry.unread))));
  for(const id of ['left','right']) {
    const pill=el('header-'+sideId(id)+'-pill');
    pill.classList.toggle('active',!dmMode&&network===id);
    pill.textContent=radioLabel(id);
    pill.classList.toggle('offline',!!state[id].ownId&&!state[id].connected);
    pill.classList.toggle('unread',!!state[id].unread);
    renderHeaderChannelSelector(id);
    renderDeviceSelector(id);
    const toggle=el('toggle-'+sideId(id)+'-users');
    toggle.textContent=usersListVisible[id]?'Users ▾':'Users ▸';
    toggle.classList.toggle('active',usersListVisible[id]);
    toggle.setAttribute('aria-expanded',String(usersListVisible[id]));
  }
  for(const screen of [el('channels-screen'),el('dm-screen')]) {
    screen.style.setProperty('--chat-font-extra',chatFontStep+'px');
    screen.classList.toggle('hide-mc-users',!usersListVisible.left);
    screen.classList.toggle('hide-mt-users',!usersListVisible.right);
    screen.classList.toggle('one-list-hidden',usersListVisible.left!==usersListVisible.right);
    screen.classList.toggle('both-lists-hidden',!usersListVisible.left&&!usersListVisible.right);
  }
  el('dm-screen').hidden=!dmMode;
  el('channels-screen').hidden=dmMode;
  if(dmMode) renderDmScreen();
  else renderChannelsScreen();
}
function renderHeaderChannelSelector(id) {
  const select=el('header-'+sideId(id)+'-channel'); clear(select);
  const available=state[id].channels;
  if(!available.some(c=>c.index===channel[id]) && available.length) channel[id]=available[0].index;
  if(!available.length) {
    const option=node('option','',state[id].loading?'Loading…':'No channels');
    option.value=''; select.appendChild(option); select.disabled=true;
  } else {
    select.disabled=false;
    for(const item of available) {
      const option=node('option','',item.name); option.value=item.index; select.appendChild(option);
    }
    select.value=String(channel[id]);
  }
}
function renderChannelSide(id) {
  const side=el('channel-'+sideId(id)+'-side'); clear(side);
  side.appendChild(sideMagnifier('channel',id));
  if(state[id].loading) side.appendChild(node('div','dm-side-empty','Loading '+radioLabel(id)+' messages…'));
  const available=state[id].channels;
  side.appendChild(node('div','dm-section-title','New messages in:'));
  const channels=available.filter(item=>conversationUnread(id,'',item.index));
  if(!channels.length) side.appendChild(node('div','dm-side-empty',state[id].loading?'Loading…':'None'));
  for(const item of channels) {
    const button=node('button','channel-choice unread'+
      (network===id && channel[id]===item.index?' active':''),item.name);
    button.onclick=()=>selectChannel(id,item.index); side.appendChild(button);
  }
  side.appendChild(node('div','dm-section-title','Messageable users'));
  const contacts=state[id].nodes.filter(item=>nodeMessageable(id,item))
    .sort(compareContacts);
  if(!contacts.length) side.appendChild(node('div','dm-side-empty',state[id].loading?'Loading contacts…':'No users available'));
  for(const contact of contacts) {
    const button=node('button','dm-contact'+(conversationUnread(id,contact.id,-1)?' unread':''),contact.name||contact.id);
    button.title=(contact.name||contact.id)+' · '+contact.id;
    bindDmContact(button,id,contact.id,false);
    side.appendChild(button);
  }
}
function renderChannelsScreen() {
  renderChannelSide('left'); renderChannelSide('right');
  const radio=state[network], available=radio.channels;
  const selectedChannel=radio.channels.find(c=>c.index===channel[network]);
  el('channel-chat-title').textContent=(selectedChannel?selectedChannel.name:'Choose a channel')+
    ' · '+radioLabel(network)+(radio.ownName?' · '+radio.ownName:'');
  const messages = el('chat-messages'); clear(messages);
  const entries=selectedChannel?radio.chats[String(channel[network])]||[]:[];
  if(selectedChannel)addHistoryLoader(messages,network,'',channel[network],entries);
  for (const entry of entries.filter(item=>!item.reaction)) {
    const bubble = node('div', 'android-msg' + (entry.own ? ' own' : ''));
    appendMessageMeta(bubble,network,entry,true);
    const body = node('div', 'body');
    if(entry.replyId) {
      const original=entries.find(item=>item.packetId===entry.replyId);
      bubble.appendChild(node('div','message-quote',original?'↪ '+messageIdentity(network,original).name+': '+messageIdentity(network,original).text:'↪ Reply to an unavailable message'));
    }
    appendLinkedText(body, messageIdentity(network,entry).text);
    bubble.appendChild(body);
    appendMessageDelivery(bubble,network,entry,true);
    if(radioType(network)==='mc'&&entry.own) {
      const count=Number.isInteger(entry.heardRepeats)?entry.heardRepeats:-1;
      const repeats=node('button','message-repeats',count<0?'Heard repeats: unavailable':'Heard repeats: '+count);
      repeats.title='Copies of this channel message heard being rebroadcast by your radio. This is not recipient delivery or a count of distinct repeaters.';
      repeats.type='button';repeats.onclick=event=>{event.stopPropagation();showMessagePath(entry,true,network);};
      bubble.appendChild(repeats);
    }

    if(Number.isFinite(entry.hops) && entry.hops>=0)
      bubble.appendChild(node('div','message-hops',entry.hops+' hops'));
    bindMessageActions(bubble,network,entry,{peer:null,channel:channel[network]});
    appendReactions(bubble,network,entry,entries);
    messages.appendChild(bubble);
  }
  const empty = el('chat-empty');
  empty.hidden = entries.length > 0;
  if (radio.loading) empty.textContent = 'Messages are still loading from this radio…';
  else if (!radio.connected) empty.textContent = 'Offline history · reconnect this device to send.';
  else empty.textContent = 'No messages in this channel.';
  const canSend=radio.connected && !!selectedChannel;
  el('chat-input').placeholder=canSend?'Message '+selectedChannel.name+'…':!radio.connected?'Reconnect this device to send':'Wait for channel names…';
  el('chat-input').disabled = false;
  el('chat-send').disabled = !canSend;
  updateMessageBudget(el('chat-input'));
  messages.scrollTop = messages.scrollHeight;
  renderReplyPreview();
}
function renderMyNodes() {
  if (!state) return;
  const list = el('my-nodes-list'); clear(list);
  const known=state.myNodes.filter(item=>item.id);
  if (!known.length) list.appendChild(node('div','empty-panel','No saved radios yet. Scan and connect one to add it here.'));
  for (const item of known) {
    const card = node('section','radio-card'+(item.unread?' unread-node':''));
    const picker=node('label','known-node-picker');
    const checked=node('input',''); checked.type='checkbox'; checked.checked=selectedKnown.has(item.radio+':'+item.id);
    checked.onchange=()=>{const key=item.radio+':'+item.id; if(checked.checked) selectedKnown.add(key); else selectedKnown.delete(key); updateConnectSelected();};
    picker.append(checked,node('span','',item.connected?'Connected':'Select'));
    card.appendChild(picker);
    card.appendChild(node('span','radio-type-badge',(item.detectedRadio||item.radio).toUpperCase()));
    if(item.connecting) {const progress=node('progress','known-progress');progress.setAttribute('aria-label','Connecting radio');card.append(progress,node('div','node-facts',item.connectionStatus||'Connecting…'));}
    const heading=node('div','known-node-heading');
    heading.appendChild(node('strong','',item.name || item.id));
    if(item.connected) heading.appendChild(node('strong','connected-label','Connected'));
    card.appendChild(heading);
    if(item.unread) card.appendChild(node('span','unread-badge',item.dmUnread?'Unread direct messages':'Unread messages'));
    card.appendChild(node('div','node-facts',(item.detectedRadio||item.radio).toUpperCase() + (item.id ? ' · ' + item.id : '') +
      (item.connected ? ' · Connected' : '') + (item.lastSeen ? ' · Last connected ' + new Date(item.lastSeen).toLocaleString(undefined,{hourCycle:'h23'}) : ' · From an older app version')));
    const actions = node('div','actions');
    const view = node('button','','View messages');
    view.onclick = () => viewSavedMessages(item.radio,item.id);
    const remove = node('button','disconnect-btn','Remove');
    remove.onclick = () => AndroidBridge.removeKnown(item.radio,item.id);
    const connect=node('button','',item.connected||item.connecting?'Disconnect':'Connect');
    connect.onclick=()=>item.connected||item.connecting?AndroidBridge.disconnectKnown(item.radio,item.id):AndroidBridge.connectKnown(item.radio,item.id);
    actions.append(connect,view,remove); card.appendChild(actions); list.appendChild(card);
  }
  updateConnectSelected();
}
window.onSavedNodeRemoved = function () { closeSettingsWindow('history');savedNode=null; clear(el('my-node-history')); };
window.onAppBackupImported = function () { closeSettingsWindow('history');historyPages.clear();savedNode=null; clear(el('my-node-history')); };
window.openSavedChatView=ch=>{closeSettingsWindow('history');switchTab('chat');if(ch<0)openDmScreen(true);else selectChannel('left',ch);};
window.onSavedNode = function (data) {
  if(!pendingSavedView||pendingSavedView.radio!==data.radio||pendingSavedView.id!==data.id)return;
  savedNode=data;
  if (!savedNode.channels.length) {
    const numbers=[...new Set(savedNode.messages.filter(m=>m.channel>=0).map(m=>m.channel))].sort((a,b)=>a-b);
    savedNode.channels=numbers.map(index=>({index,name:'Unnamed channel '+index}));
  }
  if (savedChannel === null || (savedChannel !== -1 && !data.channels.some(c=>c.index===savedChannel)))
    savedChannel=data.channels.length?data.channels[0].index:-1;
  renderSavedHistory();
};
function renderSavedHistory() {
  const host=el('my-node-history'); clear(host);
  if (!savedNode) return;
  host.appendChild(node('div','section-heading','Saved messages · '+savedNode.radio.toUpperCase()+' · '+savedNode.id));
  const actions=node('div','actions');
  const older=node('button','','Open Chat · browse all messages');older.onclick=()=>AndroidBridge.openSavedChat(savedNode.radio,savedNode.id,savedChannel);actions.appendChild(older);
  const previewHelp=node('p','help','Preview: newest 100 records per conversation. Open Chat to load older messages. Exports and deletion include all stored messages.');
  previewHelp.id='saved-history-help';previewHelp.hidden=true;
  const info=node('button','info-toggle','i');info.type='button';info.setAttribute('aria-label','Saved history information');
  info.setAttribute('aria-controls',previewHelp.id);info.setAttribute('aria-expanded','false');info.onclick=()=>toggleInformation(info);
  host.append(info,previewHelp);
  for (const [label,format] of [['Export backup','json'],['Export CSV','csv']]) {
    const button=node('button','',label);
    button.onclick=()=>AndroidBridge.exportNode(savedNode.radio,savedNode.id,format);
    actions.appendChild(button);
  }
  host.appendChild(actions);
  const cleanup=node('div','my-node-cleanup');
  cleanup.appendChild(node('span','','Erase messages older than'));
  const days=node('input','form-input');
  days.type='number'; days.min='1'; days.max='36500'; days.value='30';
  days.setAttribute('aria-label','Days');
  cleanup.appendChild(days);
  cleanup.appendChild(node('span','','days'));
  const erase=node('button','','Erase');
  erase.onclick=()=>AndroidBridge.eraseOlder(savedNode.radio,savedNode.id,Number(days.value));
  cleanup.appendChild(erase); host.appendChild(cleanup);
  const tabs=node('div','my-node-channels');
  for(const c of savedNode.channels) {
    const button=node('button','cpill'+(savedChannel===c.index?' active':''),c.name);
    button.onclick=()=>{savedChannel=c.index;renderSavedHistory();};
    tabs.appendChild(button);
  }
  const dm=node('button','cpill'+(savedChannel===-1?' active':''),'Direct');
  dm.onclick=()=>{savedChannel=-1;renderSavedHistory();}; tabs.appendChild(dm);
  host.appendChild(tabs);
  const messages=node('div','my-node-messages');
  const selected=savedNode.messages.filter(m=>m.channel===savedChannel);
  for(const m of selected.filter(item=>!item.reaction)) {
    const entry=node('div','android-msg'+(m.own?' own':''));
    entry.appendChild(node('div','meta',(m.peer?m.peer+' · ':'')+m.sender+' · '+m.time));
    const body=node('div','body'); appendLinkedText(body,m.text); entry.appendChild(body);
    appendReactions(entry,savedNode.radio,m,selected);
    messages.appendChild(entry);
  }
  if(!selected.length) messages.appendChild(node('div','empty-panel','No saved messages in this conversation.'));
  host.appendChild(messages);
}
function isOwnNode(radio,id) {
  const type=radioType(radio),canonical=value=>{
    const key=String(value||'').toLowerCase();
    return type==='mc'&&/^(?:[0-9a-f]{12}|[0-9a-f]{64})$/.test(key)?key.slice(0,12):key;
  },same=value=>canonical(value)===canonical(id);
  return [...(state.myNodes||[]),...(state.devices||[])].some(item=>item.radio===type&&same(item.id)) ||
    ['mc','mt','left','right'].some(slot=>radioType(slot)===type&&same(state[slot]?.ownId));
}
function renderNodes() {
  if (!state) return;
  const list = el('nodes-list'); clear(list);
  const search = el('nodes-search').value.toLowerCase();
  const roleMenu=el('nodes-role');
  const roles=[...new Set(['mc','mt'].flatMap(id=>state[id].nodes.map(item=>item.role)).filter(Boolean))].sort();
  clear(roleMenu); const allRoles=node('option','','All roles'); allRoles.value='all'; roleMenu.appendChild(allRoles);
  for(const role of roles){const option=node('option','',role);option.value=role;roleMenu.appendChild(option);}
  if(nodeRole!=='all'&&!roles.includes(nodeRole)) nodeRole='all';
  roleMenu.value=nodeRole;
  for(const id of ['mc','mt']) {
    const menu=el('nodes-'+id+'-device');clear(menu);
    const devices=state.devices.filter(item=>item.radio===id);
    if(!devices.length){const option=node('option','','No device');option.value='';menu.appendChild(option);menu.disabled=true;}
    else {menu.disabled=false;for(const item of devices){const option=node('option','',(item.unread?'● ':'')+(item.name||item.id));option.value=item.key;menu.appendChild(option);}}
    menu.value=id==='mc'?state.selectedMc:state.selectedMt;
  }
  el('nodes-sort').value=nodeSort;
  let count = 0;
  const sortedRows=['mc','mt'].filter(id=>nodeFilter==='all'||nodeFilter===id).flatMap(id=>state[id].nodes.map(entry=>({id,entry}))).sort(compareNodeRows);
  for(const {id,entry} of sortedRows) {
      if(isOwnNode(id,entry.id)) continue;
      const heard=Number(entry.lastHeard)||0;
      if(heard>0&&heard<Date.now()/1000-30*86400) continue;
      if(!heard&&!el('nodes-show-unknown').checked) continue;
      if (favoritesOnly && !entry.favorite) continue;
      if(nodeKind==='infrastructure'&&!nodeInfrastructure(entry)) continue;
      if(nodeKind==='users'&&nodeInfrastructure(entry)) continue;
      if(nodeRole!=='all'&&entry.role!==nodeRole) continue;
      if(el('nodes-hide-unmessageable').checked&&!nodeMessageable(id,entry)) continue;
      if (!(entry.name + ' ' + localNodeName(id,entry.id) + ' ' + entry.id + ' ' + entry.role).toLowerCase().includes(search)) continue;
      const row = node('div', 'android-node');
      row.dataset.nodeKey=id+':'+entry.id;
      const heading = node('button', 'node-heading');
      heading.type = 'button';
      heading.appendChild(node('span', 'node-star', entry.favorite ? '★' : '☆'));
      heading.appendChild(node('small', '', id.toUpperCase()));
      heading.appendChild(node('span', 'node-name', namedNode(id,entry.id,entry.name)));
      const toggle=()=>{ navigationStack.push(navigationState()); openedNode = openedNode === id + ':' + entry.id ? null : id + ':' + entry.id; scrollToOpenedNode=!!openedNode; renderNodes(); };
      heading.onclick=event=>{event.stopPropagation();toggle();};
      row.onclick=event=>{if(event.target.closest('button,input,select,textarea,a,.node-details'))return;toggle();};
      row.appendChild(heading);
      row.appendChild(node('div', 'node-subtitle', (entry.model || entry.role) + (entry.model ? ' · ' + entry.role : '')));
      const facts = [heard?'Last heard '+new Date(heard*1000).toLocaleString(undefined,{hourCycle:'h23'}):'Last heard unknown'];
      if (entry.hops >= 0) facts.push((id === 'mc' ? 'Route hops ' : 'Hops ') + entry.hops);
      if (Number.isFinite(entry.latitude) && Number.isFinite(entry.longitude)) {
        if (Number.isFinite(state[id].ownLatitude) && Number.isFinite(state[id].ownLongitude))
          facts.push('Distance ' + distanceKm(state[id].ownLatitude, state[id].ownLongitude, entry.latitude, entry.longitude) + ' km');
        else facts.push('Position available');
      }
      if (facts.length) row.appendChild(node('div', 'node-facts', facts.join(' · ')));
      if (openedNode === id + ':' + entry.id) row.appendChild(nodeDetails(id, entry));
      list.appendChild(row); count++;
  }
  el('nodes-summary').textContent = count + ' nodes and contacts · last 30 days (unknown hidden unless selected)';
  if (!count && !Object.keys(state.mc.directs).length && !Object.keys(state.mt.directs).length)
    list.appendChild(node('div', 'empty-panel', favoritesOnly ? 'No favorite nodes in this view.' : 'Connect a radio in Config to load nodes and contacts.'));
  if(scrollToOpenedNode&&openedNode){
    const row=[...list.querySelectorAll('.android-node')].find(item=>item.dataset.nodeKey===openedNode);
    if(row) requestAnimationFrame(()=>row.scrollIntoView({block:'center',behavior:'smooth'}));
    scrollToOpenedNode=false;
  }
}
window.onNodeActionResult = function (message,key=null) {
  pendingCompose=null;
  if(/^Direct message sent to radio/.test(message)) return;
  if(el('dm-context').hidden && /failed|error|first|wait|choose|limit|shorten|remove|permission|unavailable/i.test(message)) onActionNotice('Action result',message);
  const popup=el('dm-context');
  if(!popup.hidden&&key&&popup.firstChild?.dataset.actionKey===key) { let status=el('popup-action-status'); if(!status){status=node('p','','');status.id='popup-action-status';popup.firstChild.appendChild(status);} status.textContent=message; }
};
function sendMsg() {
  if (!state || pendingCompose || !state[network].connected || dmMode ||
      !state[network].channels.some(c => c.index === channel[network])) return;
  const input = el('chat-input');
  const text = input.value.trim();
  if (!text) return;
  if (!validMessageBudget(input)) return;
  pendingCompose={key:state[network].key,peer:'',channel:channel[network],text};
  const reply=channelReply&&channelReply.key===state[network].key&&channelReply.channel===channel[network]?channelReply:null;
  if(reply?.packetId) AndroidBridge.sendChannelReply(state[network].key,channel[network],text,reply.packetId);
  else AndroidBridge.sendMessage(state[network].key, channel[network], text);
}
window.onRadioState = function (next) {
  next={...next,left:next.left??{...next.mc,radio:'mc'},right:next.right??{...next.mt,radio:'mt'}};
  next.mc.radio='mc';next.mt.radio='mt';
  for(const slot of ['left','right'])if(state&&(state[slot].ownId!==next[slot].ownId||state[slot].radio!==next[slot].radio)){
    if(network===slot){el('chat-input').value='';channelReply=null;}
    channel[slot]=next[slot].channels[0]?.index??0;
  }
  if (state && dmSelection.radio && (state[dmSelection.radio].ownId !== next[dmSelection.radio].ownId||state[dmSelection.radio].radio !== next[dmSelection.radio].radio)) {
    saveDmDraft();
    dmSelection={radio:null,peer:null};
    el('dm-input').value='';
    el('dm-chat-status').textContent='';
  }
  const initial=state===null;
  const scrollPositions=['chat-messages','dm-chat-history'].map(id=>{
    const e=el(id);return {e,top:e.scrollTop,keep:e.scrollHeight-e.clientHeight-e.scrollTop>32};
  });
  const previousChatKey=state?.[network]?.key;
  if(state&&['left','right'].some(slot=>state[slot]?.historyRevision!==next[slot]?.historyRevision))historyPages.clear();
  state = next;
  el('message-split-enabled').checked=next.messageSplitEnabled!==false;
  if(document.activeElement!==el('message-split-interval'))el('message-split-interval').value=next.messageSplitIntervalSeconds??1;
  el('connect-all-available').disabled=!!next.connectingAvailable;
  el('connect-available-status').hidden=!next.connectingAvailable;
  el('multi-send-enabled').checked=!!next.multiSendEnabled;el('new-multi-send').disabled=!next.multiSendEnabled;el('new-multi-send').hidden=!next.multiSendEnabled;
  if(document.activeElement!==el('channel-observation'))el('channel-observation').value=next.channelObservationSeconds??5;
  if(document.activeElement!==el('send-settling'))el('send-settling').value=next.settlingSeconds||3;
  for(const [id,value] of [['mc-direct-attempts',next.mcDirectAttempts],['mc-flood-attempts',next.mcFloodAttempts]])if(document.activeElement!==el(id))el(id).value=value||4;
  if(initial&&!next.devices.length&&!next.mc.connected&&!next.mt.connected) {
    restoringNavigation=true; switchTab('settings'); showSettingsPage('home'); restoringNavigation=false; navigationStack.length=0;
  }
  chatFontStep=Math.max(0,Math.min(6,Number(next.chatFontStep)||0));
  composerFontStep=Math.max(0,Math.min(6,Number(next.composerFontStep)||0));
  sideFontSteps={left:Number(next.sideMcFontStep)||0,right:Number(next.sideMtFontStep)||0};
  applyComposerFont();
  if(pendingUnreadDevice && next.devices.some(item=>item.key===pendingUnreadDevice.key &&
      item.key===(item.radio==='mc'?next.selectedMc:next.selectedMt))) {
    const kind=pendingUnreadDevice.kind; pendingUnreadDevice=null;
    if(kind==='dm') { openDmScreen(); return; }
    openChannelsScreen(); return;
  }
  el('mc-status').textContent = next.mc.state;
  el('mt-status').textContent = next.mt.state;
  for (const id of ['mc', 'mt']) {
    const progress = el(id + '-progress');
    progress.hidden = !next[id].scanning;
    progress.value = next[id].scanProgress;
    el(id + '-stop-scan').hidden = !next[id].scanning;
  }
  el('mc-disconnect').hidden = !next.mc.connected;
  el('mt-disconnect').hidden = !next.mt.connected;
  el('message-audio').checked = next.audioEnabled;
  el('background-message-notifications').checked = !!next.backgroundMessageNotifications;
  el('message-notifications-status').textContent = !next.messageNotificationsAllowed
    ? 'Android notifications are blocked. Enable them in Android notification settings to receive background previews.'
    : next.backgroundMessageNotifications ? 'Background message notifications are on.' : 'Background message notifications are off.';
  renderSoundConfig();
  el('hdr-radio-name').textContent = next.devices.length+' connected';
  el('nav-chat').classList.toggle('unread',next.myNodes.some(item=>item.unread));
  renderChat(); renderNodes(); renderMyNodes();
  if(previousChatKey===state[network]?.key) for(const saved of scrollPositions) if(saved.keep)saved.e.scrollTop=saved.top;
  if(settingsPage==='channel-backup') renderBackupDevices();
};
window.onScanProgress = function (id, value) {
  el(id + '-progress').value = value;
};
el('side-zoom-body').addEventListener('click',event=>{
  if(event.target.closest('.channel-choice,.dm-contact')) closeSideZoom();
});
el('side-zoom').addEventListener('click',event=>{
  if(event.target===el('side-zoom')) closeSideZoom();
});

function clock() {
  const now=new Date(), two=value=>String(value).padStart(2,'0');
  el('hdr-time').textContent=two(now.getHours())+':'+two(now.getMinutes())+' - '+
    two(now.getDate())+'/'+two(now.getMonth()+1)+'/'+now.getFullYear();
}
function updateConnectSelected() {
  const pending=state.myNodes.filter(item=>!item.connected&&!item.connecting&&selectedKnown.has(item.radio+':'+item.id));
  el('connect-selected').disabled=!pending.length;
  el('disconnect-selected').disabled=!state.myNodes.some(item=>(item.connected||item.connecting)&&selectedKnown.has(item.radio+':'+item.id));
  el('disconnect-all').disabled=!(state.canDisconnectAll||state.devices.length||state.myNodes.some(item=>item.connecting));
}
function connectSelected() {
  const pending=state.myNodes.filter(item=>!item.connected&&!item.connecting&&selectedKnown.has(item.radio+':'+item.id));
  renderMyNodes();
  for(const item of pending) AndroidBridge.connectKnown(item.radio,item.id);
}
function disconnectSelected() {
  for(const item of state.myNodes.filter(item=>(item.connected||item.connecting)&&selectedKnown.has(item.radio+':'+item.id))) AndroidBridge.disconnectKnown(item.radio,item.id);
}
function renderBackupDevices() {
  if(!state) return;
  const select=el('backup-device'), old=select.value;
  clear(select);
  for(const device of state.devices) {
    const option=node('option','',device.radio.toUpperCase()+' · '+(device.name||device.id));
    option.value=device.key; select.appendChild(option);
  }
  if(state.devices.some(item=>item.key===old)) select.value=old;
  select.disabled=!state.devices.length;
  loadBackupChannels();
}
function loadBackupChannels() {
  const key=el('backup-device').value;
  if(!key) { clear(el('backup-channel')); updateBackupExport(); return; }
  el('backup-channel-export').disabled=true;
  AndroidBridge.getBackupChannels(key);
}
window.onBackupChannels=function(key,channels) {
  if(key!==el('backup-device').value) return;
  const select=el('backup-channel'),old=select.dataset.device===key?select.value:'';
  clear(select); select.dataset.device=key;
  for(const channel of channels) { const option=node('option','',channel.name); option.value=channel.index; select.appendChild(option); }
  if(channels.some(item=>String(item.index)===old)) select.value=old;
  select.disabled=!channels.length;
  updateBackupExport();
};
function updateBackupExport() { el('backup-channel-export').disabled=!el('backup-device').value||!el('backup-channel').value; }
window.onActionNotice=function(title,text) { AndroidBridge.showActionNotice(title,text); };
function navigationState() {
  return {activeTab,settingsPage,network,channel:{...channel},dmMode,savedDmMode,dmSelection:{...dmSelection},openedNode};
}
function navigateBack() {
  if(!el('dm-context').hidden) { el('dm-context').hidden=true; return; }
  const dialog=document.querySelector('dialog.settings-window[open]');
  if(dialog){closeSettingsWindow(dialog.id.replace('settings-window-',''));return;}
  const focused=document.activeElement;
  if(focused&&focused.matches('input,textarea')) { focused.blur(); return; }
  if(sideZoom) { closeSideZoom(); return; }
  const previous=navigationStack.pop();
  restoringNavigation=true;
  try {
    if(previous) {
      network=previous.network; channel=previous.channel; dmMode=previous.dmMode;savedDmMode=previous.savedDmMode;
      dmSelection=previous.dmSelection; openedNode=previous.openedNode;
      switchTab(previous.activeTab); showSettingsPage(previous.settingsPage);
      renderChat(); renderNodes();
    }
  } finally { restoringNavigation=false; }
}
// Record complete user transitions once, including nested navigation helpers.
for(const name of ['switchTab','showSettingsPage','setNetwork','openDmScreen','openChannelsScreen','selectChannel','selectDmPeer','openDmForNode']) {
  const original=window[name];
  window[name]=function(...args) {
    const before=navigationState(), outer=navigationDepth++===0;
    try { return original(...args); }
    finally {
      navigationDepth--;
      if(outer&&!restoringNavigation&&JSON.stringify(before)!==JSON.stringify(navigationState())) {
        navigationStack.push(before); if(navigationStack.length>100) navigationStack.shift();
      }
    }
  };
}
document.addEventListener('keydown',event=>{if(event.key==='Escape') { event.preventDefault(); navigateBack(); }});

clock(); setInterval(clock, 30000);
AndroidBridge.refresh();

bindHold(el('chat-dm-pill'),()=>openDmScreen(true),()=>openDmScreen(false));

function toggleInformation(button) {
  const text=el(button.getAttribute('aria-controls'));
  text.hidden=!text.hidden;button.setAttribute('aria-expanded',String(!text.hidden));
}
function connectAllAvailable() {
  el('connect-all-available').disabled=true;
  el('connect-available-status').hidden=false;
  AndroidBridge.connectAllAvailable();
}
