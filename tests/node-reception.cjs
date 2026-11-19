const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path');
const {pathToFileURL}=require('node:url');
(async()=>{const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});try{
 const page=await browser.newPage({viewport:{width:390,height:780}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{window.AndroidBridge=new Proxy({},{get:()=>()=>{}});});
 await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
 await page.evaluate(()=>{
  const signal={snr:-16,rssi:-116,hops:0,time:1700000000000,settings:'Long Fast',mqtt:false};
  const radio=r=>({radio:r,key:r+'1',ownId:'own-'+r,ownName:'Receiver '+r,connected:true,scanProgress:0,loading:false,channels:[{index:0,name:'Team Alpha'},{index:2,name:'Local'}],nodes:[],directs:{},chats:{0:[],2:[]}});
  window.f={mc:radio('mc'),mt:radio('mt'),devices:[],myNodes:[],nodeNames:{},sound:{mode:'shared',shared:{}}};
  f.mt.nodes=[{id:'!11111111',name:'Portable',role:'Client',hops:0,lastSignal:signal},
   {id:'!22222222',name:'Relayed',role:'Client',hops:2,lastSignal:{...signal,hops:2}},
   {id:'!33333333',name:'Unknown',role:'Client',hops:-1},
   {id:'!44444444',name:'MQTT',role:'Client',hops:0,lastSignal:{...signal,mqtt:true}}];
  f.mc.nodes=[{id:'abcdefabcdef',name:'MC direct',role:'User',hops:3,lastSignal:{...signal,snr:0,rssi:undefined,settings:'869.618 MHz · BW 62.5 kHz · SF8 · CR4/8',timeSource:'message'}},
   {id:'eeeeeeeeeeee',name:'Outgoing route only',role:'User',hops:0}];
  onRadioState(f);switchTab('nodes');
  window.people=[{id:'p1',name:'Alice <friend>',nodes:[{radio:'mt',id:'!11111111'}]},
   {id:'p2',displayName:'Second contact',nodes:[{radio:'mt',id:'!11111111'}]},
   {id:'p3',name:'Deleted',deletedAt:1,nodes:[{radio:'mt',id:'!11111111'}]}];
  onContactsData({items:people,view:{}});
 });
 const first=page.locator('[data-node-key="mt:!11111111"]');
 assert.equal(await first.locator('.node-contact-name').innerText(),'· Alice <friend> / Second contact');
 assert.equal(await first.locator('friend').count(),0);
 await page.locator('#nodes-search').fill('Alice');assert.equal(await page.locator('.android-node').count(),1);
 await page.locator('#nodes-search').fill('');
 await first.locator('.node-heading').click();
 assert.match(await first.locator('.node-signal-report').innerText(),/SNR -16 dB · Good/);
 assert.match(await first.locator('.node-signal-report').innerText(),/RSSI -116 dBm/);
 assert.equal(await first.locator('.signal-good').count(),1);assert.equal(await first.locator('.signal-fair').count(),1);
 await page.locator('#nodes-direct-only').check();
 assert.deepEqual((await page.locator('.android-node').evaluateAll(rows=>rows.map(r=>r.dataset.nodeKey))).sort(),['mc:abcdefabcdef','mt:!11111111']);
 await page.locator('#nodes-direct-only').uncheck();
 const relayed=page.locator('[data-node-key="mt:!22222222"]');await relayed.locator('.node-heading').click();
 assert.match(await relayed.innerText(),/Final-hop reception · 2 hops/);
 await page.evaluate(()=>{people[0].name='Renamed';people[1].deletedAt=1;onContactsData({items:people,view:{}});f.mt.connected=false;onRadioState(f);});
 assert.equal(await first.locator('.node-contact-name').innerText(),'· Renamed');
 assert.doesNotMatch(await first.innerText(),/reachability|availability|Saved record/);
 assert.equal(await page.evaluate(()=>{const d=nodeDetails('mt',{id:'missing'});return [...d.querySelectorAll('button')].find(b=>b.textContent==='Show on map').disabled;}),true);
 assert.equal(await page.evaluate(()=>{const d=nodeDetails('mt',{id:'positioned',latitude:38,longitude:23});return [...d.querySelectorAll('button')].find(b=>b.textContent==='Show on map').disabled;}),false);
 const ratings=await page.evaluate(()=>({
  floors:['Short Fast','Short Slow','Medium Fast','Medium Slow','Long Fast','Long Slow'].map(n=>nodeSnrFloor('mt',n)),
  snr:[-17.49,-17.5,-22.99,-23,-25,-25.01].map(v=>nodeSnrRating(v,-17.5)[0]),
  rssi:[-114,-115,-119,-120,-125,-126].map(nodeRssiRating),
  unknown:nodeSnrRating(-10,nodeSnrFloor('mt',''))[0]
 }));
 assert.deepEqual(ratings,{floors:[-7.5,-10,-12.5,-15,-17.5,-20],snr:['good','fair','fair','bad','bad','none'],rssi:['good','fair','fair','bad','bad','none'],unknown:'unknown'});
 await page.evaluate(()=>{f.mt.connected=true;f.devices=[{radio:'mc',id:'own-mc',key:'mc1',name:'Receiver MC'},{radio:'mt',id:'own-mt',key:'mt1',name:'Receiver MT'}];onRadioState(f);switchTab('chat');openChannelsScreen();});
 const selector=page.locator('#header-mc-channel');
 assert.equal(await selector.evaluate(e=>getComputedStyle(e).fontWeight),'700');
 assert.deepEqual(await selector.locator('option').allTextContents(),['Team Alpha','Local']);
 for(const viewport of [{width:320,height:640},{width:390,height:780},{width:780,height:390},{width:390,height:360}]){
  await page.setViewportSize(viewport);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'No horizontal overflow');
 }
 await page.setViewportSize({width:390,height:780});await page.screenshot({path:'build/previews/chat-channel-019.png'});
 await page.evaluate(()=>{switchTab('nodes');el('nodes-search').value='Portable';openedNode='mt:!11111111';renderNodes();});
 await page.screenshot({path:'build/previews/node-reception-019.png'});
 assert.deepEqual(errors,[]);console.log('Node contacts, reception colours, zero-hop evidence, unavailable wording and channel selector checks passed');
}finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});
