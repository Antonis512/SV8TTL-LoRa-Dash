'use strict';
const memberWords={
  shareLocation:['Send my location','Αποστολή τοποθεσίας μου'],locating:['Getting phone location…','Αναζήτηση τοποθεσίας τηλεφώνου…'],
  viewLocation:['View on map','Προβολή στον χάρτη'],close:['Close','Κλείσιμο'],location:['Location','Τοποθεσία'],
  routeChanged:['Destination changed. Choose it again.','Ο προορισμός άλλαξε. Επιλέξτε τον ξανά.'],
  myNodes:['My Nodes','Οι κόμβοι μου'],offline:['Disconnected','Αποσυνδεδεμένη'],online:['Connected','Συνδεδεμένη'],noSaved:['No saved radios.','Δεν υπάρχουν αποθηκευμένοι ασύρματοι.'],
  discovering:['Discovering and connecting available radios…','Αναζήτηση και σύνδεση διαθέσιμων ασυρμάτων…'],
  singleDestination:['Send to one channel or one contact at a time. Choosing another replaces your selection.','Στέλνετε σε ένα κανάλι ή σε μία επαφή κάθε φορά. Κάθε νέα επιλογή αντικαθιστά την προηγούμενη.'],
  singleRecipient:['Choose one contact and one of their nodes. Selecting another contact or node replaces the previous choice.','Επιλέξτε μία επαφή και έναν από τους κόμβους της. Αν επιλέξετε άλλη επαφή ή άλλον κόμβο, η νέα επιλογή αντικαθιστά την προηγούμενη.'],
  translate:['Translate:','Μετάφραση:'],connected:['connected','συνδεδεμένα'],direct:['DM','Προσωπικό μήνυμα'],mode:['Untrained Member Mode','Λειτουργία για μη εκπαιδευμένα μέλη'],
  disable:['Disable Untrained Member Mode','Απενεργοποίηση λειτουργίας για μη εκπαιδευμένα μέλη'],
  enabled:['Enable member mode','Ενεργοποίηση λειτουργίας μέλους'],connect:['Connect all available devices','Σύνδεση όλων των διαθέσιμων συσκευών'],
  connecting:['Connecting…','Σύνδεση…'],device:['Choose device for messaging','Επιλέξτε συσκευή για αποστολή μηνυμάτων'],
  channels:['Send to a channel','Αποστολή σε κανάλι'],dm:['Send direct message to a contact','Αποστολή προσωπικού μηνύματος σε επαφή'],
  recipients:['Choose contact and node','Επιλέξτε επαφή και κόμβο'],approved:['Prepared contacts','Εγκεκριμένες επαφές'],
  setupChannels:['Channels available to the member','Κανάλια διαθέσιμα στο μέλος'],
  setupHelp:['Select channels reported by each connected radio and contacts from your address book.','Επιλέξτε κανάλια από κάθε συνδεδεμένο ασύρματο και επαφές από τον κατάλογό σας.'],
  noDevices:['No connected devices','Δεν υπάρχουν συνδεδεμένες συσκευές'],noChannels:['No designated channels available','Δεν υπάρχουν διαθέσιμα καθορισμένα κανάλια'],
  noPeople:['No prepared contacts available','Δεν υπάρχουν διαθέσιμες εγκεκριμένες επαφές'],noContacts:['Add people and assign their nodes in Contacts.','Προσθέστε επαφές και αντιστοιχίστε τους κόμβους τους στην καρτέλα Επαφές.'],
  noRoute:['No reachable assigned nodes','Κανένας διαθέσιμος αντιστοιχισμένος κόμβος'],reachable:['Reachable nodes','Διαθέσιμοι κόμβοι'],
  ok:['OK','Εντάξει'],chooseContact:['Choose a contact before continuing.','Επιλέξτε μια επαφή για να συνεχίσετε.'],chooseNode:['Choose an available node for this contact.','Επιλέξτε έναν διαθέσιμο κόμβο για αυτή την επαφή.'],cancel:['Cancel','Ακύρωση'],save:['Save','Αποθήκευση'],send:['Send','Αποστολή'],message:['Message','Μήνυμα'],
  templates:['Use a message template','Χρήση προτύπου μηνύματος'],history:['Messages','Μηνύματα'],reply:['Reply','Απάντηση'],replying:['Reply to','Απάντηση προς'],
  limit:['Split limit reached (3 of 3 parts).','Φτάσατε στο όριο διαίρεσης (3 από 3 μέρη).'],
  tooLong:['Message too long. Shorten it before sending.','Το μήνυμα είναι πολύ μεγάλο. Μειώστε το μήκος του πριν από την αποστολή.'],
  splitOff:['Splitting is off. Shorten the message.','Η διαίρεση είναι απενεργοποιημένη. Μειώστε το μήκος του μηνύματος.'],
  parts:['Message parts','Μέρη μηνύματος'],choose:['Choose one destination.','Επιλέξτε έναν προορισμό.'],
  unavailable:['Unavailable','Μη διαθέσιμη'],
  queued:['Queued for sending','Σε αναμονή για αποστολή'],error:['Could not queue this message. Check the connected devices and destinations.','Δεν ήταν δυνατή η αποστολή. Ελέγξτε τις συνδεδεμένες συσκευές και τους προορισμούς.'],
  you:['You','Εσείς'],chat:['Chat','Συνομιλία'],config:['Config','Ρυθμίσεις'],noMessages:['No messages yet.','Δεν υπάρχουν ακόμη μηνύματα.']
};
const mtWord=k=>memberWords[k][state?.memberLanguage==='el'?1:0];
let memberDraftConfig=null,memberKey='',memberReply=null,memberBusy=false,memberResult='',memberSentText='';
let memberChannel='',memberContact='',memberPeer='';
function clearMemberDestination(){memberChannel='';memberContact='';memberPeer='';memberReply=null;}
const memberDeviceId=d=>d.radio+':'+(d.ownId||d.id);
const channelKey=c=>JSON.stringify([c.index,c.name]);
const memberConfig=()=>state?.memberConfig||{};
const memberRadios=()=>state?.memberRadios||[];
const memberRadio=()=>memberRadios().find(d=>memberDeviceId(d)===memberKey);
const memberPeople=()=>contactPeople().filter(c=>(memberConfig().contacts||[]).includes(c.id));
function memberLanguage(lang){AndroidBridge.setMemberLanguage(lang);}
function syncMemberMode(changed){
  const enabled=!!memberConfig().enabled;
  document.body.classList.toggle('member-mode',enabled);el('member-chat').hidden=!enabled;
  for(const e of document.querySelectorAll('[data-member-text]'))e.textContent=mtWord(e.dataset.memberText);
  el('member-config-button').firstElementChild.textContent=mtWord(enabled?'disable':'mode');
  for(const [i,b] of [...document.querySelectorAll('.member-languages button')].entries())b.setAttribute('aria-pressed',(state.memberLanguage==='el')===(i===1));
  for(const [tab,key,normal] of [['chat','chat','Chat'],['settings','config','Config']])
    document.querySelector('[data-tab="'+tab+'"] span:last-child').textContent=enabled?mtWord(key):normal;
  if(el('member-recipients').open)renderMemberPeople();
  if(el('member-setup').open)renderMemberSetup();
  if(changed){
    clearMemberDestination();memberKey='';memberResult='';
    el('member-input').value='';el('member-use-channel').checked=false;el('member-use-dm').checked=false;
    if(enabled){for(const dialog of document.querySelectorAll('dialog[open]'))dialog.close();navigationStack.length=0;showSettingsPage('home');switchTab('chat');AndroidBridge.loadContacts();}
  }
}
function memberCheck(parent,label,checked,change){
  const row=node('label','member-check'),input=node('input','');input.type='checkbox';input.checked=checked;
  input.onchange=()=>change(input.checked);row.append(input,document.createTextNode(label));parent.append(row);return input;
}
function openMemberSetup(){
  if(memberConfig().enabled){AndroidBridge.disableMemberMode();return;}
  memberDraftConfig=JSON.parse(JSON.stringify({enabled:false,channels:{},contacts:[],...memberConfig()}));
  renderMemberSetup();el('member-setup').showModal();AndroidBridge.loadContacts();
}
function renderMemberSetup(){
  const cfg=memberDraftConfig,body=el('member-setup-body');body.replaceChildren();
  memberCheck(body,mtWord('enabled'),cfg.enabled,on=>cfg.enabled=on);
  body.append(node('p','help',mtWord('setupHelp')),node('h3','',mtWord('setupChannels')));
  const devices=(state?.devices||[]).filter(d=>!d.loading);
  if(!devices.length)body.append(node('p','',mtWord('noDevices')));
  for(const d of devices){
    body.append(node('strong','',d.name+' · '+d.radio.toUpperCase()));
    const id=memberDeviceId(d),choices=cfg.channels[id]||[];
    for(const c of d.channels||[])memberCheck(body,c.name,choices.some(x=>channelKey(x)===channelKey(c)),on=>{
      cfg.channels[id]=(cfg.channels[id]||[]).filter(x=>channelKey(x)!==channelKey(c));
      if(on)cfg.channels[id].push({name:c.name,index:c.index});
    });
  }
  body.append(node('h3','',mtWord('approved')));
  if(!contactPeople().length)body.append(node('p','',mtWord('noContacts')));
  for(const c of contactPeople())memberCheck(body,contactName(c),cfg.contacts.includes(c.id),on=>{
    cfg.contacts=cfg.contacts.filter(id=>id!==c.id);if(on)cfg.contacts.push(c.id);
  });
}
function saveMemberSetup(){if(memberConfig().enabled)return;AndroidBridge.saveMemberConfig(JSON.stringify(memberDraftConfig));el('member-setup').close();}
function memberContactsChanged(){
  if(el('member-setup').open)renderMemberSetup();
  if(memberConfig().enabled){renderMemberChat();if(el('member-recipients').open)renderMemberPeople();}
}
function memberAllowed(d){
  const allowed=memberConfig().channels?.[memberDeviceId(d)]||[];
  return d.channels.filter(c=>allowed.some(x=>channelKey(x)===channelKey(c)));
}
function memberRoutes(person){
  const d=memberRadio();
  if(!d)return [];
  return (person.nodes||[]).filter(a=>{
    if(a.radio!==d.radio||a.id===d.ownId)return false;
    const n=d.nodes.find(x=>x.id===a.id&&!x.savedOnly);
    if(!n)return false;
    return d.radio==='mc'?['User','Room server'].includes(n.role)&&state.devices.find(x=>x.key===d.key)?.pathNodes?.some(x=>x.id===a.id):/^!(?!00000000|ffffffff)[0-9a-f]{8}$/i.test(a.id);
  });
}
function memberTarget(){
  const device=memberRadio();if(!device)return null;
  if(memberReply)return {device,peer:memberReply.peer,channel:memberReply.channel};
  if(el('member-use-channel').checked){
    const c=memberAllowed(device).find(c=>channelKey(c)===memberChannel);
    if(c)return {device,channel:c.index,peer:''};
  }else if(el('member-use-dm').checked){
    const person=memberPeople().find(c=>c.id===memberContact);
    if(person&&memberRoutes(person).some(a=>a.id===memberPeer))return {device,peer:memberPeer,channel:-1};
  }
  return null;
}
function memberChoice(parent,group,label,checked,change){
  const input=memberCheck(parent,label,checked,change);
  input.type='radio';input.name=group;
  return input;
}
function renderMemberPeople(){
  const host=el('member-people');host.replaceChildren();
  updateMemberRecipientWarning();
  if(!memberPeople().length)host.append(node('p','',mtWord('noPeople')));
  for(const c of memberPeople()){
    const routes=memberRoutes(c),group=node('section','member-contact-group');
    host.append(group);
    memberChoice(group,'member-contact',contactName(c),memberContact===c.id,()=>{
      memberContact=c.id;memberPeer=routes.length===1?routes[0].id:'';
      memberChangeRoutes();renderMemberPeople();
    });
    if(memberContact!==c.id)continue;
    if(!routes.length)group.append(node('p','help',mtWord('noRoute')));
    for(const a of c.nodes||[]){
      const available=routes.includes(a);
      const label=[a.label||a.name,a.radio.toUpperCase(),a.id,available?'':mtWord('unavailable')].filter(Boolean).join(' · ');
      memberChoice(group,'member-node',label,available&&memberPeer===a.id,()=>{
        memberPeer=a.id;memberChangeRoutes();renderMemberPeople();
      }).disabled=!available;
    }
  }
}
function updateMemberRecipientWarning(){
  const warning=el('member-recipient-warning');
  if(warning.hidden)return;
  const person=memberPeople().find(c=>c.id===memberContact);
  if(person&&memberTarget()?.peer){warning.hidden=true;return;}
  warning.textContent=mtWord(person?'chooseNode':'chooseContact');
}
function openMemberRecipients(){
  el('member-recipient-warning').hidden=true;
  renderMemberPeople();el('member-recipients').showModal();
}
function cancelMemberRecipients(){
  memberContact='';memberPeer='';el('member-use-dm').checked=false;
  el('member-recipients').close();memberChangeRoutes();
}
function confirmMemberRecipients(){
  if(memberTarget()?.peer){el('member-recipients').close();return;}
  el('member-recipient-warning').hidden=false;updateMemberRecipientWarning();
}
function memberToggleDM(){memberChangeRoutes();openMemberRecipients();}
function memberChangeRadio(){memberKey=el('member-radio').value;clearMemberDestination();memberChangeRoutes();}
function memberChangeRoutes(){memberReply=null;memberResult='';renderMemberChat();}
function memberBudget(){
  const target=memberTarget(),text=el('member-input').value.trim();
  const count=target&&text?AndroidBridge.messagePartCount(text,target.peer?target.device.directTextLimit:target.device.channelTextLimit):0;
  const max=state?.messageSplitEnabled===false?1:3;
  const warning=count<0||count>max;
  const label=!target?'choose':warning?(max===1?'splitOff':'tooLong'):count===3?'limit':'parts';
  el('member-budget').textContent=mtWord(label)+(label==='parts'?' '+count+' / '+max:'');
  el('member-budget').classList.toggle('warning',warning||count===3);
  el('member-location').disabled=memberBusy||!target;
  el('member-send').disabled=memberBusy||!text||!target||warning;
}
let pendingMemberLocation=null;
function chatLocationDestination(){
  const device=state?.[network];
  const selected=device?.channels.find(c=>c.index===channel[network]);
  if(!device?.connected||dmMode||!selected||state?.memberConfig?.enabled)return null;
  return {radio:radioType(network),nodeId:device.ownId,key:device.key,peer:'',channel:selected.index,label:selected.name};
}
function startChatLocation(){
  const dest=chatLocationDestination();
  if(!dest||pendingCompose)return;
  const token=++markPhoneSequence;
  pendingMemberLocation={token,dest,chat:true};
  el('member-location-target').textContent=dest.radio.toUpperCase()+' · '+dest.label;
  el('member-location-status').textContent=mtWord('locating');
  el('member-location-confirm').disabled=true;
  el('member-location-dialog').showModal();
  AndroidBridge.fetchMarkPosition(token);
}
function locationDestination(){
  const t=memberTarget();
  if(!t||!memberConfig().enabled)return null;
  const label=t.peer?(memberReply?.label||contactName(memberPeople().find(c=>c.id===memberContact))||t.peer):t.device.channels.find(c=>c.index===t.channel)?.name;
  return {radio:t.device.radio,nodeId:t.device.ownId,peer:t.peer,channel:t.channel,sourceId:memberReply?.id||0,label};
}
function startMemberLocation(){
  const dest=locationDestination();
  if(!dest||memberBusy)return;
  const token=++markPhoneSequence;
  pendingMemberLocation={token,dest};
  el('member-location-target').textContent=[dest.radio.toUpperCase(),dest.label,dest.peer].filter(Boolean).join(' · ');
  el('member-location-status').textContent=mtWord('locating');
  el('member-location-confirm').disabled=true;
  el('member-location-dialog').showModal();
  AndroidBridge.fetchMarkPosition(token);
}
function cancelMemberLocation(){
  if(pendingMemberLocation)AndroidBridge.cancelMarkPosition(pendingMemberLocation.token);
  pendingMemberLocation=null;
}
function memberLocationFix(token,lat,lon,message){
  if(pendingMemberLocation?.token!==token)return false;
  const valid=Number.isFinite(lat)&&Number.isFinite(lon);
  if(valid)pendingMemberLocation.fix={lat,lon};
  el('member-location-status').textContent=valid?[lat.toFixed(6),lon.toFixed(6)].join(', ')+' · '+message:message;
  el('member-location-confirm').disabled=!valid;
  return true;
}
function confirmMemberLocation(){
  const pending=pendingMemberLocation;
  if(!pending?.fix||memberBusy)return;
  if(JSON.stringify(pending.chat?chatLocationDestination():locationDestination())!==JSON.stringify(pending.dest)){
    el('member-location-status').textContent=mtWord('routeChanged');
    el('member-location-confirm').disabled=true;return;
  }
  if(pending.chat){
    if(pendingCompose)return;
    const text='m:'+pending.fix.lat.toFixed(6)+','+pending.fix.lon.toFixed(6)+'|Location|poi';
    AndroidBridge.sendMessage(pending.dest.key,pending.dest.channel,text);
    el('member-location-dialog').close();
    return;
  }
  memberBusy=true;memberBudget();
  AndroidBridge.sendMemberLocation(JSON.stringify([pending.dest]),pending.fix.lat,pending.fix.lon);
  el('member-location-dialog').close();
}
window.onMemberLocationResult=ok=>{
  memberBusy=false;memberResult=ok?'queued':'error';renderMemberChat();
};
function sendMember(){
  memberBudget();if(el('member-send').disabled)return;
  const t=memberTarget();
  const targets=[{radio:t.device.radio,nodeId:t.device.ownId,peer:t.peer,channel:t.channel,sourceId:memberReply?.id||0}];
  memberSentText=el('member-input').value;memberBusy=true;memberBudget();
  AndroidBridge.sendMemberMessage(JSON.stringify(targets),memberSentText);
}
window.onMemberSendResult=ok=>{
  memberBusy=false;memberResult=ok?'queued':'error';
  if(ok){if(el('member-input').value===memberSentText)el('member-input').value='';memberReply=null;}
  renderMemberChat();
};
function memberUseTemplate(){const item=messageTemplates()[Number(el('member-template').value)];if(item)el('member-input').value=item.text;el('member-template').selectedIndex=0;memberBudget();}
function renderMemberHistory(){
  const host=el('member-history'),entries=[];
  for(const device of memberRadios())for(const c of device.channels){
    for(const msg of device.chats?.[c.index]||[])if(!msg.reaction)
      entries.push({msg,device,channel:c,label:c.name});
  }
  const owners=new Map();
  for(const c of contactPeople())for(const a of c.nodes||[])if(!owners.has(a.radio+':'+a.id))owners.set(a.radio+':'+a.id,c);
  for(const d of memberRadios())for(const [peer,messages] of Object.entries(d.directs||{})){
    const person=owners.get(d.radio+':'+peer);
    for(const msg of messages)if(!msg.reaction)entries.push({msg,device:d,peer,person,label:person?contactName(person):peer});
  }
  entries.sort((a,b)=>compareMessageTime(a.msg,b.msg));
  const previousTop=host.scrollTop;
  const followLatest=host.scrollHeight-host.clientHeight-previousTop<=4;
  const viewportTop=host.getBoundingClientRect().top;
  const anchor=followLatest?null:[...host.children].find(row=>row.getBoundingClientRect().bottom>viewportTop);
  const anchorOffset=anchor?anchor.getBoundingClientRect().top-viewportTop:0;

  host.replaceChildren();
  if(!entries.length)host.append(node('p','help',mtWord('noMessages')));
  for(const entry of entries){
    const {msg,device:d,channel,person,label}=entry;
    const identity=messageIdentity(d.key,msg,d);
    const row=node('article',msg.own?'member-own':''),content=node('div','member-message');
    content.append(node('strong','',msg.own?mtWord('you'):identity.name),node('small','', ' · '+msg.time),node('p','',identity.text));
    appendLocationPreview(content.lastElementChild,identity);
    row.dataset.messageId=String(msg.id);
    row.append(content);
    if(!msg.own){
      const b=node('button','',mtWord('reply'));
      b.onclick=()=>{
        clearMemberDestination();memberKey=memberDeviceId(d);
        el('member-use-channel').checked=false;el('member-use-dm').checked=false;
        memberReply={id:msg.id,label,channel:channel?channel.index:-1,peer:entry.peer||''};
        if(channel&&d.radio==='mc'){
          const prefix='@['+identity.name.replace(/[\[\]]/g,'')+'] ';
          if(!el('member-input').value.startsWith(prefix))el('member-input').value=prefix+el('member-input').value;
        }
        memberResult='';renderMemberChat();el('member-input').focus();
      };
      row.append(b);
    }
    row.append(node('small','member-route',d.radio.toUpperCase()+' · '+(d.ownName||d.ownId)+' · '+(channel?label:mtWord('direct')+' · '+label)));
    host.append(row);
  }
  if(followLatest)host.scrollTop=host.scrollHeight;
  else{
    const retained=anchor&&[...host.children].find(row=>row.dataset.messageId===anchor.dataset.messageId);
    host.scrollTop=retained?host.scrollTop+retained.getBoundingClientRect().top-viewportTop-anchorOffset:previousTop;
  }
}
function renderMemberChat(){
  if(!memberConfig().enabled)return;
  const radios=memberRadios();
  if(!memberRadio()){
    memberKey=radios.length?memberDeviceId(radios[0]):'';
    clearMemberDestination();
  }
  const d=memberRadio(),picker=el('member-radio');
  mapChoices(picker,radios.length?radios.map(r=>[memberDeviceId(r),(r.ownName||r.ownId)+' · '+r.radio.toUpperCase()]):[['',mtWord('noDevices')]]);
  picker.value=memberKey;picker.disabled=!radios.length;
  const host=el('member-channel-list');host.replaceChildren();host.hidden=!el('member-use-channel').checked;
  const channels=d?memberAllowed(d):[];
  if(!channels.length)host.textContent=mtWord('noChannels');
  for(const c of channels)memberChoice(host,'member-channel',c.name,memberChannel===channelKey(c),()=>{
    memberChannel=channelKey(c);memberChangeRoutes();
  });
  const pick=el('member-pick');pick.hidden=!el('member-use-dm').checked;
  const person=memberPeople().find(c=>c.id===memberContact);
  pick.textContent=[mtWord('recipients'),person&&contactName(person),memberPeer].filter(Boolean).join(' · ');
  el('member-reply-info').textContent=memberReply?mtWord('replying')+' '+memberReply.label:'';
  el('member-result').textContent=memberResult?mtWord(memberResult):'';
  const templates=messageTemplates(),menu=el('member-template');
  mapChoices(menu,[['',mtWord('templates')],...templates.map((t,i)=>[String(i),t.title])]);
  menu.setAttribute('aria-label',mtWord('templates'));menu.disabled=!templates.length;
  renderMemberHistory();memberBudget();
}
