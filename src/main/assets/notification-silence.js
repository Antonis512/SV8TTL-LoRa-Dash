'use strict';
function renderSilenceTargets(){
  const scope=el('silence-scope').value,select=el('silence-target'),previous=select.value;
  el('silence-target-label').hidden=scope==='all';clear(select);
  const choices=new Map();
  if(scope==='contact')for(const c of contactPeople())choices.set(c.id,contactName(c));
  if(scope==='node'){
    const radios=[...contactRadios,...Object.entries(state||{}).filter(([key,r])=>r&&Array.isArray(r.nodes)).map(([key,r])=>({...r,radio:r.radio||radioType(key)}))];
    for(const r of radios)for(const n of r.nodes||[]){const radio=r.radio||(String(r.key||'').toLowerCase().startsWith('mc')?'mc':'mt');choices.set(radio+':'+n.id,(n.name||n.id)+' · '+radio.toUpperCase()+' · '+n.id);}
    for(const c of contactPeople())for(const n of c.nodes||[]){const id=n.radio+':'+n.id;if(!choices.has(id))choices.set(id,(n.name||n.id)+' · '+n.radio.toUpperCase()+' · '+n.id);}
  }
  for(const [id,label] of [...choices].sort((a,b)=>a[1].localeCompare(b[1]))){const option=node('option','',label);option.value=id;select.appendChild(option);}
  if(choices.has(previous))select.value=previous;
}
function applySilence(){
  const scope=el('silence-scope').value,target=scope==='all'?'':el('silence-target').value;
  const minutes=el('silence-duration').value==='timed'?Number(el('silence-minutes').value):0;
  if(scope!=='all'&&!target){el('silence-status').textContent=tr('Choose a contact or node first.');return;}
  if(!Number.isInteger(minutes)||minutes<0||minutes>525600||(el('silence-duration').value==='timed'&&minutes===0)){el('silence-status').textContent=tr('Enter 1–525600 minutes.');return;}
  const label=scope==='all'?'All notifications':el('silence-target').selectedOptions[0].textContent;
  AndroidBridge.setNotificationSilence(scope,target,label,minutes);
}
function renderSilenceRules(rules){
  const host=el('silence-rules');clear(host);
  for(const [key,rule] of Object.entries(rules)){
    if(rule.until!==-1&&rule.until<=Date.now())continue;
    const row=node('p','help',rule.label+' · '+(rule.until===-1?'until restored':'until '+signalDate(rule.until))+' ');
    const button=node('button','silence-action',tr('Restore alerts'));button.type='button';button.onclick=()=>AndroidBridge.clearNotificationSilence(key);row.appendChild(button);host.appendChild(row);
  }
  el('silence-status').textContent=host.children.length?tr('Active silences shown below.'):tr('No active silences.');
}
