const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const {pathToFileURL}=require('node:url');
const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
 try {
  const page=await browser.newPage({viewport:{width:390,height:780}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(()=>{window.calls=[];window.AndroidBridge=new Proxy({},{get:(_,name)=>(...args)=>{calls.push([name,...args]);}});});
  await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
  await page.evaluate(()=>{
   const radio=(type,id)=>({radio:type,key:type+'-offline',name:type+' receiver',ownName:type+' receiver',ownId:id,connected:false,loading:false,channels:[],nodes:[],directs:{},chats:{},scanProgress:0});
   const mt=radio('mt','!11111111'),mc=radio('mc','abcdefabcdef');
   mt.nodes=[{id:'!22222222',name:'Old hilltop',role:'Router',lastHeard:Math.floor(Date.now()/1000)-60*86400,savedOnly:true,latitude:38,longitude:23},{id:'!33333333',name:'Unknown time',role:'Client',lastHeard:0,savedOnly:true}];
   window.fixture={mt,mc,left:mc,right:mt,selectedMt:mt.key,selectedMc:mc.key,devices:[],myNodes:[{radio:'mt',id:mt.ownId,name:'Home receiver',connected:false},{radio:'mt',id:'!44444444',name:'Field receiver',connected:false},{radio:'mc',id:mc.ownId,name:'MC receiver',connected:false}],sound:{mode:'shared',shared:{}},multiSendEnabled:true,messageSplitEnabled:true};
   onRadioState(fixture);switchTab('nodes');
  });
  assert.equal(await page.locator('#nodes-mt-device option').count(),2);
  assert.equal(await page.locator('#nodes-mt-device').inputValue(),'saved:!11111111');
  assert.equal(await page.locator('#nodes-list .android-node').count(),2,'Old and unknown saved nodes visible offline');
  await page.getByRole('button',{name:/Old hilltop/}).click();
  assert.equal(await page.locator('.node-details .node-actions').count(),0,'Offline node has no radio-send actions');
  assert.match(await page.locator('#nodes-list').innerText(),/Saved node · availability unknown/);
  await page.locator('#nodes-mt-device').selectOption('saved:!44444444');
  assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['selectDevice','mt','saved:!44444444']);
  await page.evaluate(()=>{
   mapData.nodes=[{radio:'mt',device:'!11111111',id:'!22222222',name:'Old hilltop',role:'Router',lat:38,lon:23,positionAt:1},{radio:'mt',device:'!44444444',id:'!55555555',name:'Field node',role:'Client',lat:39,lon:24,positionAt:1}];renderMapFilters();
  });
  assert.match(await page.locator('#map-device').innerText(),/Home receiver · offline/);
  assert.match(await page.locator('#map-device').innerText(),/Field receiver · offline/);
  assert.equal(await page.evaluate(()=>{el('map-device').value='mt:!44444444';return mapFilterRows()[0].id;}),'!55555555');
  await page.evaluate(()=>{
   fixture.mt.connected=true;fixture.mt.channelTextLimit=200;fixture.mt.directTextLimit=200;
   fixture.mt.channels=[{index:0,name:'Local'}];fixture.mt.nodes[0].savedOnly=false;
   fixture.devices=[{radio:'mt',key:fixture.mt.key,id:fixture.mt.ownId,name:'Home receiver'}];
   onRadioState(fixture);switchTab('chat');selectChannel('right',0);
   el('chat-input').value='x'.repeat(600);updateMessageBudget(el('chat-input'));
  });
  assert.equal(await page.locator('#chat-send').isEnabled(),true);
  await page.evaluate(()=>{el('chat-input').value='x'.repeat(601);updateMessageBudget(el('chat-input'));});
  assert.equal(await page.locator('#chat-send').isDisabled(),true);
  assert.match(await page.locator('#chat-input-budget').innerText(),/maximum 3/);
  assert.equal(await page.evaluate(()=>validMessageBudget(el('chat-input'))),false);
  assert.equal((await page.locator('#chat-input').inputValue()).length,601,'Rejected draft retained');
  await page.evaluate(()=>{el('chat-input').value='👨‍👩‍👧‍👦'.repeat(25);updateMessageBudget(el('chat-input'));});
  assert.equal(await page.locator('#chat-send').isDisabled(),true,'Grapheme overflow is rejected');
  await page.evaluate(()=>{fixture.multiSendCooldownMs=300000;onRadioState(fixture);el('chat-input').value='Still allowed';updateMessageBudget(el('chat-input'));});
  assert.equal(await page.locator('#new-multi-send').isDisabled(),true);
  assert.match(await page.locator('#new-multi-send').innerText(),/5:00/);
  assert.equal(await page.locator('#chat-send').isEnabled(),true,'Ordinary sends remain enabled');
  await page.evaluate(()=>{multiSendReadyAt=performance.now()-1;updateMultiSendAvailability();});
  assert.equal(await page.locator('#new-multi-send').isEnabled(),true);
  for(const [width,height] of [[360,640],[390,780],[780,390]]) {
   await page.setViewportSize({width,height});await page.evaluate(()=>switchTab('nodes'));
   assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Node filters fit viewport');
  }
  await page.screenshot({path:'build/offline-nodes-preview.png'});
  assert.deepEqual(errors,[]);
  console.log('Offline Nodes/Map, saved-device selection, three-part composer and cooldown UI passed');
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
