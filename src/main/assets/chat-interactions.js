'use strict';

// Keep the gesture alive across radio-state renders; tiny finger motion is not a scroll.
let holdGesture=null, suppressMessageTapUntil=0;
function clearHold() { if(holdGesture) clearTimeout(holdGesture.timer); holdGesture=null; }
function bindHold(element,onHold,onTap) {
  element.classList.add('gesture-target');
  element._holdAction=onHold;
  element.onclick=event=>{
    if(Date.now()<suppressMessageTapUntil) {event.preventDefault();return;}
    if(event.target.closest('a')) return;
    onTap?.();
  };
  element.oncontextmenu=event=>{
    if(event.target.closest('a')) return;
    event.preventDefault(); event.stopPropagation();
    if(holdGesture?.fired) return;
    clearHold(); window.getSelection()?.removeAllRanges();
    suppressMessageTapUntil=Date.now()+450; onHold();
  };
}
document.addEventListener('pointerdown',event=>{
  if(!holdGesture) suppressMessageTapUntil=0; // A new deliberate tap is not the held gesture's synthetic click.
  if(event.button!==0 || event.target.closest('input,textarea,a,.reaction-chip')) return;
  const target=event.target.closest('.gesture-target');
  if(!target) return;
  // Avoid collapsing the focused composer between pointer-down and the message tap.
  if(document.activeElement?.matches('#chat-input,#dm-input')) event.preventDefault();
  clearHold();
  const action=target._holdAction;
  const gesture={x:event.clientX,y:event.clientY,id:event.pointerId,fired:false};
  gesture.timer=setTimeout(()=>{
    if(holdGesture!==gesture) return;
    gesture.fired=true; suppressMessageTapUntil=Date.now()+1000;
    window.getSelection()?.removeAllRanges(); action();
  },550);
  holdGesture=gesture;
},{passive:false});
document.addEventListener('pointermove',event=>{
  if(holdGesture&&!holdGesture.fired&&Math.hypot(event.clientX-holdGesture.x,event.clientY-holdGesture.y)>14) clearHold();
},{passive:true});
document.addEventListener('pointerup',event=>{
  if(holdGesture?.fired) {event.preventDefault();suppressMessageTapUntil=Date.now()+450;}
  clearHold();
});
document.addEventListener('pointercancel',clearHold);
document.addEventListener('click',event=>{
  if(Date.now()<suppressMessageTapUntil) {event.preventDefault();event.stopImmediatePropagation();}
},true);
document.addEventListener('selectstart',event=>{
  if(!(event.target instanceof Element)||!event.target.closest('input,textarea,[contenteditable="true"]')) event.preventDefault();
});

function dialogCard(title) {
  window.getSelection()?.removeAllRanges(); closeSideZoom();
  const overlay=el('dm-context');clear(overlay);overlay.hidden=false;
  (document.querySelector('dialog.settings-window[open]')||document.body).appendChild(overlay);
  const card=node('div','dm-context-card'); card.appendChild(node('strong','',title));
  overlay.appendChild(card);
  card.tabIndex=-1; card.focus();
  overlay.onclick=event=>{if(event.target===overlay) overlay.hidden=true;};
  return card;
}
function dialogAction(card,label,action) {
  const button=node('button','',label);button.type='button';button.onclick=action;card.appendChild(button);return button;
}
function cancelDialog(card) {dialogAction(card,'Cancel',()=>el('dm-context').hidden=true);}

