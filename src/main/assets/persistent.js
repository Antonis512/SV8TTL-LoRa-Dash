'use strict';
let persistentTarget=null,persistentKind=false,persistentInput='',persistentEditingUntil=0;
const persistentEditors='#chat-input,#dm-input,#member-input,#persistent-text,.node-dm-composer input,.node-dm-composer textarea';
const persistentWords={
 en:{message:'Persistent message',location:'Persistent location',start:'Start',close:'Close',stop:'Stop',resume:'Resume',answered:'Mark answered / Stop',interval:'Interval (minutes, minimum 2)',expiry:'Expire after (minutes; blank = until stopped)',retries:'Maximum retries (optional; after first send)',empty:'No persistent jobs.',choose:'Connect a radio and choose a destination.',text:'Message',locationHelp:'Send an initial position, then only after moving 100 m and waiting the interval. Requires accuracy ≤25 m and a fix ≤30 seconds old.',messageHelp:'Stops automatically on mesh / repeater evidence, delivery, a linked reply, expiry or cancellation. Each message must fit one radio packet.',jobs:'Persistent jobs',reply:'Recipient sent a later message.',active:'active'},
 el:{message:'Επίμονο μήνυμα',location:'Επαναλαμβανόμενη θέση',start:'Έναρξη',close:'Κλείσιμο',stop:'Διακοπή',resume:'Συνέχιση',answered:'Απαντήθηκε / Διακοπή',interval:'Διάστημα (λεπτά, τουλάχιστον 2)',expiry:'Λήξη μετά από (λεπτά· κενό = μέχρι διακοπή)',retries:'Μέγιστες επαναλήψεις (προαιρετικά, μετά την πρώτη αποστολή)',empty:'Δεν υπάρχουν επίμονες αποστολές.',choose:'Συνδέστε κόμβο και επιλέξτε προορισμό.',text:'Μήνυμα',locationHelp:'Αρχική αποστολή θέσης και μετά μόνο με μετακίνηση 100 μ. και παρέλευση του διαστήματος. Ακρίβεια ≤25 μ. και θέση έως 30 δευτερολέπτων.',messageHelp:'Σταματά με επιβεβαίωση δικτύου / αναμεταδότη, παράδοση, συνδεδεμένη απάντηση, λήξη ή ακύρωση. Το μήνυμα πρέπει να χωρά σε ένα πακέτο.',jobs:'Επίμονες αποστολές',reply:'Ο παραλήπτης έστειλε νεότερο μήνυμα.',active:'ενεργές'}
};
function pw(key){return tr(persistentWords.en[key]);}
function persistentDestination(){return state?.memberConfig?.enabled?locationDestination():chatLocationDestination();}
function persistentButton(text,fn){const b=document.createElement('button');b.type='button';b.textContent=text;b.onclick=fn;return b;}
function installPersistentControls(){
 for(const [prefix,inputId,positionId] of [['chat','chat-input','chat-position'],['dm','dm-input','dm-position'],['member','member-input','member-location']]){
  const input=el(inputId),position=el(positionId);if(!input||!position)continue;
  const row=document.createElement('div');row.className='persistent-controls';
  for(const [location,label] of [[false,'P. Msg'],[true,'P. Location']]){
   const b=persistentButton(label,()=>openPersistent(location,inputId));b.onpointerdown=e=>e.preventDefault();b.dataset.persistentKind=location?'location':'message';b.title=location?tr('Persistent location'):tr('Persistent message');row.append(b);
  }
  if(prefix==='member')position.before(row);else input.parentNode.before(row);
  row.append(position);
  const status=persistentButton('',()=>openPersistentJobs());status.className='persistent-summary';status.hidden=true;row.after(status);
 }
 const dialog=document.createElement('dialog');dialog.id='persistent-dialog';dialog.className='persistent-dialog';
 dialog.innerHTML='<h3 id="persistent-title"></h3><p id="persistent-target"></p><p id="persistent-help" class="help"></p><form id="persistent-form"><label id="persistent-text-label" for="persistent-text"></label><textarea id="persistent-text" rows="3"></textarea><label id="persistent-interval-label" for="persistent-interval"></label><input id="persistent-interval" type="number" min="2" max="10080" step="1" value="2" required><label id="persistent-expiry-label" for="persistent-expiry"></label><input id="persistent-expiry" type="number" min="1" max="43200" step="1" value="60"><label id="persistent-retries-label" for="persistent-retries"></label><input id="persistent-retries" type="number" min="0" max="10000" step="1"><p id="persistent-error" role="status"></p><div class="persistent-controls"><button id="persistent-start" type="submit"></button><button id="persistent-close" type="button"></button></div></form><div id="persistent-job-list"></div>';
 document.body.append(dialog);el('persistent-close').onclick=()=>dialog.close();el('persistent-form').onsubmit=e=>{e.preventDefault();submitPersistent();};
 dialog.addEventListener('close',reportPersistentComposer);
 for(const event of ['input','focusin','focusout'])document.addEventListener(event,e=>{
  if(e.target.matches?.(persistentEditors)){
   if(event==='input')persistentEditingUntil=Date.now()+45000;
   setTimeout(reportPersistentComposer,0);
  }
 });
 setInterval(reportPersistentComposer,1000);
}
function reportPersistentComposer(){
 const composing=[...document.querySelectorAll(persistentEditors)].some(e=>e.getClientRects().length&&e.value.trim()&&(document.activeElement===e||Date.now()<persistentEditingUntil));
 AndroidBridge.persistentComposer?.(!!composing);
}
function persistentLabels(){
 for(const [id,key] of [['persistent-interval-label','interval'],['persistent-expiry-label','expiry'],['persistent-retries-label','retries'],['persistent-text-label','text'],['persistent-start','start'],['persistent-close','close']])el(id).textContent=pw(key);
}
function openPersistent(location,inputId){
 persistentKind=location;persistentInput=inputId;persistentTarget=persistentDestination();
 if(!persistentTarget){openPersistentJobs();el('persistent-error').textContent=pw('choose');return;}
 // Reopening an active control manages its original job instead of creating duplicates.
 const existing=(state?.persistentJobs||[]).some(j=>j.state!=='done'&&!!j.location===location&&j.radio===persistentTarget.radio&&j.nodeId===persistentTarget.nodeId&&j.peer===persistentTarget.peer&&j.channel===persistentTarget.channel);
 if(existing){openPersistentJobs();return;}
 persistentLabels();el('persistent-title').textContent=pw(location?'location':'message');
 el('persistent-target').textContent=persistentTarget.radio.toUpperCase()+' · '+persistentTarget.label;
 el('persistent-help').textContent=pw(location?'locationHelp':'messageHelp');
 el('persistent-text').value=el(inputId).value;
 for(const id of ['persistent-text','persistent-text-label','persistent-retries','persistent-retries-label'])el(id).hidden=location;
 el('persistent-text').required=!location;el('persistent-text').disabled=location;
 el('persistent-start').hidden=false;el('persistent-error').textContent='';el('persistent-job-list').hidden=true;
 for(const id of ['persistent-interval','persistent-interval-label','persistent-expiry','persistent-expiry-label'])el(id).hidden=false;
 el('persistent-dialog').showModal();
}
function submitPersistent(){
 if(!persistentTarget)return;
 const current=persistentDestination();
 if(!current||current.radio!==persistentTarget.radio||current.nodeId!==persistentTarget.nodeId||current.peer!==persistentTarget.peer||current.channel!==persistentTarget.channel){el('persistent-error').textContent=pw('choose');return;}
 el('persistent-start').disabled=true;
 AndroidBridge.startPersistent(JSON.stringify(persistentTarget),el('persistent-text').value,persistentKind,Number(el('persistent-interval').value),Number(el('persistent-expiry').value)||0,persistentKind||el('persistent-retries').value===''?-1:Number(el('persistent-retries').value));
}
window.onPersistentStarted=()=>{
 el('persistent-start').disabled=false;
 // Starting a job does not clear or replace the user's draft.
 persistentEditingUntil=0;
 if(document.activeElement instanceof HTMLElement)document.activeElement.blur();
 el('persistent-dialog').close();
 // Closing a dialog can restore focus to the original editor. Leave the saved
 // draft intact, but do not mistake it for continued typing after Start.
 if(document.activeElement instanceof HTMLElement)document.activeElement.blur();
 openPersistentJobs();
};
window.onPersistentError=message=>{el('persistent-start').disabled=false;el('persistent-error').textContent=tr(message)||tr('Could not update persistent job');};
function openPersistentJobs(){
 persistentLabels();persistentTarget=null;el('persistent-title').textContent=pw('jobs');el('persistent-target').textContent='';el('persistent-help').textContent='';el('persistent-error').textContent='';
 for(const id of ['persistent-text','persistent-text-label','persistent-interval','persistent-interval-label','persistent-expiry','persistent-expiry-label','persistent-retries','persistent-retries-label','persistent-start'])el(id).hidden=true;
 el('persistent-text').required=false;el('persistent-job-list').hidden=false;
 renderPersistentJobs();if(!el('persistent-dialog').open)el('persistent-dialog').showModal();
}
function renderPersistentJobs(){
 const jobs=state?.persistentJobs||[],live=jobs.filter(j=>j.state!=='done');
 for(const b of document.querySelectorAll('.persistent-summary')){b.hidden=!jobs.length;b.textContent=pw('jobs')+' · '+live.length+' '+pw('active')+(live.some(j=>j.answerAvailable)?' · '+pw('reply'):'');}
 const target=persistentDestination();
 for(const b of document.querySelectorAll('[data-persistent-kind]')){
  const active=target&&live.some(j=>j.radio===target.radio&&j.nodeId===target.nodeId&&j.channel===target.channel&&j.peer===target.peer&&!!j.location===(b.dataset.persistentKind==='location'));
  b.classList.toggle('active',!!active);b.setAttribute('aria-pressed',String(!!active));
 }
 const list=el('persistent-job-list');if(!list||list.hidden)return;list.replaceChildren();
 for(const job of [...jobs].reverse()){
  const card=document.createElement('section');card.className='persistent-job';
  const title=document.createElement('strong');title.textContent=pw(job.location?'location':'message')+' · '+job.radio.toUpperCase()+' · '+(job.label||job.peer||job.channelName);card.append(title);
  if(!job.location){const text=document.createElement('p');text.textContent=job.text;card.append(text);}
  const status=document.createElement('p');status.textContent=tr(job.detail)+' · '+job.attempts+' transmission(s)'+(job.state==='active'&&job.nextAt>Date.now()?tr(' · Next check ')+new Date(job.nextAt).toLocaleTimeString():'')+(job.expires?tr(' · Expires ')+new Date(job.expires).toLocaleString():'');card.append(status);
  if(job.state!=='done'){
   const controls=document.createElement('div');controls.className='persistent-controls';
   if(job.answerAvailable){const hint=document.createElement('p');hint.textContent=pw('reply');card.append(hint);controls.append(persistentButton(pw('answered'),()=>AndroidBridge.persistentAction(job.id,'answered')));}
   if(job.state==='paused')controls.append(persistentButton(pw('resume'),()=>AndroidBridge.persistentAction(job.id,'resume')));
   controls.append(persistentButton(pw('stop'),()=>AndroidBridge.persistentAction(job.id,'stop')));card.append(controls);
  }
  list.append(card);
 }
 if(!jobs.length)list.textContent=pw('empty');
}
installPersistentControls();
