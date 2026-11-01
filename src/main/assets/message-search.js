/* Conversation search. Each scope is one radio, saved device and conversation. */
const messageSearches=new Map();
const messageSearchHosts={channel:'channel-message-search',dm:'dm-message-search',saved:'saved-message-search'};
const messageSearchExpanded={channel:true,dm:true};
function updateMessageSearchToggle(kind){
  const button=el(kind+'-search-toggle');if(!button)return;
  button.disabled=!messageSearches.has(kind);
  button.setAttribute('aria-expanded',String(!!messageSearchExpanded[kind]));
  button.setAttribute('aria-label',messageSearchExpanded[kind]?'Hide message search':'Show message search');
}
function toggleMessageSearch(kind){
  if(!messageSearches.has(kind))return;
  messageSearchExpanded[kind]=!messageSearchExpanded[kind];
  el(messageSearchHosts[kind]).hidden=!messageSearchExpanded[kind];
  updateMessageSearchToggle(kind);
}
function resetMessageSearch(kind){messageSearches.delete(kind);const host=el(messageSearchHosts[kind]);host.hidden=true;host.replaceChildren();updateMessageSearchToggle(kind);}
function messageSearchScope(kind,radio,id,peer,channel){return JSON.stringify([kind,radio,id,peer,channel]);}
function messageSearchActive(search){return !!search&&search.senders!==null&&
  (search.kind==='saved'||!!search.query.trim()||search.selected.size!==search.senders.length);}
