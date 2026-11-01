'use strict';
let logItems=[],logView='note',logFilter='',logFilters=new Set(),logSelected=new Set(),logEditing=null,logOpen=null,logPendingMessage='';
const logById=id=>logItems.find(x=>x.id===id);
const logOf=kind=>logItems.filter(x=>x.kind===kind);
function logId(){return 'log-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,12);}
function logDate(value){return new Date(value).toLocaleString();}
function logPersist(item){AndroidBridge.saveLogItem(JSON.stringify(item));}
function loadLog(){AndroidBridge.loadLogItems();}
window.onLogItems=function(items){logItems=Array.isArray(items)?items:[];renderLog();renderLogEditorCategories();};
function setLogView(view,filter=''){logView=view;logFilter=filter;logFilters=new Set(filter&&view==='note'?[filter]:[]);logSelected.clear();if(view!=='note')el('log-body-search').checked=false;renderLog();}
function toggleLogCategoryFilter(id){if(logFilters.has(id))logFilters.delete(id);else logFilters.add(id);renderLog();}
function logCategories(item){const kind=item.kind==='template'?'templateCategory':'category';return(item.categories||[]).map(logById).filter(x=>x&&x.kind===kind);}
function logChip(cat,clickable){const b=node(clickable?'button':'span','log-chip',cat.title);if(clickable){b.type='button';b.onclick=()=>{closeLogDetail();setLogView(cat.kind==='templateCategory'?'template':'note',cat.id);};}return b;}
function logSort(items){const order=el('log-sort').value;return items.sort((a,b)=>{if(order.startsWith('title'))return a.title.localeCompare(b.title,undefined,{sensitivity:'base'})*(order.endsWith('desc')?-1:1);const field=order.startsWith('created')?'created':'edited';return ((Number(a[field])||0)-(Number(b[field])||0))*(order.endsWith('desc')?-1:1)||a.title.localeCompare(b.title);});}
function renderLogFilters(){
  const bar=el('log-filter-bar');clear(bar);bar.hidden=!['note','template'].includes(logView);if(bar.hidden)return;
  const filters=node('div','log-filter-scroll');bar.appendChild(filters);
  if(logView==='template'){for(const [id,label] of [['','All'],['uncategorised','Uncategorised']]){const b=node('button',logFilter===id?'active':'',label);b.onclick=()=>setLogView('template',id);filters.appendChild(b);}logSort(logOf('templateCategory')).forEach(cat=>{const b=node('button',logFilter===cat.id?'active':'',cat.title);b.onclick=()=>setLogView('template',cat.id);filters.appendChild(b);});return;}
  const all=node('button',!logFilters.size?'active':'','All');all.onclick=()=>setLogView('note');filters.appendChild(all);
  const unc=node('button',logFilters.has('uncategorised')?'active':'','Uncategorised');unc.onclick=()=>toggleLogCategoryFilter('uncategorised');filters.appendChild(unc);
  logSort(logOf('category')).forEach(cat=>{const b=node('button',logFilters.has(cat.id)?'active':'',cat.title);b.onclick=()=>toggleLogCategoryFilter(cat.id);filters.appendChild(b);});
  const select=node('button','','Select all shown');select.onclick=()=>{document.querySelectorAll('#log-list input[type=checkbox]').forEach(box=>{box.checked=true;box.dispatchEvent(new Event('change'));});};bar.appendChild(select);
  const del=node('button','','Delete all notes');del.onclick=deleteAllNotes;bar.appendChild(del);
}
function renderLog(){
  if(!el('log-list'))return;
  document.querySelectorAll('.log-views button').forEach((b,i)=>b.classList.toggle('active',['note','category','template','templateCategory'][i]===logView));
  el('log-body-search-wrap').hidden=logView!=='note';el('log-search').placeholder=logView==='note'?(el('log-body-search').checked?'Search note text…':'Search title or category…'):'Search name…';
  renderLogFilters();
  const q=el('log-search').value.trim().toLocaleLowerCase(),bodySearch=el('log-body-search').checked;
  const items=logSort(logOf(logView).filter(item=>{
    if(logView==='note'&&logFilters.size&&!([...logFilters].some(id=>id==='uncategorised'?!(item.categories||[]).length:(item.categories||[]).includes(id))))return false;
    if(logView==='template'&&logFilter==='uncategorised'&&(item.categories||[]).length)return false;
    if(logView==='template'&&logFilter&&logFilter!=='uncategorised'&&!(item.categories||[]).includes(logFilter))return false;
    const searchable=bodySearch&&logView==='note'?item.body:item.title+' '+logCategories(item).map(x=>x.title).join(' ');
    return !q||searchable.toLocaleLowerCase().includes(q);
  }));
  const list=el('log-list');clear(list);if(!items.length)list.appendChild(node('p','log-empty',q?'No matching items.':'Nothing here yet. Tap + New to begin.'));
  items.forEach(item=>{
    const row=node('div','log-item');
    if(logView==='note'){const box=node('input','');box.type='checkbox';box.checked=logSelected.has(item.id);box.setAttribute('aria-label','Select '+item.title);box.onchange=()=>{if(box.checked)logSelected.add(item.id);else logSelected.delete(item.id);renderLogBulk();};row.appendChild(box);}
    const main=node('button','log-item-main');main.type='button';main.appendChild(node('strong','',item.title));
    if(item.kind==='note'||item.kind==='template'){const chips=node('div','log-chips');logCategories(item).forEach(c=>chips.appendChild(logChip(c,false)));main.append(chips,node('span','log-preview',item.body.slice(0,150)));}
    main.appendChild(node('small','',logDate(item.edited)));main.onclick=()=>item.kind==='note'?openLogDetail(item.id):item.kind==='category'?setLogView('note',item.id):item.kind==='templateCategory'?setLogView('template',item.id):openLogEditor(item.kind,item);row.appendChild(main);
    if(item.kind!=='note'){const edit=node('button','log-small-action','Edit');edit.onclick=()=>openLogEditor(item.kind,item);const del=node('button','log-small-action','Delete');del.onclick=()=>deleteLogItem(item);row.append(edit,del);}
    list.appendChild(row);
  });renderLogBulk();
}
function renderLogBulk(){
  el('log-bulk-bar').hidden=logView!=='note'||!logSelected.size;el('log-selected-count').textContent=logSelected.size+' selected';
  const picker=el('log-bulk-category'),old=picker.value;clear(picker);picker.appendChild(new Option('Choose category',''));logOf('category').forEach(c=>picker.appendChild(new Option(c.title,c.id)));picker.value=old;
}
function clearLogSelection(){logSelected.clear();renderLog();}
function addCategoryToSelected(){const id=el('log-bulk-category').value;if(!id)return;[...logSelected].forEach(noteId=>{const item=logById(noteId);if(item&&!(item.categories||[]).includes(id))logPersist({...item,categories:[...(item.categories||[]),id],edited:Date.now()});});clearLogSelection();}
function deleteSelectedNotes(){const ids=[...logSelected];if(ids.length&&confirm('Delete '+ids.length+' selected notes?')){AndroidBridge.deleteLogItems(JSON.stringify(ids));clearLogSelection();}}
function deleteAllNotes(){const ids=logOf('note').map(x=>x.id);if(ids.length&&confirm('Delete ALL '+ids.length+' notes? This cannot be undone.'))AndroidBridge.deleteLogItems(JSON.stringify(ids));}
function newLogItem(){openLogEditor(logView);}
function openLogEditor(kind,item=null,body=''){
  logEditing=item?{...item}:null;el('log-editor').dataset.kind=kind;
  el('log-editor-heading').textContent=(item?'Edit ':'New ')+({note:'note',category:'category',template:'note template',templateCategory:'template category'}[kind]);
  el('log-title').value=item?.title||'';el('log-body').value=item?.body||body;el('log-editor-status').textContent='';
  el('log-note-fields').hidden=kind!=='note';el('log-template-fields').hidden=kind!=='template';el('log-body').parentElement.hidden=kind==='category'||kind==='templateCategory';el('log-save-template').hidden=kind!=='note';
  clear(el('log-editor-categories'));clear(el('log-editor-template-categories'));
  const picker=el('log-template-picker');clear(picker);picker.appendChild(new Option('Blank note',''));logOf('template').forEach(t=>picker.appendChild(new Option(t.title,t.id)));
  if(!el('log-editor').open)el('log-editor').showModal();renderLogEditorCategories();el('log-title').focus();
}
function closeLogEditor(){el('log-editor').close();logEditing=null;logPendingMessage='';}
function renderLogEditorCategories(){
  if(!el('log-editor')?.open)return;
  for(const [kind,target] of [['category','log-editor-categories'],['templateCategory','log-editor-template-categories']]){
    const container=el(target),existing=container.querySelector('input')!==null,current=new Set([...container.querySelectorAll('input:checked')].map(x=>x.value)),selected=existing?current:new Set(logEditing?.categories||[]);clear(container);
    logSort(logOf(kind)).forEach(cat=>{const label=node('label','log-category-choice'),check=node('input','');check.type='checkbox';check.value=cat.id;check.checked=selected.has(cat.id);label.append(check,document.createTextNode(cat.title));container.appendChild(label);});
  }
}
function createLogCategory(kind,inputId,target){
  const input=el(inputId),title=input.value.trim();if(!title)return;
  if(logOf(kind).some(c=>c.title.toLocaleLowerCase()===title.toLocaleLowerCase())){el('log-editor-status').textContent='Category already exists.';return;}
  const now=Date.now(),item={id:logId(),kind,title,body:'',categories:[],created:now,edited:now};logItems.push(item);logPersist(item);input.value='';renderLogEditorCategories();
  const check=[...el(target).querySelectorAll('input')].find(c=>c.value===item.id);if(check)check.checked=true;
}
function createCategoryInEditor(){createLogCategory('category','log-new-category','log-editor-categories');}
function createTemplateCategoryInEditor(){createLogCategory('templateCategory','log-new-template-category','log-editor-template-categories');}
function applyNoteTemplate(id){const template=logById(id);if(!template)return;const body=el('log-body');if(body.value&&!confirm('Insert template into this note?'))return;body.value=template.body+(body.value?'\n\n'+body.value:'');logPendingMessage='';}
function saveLogEditor(){
  const kind=el('log-editor').dataset.kind,title=el('log-title').value.trim(),body=el('log-body').value;
  if(!title){el('log-editor-status').textContent='Enter a title.';return;}if(title.length>200||body.length>200000){el('log-editor-status').textContent='This item is too long.';return;}
  const target=kind==='note'?'log-editor-categories':'log-editor-template-categories',categories=['note','template'].includes(kind)?[...el(target).querySelectorAll('input:checked')].map(x=>x.value):[];
  const now=Date.now(),item={id:logEditing?.id||logId(),kind,title,body,categories,created:logEditing?.created??now,edited:now};
  logItems=logItems.filter(x=>x.id!==item.id);logItems.push(item);logPersist(item);closeLogEditor();renderLog();
}
function noteDraftAsTemplate(){
  const body=el('log-body').value,title=el('log-title').value.trim();if(!body.trim()){el('log-editor-status').textContent='Write some text first.';return;}
  el('log-draft-template-title').value=title?title+' template':'';el('log-draft-template-body').value=body;
  const cats=el('log-draft-template-categories');clear(cats);logSort(logOf('templateCategory')).forEach(cat=>{const label=node('label','log-category-choice'),box=node('input','');box.type='checkbox';box.value=cat.id;label.append(box,document.createTextNode(cat.title));cats.appendChild(label);});
  el('log-template-from-note').showModal();
}
function saveNoteDraftTemplate(){
  const title=el('log-draft-template-title').value.trim(),body=el('log-draft-template-body').value;
  if(!title||!body.trim()){el('log-draft-template-title').focus();return;}
  const now=Date.now(),categories=[...el('log-draft-template-categories').querySelectorAll('input:checked')].map(x=>x.value),item={id:logId(),kind:'template',title,body,categories,created:now,edited:now};logItems.push(item);logPersist(item);el('log-template-from-note').close();el('log-editor-status').textContent='Note template saved.';
}
function openLogDetail(id){
  const item=logById(id);if(!item)return;logOpen=id;el('log-detail-title').textContent=item.title;el('log-detail-body').textContent=item.body;el('log-detail-dates').textContent='Created '+logDate(item.created)+' · Edited '+logDate(item.edited);
  const cats=el('log-detail-categories');clear(cats);logCategories(item).forEach(c=>cats.appendChild(logChip(c,true)));el('log-detail').showModal();
}
function closeLogDetail(){if(el('log-detail').open)el('log-detail').close();logOpen=null;}
function editOpenNote(){const item=logById(logOpen);closeLogDetail();if(item)openLogEditor('note',item);}
function deleteOpenNote(){const item=logById(logOpen);if(item&&confirm('Delete note "'+item.title+'"?')){AndroidBridge.deleteLogItems(JSON.stringify([item.id]));closeLogDetail();}}
function deleteLogItem(item){
  const category=item.kind==='category'||item.kind==='templateCategory';
  if(!confirm('Delete "'+item.title+'"?'+(category?' Items in it will be kept and lose this category.':'')))return;
  AndroidBridge.deleteLogItems(JSON.stringify([item.id]));
}
function addMessageToLog(source){
  switchTab('log');setLogView('note');openLogEditor('note');el('log-title').value=source.own?'My message':'Message from '+source.sender;
  const lines=['Message '+(source.own?'sent by ':'from ')+source.sender];
  if(source.time)lines.push('Time: '+source.time);if(source.network)lines.push('Network: '+source.network);if(source.device)lines.push('Device: '+source.device);if(source.conversation)lines.push('Conversation: '+source.conversation);
  if(source.status)lines.push('Status when saved: '+source.status);
  logPendingMessage=lines.join('\n')+'\n\n'+source.text;el('log-body').value=logPendingMessage;
}