function messageIdentity(radio,message) {
  if(message.own) return {name:state[radio].ownName||'You',id:null,text:message.text};
  const exact=state[radio].nodes.find(item=>item.id===message.sender);
  if(exact) return {name:exact.name||exact.id,id:exact.id,text:message.text};
  if(radioType(radio)==='mc'&&message.sender==='MeshCore') {
    // Channel packets carry a display-name prefix, not a public key. Never guess between duplicate names.
    const matches=state[radio].nodes.filter(item=>item.name&&message.text.startsWith(item.name+': '));
    const longest=matches.reduce((n,item)=>Math.max(n,item.name.length),0);
    const candidates=matches.filter(item=>item.name.length===longest);
    if(candidates.length===1) return {name:candidates[0].name,id:candidates[0].id,text:message.text.slice(longest+2)};
    const colon=message.text.indexOf(': ');
    if(colon>0) return {name:message.text.slice(0,colon),id:null,text:message.text.slice(colon+2),ambiguous:candidates.length>1};
  }
  return {name:message.senderName||message.sender,id:null,text:message.text};
}
function deliveryLabel(value) {
  if(value&&value.startsWith('sending: ')) return value.slice(9);
  if(value&&value.startsWith('unconfirmed: ')) return 'Delivery unconfirmed · '+value.slice(13);
  const labels={queued:'Queued for radio',sent_to_radio:'Sent to radio · awaiting acknowledgment',accepted:'Radio accepted · awaiting acknowledgment',
    sent_radio:'Sent by radio · delivery unconfirmed',sent:'Sent by radio · delivery unconfirmed',
    delivered_mesh:'Delivered to mesh',delivered:'Delivered to recipient',relayed:'Relayed · recipient unconfirmed',
    unconfirmed:'Delivery unconfirmed',failed:'Failed'};
  return labels[value]||value;
}
function appendMessageMeta(bubble,radio,message,channelMessage=false) {
  const meta=node('div','meta');
  meta.appendChild(node('strong','message-sender',messageIdentity(radio,message).name));
  meta.appendChild(node('span','message-time',' · '+message.time));
  bubble.appendChild(meta);
}
function appendMessageDelivery(bubble,radio,message,channelMessage=false) {
  if(message.own) bubble.appendChild(node('div','message-delivery',channelMessage&&radioType(radio)==='mc'&&['sent_radio','sent'].includes(message.delivery)?'Sent':deliveryLabel(message.delivery)));
}
function bindMessageActions(bubble,radio,message,where) {
  const key=state[radio].key;
  const valid=()=>state[radio].key===key;
  const hold=()=>{
    if(!valid()) return;
    if(message.own) {showMessageMenu(radio,message,where);return;}
    const identity=messageIdentity(radio,message);
    if(identity.id) showMessageNodeActions(radio,identity.id);
    else showMessageMenu(radio,message,where);
  };
  bindHold(bubble,hold,()=>{
    if(!valid()) return;
    showMessageMenu(radio,message,where);
  });
}
function replyToMessage(radio,message,where) {
  const identity=messageIdentity(radio,message);
  el('dm-context').hidden=true;
  if(where.peer) selectDmPeer(radio,where.peer);
  else selectChannel(radio,where.channel);
  const input=el(dmMode?'dm-input':'chat-input');
  if(radioType(radio)==='mc'&&!where.peer) input.value='@['+identity.name.replace(/[\[\]]/g,'')+'] '+input.value;
  if(radioType(radio)==='mt'&&!where.peer) {
    channelReply={key:state[radio].key,channel:where.channel,packetId:message.packetId||0,name:identity.name,text:identity.text};
    renderReplyPreview();
  }
  input.focus(); resizeComposer(input);
}
function showReplyPrompt(radio,message,where) {
  const card=dialogCard('Reply');
  const identity=messageIdentity(radio,message);
  card.appendChild(node('p','',identity.name));
  if(!message.own) dialogAction(card,'Reply',()=>replyToMessage(radio,message,where));
  else card.appendChild(node('p','','This is your message.'));
  if(identity.id&&!where.peer) dialogAction(card,'Direct message',()=>{el('dm-context').hidden=true;selectDmPeer(radio,identity.id);});
  reactionMenu(card,radio,message);
  cancelDialog(card);
}
function showMessageMenu(radio,message,where) {
  const identity=messageIdentity(radio,message),key=state[radio].key;
  const card=dialogCard('Message actions · '+namedNode(radio,identity.id,identity.name));
  if(radioType(radio)==='mc'&&message.own&&!where.peer) {
    const count=Number.isInteger(message.heardRepeats)?message.heardRepeats:-1;
    card.appendChild(node('p','',count<0?'Heard repeats are unavailable for this message.':
      'Heard repeats: '+count+'. Copies your radio heard being rebroadcast; not a count of unique repeaters or recipient delivery.'));
  }
  if(message.own) {
    const broadcast=radioType(radio)==='mc'&&!where.peer&&['sent','sent_radio'].includes(message.delivery);
    card.appendChild(node('p','delivery-explanation',broadcast?'Sent':deliveryLabel(message.delivery)));
    let explanation='';
    if((message.delivery||'').startsWith('unconfirmed')) explanation='No final confirmation was received. The message may have arrived; retrying can create a duplicate.';
    else if(['queued','sent_to_radio','accepted'].includes(message.delivery)) explanation='Waiting for the radio or mesh acknowledgment. This is not a delivery confirmation.';
    else if(message.delivery==='delivered_mesh') explanation='The radio confirmed the broadcast reached the mesh. This does not confirm every recipient received it.';
    else if(message.delivery==='relayed') explanation='A relay was heard, but the recipient has not confirmed receipt.';
    else if(['sent','sent_radio'].includes(message.delivery)) explanation=broadcast?'The radio accepted the broadcast. Heard repeats show copies received back, not delivery to every recipient.':'The radio accepted the send. Remote delivery has not been confirmed.';
    if(explanation) card.appendChild(node('p','',explanation));
    const retrying=(message.delivery||'').startsWith('sending:');
    const pending=retrying||['queued','sent_to_radio','accepted'].includes(message.delivery);
    if(retrying) dialogAction(card,'Stop retries',()=>{el('dm-context').hidden=true;AndroidBridge.messageAction(key,message.id,'cancel');});
    if(!pending) dialogAction(card,radioType(radio)==='mc'?'Send Again':'Resend',()=>{el('dm-context').hidden=true;AndroidBridge.messageAction(key,message.id,'retry');});
  }
  reactionMenu(card,radio,message);
  dialogAction(card,'Copy Text',()=>{AndroidBridge.copyText(identity.text);el('dm-context').hidden=true;});
  if(!message.own) dialogAction(card,'Reply',()=>replyToMessage(radio,message,where));
  if(radioType(radio)==='mc'&&message.own&&!where.peer) dialogAction(card,'Heard Repeats',()=>showMessagePath(message,true,radio));
  if(radioType(radio)==='mc'&&!message.own) dialogAction(card,'View Message Paths',()=>showMessagePath(message,false,radio));
  if(identity.id&&!where.peer) dialogAction(card,'Direct message',()=>{el('dm-context').hidden=true;selectDmPeer(radio,identity.id);});
  if(identity.id) dialogAction(card,'Node actions',()=>showMessageNodeActions(radio,identity.id));
  else if(!message.own) card.appendChild(node('p','help',identity.ambiguous?'Several contacts use this name. Choose the intended contact from the user list.':'No matching contact is loaded for this sender.'));
  dialogAction(card,'Delete Message',()=>{el('dm-context').hidden=true;AndroidBridge.messageAction(key,message.id,'delete');});
  cancelDialog(card);
}
function showMessagePath(message,own=false,radio='mc') {
  const card=dialogCard(own?'Heard Repeats':'Message Paths');
  const paths=Array.isArray(message.paths)?message.paths:[];
  if(!paths.length) card.appendChild(node('p','', 'No path details were captured for this message. Radio logs must arrive while connected; old history cannot supply missing paths.'));
  for(const record of paths) {
    if(!record||!Array.isArray(record.path)||!record.path.every(hash=>typeof hash==='string')) continue;
    const row=node('div','trace-route');
    const names=record.path.map(hash=>{
      const candidates=[...new Map(state[radio].nodes.filter(n=>n.role==='Repeater'&&/^(?:[0-9a-f]{2}){1,3}$/i.test(hash)&&n.id.toLowerCase().startsWith(hash.toLowerCase())).map(n=>[n.id.toLowerCase(),n])).values()];
      if(candidates.length===1)return (candidates[0].name||candidates[0].id)+' ('+hash+')';
      if(!candidates.length)return hash+' · name unavailable';
      const labels=candidates.sort((a,b)=>a.id.toLowerCase().localeCompare(b.id.toLowerCase()))
        .map(n=>n.name?n.name+' ['+n.id.toLowerCase()+']':n.id.toLowerCase());
      return hash+' · multiple repeaters: '+labels.join(' / ');
    });
    row.appendChild(node('p','',names.length?names.join(' → ')+' → Your radio':'Direct reception → Your radio'));
    row.appendChild(node('small','', 'Received SNR '+(record.snr/4)+' dB · RSSI '+record.rssi+' dBm'));
    card.appendChild(row);
  }
  card.appendChild(node('p','help','A short path ID can match several repeaters. When names are listed together, the received path does not uniquely identify one of them.'));
  if(own) card.appendChild(node('p','help','Each route is a rebroadcast heard by your companion. The last repeater in that route transmitted the copy your radio heard. Counts are receptions, not proof that every recipient received the message.'));
  cancelDialog(card);
}