function ensureMessageSearch(kind,radio,id,peer,channel,knownCount=0){
  const host=el(messageSearchHosts[kind]);
  if(!radio||!id||channel<0&&!peer){host.hidden=true;messageSearches.delete(kind);updateMessageSearchToggle(kind);return null;}
  host.hidden=messageSearchExpanded[kind]===false;const scope=messageSearchScope(kind,radio,id,peer,channel);
  let search=messageSearches.get(kind);
  if(!search||search.scope!==scope){
    search={kind,scope,radio,id,peer,channel,query:'',senders:null,selected:new Set(),results:[],more:false,beforeTime:0,beforeId:0,request:0,loading:false,knownCount};
    messageSearches.set(kind,search);buildMessageSearchBar(search);
    AndroidBridge.messageSearchSenders(radio,id,peer,channel,scope);
  }else if(knownCount!==search.knownCount){
    search.knownCount=knownCount;AndroidBridge.messageSearchSenders(radio,id,peer,channel,scope);
  }
  updateMessageSearchToggle(kind);
  return search;
}
function buildMessageSearchBar(search){
  const host=el(messageSearchHosts[search.kind]);host.replaceChildren();
  const row=document.createElement('div');row.className='message-search-row';
  const input=document.createElement('input');input.type='search';input.className='form-input';input.maxLength=200;
  input.placeholder='Search message body';input.setAttribute('aria-label','Search message body');input.value=search.query;
  input.oninput=()=>{search.query=input.value;clearTimeout(search.timer);search.timer=setTimeout(()=>requestMessageSearch(search),220);};
  const senderButton=document.createElement('button');senderButton.type='button';senderButton.textContent='Senders';
  senderButton.onclick=()=>{const choices=host.querySelector('.message-search-senders');choices.hidden=!choices.hidden;};
  const clearButton=document.createElement('button');clearButton.type='button';clearButton.textContent='Clear';
  clearButton.onclick=()=>{input.value='';search.query='';search.selected=new Set((search.senders||[]).map(s=>s.key));
    renderMessageSearchSenders(search);requestMessageSearch(search);};
  row.append(input,senderButton,clearButton);host.appendChild(row);
  const choices=document.createElement('div');choices.className='message-search-senders';choices.hidden=true;host.appendChild(choices);
  const status=document.createElement('div');status.className='message-search-status';status.setAttribute('role','status');host.appendChild(status);
}
window.onMessageSearchSenders=(scope,senders)=>{
  for(const search of messageSearches.values())if(search.scope===scope){
    const old=search.senders,all=old===null||search.selected.size===old.length;
    search.senders=senders;search.selected=new Set(senders.filter(s=>all||search.selected.has(s.key)).map(s=>s.key));
    renderMessageSearchSenders(search);if(messageSearchActive(search))requestMessageSearch(search);
  }
};
function renderMessageSearchSenders(search){
  const host=el(messageSearchHosts[search.kind]),choices=host.querySelector('.message-search-senders');if(!choices)return;
  choices.replaceChildren();const toolbar=document.createElement('div');toolbar.className='message-search-choices-actions';
  for(const [label,all] of [['All',true],['None',false]]){const button=document.createElement('button');button.type='button';button.textContent=label;
    button.onclick=()=>{search.selected=new Set(all?(search.senders||[]).map(s=>s.key):[]);renderMessageSearchSenders(search);requestMessageSearch(search);};toolbar.appendChild(button);}
  choices.appendChild(toolbar);
  for(const sender of search.senders||[]){const label=document.createElement('label'),box=document.createElement('input');box.type='checkbox';box.checked=search.selected.has(sender.key);
    box.onchange=()=>{if(box.checked)search.selected.add(sender.key);else search.selected.delete(sender.key);requestMessageSearch(search);};
    const caption=document.createElement('span');let display=sender.name;
    if(sender.key!=='own'&&state){const slot=['left','right'].find(s=>state[s]?.ownId===search.id&&state[s]?.radio===search.radio);
      const contact=slot?state[slot].nodes?.find(n=>n.id===sender.name):null;if(contact?.name&&contact.name!==sender.name)display=contact.name+' · '+sender.name;}
    caption.textContent=display+' ('+sender.count+')';label.append(box,caption);choices.appendChild(label);}
  host.querySelector('.message-search-row button').textContent='Senders'+(search.senders?' ('+search.selected.size+'/'+search.senders.length+')':'');
}
function requestMessageSearch(search,more=false){
  if(messageSearches.get(search.kind)!==search)return;
  const active=messageSearchActive(search);search.request++;
  if(!active){search.results=[];search.more=false;search.loading=false;showMessageSearchState(search);return;}
  if(search.senders===null)return;
  if(!more){search.results=[];search.beforeTime=0;search.beforeId=0;}
  search.loading=true;showMessageSearchState(search);
  AndroidBridge.searchStoredMessages(search.radio,search.id,search.peer,search.channel,search.query,
    JSON.stringify([...search.selected]),more?search.beforeTime:0,more?search.beforeId:0,search.scope,search.request);
}
window.onMessageSearchPage=result=>{
  for(const search of messageSearches.values())if(search.scope===result.scope&&search.request===result.request){
    search.results.push(...result.messages);search.more=!!result.more;search.beforeTime=result.beforeTime;search.beforeId=result.beforeId;
    search.loading=false;showMessageSearchState(search);
  }
};
window.onMessageSearchError=(scope,request,message)=>{
  for(const search of messageSearches.values())if(search.scope===scope&&search.request===request){search.loading=false;
    el(messageSearchHosts[search.kind]).querySelector('.message-search-status').textContent=message;}
};
function showMessageSearchState(search){
  const host=el(messageSearchHosts[search.kind]);if(!host)return;
  const status=host.querySelector('.message-search-status');if(status)status.textContent=!messageSearchActive(search)?'':
    search.loading?'Searching all saved messages…':search.results.length+' matching messages shown · newest first'+(search.more?' · more available':'');
  if(search.kind==='saved')renderSavedHistory();else if(search.kind==='dm')renderDmScreen();else renderChannelsScreen();
}
function renderMessageSearchResults(kind,list){
  const search=messageSearches.get(kind);if(!messageSearchActive(search))return false;
  list.replaceChildren();list.onscroll=null;
  for(const entry of search.results){
    const bubble=node('div','android-msg'+(entry.own?' own':''));
    if(kind==='saved')bubble.appendChild(node('div','meta',(entry.peer?entry.peer+' · ':'')+(entry.displaySender||entry.sender)+' · '+entry.time));
    else appendMessageMeta(bubble,kind==='channel'?network:dmSelection.radio,entry,kind==='channel');
    const body=node('div','body');appendLinkedText(body,entry.displayText??entry.text);bubble.appendChild(body);
    if(kind!=='saved'){
      const slot=kind==='channel'?network:dmSelection.radio;
      appendMessageDelivery(bubble,slot,entry,kind==='channel');
      if(Number.isFinite(entry.hops)&&entry.hops>=0)bubble.appendChild(node('div','message-hops',entry.hops+' hops'));
      bindMessageActions(bubble,slot,entry,kind==='channel'?{peer:null,channel:search.channel}:{peer:search.peer,channel:-1});
    }
    list.appendChild(bubble);
  }
  if(!search.loading&&!search.results.length)list.appendChild(node('div','empty-panel','No messages match this search and sender selection.'));
  if(search.more&&!search.loading){const more=node('button','history-older',kind==='saved'&&!search.query.trim()&&search.selected.size===search.senders.length?'Load older messages':'Load more matches');more.type='button';more.onclick=()=>requestMessageSearch(search,true);list.appendChild(more);}
  return true;
}
