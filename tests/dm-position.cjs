const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path');
const {pathToFileURL}=require('node:url');
(async()=>{const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});try{
 const page=await browser.newPage({viewport:{width:390,height:780},hasTouch:true}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{window.calls=[];window.AndroidBridge=new Proxy({},{get:(_,name)=>(...args)=>calls.push([name,...args])});});
 await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
 await page.evaluate(()=>{
  const radio=r=>({radio:r,key:r+'1',ownId:'own-'+r,ownName:r,connected:true,scanProgress:0,loading:false,channels:[{index:0,name:'Local'}],nodes:[{id:r==='mc'?'abcdefabcdef':'!12345678',name:'Alice',role:'User'},{id:r==='mc'?'bbbbbbbbbbbb':'!87654321',name:'Bob',role:'User'}],directs:{},chats:{}});
  window.fixture={mc:radio('mc'),mt:radio('mt'),devices:[],myNodes:[],sound:{mode:'shared',shared:{}}};onRadioState(fixture);switchTab('chat');
 });
 for(const [radio,peer] of [['mc','abcdefabcdef'],['mt','!12345678']]){
  await page.evaluate(({radio,peer})=>selectDmPeer(radio,peer),{radio,peer});
  assert(await page.locator('#dm-position').isVisible());assert(!(await page.locator('#dm-position').isDisabled()));
  await page.locator('#dm-input').fill('Keep my unsent draft');
  await page.locator('#dm-position').tap();assert(await page.locator('#member-location-confirm').isDisabled());
  assert.match(await page.locator('#member-location-target').innerText(),new RegExp(peer));
  await page.evaluate(()=>memberLocationFix(pendingMemberLocation.token,37.9,23.7,'Phone position'));
  await page.locator('#member-location-confirm').click();
  assert.deepEqual(await page.evaluate(()=>calls.filter(c=>c[0]==='nodeAction').at(-1)),['nodeAction',radio+'1',peer,'dm','m:37.900000,23.700000|Location|poi']);
  assert.equal(await page.locator('#dm-input').inputValue(),'Keep my unsent draft');
 }
 const sent=()=>page.evaluate(()=>calls.filter(c=>c[0]==='nodeAction').length);
 for(const change of ['recipient','device','disconnect']){
  await page.evaluate(()=>{fixture.mt.connected=true;onRadioState(fixture);selectDmPeer('mt','!12345678');});
  await page.locator('#dm-position').tap();await page.evaluate(()=>memberLocationFix(pendingMemberLocation.token,37.9,23.7,'Phone position'));
  const before=await sent();
  await page.evaluate(change=>{if(change==='recipient')selectDmPeer('mt','!87654321');else if(change==='device')state[dmSelection.radio].key='mt-other';else state[dmSelection.radio].connected=false;confirmMemberLocation();},change);
  assert.equal(await sent(),before);assert(await page.locator('#member-location-confirm').isDisabled());await page.evaluate(()=>el('member-location-dialog').close());
 }
 await page.evaluate(()=>{onRadioState(fixture);selectDmPeer('mt','!12345678');});
 await page.locator('#dm-position').tap();const before=await sent();
 await page.evaluate(()=>{window.oldToken=pendingMemberLocation.token;memberLocationFix(oldToken,NaN,NaN,'Location unavailable');confirmMemberLocation();});
 assert.equal(await sent(),before);assert(await page.locator('#member-location-confirm').isDisabled());
 await page.evaluate(()=>el('member-location-dialog').close());await page.waitForTimeout(50);
 assert.equal(await page.evaluate(()=>memberLocationFix(oldToken,37.9,23.7,'Late fix')),false);
 const size=await page.locator('#dm-position').boundingBox();
 await page.locator('#dm-input').focus();await page.setViewportSize({width:390,height:360});
 const small=await page.locator('#dm-position').boundingBox();assert.equal(small.height,size.height);assert.equal(small.width,size.width);
 assert(await page.locator('#dm-position').isVisible());assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 const navTop=await page.locator('#bottom-nav').evaluate(e=>e.getBoundingClientRect().top);
 for(const id of ['dm-position','dm-reply','dm-input']){const rect=await page.locator('#'+id).boundingBox();assert(rect.y>=0&&rect.y+rect.height<=navTop,id+' stays above navigation');}
 await page.screenshot({path:'build/previews/dm-position-keyboard.png'});
 await page.evaluate(()=>{state[dmSelection.radio].connected=false;renderDmScreen();});
 assert(await page.locator('#dm-position').isDisabled());
 assert.deepEqual(errors,[]);console.log('DM position: MC/MT target, confirmation, draft preservation, destination-change guards, unavailable/late GPS, disconnected state and keyboard size passed');
}finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});