function showAdvertActions(deviceKey) {
  const key=deviceKey;
  if(!key) return;
  const card=dialogCard('Send Advert');
  for(const [label,action] of [['Zero Hop','zero'],['Flood Routed','flood'],['Copy to Clipboard','copy']]) {
    dialogAction(card,label,()=>AndroidBridge.sendAdvert(key,action));
  }
  const done=dialogAction(card,'Cancel',()=>el('dm-context').hidden=true); done.id='advert-dismiss';
  card.dataset.advertKey=key;
}
function showChatAdvert(side) {
  const radio=state[side];
  if(radio?.connected&&radioType(side)==='mc') showAdvertActions(radio.key);
}

window.onAdvertResult=function(key,message,success) {
  const card=el('dm-context').firstChild;
  if(el('dm-context').hidden||card?.dataset.advertKey!==key) return;
  let result=card.querySelector('.advert-result');
  if(!result) {result=node('p','advert-result');card.appendChild(result);}
  result.textContent=message;
  if(success) {el('advert-dismiss').textContent='OK';card.querySelectorAll('button:not(#advert-dismiss)').forEach(b=>b.disabled=true);}
};

window.onTraceResult=function(result) {
  // A response is bound to the originating radio and request even if selectors change.
  activeTrace=result;
  const card=dialogCard((result.action==='ping'?'Ping':'TR')+' · '+result.radio.toUpperCase()+' · '+result.name);
  card.classList.add('trace-card');card.dataset.requestId=result.requestId;
  if(result.pending) card.appendChild(node('p','',result.action==='ping'?'Pinging… waiting for the radio’s reply.':'Tracing… waiting for the radio’s reply.'));
  else {
    if(result.note) card.appendChild(node('p','help',result.note));
    for(const [label,hops] of [['Outward route',result.forward||[]],['Return route',result.backward||[]]]) {
      const group=node('section','trace-direction');group.appendChild(node('h3','',label));
      if(!hops.length) group.appendChild(node('p','','Not reported'));
      hops.forEach((hop,index)=>{
        if(index) group.appendChild(node('div','trace-link','↓ '+(Number.isFinite(hop.snr)?hop.snr+' dB':'SNR unavailable')));
        const row=node('div','trace-hop');row.append(node('strong','',hop.name),node('small','',hop.id));group.appendChild(row);
      });
      card.appendChild(group);
    }
  }
  dialogAction(card,'Close',()=>el('dm-context').hidden=true);
};
function changeComposerFont(delta) {
  composerFontStep=Math.max(0,Math.min(6,composerFontStep+delta));
  applyComposerFont();AndroidBridge.setTextSize('composer',composerFontStep);
}
window.onComposeReleased=function(key,peer,text) {
  if(pendingCompose?.key===key&&pendingCompose.peer===peer&&pendingCompose.text===text)pendingCompose=null;
};
window.onComposeQueued=function(key,peer,text) {
  if(!pendingCompose||pendingCompose.key!==key||pendingCompose.peer!==peer||pendingCompose.text!==text) return;
  if(peer&&pendingCompose.draftKey&&dmDrafts.get(pendingCompose.draftKey)?.trim()===text)dmDrafts.delete(pendingCompose.draftKey);
  const input=el(peer?'dm-input':'chat-input');
  const current=peer?dmMode&&dmSelection.peer===peer&&state[dmSelection.radio]?.key===key:!dmMode&&state[network]?.key===key&&channel[network]===pendingCompose.channel;
  if(current&&input.value.trim()===text) {
    input.value='';input.blur();document.body.classList.remove('composing');resizeComposer(input);
    AndroidBridge.finishComposing();
  }
  pendingCompose=null;
  if(!peer) {channelReply=null;renderReplyPreview();}
};
function applyComposerFont() {
  document.documentElement.style.setProperty('--composer-font',(15+composerFontStep*2)+'px');
  document.querySelectorAll('.composer-font-value').forEach(item=>item.textContent=(15+composerFontStep*2)+' px');
  document.querySelectorAll('#chat-input,#dm-input').forEach(resizeComposer);
  if(sideZoom) {
    sideZoom.step=sideFontSteps[sideZoom.radio];
    el('side-zoom-panel').style.setProperty('--side-zoom-extra',(sideZoom.step*2)+'px');
  }
}
function resizeComposer(input) {
  updateMessageBudget(input);
  document.body.classList.toggle('composing',document.activeElement?.matches('#chat-input,#dm-input'));
  const active=document.activeElement===input||!!input.value;
  input.classList.toggle('editing',active);
  input.style.height='auto';
  const max=window.innerHeight<420?48:Math.max(64,Math.min(220,window.innerHeight*.32));
  const min=active?Math.min(90,max):44;
  input.style.height=Math.min(max,Math.max(min,input.scrollHeight))+'px';
}
function messageBudget(input) {
  const direct=input.id==='dm-input', radio=direct?dmSelection.radio:network;
  const device=state&&state[radio];
  const limit=device?Number(device[direct?'directTextLimit':'channelTextLimit']??(radioType(radio)==='mt'?200:direct?150:Math.max(0,158-new TextEncoder().encode(device.ownName||'').length))):0;
  const text=input.value.trim(), used=new TextEncoder().encode(text).length;
  let parts=used?1:0;
  if(limit&&used>limit) {
    // The phone uses the same grapheme splitter as the native send preview.
    const nativeCount=typeof AndroidBridge.messagePartCount==='function'?AndroidBridge.messagePartCount(text,limit):undefined;
    if(Number.isInteger(nativeCount)) parts=nativeCount;
    else {
      parts=1;let bytes=0;
      for(const {segment} of new Intl.Segmenter(undefined,{granularity:'grapheme'}).segment(text)) {
        const size=new TextEncoder().encode(segment).length;
        if(size>limit){parts=-1;break;}
        if(bytes+size>limit){parts++;bytes=0;}
        bytes+=size;
      }
    }
  }
  return {limit,used,parts,split:state?.messageSplitEnabled!==false,invalid:text.includes('\0')||parts<0};
}
function updateMessageBudget(input) {
  const {limit,used,invalid,split,parts}=messageBudget(input);
  let counter=el(input.id+'-budget');
  if(!counter) {
    counter=node('span','message-budget','');counter.id=input.id+'-budget';
    input.parentElement.previousElementSibling.appendChild(counter);
  }
  counter.textContent=limit?`${used} / ${limit} bytes`+(used>limit?(!split?' · Message Split disabled':parts>0?` · ${parts} split messages`:' · cannot split this character sequence'):''):'Waiting for radio limits';
  counter.classList.toggle('over-limit',used>limit||invalid);
  counter.title='UTF-8 bytes: Greek letters and emoji can use more than one byte.';
  const direct=input.id==='dm-input',radio=direct?dmSelection.radio:network,device=state&&state[radio];
  const ready=device&&device.connected&&!device.loading&&(direct?device.nodes.some(n=>n.id===dmSelection.peer&&nodeMessageable(radio,n)):device.channels.some(c=>c.index===channel[radio]));
  el(direct?'dm-reply':'chat-send').disabled=!ready||input.disabled||!used||!limit||(!split&&used>limit)||invalid;
}
function validMessageBudget(input) {
  const {limit,used,invalid,split}=messageBudget(input);
  updateMessageBudget(input);
  if(!limit||(!split&&used>limit)||invalid) {
    onNodeActionResult(invalid?'Remove NUL characters or shorten the character sequence that exceeds the message limit.':`Message uses ${used} UTF-8 bytes; this radio allows ${limit}. Shorten the text to send.`);
    return false;
  }
  return true;
}
for(const input of document.querySelectorAll('#chat-input,#dm-input')) {
  for(const event of ['input','focus','blur']) input.addEventListener(event,()=>resizeComposer(input));
}
window.addEventListener('resize',()=>document.querySelectorAll('#chat-input,#dm-input').forEach(resizeComposer));

