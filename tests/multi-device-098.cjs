const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const {pathToFileURL}=require('node:url');const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
 try {
  const page=await browser.newPage({viewport:{width:390,height:780}}),errors=[];
  page.setDefaultTimeout(5000);page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(()=>{
   window.calls=[];window.AndroidBridge=new Proxy({},{get:(_,name)=>(...args)=>{
    calls.push([name,...args]);
    if(name==='selectChatDevice'){
     const [side,radio,key]=args;
     fixture[side]=key?radios.find(r=>r.radio===radio&&(r.key===key||'saved:'+r.ownId===key)):blank();
     onRadioState(fixture);
    }
    if(name==='deleteConversation'){
     const r=radios.find(r=>r.key===args[0]);delete r.directs[args[1]];onRadioState(fixture);
    }
   }});
  });
  await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
  await page.evaluate(()=>{
   window.blank=()=>({radio:'mt',key:'empty',ownId:'',ownName:'',connected:false,channels:[],nodes:[],directs:{},chats:{},state:'Disconnected'});
   window.radios=['mc','mt','mt'].map((radio,i)=>({radio,key:'device'+i,ownId:'own'+i,ownName:'Radio '+i,name:'Radio '+i,connected:true,channels:[{index:i,name:'Channel name '+i}],nodes:[{id:'!abcdef12',name:'Alice '+i,role:radio==='mc'?'User':'Client'},{id:'!11223344',name:'No conversation',role:'Client'}],directs:{'!abcdef12':[{id:10+i,packetId:100+i,sender:'!abcdef12',text:'DM on '+i,time:'12:00'}]},chats:{[i]:[{id:20+i,packetId:200+i,sender:'!abcdef12',text:'Channel on '+i,time:'12:00'}]},state:'Connected',scanProgress:0,loading:false,directTextLimit:200,channelTextLimit:200}));
   window.fixture={mc:radios[0],mt:radios[1],left:radios[0],right:radios[1],selectedMc:'device0',selectedMt:'device1',devices:radios.map(r=>({key:r.key,radio:r.radio,id:r.ownId,name:r.name})),myNodes:radios.map(r=>({id:r.ownId,radio:r.radio,name:r.name,connected:true})),sound:{mode:'shared',shared:{}}};
   onRadioState(fixture);switchTab('chat');
  });
  assert.equal(await page.locator('#chat-mc-device option').count(),4);
  assert.equal(await page.locator('#chat-mt-device option').count(),4);
  assert.equal(await page.locator('#chat-header-bar #chat-mc-device').count(),1);
  assert.equal(await page.locator('#chat-device-bar #header-mc-channel').count(),1);
  await page.locator('#chat-mc-device').selectOption('mt|device1');
  await page.locator('#chat-mt-device').selectOption('mt|device2');
  assert.equal(await page.locator('#header-mc-pill').innerText(),'MT');
  assert.equal(await page.locator('#header-mt-pill').innerText(),'MT');
  await page.locator('#header-mc-channel').selectOption('1');
  assert.match(await page.locator('#chat-messages').innerText(),/Channel on 1/);
  await page.locator('#header-mt-channel').selectOption('2');
  assert.match(await page.locator('#chat-messages').innerText(),/Channel on 2/);
  await page.locator('#chat-messages .body').click();await page.getByRole('button',{name:'Emoji',exact:true}).click();await page.getByRole('button',{name:'React 👍',exact:true}).click();
  assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['react','device2',22,'👍']);
  await page.evaluate(()=>{radios[2].connected=false;fixture.devices=fixture.devices.filter(d=>d.key!=='device2');fixture.myNodes[2].connected=false;onRadioState(fixture);});
  assert.equal(await page.locator('#header-mt-pill').evaluate(e=>e.classList.contains('offline')),true);
  assert.match(await page.locator('#chat-messages').innerText(),/Channel on 2/);
  assert.equal(await page.locator('#chat-send').isDisabled(),true);
  // A real long press selects saved conversations without requiring Config.
  const pill=await page.locator('#chat-dm-pill').boundingBox();await page.mouse.move(pill.x+pill.width/2,pill.y+pill.height/2);await page.mouse.down();await page.waitForTimeout(650);await page.mouse.up();
  assert.equal(await page.evaluate(()=>savedDmMode),true);
  assert.deepEqual(await page.locator('#dm-mc-side .dm-contact').allTextContents(),['Alice 1']);
  assert.deepEqual(await page.locator('#dm-mt-side .dm-contact').allTextContents(),['Alice 2']);
  await page.locator('#dm-mc-side .dm-contact').click();
  assert.match(await page.locator('#dm-chat-history').innerText(),/DM on 1/);
  await page.locator('#dm-input').fill('Draft for device 1');await page.locator('#dm-input').blur();
  await page.locator('#dm-mt-side .dm-contact').click();
  assert.equal(await page.locator('#dm-input').inputValue(),'');
  await page.locator('#dm-mc-side .dm-contact').click();
  assert.equal(await page.locator('#dm-input').inputValue(),'Draft for device 1');
  await page.locator('#dm-input').fill('Continue left');await page.locator('#dm-reply').click();
  assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['nodeAction','device1','!abcdef12','dm','Continue left']);
  await page.evaluate(()=>onComposeQueued('device1','!abcdef12','Continue left'));
  await page.locator('#dm-mt-side .dm-contact').click();
  assert.match(await page.locator('#dm-chat-history').innerText(),/DM on 2/);
  assert.equal(await page.locator('#dm-reply').isDisabled(),true);
  await page.locator('#dm-mt-side .dm-contact').dispatchEvent('contextmenu');
  assert.deepEqual(await page.locator('#dm-context button').allTextContents(),['Delete this Conversation','Cancel']);
  await page.getByRole('button',{name:'Cancel',exact:true}).dispatchEvent('click');
  assert.equal(await page.evaluate(()=>calls.some(c=>c[0]==='deleteConversation')),false);
  await page.waitForTimeout(500);
  await page.locator('#dm-mt-side .dm-contact').dispatchEvent('contextmenu');
  await page.waitForTimeout(500);await page.getByRole('button',{name:'Delete this Conversation',exact:true}).click();
  assert.deepEqual(await page.evaluate(()=>calls.find(c=>c[0]==='deleteConversation')),['deleteConversation','device2','!abcdef12']);
  assert.equal(await page.locator('#dm-mt-side .dm-contact').count(),0);
  assert.equal(await page.locator('#dm-mc-side .dm-contact').count(),1);
  await page.locator('#chat-mt-device').selectOption('');
  assert.equal(await page.locator('#header-mt-pill').innerText(),'');
  assert.equal(await page.locator('#header-mt-pill').evaluate(e=>e.classList.contains('offline')),false);
  await page.locator('#chat-mt-device').selectOption('mc|device0');
  assert.equal(await page.locator('#header-mt-pill').innerText(),'MC');
  for(const [width,height] of [[360,640],[390,780],[780,390]]){
   await page.setViewportSize({width,height});
   assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  }
  await page.setViewportSize({width:390,height:780});
  await page.screenshot({path:'build/multi-device-098.png'});
  await page.evaluate(()=>{switchTab('settings');showSettingsPage('home');});
  assert.deepEqual((await page.locator('.settings-shortcuts strong').allTextContents()).slice(-2),['Export debug log','About']);
  await page.evaluate(()=>showSettingsPage('nodes'));
  const available=page.getByRole('button',{name:'Connect All Available',exact:true});
  assert.equal(await available.evaluate(e=>getComputedStyle(e).backgroundColor),await page.getByRole('button',{name:'View messages',exact:true}).first().evaluate(e=>getComputedStyle(e).backgroundColor));
  const heading=page.locator('.known-node-heading').first();
  const nameBox=await heading.locator('strong').first().boundingBox(),statusBox=await heading.locator('.connected-label').boundingBox();
  assert.ok(Math.abs(nameBox.y-statusBox.y)<2,'Connected should share the name row when it fits');
  await page.evaluate(()=>{fixture.myNodes[0].name='A very long radio name which must wrap without hiding the connection status';onRadioState(fixture);});
  await page.setViewportSize({width:320,height:640});
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await page.screenshot({path:'build/my-nodes-layout.png'});
  assert.deepEqual(errors,[]);console.log('Multi-device Chat and My Nodes layout/config ordering passed.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
