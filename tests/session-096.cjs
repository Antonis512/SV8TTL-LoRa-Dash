const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const {pathToFileURL}=require('node:url');const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
 try {
 const page=await browser.newPage({viewport:{width:390,height:780}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{window.calls=[];window.AndroidBridge=new Proxy({},{get:(_,name)=>(...args)=>calls.push([name,...args])});});
 await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
 await page.evaluate(()=>{
  const radio=r=>({key:r+'1',ownId:r+'own',ownName:r,name:r,connected:false,channels:[],nodes:[],directs:{},chats:{},state:'Disconnected',scanProgress:0});
  window.fixture={mc:radio('mc'),mt:radio('mt'),devices:[],selectedMc:'mc1',selectedMt:'mt1',myNodes:[{radio:'mc',id:'mcown',name:'Saved MC'},{radio:'mt',id:'mtown',name:'Saved MT'}],sound:{mode:'shared',shared:{}}};
  onRadioState(fixture);
 });
 assert.equal(await page.locator('#tab-settings').isVisible(),true);
 await page.evaluate(()=>navigateBack());assert.equal(await page.locator('#tab-settings').isVisible(),true);
 await page.evaluate(()=>{switchTab('chat');onRadioState(fixture);});assert.equal(await page.locator('#tab-chat').isVisible(),true);
 await page.locator('#chat-mt-device').selectOption('mt|saved:mtown');
 assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['selectChatDevice','right','mt','saved:mtown']);
 await page.evaluate(()=>{
  fixture.mt.directs['!aabbccdd']=[{id:12,sender:'You',own:true,text:'Outgoing only',delivery:'failed: all radio retries exhausted',time:'12:00'}];onRadioState(fixture);openDmScreen();
 });
 assert.equal(await page.locator('#dm-mt-side .dm-contact').count(),1);
 await page.locator('#dm-mt-side .dm-contact').click();
 assert.match(await page.locator('#dm-chat-history').innerText(),/Outgoing only/);
 assert.equal(await page.locator('#dm-reply').isDisabled(),true);
 assert.equal(await page.locator('#dm-chat-history .body').evaluate(e=>!!(e.compareDocumentPosition(e.parentElement.querySelector('.message-delivery'))&Node.DOCUMENT_POSITION_FOLLOWING)),true);
 await page.evaluate(()=>{
  fixture.mt.connected=true;fixture.mt.channels=[{index:2,name:'Friends'}];fixture.mt.nodes=[{id:'!aabbccdd',name:'Alice',role:'Client',lastHeard:Date.now()/1000}];
  fixture.devices=[{radio:'mt',key:'mt1',id:'mtown',name:'MT'}];fixture.mt.chats[2]=[{id:5,packetId:123456,sender:'!aabbccdd',text:'Hello channel',time:'12:00'}];onRadioState(fixture);
 });
 await page.locator('#dm-input').fill('Continue');assert.equal(await page.locator('#dm-reply').isDisabled(),false);
 await page.evaluate(()=>{selectChannel('mt',2);replyToMessage('mt',fixture.mt.chats[2][0],{channel:2});});
 await page.locator('#chat-input').fill('Channel response');await page.locator('#chat-send').click();
 assert.deepEqual(await page.evaluate(()=>calls.filter(c=>c[0]==='sendChannelReply').at(-1)),['sendChannelReply','mt1',2,'Channel response',123456]);
 assert.equal(await page.evaluate(()=>dmMode),false);
 await page.evaluate(()=>{onComposeQueued('mt1','','Channel response');fixture.mc.nodes=[{id:'08aaaa',role:'User',name:'User'},{id:'08bbbb',role:'Repeater',name:'Hill'},{id:'08bbbb',role:'Repeater',name:'Hill'}];showMessagePath({paths:[{path:['08'],snr:8,rssi:-80}]},true);});
 assert.match(await page.locator('#dm-context').innerText(),/Hill \(08\)/);
 await page.evaluate(()=>{fixture.mc.nodes.push({id:'08cccc',role:'Repeater',name:'Other'});showMessagePath({paths:[{path:['08','08bbbb'],snr:8,rssi:-80}]},true);});
 assert.match(await page.locator('#dm-context').innerText(),/08 · multiple repeaters: Hill \[08bbbb\] \/ Other \[08cccc\].*Hill \(08bbbb\)/);
 await page.evaluate(()=>{fixture.mc.nodes=[{id:'08aabb123456',role:'Repeater',name:'Hill'},{id:'08aacc123456',role:'Repeater',name:'Valley'},{id:'08aabb654321',role:'User',name:'Client'}];showMessagePath({paths:[{path:['08AABB','07'],snr:8,rssi:-80}]},true);});
 assert.match(await page.locator('#dm-context').innerText(),/Hill \(08AABB\).*07 · name unavailable/);
 assert.doesNotMatch(await page.locator('#dm-context').innerText(),/Client/);
 await page.evaluate(()=>{state.left={...fixture.mc,nodes:[{id:'08ff00123456',role:'Repeater',name:'Other radio hill'}]};showMessagePath({paths:[{path:['08'],snr:8,rssi:-80}]},true,'left');});
 assert.match(await page.locator('#dm-context').innerText(),/Other radio hill \(08\)/);
 assert.doesNotMatch(await page.locator('#dm-context').innerText(),/Valley/);
 await page.evaluate(()=>{onNodeActionResult('Direct message sent to radio for Alice');});
 assert.equal(await page.locator('#nodes-action-status').innerText(),'');assert.equal(await page.locator('#advert-status').innerText(),'');assert.equal(await page.locator('#popup-action-status').count(),0);
 await page.evaluate(()=>{el('dm-context').hidden=true;fixture.mc.connected=true;fixture.devices.push({radio:'mc',key:'mc1',id:'mcown',name:'MC'});onRadioState(fixture);switchTab('settings');showSettingsPage('adverts');showAdvertActions();onAdvertResult('other','Advert accepted by radio',true);});
 assert.equal(await page.locator('#advert-dismiss').innerText(),'Cancel');
 await page.evaluate(()=>onAdvertResult('mc1','MeshCore command error 2',false));assert.equal(await page.locator('#advert-dismiss').innerText(),'Cancel');
 await page.evaluate(()=>onAdvertResult('mc1','Advert accepted by radio',true));assert.equal(await page.locator('#advert-dismiss').innerText(),'OK');
 await page.locator('#advert-dismiss').click();
 await page.evaluate(()=>{
  const now=Date.now()/1000;fixture.mc.nodes=[{id:'01',name:'Recent',role:'User',lastHeard:now},{id:'02',name:'Old',role:'User',lastHeard:now-31*86400},{id:'03',name:'Unknown',role:'User',lastHeard:0}];fixture.mt.nodes=[];onRadioState(fixture);switchTab('nodes');
 });
 assert.equal(await page.locator('.node-heading').count(),1);
 await page.locator('#nodes-show-unknown').check();assert.equal(await page.locator('.node-heading').count(),2);
 await page.evaluate(()=>{fixture.mc.nodes[1].lastHeard=Date.now()/1000;onRadioState(fixture);});assert.equal(await page.locator('.node-heading').count(),3);
 await page.evaluate(()=>{switchTab('settings');showSettingsPage('about');onAbout('0.9.6','MIT License\nCopyright (c) 2026 Filip');});
 assert.match(await page.locator('#settings-about-page').innerText(),/Beta edition · 0.9.6/);
 await page.screenshot({path:'build/about-096.png'});
 for(const viewport of [{width:390,height:780},{width:390,height:430},{width:780,height:390},{width:360,height:640}]) {
  await page.setViewportSize(viewport);await page.evaluate(()=>{switchTab('chat');selectChannel('mt',2);replyToMessage('mt',fixture.mt.chats[2][0],{channel:2});});
  const box=await page.locator('#chat-input').boundingBox(),nav=await page.locator('#bottom-nav').boundingBox();assert.ok(box.y>=0&&box.y+box.height<=nav.y+1);
 }
 assert.deepEqual(errors,[]);console.log('0.9.6 startup, history, replies, status placement, collision, advert, node age and About checks passed');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