// Keep the editor and Android IME focused while tapping composer controls.
for(const button of document.querySelectorAll('#chat-send,#dm-reply,.composer-font-tools button')) {
  button.addEventListener('pointerdown',event=>{if(document.activeElement?.matches('#chat-input,#dm-input')) event.preventDefault();});
  button.addEventListener('mousedown',event=>{if(document.activeElement?.matches('#chat-input,#dm-input')) event.preventDefault();});
}

let interruptedEditor=null;
window.beforeConnectionWarning=()=>{
  const input=document.activeElement;
  interruptedEditor=input?.matches('#chat-input,#dm-input')?{id:input.id,start:input.selectionStart,end:input.selectionEnd}:null;
};
window.afterConnectionWarning=()=>{
  if(interruptedEditor){const input=el(interruptedEditor.id);input.focus();input.setSelectionRange(interruptedEditor.start,interruptedEditor.end);resizeComposer(input);}
  interruptedEditor=null;
};

let channelReply=null;
function renderReplyPreview() {
  let preview=el('channel-reply-preview');
  if(!preview) {preview=node('div','reply-preview');preview.id='channel-reply-preview';el('chat-input').parentElement.before(preview);}
  clear(preview);
  const reply=channelReply;
  preview.hidden=!reply||dmMode||reply.key!==state?.[network]?.key||reply.channel!==channel[network];
  if(preview.hidden) return;
  preview.appendChild(node('span','','Reply to '+reply.name+': '+reply.text+(reply.packetId?'':' · original reference unavailable')));
  dialogAction(preview,'×',()=>{channelReply=null;renderReplyPreview();});
}
window.onAbout=function(version,notices) {
  el('about-version').textContent='Beta edition · '+version;
  el('about-notices').textContent=notices;
};

