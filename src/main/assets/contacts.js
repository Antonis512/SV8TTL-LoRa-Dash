'use strict';
let contactItems=[],contactRadios=[],contactOpen='',contactEdit=null,contactSaveDone=null,contactSaving=false,contactPrefsLoaded=false;
let contactPickerRefresh=null;
const contactActionReceivers=new Map();
let contactView={query:'',favorites:false,categories:[],sort:'name',network:'all'},contactNodeView={query:'',network:'all',role:'all',favorite:false,heard:'all',device:'all',kind:'all',rf:'all',messageable:false,sort:'name'},contactExpandedNode='';
const contactClone=x=>JSON.parse(JSON.stringify(x));
const contactId=()=> 'person-'+crypto.randomUUID();
const contactById=id=>contactItems.find(x=>x.id===id);
const contactPeople=()=>contactItems.filter(x=>x.kind!=='category'&&!x.deletedAt);
window.contactNamesForNode=(radio,id)=>contactPeople().filter(c=>(c.nodes||[]).some(n=>n.radio===radio&&String(n.id).toLowerCase()===String(id).toLowerCase())).map(contactName);
const contactCategories=()=>contactItems.filter(x=>x.kind==='category'&&!x.deletedAt);
const contactName=c=>c.displayName||[c.name,c.surname].filter(Boolean).join(' ')||c.alias||'Unnamed';
const contactNorm=x=>String(x??'').normalize('NFD').replace(/\p{M}/gu,'').toLocaleLowerCase().replace(/ς/g,'σ');
const contactPhone=x=>String(x??'').replace(/[^0-9+]/g,'');
const contactNodeId=n=>n.radio+':'+n.id;
const contactNodeIdentity=(radio,id)=>radio+':'+String(id||'').toLowerCase();
const contactReceiverId=device=>device.radio+':'+String(device.ownId||device.key||'').toLowerCase();
const contactNow=()=>Date.now();
function contactButton(host,label,fn){const b=node('button','',label);b.type='button';b.onclick=fn;host.appendChild(b);return b;}
function contactSection(host,title){const box=node('section','contact-section');box.appendChild(node('h3','',title));host.appendChild(box);return box;}
function contactCheck(host,label,checked,fn){const row=node('label','contact-check'),box=node('input');box.type='checkbox';box.checked=!!checked;box.onchange=()=>fn(box.checked);row.append(box,document.createTextNode(label));host.appendChild(row);return box;}
function contactAvatar(c){if(c.photo?.startsWith('data:image/jpeg;base64,')){const img=node('img','contact-avatar');img.src=c.photo;img.alt='Profile photo of '+contactName(c);return img;}return node('span','contact-avatar',contactName(c).split(/\s+/).slice(0,2).map(s=>s[0]).join('').toUpperCase());}
function contactNotice(text){el('contacts-status').textContent=tr(text);}
function contactDialog(title){contactPickerRefresh=null;const dialog=el('contacts-dialog'),body=el('contacts-dialog-content');dialog.dataset.view='';clear(body);body.appendChild(node('h2','',title));if(!dialog.open)dialog.showModal();dialog.scrollTop=0;return body;}
function closeContactDialog(){if(contactEdit&&!confirm(tr('Discard unsaved contact changes?')))return;contactEdit=null;el('contacts-dialog').close();}
el('contacts-dialog').addEventListener('cancel',e=>{e.preventDefault();closeContactDialog();});
function contactsSave(items,done=()=>{}){
  if(contactSaving)return;contactSaving=true;contactSaveDone=done;contactNotice(tr('Saving contacts…'));
  AndroidBridge.saveContacts(JSON.stringify(items.map(c=>({...c,edited:contactNow()}))));
}
window.onContactsSaved=()=>{contactSaving=false;const done=contactSaveDone;contactSaveDone=null;contactNotice(tr('Saved'));done?.();};
window.onContactError=message=>{contactSaving=false;contactSaveDone=null;contactNotice(message);const host=el('contacts-dialog-content');if(el('contacts-dialog').open){host.querySelector('.contact-error')?.remove();host.appendChild(node('p','contact-error',message));}};
window.onContactNotice=contactNotice;
window.resetContactPreferences=()=>{contactPrefsLoaded=false;AndroidBridge.loadContacts();};
window.onContactsData=data=>{
  contactItems=Array.isArray(data.items)?data.items:[];
  if(!contactPrefsLoaded){contactView={...contactView,...data.view};contactPrefsLoaded=true;el('contacts-search').value=contactView.query;el('contacts-favorites').checked=contactView.favorites;el('contacts-sort').value=contactView.sort;el('contacts-network').value=contactView.network;}
  window.renderSilenceTargets?.();
  renderContacts();window.memberContactsChanged?.();window.mapContactsChanged?.();
  if(activeTab==='nodes')renderNodes();
  if(el('contacts-dialog').open)contactPickerRefresh?.();
};
window.onContactNodes=radios=>{contactRadios=radios||[];installContactRadios();window.renderSilenceTargets?.();if(activeTab==='contacts')renderContacts();if(activeTab==='nodes')contactKeepScroll(el('tab-nodes'),renderNodes);};
function installContactRadios(){if(!state)return;contactRadios.forEach((r,i)=>{state['contact-radio-'+i]={...r,channels:r.channels||[],chats:{},directs:{}};});}
function contactViewChanged(){contactView={...contactView,query:el('contacts-search').value,favorites:el('contacts-favorites').checked,sort:el('contacts-sort').value,network:el('contacts-network').value};AndroidBridge.contactsView(JSON.stringify(contactView));renderContacts();}
function contactNavOverflow(){const nav=el('bottom-nav');nav.classList.toggle('more-left',nav.scrollLeft>2);nav.classList.toggle('more-right',nav.scrollLeft+nav.clientWidth<nav.scrollWidth-2);}
el('bottom-nav').addEventListener('scroll',contactNavOverflow);window.addEventListener('resize',contactNavOverflow);requestAnimationFrame(contactNavOverflow);
function contactFields(c){
  const fields=[['Name',[c.name,c.surname].filter(Boolean).join(' ')],['Display name',c.displayName],['Alias',c.alias],['Organization',c.organization],['Role',c.role]];
  for(const [key,label] of [['callsigns',tr('Callsign')],['phones',tr('Phone')],['emails',tr('Email')],['addresses',tr('Address')],['custom',tr('Custom field')]])for(const v of c[key]||[])fields.push([v.label||label,v.value]);
  for(const n of c.notes||[])fields.push(['Note',n.text]);
  for(const id of c.categories||[])fields.push(['Group',contactById(id)?.title]);
  for(const n of c.nodes||[]){fields.push(['Node',[n.name,n.id,n.label,n.assignmentRole,...contactNodeRows(n).map(r=>r.entry.name)].filter(Boolean).join(' ')]);}
  return fields.filter(f=>f[1]);
}
function contactNear(a,b){
  if(a.length<4||Math.abs(a.length-b.length)>1)return false;
  let i=0,j=0,errors=0;while(i<a.length&&j<b.length){if(a[i]===b[j]){i++;j++;continue;}if(++errors>1)return false;if(a.length>=b.length)i++;if(b.length>=a.length)j++;}return errors+(i<a.length||j<b.length?1:0)<=1;
}
function contactMatch(c,query){
  const tokens=contactNorm(query).trim().split(/\s+/).filter(Boolean),fields=contactFields(c);if(!tokens.length)return {score:0,reason:''};
  let score=0,reason='';
  for(const token of tokens){let best=0,why='';for(const [label,value] of fields){const text=contactNorm(value);let rank=text===token?10:text.includes(token)?6:0;
      if(!rank&&/\d/.test(token)&&contactPhone(value).includes(contactPhone(token))&&contactPhone(token).length>=3)rank=5;
      if(!rank&&text.split(/[^\p{L}\p{N}]+/u).some(w=>contactNear(token,w)))rank=2;
      if(rank>best){best=rank;const at=text.indexOf(token),start=Math.max(0,at-30);why=label+': '+(start?'…':'')+String(value).slice(start,start+150);}}
    if(!best)return null;score+=best;if(!reason)reason=why;
  }return {score,reason};
}
function nodeReceiverRows(radio,id){
  const rows=new Map(),identity=contactNodeIdentity(radio,id);
  const add=(device,slot)=>{
    if(device.radio!==radio)return;
    for(const entry of device.nodes||[])if(contactNodeIdentity(radio,entry.id)===identity){
      const key=contactReceiverId(device),old=rows.get(key);
      if(!old||Number(entry.lastHeard||0)>Number(old.entry.lastHeard||0)||(Number(entry.lastHeard||0)===Number(old.entry.lastHeard||0)&&!entry.savedOnly&&old.entry.savedOnly))rows.set(key,{slot,device,entry});
    }
  };
  contactRadios.forEach((device,i)=>add(device,'contact-radio-'+i));
  // Include current selected receivers while the saved-device snapshot is loading.
  for(const slot of ['mc','mt','left','right'])if(state?.[slot]?.ownId)add({...state[slot],radio:radioType(slot)},slot);
  return [...rows.values()];
}
function nodeReceivingInfo(radio,id){
  const panel=node('div','node-receivers'),rows=nodeReceiverRows(radio,id);
  panel.appendChild(node('strong','',tr('Receiving devices')));
  if(!rows.length)panel.appendChild(node('p','help',tr('No receiving-device records available.')));
  for(const {device,entry} of rows){
    const line=node('p','help');
    line.textContent=(device.name||device.ownName||device.ownId||tr('Unnamed device'))+' · '+(device.ownId||device.key||'')+' · '+(device.connected?tr('Connected'):tr('Offline'))+
      (entry.lastHeard?tr(' · Last heard ')+new Date(entry.lastHeard*1000).toLocaleString():tr(' · Last heard unknown'))+
      (entry.favorite?tr(' · Favorite on this device'):'');
    panel.appendChild(line);
  }
  return panel;
}
function contactNodeRows(assignment){
  const rows=nodeReceiverRows(assignment.radio,assignment.id).map(row=>({...row,assignment}));
  if(!rows.length)rows.push({slot:assignment.radio,device:{radio:assignment.radio,name:'No receiving device'},entry:{id:assignment.id,name:assignment.name||assignment.id,role:assignment.role||'Unknown',savedOnly:true},assignment});
  return rows;
}
function contactHeard(c){return Math.max(0,...(c.nodes||[]).flatMap(n=>contactNodeRows(n).map(r=>Number(r.entry.lastHeard)||0)));}
function contactSummary(c){const radios=[...new Set((c.nodes||[]).map(n=>n.radio.toUpperCase()))];const last=contactHeard(c);return (c.callsigns||[]).map(x=>x.value).join(' · ')+(c.nodes?.length?' · '+c.nodes.length+' nodes ('+radios.join('/')+')':'')+(last?' · Node last heard '+new Date(last*1000).toLocaleString():'');}
function contactKeepScroll(host,draw){
  const top=host.scrollTop,left=host.scrollLeft;
  try{return draw();}finally{host.scrollTop=top;host.scrollLeft=left;}
}
function renderContacts(){return contactKeepScroll(el('tab-contacts'),renderContactsContent);}
function renderContactsContent(){
  if(!el('contacts-list'))return;installContactRadios();
  el('contacts-list-view').hidden=!!contactOpen;el('contact-detail').hidden=!contactOpen;
  if(contactOpen){renderContactDetail();return;}
  const categories=el('contacts-categories');clear(categories);
  const groups=contactCategories();
  const selectedGroup=contactView.categories.find(id=>id==='uncategorized'||groups.some(g=>g.id===id))||'';
  contactView.categories=selectedGroup?[selectedGroup]:[];
  contactSelect(categories,tr('Group'),selectedGroup,[['',tr('All groups')],['uncategorized',tr('No group')],...groups.map(g=>[g.id,g.title])],value=>{contactView.categories=value?[value]:[];contactViewChanged();});
  const matching=contactPeople().map(c=>({c,match:contactMatch(c,contactView.query)})).filter(x=>x.match);
  const shown=matching.filter(({c})=>(!contactView.favorites||c.favorite)&&(!contactView.categories.length||contactView.categories.some(id=>id==='uncategorized'?!(c.categories||[]).some(id=>contactById(id)&&!contactById(id).deletedAt):(c.categories||[]).includes(id)))&&(contactView.network==='all'||(contactView.network==='none'?!(c.nodes||[]).length:(c.nodes||[]).some(n=>n.radio===contactView.network))));
  shown.sort((a,b)=>b.match.score-a.match.score||(contactView.sort==='edited'?b.c.edited-a.c.edited:contactView.sort==='heard'?contactHeard(b.c)-contactHeard(a.c):0)||(contactView.sort==='surname'?(a.c.surname||contactName(a.c)):(contactName(a.c))).localeCompare(contactView.sort==='surname'?(b.c.surname||contactName(b.c)):contactName(b.c),undefined,{sensitivity:'base'}));
  el('contacts-count').textContent=shown.length+' contacts'+(matching.length>shown.length?' · '+(matching.length-shown.length)+' matching contacts hidden by filters':'');
  const list=el('contacts-list');clear(list);if(!shown.length)list.appendChild(node('p','help',tr('No contacts found. Add a contact or clear your filters.')));
  for(const {c,match} of shown){const row=node('div','contact-row');row.appendChild(contactAvatar(c));const main=node('button','contact-person');main.type='button';main.append(node('strong','',contactName(c)),node('small','',contactSummary(c)),contactGroupList(c));if(match.reason)main.appendChild(node('small','contact-match',match.reason));main.onclick=()=>openContact(c.id);row.appendChild(main);const star=contactButton(row,c.favorite?'★':'☆',()=>contactsSave([{...c,favorite:!c.favorite}]));star.setAttribute('aria-label',(c.favorite?tr('Unfavorite '):tr('Favorite '))+contactName(c));list.appendChild(row);}
}
function openContact(id){contactOpen=id;contactExpandedNode='';renderContacts();}
function contactDetailBack(){if(!contactOpen)return false;contactOpen='';renderContacts();return true;}
function renderContactDetail(){return contactKeepScroll(el('tab-contacts'),renderContactDetailContent);}
function renderContactDetailContent(){
  const c=contactById(contactOpen),host=el('contact-detail');clear(host);if(!c||c.deletedAt){contactOpen='';renderContacts();return;}
  const top=node('div','contact-toolbar');contactButton(top,tr('← Contacts'),contactDetailBack);contactButton(top,c.favorite?tr('★ Favorite'):tr('☆ Favorite'),()=>contactsSave([{...c,favorite:!c.favorite}]));host.appendChild(top);
  const heading=node('div','contact-row');heading.append(contactAvatar(c),node('h2','',contactName(c)));host.appendChild(heading);
  const actions=node('div','contact-toolbar');host.appendChild(actions);
  contactButton(actions,tr('Edit'),()=>editContact(c));contactButton(actions,tr('Assign Nodes'),()=>{editContact(c);chooseContactNodes();});contactButton(actions,c.androidLink?tr('Refresh from Android'):tr('Link Android Contact'),()=>AndroidBridge.pickAndroidContact(c.id));
  contactButton(actions,tr('Share'),()=>shareContactFields(c));contactButton(actions,tr('Export'),()=>exportContactItems([c,...contactCategories().filter(cat=>(c.categories||[]).includes(cat.id))]));contactButton(actions,tr('Merge'),()=>chooseContactMerge(c));contactButton(actions,tr('Delete'),()=>deleteContact(c));
  if(c.androidLink){host.appendChild(node('p','help',tr('Linked Android contact · last refreshed ')+new Date(c.androidLink.refreshed).toLocaleString()+tr('. Refresh asks you to select the contact again and review fields.')));contactButton(host,tr('Unlink Android Contact'),()=>contactsSave([{...c,androidLink:null}]));}
  const info=contactSection(host,tr('Profile'));for(const [label,value] of [['Name',c.name],['Surname',c.surname],['Alias',c.alias],['Organization / club',c.organization],['Role',c.role]])if(value)info.appendChild(node('p','contact-value',label+': '+value));
  const groups=contactSection(host,tr('Groups'));groups.appendChild(contactGroupList(c));
  for(const [key,title] of [['callsigns',tr('Callsigns')],['phones',tr('Phone numbers')],['emails',tr('Email')],['addresses',tr('Addresses')],['custom',tr('Custom fields')]]){
    if(!c[key]?.length)continue;const section=contactSection(host,title);for(const value of c[key]){const line=node('div','contact-toolbar');line.appendChild(node('span','contact-value',(value.label?value.label+': ':'')+value.value));if(key==='phones'){contactButton(line,tr('Call'),()=>AndroidBridge.contactCommunicate('call',value.value));contactButton(line,tr('SMS'),()=>AndroidBridge.contactCommunicate('sms',value.value));}if(key==='emails')contactButton(line,tr('Email'),()=>AndroidBridge.contactCommunicate('email',value.value));section.appendChild(line);}
  }
  if(c.notes?.length){const section=contactSection(host,tr('Notes'));for(const note of [...c.notes].sort((a,b)=>b.date-a.date)){section.append(node('small','',new Date(note.date).toLocaleString()),node('p','contact-value',note.text));}}
  if(c.logs?.length){const section=contactSection(host,tr('Related Log entries'));for(const id of c.logs){const item=logById(id);contactButton(section,item?.title||tr('Unavailable Log entry'),()=>{if(item)openLogDetail(id);});}}
  renderContactNodes(host,c);
}
function contactSelect(host,label,value,options,changed){const field=node('label','contact-field',label),select=node('select','form-input');select.setAttribute('aria-label',label);for(const [key,text] of options)select.appendChild(new Option(text,key));select.value=options.some(o=>o[0]===value)?value:options[0][0];select.onchange=()=>changed(select.value);field.appendChild(select);host.appendChild(field);return select;}
function renderContactNodes(host,c){
  const section=contactSection(host,tr('Nodes')),filterBox=node('details'),filters=node('div','contact-toolbar');
  filterBox.appendChild(node('summary','',tr('Filter and sort nodes')));filterBox.appendChild(filters);filterBox.open=!!contactNodeView.filtersOpen;filterBox.ontoggle=()=>{contactNodeView.filtersOpen=filterBox.open;};
  const update=(key,value)=>{contactNodeView[key]=value;renderContactDetail();};
  const search=node('input','form-input');search.type='search';search.placeholder=tr('Search assigned nodes');search.value=contactNodeView.query;search.onchange=()=>update('query',search.value);section.append(search,filterBox);
  const rows=(c.nodes||[]).flatMap(contactNodeRows);
  const roles=[...new Map(rows.filter(r=>r.entry.role).map(r=>[r.assignment.radio+':'+r.entry.role,[r.assignment.radio+':'+r.entry.role,r.assignment.radio.toUpperCase()+' · '+displayRole(r.slot,r.entry.role)]])).values()];
  if(!roles.some(r=>r[0]===contactNodeView.role))contactNodeView.role='all';
  if(!rows.some(r=>r.slot===contactNodeView.device))contactNodeView.device='all';
  contactSelect(filters,tr('Network'),contactNodeView.network,[['all',tr('All')],['mc','MC'],['mt','MT']],v=>update('network',v));
  contactSelect(filters,tr('Role'),contactNodeView.role,[['all',tr('All roles')],...roles],v=>update('role',v));
  contactSelect(filters,tr('Type'),contactNodeView.kind,[['all',tr('All')],['infrastructure',tr('Infrastructure')],['users',tr('Users')]],v=>update('kind',v));
  contactSelect(filters,tr('Sort nodes'),contactNodeView.sort,[['name',tr('Name')],['hops-asc',tr('Hop count: lowest first')],['hops-desc',tr('Hop count: highest first')],['heard-desc','Last heard: newest first'],['heard-asc','Last heard: oldest first']],v=>update('sort',v));
  const rfValues=[...new Set(rows.map(r=>heardProfile(r.assignment.radio,r.entry.heardSettings)))];
  if(!rfValues.includes(contactNodeView.rf))contactNodeView.rf='all';
  contactSelect(filters,tr('Heard settings'),contactNodeView.rf,[['all',tr('All settings')],...rfValues.map(v=>[v,v||'Unknown'])],v=>update('rf',v));
  contactSelect(filters,tr('Receiving device'),contactNodeView.device,[['all',tr('All devices')],...[...new Map(rows.map(r=>[r.slot,[r.slot,r.device.name||r.device.ownId||r.slot]])).values()]],v=>update('device',v));
  contactSelect(filters,tr('Last heard'),contactNodeView.heard,[['all',tr('Any time')],['day',tr('Last 24 hours')],['month',tr('Last 30 days')],['unknown',tr('Unknown')]],v=>update('heard',v));
  contactCheck(filters,tr('Favorite nodes'),contactNodeView.favorite,v=>update('favorite',v));
  contactCheck(filters,tr('Hide unmessageable nodes'),contactNodeView.messageable,v=>update('messageable',v));
  rows.sort((a,b)=>{
    const key=contactNodeView.sort;if(key!=='name'){const heard=key.startsWith('heard'),av=heard?Number(a.entry.lastHeard)||0:Number(a.entry.hops??-1),bv=heard?Number(b.entry.lastHeard)||0:Number(b.entry.hops??-1),ak=heard?av>0:av>=0,bk=heard?bv>0:bv>=0;if(ak!==bk)return ak?-1:1;if(av!==bv)return(av-bv)*(key.endsWith('desc')?-1:1);}
    return (a.entry.name||a.entry.id).localeCompare(b.entry.name||b.entry.id);
  });
  const grouped=new Map();for(const row of rows){const {entry,assignment,device,slot}=row,age=Date.now()/1000-(entry.lastHeard||0);
    if(contactNodeView.network!=='all'&&assignment.radio!==contactNodeView.network||contactNodeView.role!=='all'&&assignment.radio+':'+entry.role!==contactNodeView.role||contactNodeView.device!=='all'&&slot!==contactNodeView.device||contactNodeView.favorite&&!entry.favorite||contactNodeView.heard==='day'&&age>86400||contactNodeView.heard==='month'&&age>2592000||contactNodeView.heard==='unknown'&&entry.lastHeard)continue;
    if(contactNodeView.kind==='infrastructure'&&!nodeInfrastructure(entry)||contactNodeView.kind==='users'&&nodeInfrastructure(entry)||contactNodeView.rf!=='all'&&heardProfile(assignment.radio,entry.heardSettings)!==contactNodeView.rf||contactNodeView.messageable&&(entry.savedOnly||!state?.[slot]||!nodeMessageable(slot,entry)))continue;
    if(!contactNorm([entry.name,entry.id,assignment.label,assignment.assignmentRole].join(' ')).includes(contactNorm(contactNodeView.query)))continue;
    const key=contactNodeIdentity(assignment.radio,entry.id);
    if(!grouped.has(key))grouped.set(key,[]);grouped.get(key).push(row);
  }
  let shown=0;for(const [key,observations] of grouped){
    const row=[...observations].sort((a,b)=>Number(b.entry.lastHeard||0)-Number(a.entry.lastHeard||0))[0];
    const {entry,assignment,slot}=row;
    shown++;const box=node('div','contact-node');
    const b=contactButton(box,(observations.some(r=>r.entry.favorite)?'★ ':'')+(entry.name||entry.id)+' · '+assignment.radio.toUpperCase()+(assignment.label?' · '+assignment.label:''),()=>{contactExpandedNode=contactExpandedNode===key?'':key;renderContactDetail();});b.setAttribute('aria-expanded',String(contactExpandedNode===key));
    box.appendChild(node('p','help',displayRole(slot,entry.role||'Unknown')+' · '+(assignment.assignmentRole||tr('Associated'))+(entry.lastHeard?tr(' · Last heard ')+new Date(entry.lastHeard*1000).toLocaleString():'')));
    box.appendChild(node('p','help',tr('Receiving devices: ')+[...new Set(observations.map(r=>r.device.name||r.device.ownId||'No receiving device'))].join(', ')));
    if(assignment.id.startsWith('heard-name:'))box.appendChild(node('p','help',tr('Unverified association: this sender is known only by its message name.')));
    if(contactExpandedNode===key){
      const receivers=[...new Map(observations.map(r=>[contactReceiverId(r.device),r])).values()];
      const actions=node('div','contact-node-actions');
      const draw=receiver=>{clear(actions);if(state?.[receiver.slot])actions.appendChild(nodeDetails(receiver.slot,receiver.entry));};
      const preferred=receivers.find(r=>contactReceiverId(r.device)===contactActionReceivers.get(key))||receivers.find(r=>r.device.connected&&!r.entry.savedOnly)||row;
      if(receivers.length>1)contactSelect(box,tr('Use device for node actions'),preferred.slot,receivers.map(r=>[r.slot,(r.device.name||r.device.ownId)+' · '+(r.device.connected?'Connected':'Offline')]),value=>{const receiver=receivers.find(r=>r.slot===value);contactActionReceivers.set(key,contactReceiverId(receiver.device));draw(receiver);});
      draw(preferred);box.appendChild(actions);
    }
    section.appendChild(box);
  }if(!shown)section.appendChild(node('p','help',tr('No assigned nodes match these filters.')));
}