function reactionMenu(card,radio,message) {
  if(radioType(radio)!=='mt')return;
  const key=state[radio].key;
  dialogAction(card,'Emoji',()=>{
    if(state[radio].key!==key)return;
    const picker=dialogCard('Emoji');reactionChoices(picker,radio,message);cancelDialog(picker);
  });
}
function reactionChoices(card,radio,message) {
  if(radioType(radio)!=='mt')return;
  if(!message.packetId){card.appendChild(node('p','help','Reactions unavailable: original packet reference was not saved.'));return;}
  card.appendChild(node('p','','React with thumbs up or a number'));
  const row=node('div','reaction-choices'),key=state[radio].key;
  for(const i of ['👍','0','1','2','3','4','5','6','7']) {
    const button=dialogAction(row,String(i),()=>{if(state[radio].key!==key)return;el('dm-context').hidden=true;AndroidBridge.react(key,message.id,String(i));});
    button.disabled=!state[radio].connected||state[radio].loading;
    button.setAttribute('aria-label','React '+i);
  }
  card.appendChild(row);
}
function appendReactions(bubble,radio,message,entries) {
  if(radioType(radio)!=='mt'||!message.packetId)return;
  const reactions=entries.filter(r=>r.reaction&&r.replyId===message.packetId&&(!r.peer||r.peer===message.peer));
  if(!reactions.length)return;
  const row=node('div','message-reactions');
  const seen=new Set();
  for(const r of reactions) {
    const identity=(r.own?'You':r.sender)+'\0'+r.text;
    if(seen.has(identity))continue;seen.add(identity);
    const chip=node('button','reaction-chip',r.text);chip.type='button';
    const label=(r.own?'You':state[radio]?.nodes?.find(n=>n.id===r.sender)?.name||r.senderName||r.sender)+(r.own?' · '+deliveryLabel(r.delivery):'');
    chip.onclick=event=>{event.stopPropagation();showReactionSenders(radio,reactions,r.text);};
    chip.title=label;chip.setAttribute('aria-label',r.text+' · '+label);
    if(r.own&&!['delivered','delivered_mesh'].includes(r.delivery))chip.appendChild(node('small','',deliveryLabel(r.delivery)));
    row.appendChild(chip);
  }
  bubble.appendChild(row);
}

function showReactionSenders(radio,reactions,emoji){
  const card=dialogCard('Reactions · '+emoji),seen=new Set();
  for(const r of reactions.filter(r=>r.text===emoji)){
    const id=r.own?'You':r.sender;if(seen.has(id))continue;seen.add(id);
    const name=r.own?'You':state[radio]?.nodes?.find(n=>n.id===r.sender)?.name||r.senderName||id;
    card.appendChild(node('p','reaction-sender',name+(name!==id?' · '+id:'')));
  }
  dialogAction(card,'Close',()=>el('dm-context').hidden=true);
}
function showPingOptions(radio,entry){
  const key=state[radio].key,card=dialogCard('Ping · '+(entry.name||entry.id));
  card.appendChild(node('p','help','Zero-hop Ping tests a direct link. The repeater must be within radio range. Room servers must have repeating enabled.'));
  for(const width of [1,2,4])dialogAction(card,'Ping · '+width+'-byte ID',()=>{
    if(state[radio].key!==key)return;
    const prefix=entry.id.slice(0,width*2).toLowerCase();
    const matches=state[radio].nodes.filter(n=>['Repeater','Room server'].includes(n.role)&&n.id.toLowerCase().startsWith(prefix));
    const send=()=>{el('dm-context').hidden=true;AndroidBridge.nodeAction(key,entry.id,'ping',String(width));};
    if(matches.length>1){const warning=dialogCard('Ping ID matches multiple devices');warning.appendChild(node('p','',matches.map(n=>n.name||n.id).join(' / ')+'. A reply cannot identify which one answered.'));dialogAction(warning,'Send Ping',send);cancelDialog(warning);}else send();
  });
  cancelDialog(card);
}