function newContact(){return {id:contactId(),kind:'contact',created:contactNow(),edited:contactNow(),name:'',surname:'',alias:'',displayName:'',organization:'',role:'',favorite:false,photo:'',callsigns:[],phones:[],emails:[],addresses:[],custom:[],notes:[],nodes:[],categories:[],logs:[]};}
function editContact(c=null){contactEdit={draft:c?{...newContact(),...contactClone(c)}:newContact(),token:contactId(),mergeIds:[]};renderContactEditor();}
function contactInput(host,label,value,fn,type='text'){const wrap=node('label','contact-field',label),input=node(type==='textarea'?'textarea':'input','form-input');if(type!=='textarea')input.type=type;input.value=value||'';input.setAttribute('aria-label',label);input.oninput=()=>fn(input.value);wrap.appendChild(input);host.appendChild(wrap);return input;}
function renderContactEditor(){
  const dialog=el('contacts-dialog'),token=contactEdit?.token;
  const sameEditor=dialog.open&&dialog.dataset.editorToken===token&&dialog.dataset.view==='editor';
  if(sameEditor)contactKeepScroll(dialog,renderContactEditorContent);else renderContactEditorContent();
  dialog.dataset.editorToken=token||'';dialog.dataset.view='editor';
}
function renderContactEditorContent(){
  if(!contactEdit)return;const c=contactEdit.draft,body=contactDialog(contactById(c.id)?tr('Edit Contact'):tr('New Contact'));
  const fields=node('div','contact-fields');body.appendChild(fields);
  for(const [key,label] of [['name',tr('Name')],['surname',tr('Surname')],['alias',tr('Alias')],['displayName',tr('Preferred display name')],['organization',tr('Organization / club')],['role',tr('Role')]])contactInput(fields,label,c[key],v=>c[key]=v).maxLength=500;
  contactCheck(body,tr('Favorite'),c.favorite,v=>c.favorite=v);
  const photo=node('div','contact-toolbar');photo.appendChild(contactAvatar(c));contactButton(photo,tr('Choose photo'),()=>AndroidBridge.pickContactPhoto(contactEdit.token));contactButton(photo,tr('Remove photo'),()=>{c.photo='';renderContactEditor();});body.appendChild(photo);
  for(const [key,title,label] of [['callsigns','Callsigns','Radio Amateur'],['phones','Phone numbers','Mobile'],['emails','Email addresses','Personal'],['addresses','Addresses','Home'],['custom','Custom fields','']])renderContactRepeats(body,c,key,title,label);
  const cats=contactSection(body,tr('Groups')),membership=node('div','contact-group-picker'),selected=node('div','contact-group-list');cats.appendChild(membership);
  contactSelect(membership,tr('Add to group'),'',[['',tr('Choose a group…')],...contactCategories().filter(g=>!c.categories.includes(g.id)).map(g=>[g.id,g.title])],id=>{if(id&&!c.categories.includes(id)){c.categories.push(id);renderContactEditor();}});
  membership.appendChild(selected);for(const group of contactCategories().filter(g=>c.categories.includes(g.id))){const row=node('div','contact-group-row');row.appendChild(node('span','',group.title));const remove=contactButton(row,'×',()=>{c.categories=c.categories.filter(id=>id!==group.id);renderContactEditor();});remove.setAttribute('aria-label',tr('Remove from ')+group.title);selected.appendChild(row);}
  if(!selected.childElementCount)selected.appendChild(node('span','help',tr('No groups selected.')));
  let category='';contactInput(cats,tr('New group'),'',v=>category=v);contactButton(cats,tr('Add group'),()=>{if(category.trim()){const cat={id:contactId(),kind:'category',title:category.trim(),edited:contactNow()};contactsSave([cat],()=>{c.categories.push(cat.id);renderContactEditor();});}});
  const notes=contactSection(body,tr('Dated notes'));for(const note of c.notes){const row=node('div','contact-repeat');row.appendChild(node('small','',new Date(note.date).toLocaleString()));contactInput(row,tr('Note'),note.text,v=>note.text=v,'textarea');contactButton(row,tr('Remove note'),()=>{c.notes=c.notes.filter(n=>n!==note);renderContactEditor();});notes.appendChild(row);}contactButton(notes,tr('Add note'),()=>{c.notes.push({id:contactId(),date:contactNow(),text:''});renderContactEditor();});
  const logs=contactSection(body,tr('Related Log entries'));for(const entry of logItems.filter(x=>x.kind==='note'))contactCheck(logs,entry.title,c.logs.includes(entry.id),yes=>{c.logs=yes?[...c.logs,entry.id]:c.logs.filter(x=>x!==entry.id);});if(!logItems.some(x=>x.kind==='note'))logs.appendChild(node('p','help',tr('No Log notes yet.')));
  const nodes=contactSection(body,tr('Assigned nodes'));for(const a of c.nodes){const row=node('div','contact-node');row.appendChild(node('strong','',(a.name||a.id)+' · '+a.radio.toUpperCase()));contactInput(row,tr('Node description'),a.label,v=>a.label=v);contactSelect(row,tr('Association'),a.assignmentRole||'Owner',['Owner','Operator','Maintainer','Shared','Associated'].map(x=>[x,x]),v=>a.assignmentRole=v);contactButton(row,tr('Unassign Node'),()=>{c.nodes=c.nodes.filter(n=>n!==a);renderContactEditor();});nodes.appendChild(row);}contactButton(nodes,tr('Assign Nodes'),chooseContactNodes);
  if(contactEdit.mergeIds.length)body.appendChild(node('p','help',tr('Saving merges these profiles. Conflicting information is retained in custom fields. The original profiles remain in Deleted contacts for undo.')));
  const actions=node('div','contact-toolbar');body.appendChild(actions);contactButton(actions,tr('Save Contact'),()=>saveContactDraft());contactButton(actions,tr('Cancel'),closeContactDialog);
}
function renderContactRepeats(body,c,key,title,defaultLabel){
  const section=contactSection(body,tr(title));for(const item of c[key]){
    const row=node('div','contact-repeat');contactInput(row,title+' label',item.label,v=>item.label=v);if(key==='custom')contactSelect(row,tr('Field type'),item.type||'text',[['text',tr('Text')],['number',tr('Number')],['date',tr('Date')],['url',tr('Link')]],v=>{item.type=v;renderContactEditor();});
    contactInput(row,title+' value',item.value,v=>item.value=v,key==='addresses'?'textarea':key==='custom'?(item.type||'text'):key==='phones'?'tel':key==='emails'?'email':'text');
    contactButton(row,tr('Remove'),()=>{c[key]=c[key].filter(x=>x!==item);renderContactEditor();});section.appendChild(row);
  }contactButton(section,tr('Add '+title.toLowerCase()),()=>{c[key].push({label:defaultLabel,value:'',...(key==='custom'?{type:'text'}:{})});renderContactEditor();});
}
window.onContactPhoto=result=>{if(contactEdit&&contactEdit.token===result.token){contactEdit.draft.photo=result.photo;renderContactEditor();}};
function contactDuplicate(a,b){
  if(contactNorm(contactName(a))===contactNorm(contactName(b)))return 'same name';
  for(const key of ['phones','emails','callsigns'])if((a[key]||[]).some(x=>(b[key]||[]).some(y=>(key==='phones'?contactPhone(x.value):contactNorm(x.value))===(key==='phones'?contactPhone(y.value):contactNorm(y.value))&&x.value)))return 'matching '+key;
  return '';
}
function saveContactDraft(skipDuplicates=false){
  const c=contactEdit.draft;if(![c.name,c.surname,c.alias,c.displayName].some(v=>v?.trim())){onContactError('Enter a name, alias or display name');return;}
  for(const key of ['callsigns','phones','emails','addresses','custom'])c[key]=c[key].filter(x=>String(x.value).trim());c.notes=c.notes.filter(x=>x.text.trim());
  const duplicates=contactPeople().filter(p=>p.id!==c.id&&!contactEdit.mergeIds.includes(p.id)&&contactDuplicate(c,p));
  if(!skipDuplicates&&duplicates.length){const body=contactDialog(tr('Possible duplicate'));body.appendChild(node('p','help',tr('Review a matching contact, or keep this as a separate person.')));for(const p of duplicates)contactButton(body,contactName(p)+' · '+contactDuplicate(c,p),()=>mergeIntoDraft(p));contactButton(body,tr('Save separately'),()=>saveContactDraft(true));contactButton(body,tr('Back to edit'),renderContactEditor);return;}
  const changes=[c,...contactEdit.mergeIds.map(id=>({...contactById(id),deletedAt:contactNow(),mergedInto:c.id}))];
  contactsSave(changes,()=>{contactEdit=null;el('contacts-dialog').close();openContact(c.id);});
}
function chooseContactNodes(){
  const body=contactDialog(tr('Assign Nodes')),draft=contactEdit.draft,search=node('input','form-input');search.type='search';search.placeholder=tr('Search nodes, names or IDs');body.appendChild(search);
  let radio='all',device='all';const choices=node('div');
  contactSelect(body,tr('Network'),radio,[['all',tr('All networks')],['mc','MC'],['mt','MT']],v=>{radio=v;render();});contactSelect(body,tr('Receiving device'),device,[['all',tr('All devices')],...contactRadios.map((r,i)=>[String(i),r.name||r.ownId||r.radio])],v=>{device=v;render();});body.appendChild(choices);
  const render=()=>{clear(choices);const seen=new Set();contactRadios.forEach((r,i)=>{if(radio!=='all'&&r.radio!==radio||device!=='all'&&device!==String(i))return;for(const n of r.nodes||[]){const key=r.radio+':'+n.id;if(n.id===r.ownId||seen.has(key)||!contactNorm(n.name+' '+n.id).includes(contactNorm(search.value)))continue;seen.add(key);contactCheck(choices,(n.name||n.id)+' · '+r.radio.toUpperCase()+' · '+n.id,draft.nodes.some(a=>contactNodeId(a)===key),yes=>{if(yes)draft.nodes.push({radio:r.radio,id:n.id,name:n.name,role:n.role,label:'',assignmentRole:'Owner'});else draft.nodes=draft.nodes.filter(a=>contactNodeId(a)!==key);});}});if(!seen.size)choices.appendChild(node('p','help',tr('No matching nodes. You can also assign a sender directly from its node actions in Chat.')));};
  search.oninput=render;render();contactButton(body,tr('Done'),renderContactEditor);
}
function assignNodeToContact(radio,entry){
  el('dm-context').hidden=true;AndroidBridge.loadContacts();
  const assignment={radio:radioType(radio),id:entry.id,name:entry.name,role:entry.role,label:'',assignmentRole:'Owner'};
  const body=contactDialog(tr('Assign to Contact')),search=node('input','form-input');search.type='search';search.placeholder=tr('Find a person');body.appendChild(search);const list=node('div');body.appendChild(list);
  const draw=()=>{clear(list);for(const c of contactPeople().filter(c=>contactMatch(c,search.value)))contactButton(list,contactName(c),()=>{editContact(c);if(!contactEdit.draft.nodes.some(n=>contactNodeId(n)===contactNodeId(assignment)))contactEdit.draft.nodes.push(assignment);renderContactEditor();});};search.oninput=draw;contactPickerRefresh=draw;draw();
  contactButton(body,tr('Create Contact'),()=>{editContact();contactEdit.draft.alias=entry.name||'';contactEdit.draft.nodes.push(assignment);renderContactEditor();});contactButton(body,tr('Cancel'),()=>el('contacts-dialog').close());
}
function manageContactCategories(){
  const body=contactDialog(tr('Contact groups'));for(const cat of contactCategories()){
    const row=node('div','contact-toolbar');let title=cat.title;contactInput(row,tr('Group name'),title,v=>title=v);
    contactButton(row,tr('Rename'),()=>{if(title.trim())contactsSave([{...cat,title:title.trim()}],manageContactCategories);});
    contactButton(row,tr('Delete group'),()=>{if(confirm(tr('Delete this group? Its contacts will remain.'))){const changes=[{...cat,deletedAt:contactNow()},...contactPeople().filter(c=>(c.categories||[]).includes(cat.id)).map(c=>({...c,categories:c.categories.filter(id=>id!==cat.id)}))];contactsSave(changes,()=>{contactView.categories=contactView.categories.filter(id=>id!==cat.id);contactViewChanged();manageContactCategories();});}});body.appendChild(row);
  }
  let title='';contactInput(body,tr('New group name'),'',v=>title=v);contactButton(body,tr('Create group'),()=>{if(title.trim())contactsSave([{id:contactId(),kind:'category',title:title.trim(),edited:contactNow()}],manageContactCategories);});contactButton(body,tr('Close'),()=>el('contacts-dialog').close());
}
function deleteContact(c){
  const body=contactDialog(tr('Delete ')+contactName(c)+'?');body.appendChild(node('p','help',tr('This removes the contact and its node assignments from the list. Radio history and Android contacts are unchanged. You can restore it from Deleted contacts.')));
  contactButton(body,tr('Delete Contact'),()=>contactsSave([{...c,deletedAt:contactNow()}],()=>{el('contacts-dialog').close();contactOpen='';renderContacts();contactNotice(tr('Contact deleted. Restore it from Deleted contacts / Undo.'));}));contactButton(body,tr('Cancel'),()=>el('contacts-dialog').close());
}
function contactTrash(){
  const body=contactDialog(tr('Deleted contacts / Undo'));const deleted=contactItems.filter(c=>c.kind!=='category'&&c.deletedAt&&!c.purged);
  if(!deleted.length)body.appendChild(node('p','help',tr('No deleted contacts.')));
  for(const c of deleted){const row=node('div','contact-toolbar');row.appendChild(node('span','',contactName(c)+(c.mergedInto?tr(' · merged profile'):'')));contactButton(row,tr('Restore'),()=>contactsSave([{...c,deletedAt:0,mergedInto:null}],contactTrash));contactButton(row,tr('Delete permanently'),()=>{if(confirm(tr('Permanently erase this contact, including its photo and private notes? This cannot be undone.')))contactsSave([{id:c.id,kind:'contact',alias:'Deleted contact',purged:true,deletedAt:contactNow()}],contactTrash);});body.appendChild(row);}
  contactButton(body,tr('Close'),()=>el('contacts-dialog').close());
}
function mergeIntoDraft(other){
  const c=contactEdit.draft;if(c.id===other.id)return;
  // Keep both values for conflicting scalar fields; the editor lets the user choose the display value.
  for(const key of ['name','surname','alias','displayName','organization','role']){if(!c[key])c[key]=other[key]||'';else if(other[key]&&c[key]!==other[key])c.custom.push({label:'Merged '+key,value:other[key],type:'text'});}
  if(!c.photo)c.photo=other.photo||'';if(!c.androidLink)c.androidLink=other.androidLink||null;
  c.favorite=!!(c.favorite||other.favorite);
  for(const key of ['callsigns','phones','emails','addresses','custom','notes','nodes','categories','logs']){
    const seen=new Set((c[key]||[]).map(x=>key==='nodes'?contactNodeId(x):JSON.stringify(x)));
    for(const value of other[key]||[]){const k=key==='nodes'?contactNodeId(value):JSON.stringify(value);if(!seen.has(k)){c[key].push(contactClone(value));seen.add(k);}}
  }
  if(!contactEdit.mergeIds.includes(other.id))contactEdit.mergeIds.push(other.id);renderContactEditor();
}
function chooseContactMerge(c){
  const body=contactDialog(tr('Merge into ')+contactName(c));body.appendChild(node('p','help',tr('Choose another profile. Review the combined fields before saving.')));const search=node('input','form-input');search.type='search';search.placeholder=tr('Find a contact');body.appendChild(search);const list=node('div');body.appendChild(list);
  const draw=()=>{clear(list);for(const other of contactPeople().filter(p=>p.id!==c.id&&contactMatch(p,search.value)))contactButton(list,contactName(other),()=>{editContact(c);mergeIntoDraft(other);});};search.oninput=draw;draw();contactButton(body,tr('Cancel'),()=>el('contacts-dialog').close());
}
function contactDuplicates(){
  const body=contactDialog(tr('Possible duplicates')),people=contactPeople();let count=0;
  for(let i=0;i<people.length;i++)for(let j=i+1;j<people.length;j++){const reason=contactDuplicate(people[i],people[j]);if(reason){count++;contactButton(body,contactName(people[i])+' / '+contactName(people[j])+' · '+reason,()=>{editContact(people[i]);mergeIntoDraft(people[j]);});}}
  if(!count)body.appendChild(node('p','help',tr('No matching names, callsigns, phone numbers or email addresses found.')));contactButton(body,tr('Close'),()=>el('contacts-dialog').close());
}
function exportContactItems(items){
  const body=contactDialog(tr('Export contacts'));body.appendChild(node('p','help',tr('This JSON file includes all fields, photos, notes and node assignments for the selected contacts. Store it somewhere private. Full App Backup also includes Contacts.')));
  contactButton(body,tr('Choose export file'),()=>{AndroidBridge.exportContacts(JSON.stringify({format:'sv8ttl-contacts',version:1,items}));el('contacts-dialog').close();});contactButton(body,tr('Cancel'),()=>el('contacts-dialog').close());
}
function exportAllContacts(){exportContactItems(contactItems.filter(c=>!c.deletedAt));}
function shareContactFields(c){
  const body=contactDialog(tr('Share contact fields')),selected=new Set(['identity','callsigns','phones','emails']);
  body.appendChild(node('p','help',tr('Only checked fields are shared. Private notes are off by default.')));
  const groups=[['identity',tr('Name, surname, alias and display name')],['callsigns',tr('Callsigns')],['phones',tr('Phone numbers')],['emails',tr('Email addresses')],['addresses',tr('Addresses')],['organization',tr('Organization and role')],['categories',tr('Groups')],['nodes',tr('Assigned nodes')],['custom',tr('Custom fields')],['notes',tr('Private notes')]];
  if(c.photo)contactCheck(body,tr('Profile photo'),false,yes=>yes?selected.add('photo'):selected.delete('photo'));
  for(const [key,label] of groups)contactCheck(body,label,selected.has(key),yes=>yes?selected.add(key):selected.delete(key));
  contactButton(body,tr('Share selected fields'),()=>{
    const lines=[];for(const [key,title] of groups){if(!selected.has(key))continue;
      if(key==='identity')lines.push(...[['Name',c.name],['Surname',c.surname],['Alias',c.alias],['Display name',c.displayName]].filter(x=>x[1]).map(x=>x.join(': ')));
      else if(key==='organization')lines.push(...[['Organization',c.organization],['Role',c.role]].filter(x=>x[1]).map(x=>x.join(': ')));
      else if(key==='categories')lines.push('Groups: '+(c.categories||[]).map(id=>contactById(id)?.title).filter(Boolean).join(', '));
      else for(const value of c[key]||[])lines.push(title+': '+(key==='notes'?new Date(value.date).toLocaleDateString()+' '+value.text:key==='nodes'?value.radio.toUpperCase()+' '+value.id+' '+(value.name||''):(value.label?value.label+': ':'')+value.value));
    }if(!lines.length&&!selected.has('photo')){onContactError('Choose at least one field with a value');return;}AndroidBridge.shareContact(lines.join('\n'),selected.has('photo')?c.photo:'');
  });contactButton(body,tr('Cancel'),()=>el('contacts-dialog').close());
}
window.onContactImport=items=>{
  const body=contactDialog(tr('Import contacts file')),selected=new Set(items.filter(x=>!x.deletedAt).map(x=>x.id));
  body.appendChild(node('p','help',tr('Choose contacts to import. Existing records with the same ID are kept unless you choose to replace them. Use Find duplicates afterwards to review different IDs for the same person.')));
  let replace=false;contactCheck(body,tr('Replace existing records with the same ID'),false,v=>replace=v);
  for(const c of items.filter(x=>x.kind!=='category'&&!x.deletedAt))contactCheck(body,contactName(c)+(contactById(c.id)?tr(' · already exists'):''),true,yes=>yes?selected.add(c.id):selected.delete(c.id));
  contactButton(body,tr('Import selected'),()=>{
    const chosen=items.filter(x=>x.kind!=='category'&&selected.has(x.id)&&(!contactById(x.id)||replace));
    const categories=new Set(chosen.flatMap(x=>x.categories||[]));chosen.push(...items.filter(x=>x.kind==='category'&&categories.has(x.id)&&(!contactById(x.id)||replace)));
    contactsSave(chosen,()=>{el('contacts-dialog').close();contactOpen='';renderContacts();contactNotice(tr('Contacts imported. Use Find duplicates to review matching people.'));});
  });contactButton(body,tr('Cancel'),()=>el('contacts-dialog').close());
};
window.onAndroidContact=result=>{
  const incoming=result.contact,existing=contactById(result.target),draft=existing?contactClone(existing):newContact();
  const body=contactDialog(existing?tr('Review Android contact refresh'):tr('Import Android contact'));
  body.appendChild(node('p','help',tr('Phone numbers, emails, addresses and notes are added to your existing details without duplicates. Choose other fields to replace, then review and save the profile. Saving changes this app only; Android refresh is manual.')));
  if(incoming.importNotice)body.appendChild(node('p','help',incoming.importNotice));
  if(existing?.androidLink?.lookup&&incoming.androidLink?.lookup!==existing.androidLink.lookup)body.appendChild(node('p','contact-error',tr('You selected a different Android contact. Confirm the person before applying these fields.')));
  const selected=new Set();
  for(const [key,label] of [['name',tr('Name')],['surname',tr('Surname')],['displayName',tr('Display name')],['alias',tr('Alias')],['organization',tr('Organization')],['role',tr('Role')],['phones',tr('Phone numbers')],['emails',tr('Email addresses')],['addresses',tr('Addresses')],['notes',tr('Notes')],['photo',tr('Profile photo')]]){
    if(!incoming[key]||Array.isArray(incoming[key])&&!incoming[key].length)continue;
    const enabled=Array.isArray(incoming[key])||!existing||!draft[key];if(enabled)selected.add(key);
    const preview=key==='photo'?'':Array.isArray(incoming[key])?incoming[key].map(x=>x.value||x.text).join(', '):incoming[key];
    contactCheck(body,label+(preview?' · '+preview:''),enabled,yes=>yes?selected.add(key):selected.delete(key));
    if(existing&&draft[key])body.appendChild(node('small','contact-value','Current: '+(key==='photo'?tr('Existing photo'):Array.isArray(draft[key])?draft[key].map(x=>x.value||x.text).join(', '):draft[key])));
  }
  let link=!!existing;contactCheck(body,tr('Link to this Android contact for manual refresh'),link,v=>link=v);
  contactButton(body,tr('Review profile'),()=>{
    for(const key of selected){
      if(Array.isArray(incoming[key])){
        const values=contactClone(draft[key]||[]),identity=x=>key==='phones'?contactPhone(x.value):contactNorm(key==='notes'?x.text:x.value);
        const seen=new Set(values.map(identity));
        for(const value of incoming[key]){const id=identity(value);if(id&&!seen.has(id)){values.push(contactClone(value));seen.add(id);}}
        draft[key]=values;
      }else draft[key]=contactClone(incoming[key]);
    }draft.androidLink=link?incoming.androidLink:null;
    editContact(draft);
  });contactButton(body,tr('Cancel'),()=>el('contacts-dialog').close());
};

function contactGroupList(c){const list=node('span','contact-group-list');for(const group of contactCategories().filter(g=>(c.categories||[]).includes(g.id)))list.appendChild(node('small','',group.title));return list;}
