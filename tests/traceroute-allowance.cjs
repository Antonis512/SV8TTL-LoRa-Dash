const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const {pathToFileURL}=require('node:url');
const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
 try {
  const page=await browser.newPage({viewport:{width:390,height:780}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(()=>{window.calls=[];window.AndroidBridge=new Proxy({},{get:(_,name)=>(...args)=>calls.push([name,...args])});});
  await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
  await page.evaluate(()=>{
   const radio=r=>({radio:r,key:r+'-receiver',name:r,ownName:r,ownId:r==='mt'?'!11111111':'abcdefabcdef',connected:true,loading:false,scanning:false,scanProgress:0,channels:[{index:0,name:'Local'}],nodes:[{id:r==='mt'?'!22222222':'aaaaaabbbbbb',name:r+' node',role:'User'}],directs:{},chats:{}});
   window.fixture={mc:radio('mc'),mt:radio('mt'),devices:[],myNodes:[],sound:{mode:'shared',shared:{}},mtTracerouteCooldownMs:30000};
   fixture.devices=['mc','mt'].map(r=>({radio:r,key:fixture[r].key,id:fixture[r].ownId,name:r,pathNodes:fixture[r].nodes}));
   onRadioState(fixture);switchTab('nodes');
   openedNode='mt:!22222222';renderNodes();
  });
  const tr=page.locator('#nodes-list [data-mt-traceroute]');
  assert.equal(await tr.isDisabled(),true);assert.match(await tr.innerText(),/TR \(\d+s\)/);
  await page.evaluate(()=>{openedNode='mc:aaaaaabbbbbb';renderNodes();});
  assert.equal(await page.locator('#nodes-list').getByRole('button',{name:'TR',exact:true}).isEnabled(),true,'MT cooldown does not affect MeshCore');
  await page.evaluate(()=>{fixture.mt.nodes.push({id:'!33333333',name:'Other MT node',role:'Client'});fixture.mtTracerouteCooldownMs=19000;onRadioState(fixture);openedNode='mt:!33333333';renderNodes();});
  assert.equal(await tr.isDisabled(),true,'Switching node shares remaining cooldown');
  assert.match(await tr.innerText(),/19s/);
  await page.evaluate(()=>mapTool('route'));
  await page.evaluate(()=>{const device=el('map-route-device');device.value='mt-receiver';device.onchange();});
  // Exercise the actual Map refresh callback without requiring an online map renderer.
  await page.evaluate(()=>{const device=el('map-route-device'),target=device.parentElement.parentElement.querySelectorAll('select')[1];target.value='!22222222';target.onchange();});
  assert.equal(await page.locator('[data-mt-traceroute="Request radio path"]').isDisabled(),true);
  await page.evaluate(()=>{mtTracerouteReadyAt=performance.now()-1;updateTracerouteButtons();});
  assert.equal(await page.locator('[data-mt-traceroute="Request radio path"]').isEnabled(),true);
  await page.evaluate(()=>{const device=el('map-route-device'),target=device.parentElement.parentElement.querySelectorAll('select')[1];target.value='';target.onchange();updateTracerouteButtons();});
  assert.equal(await page.locator('[data-mt-traceroute="Request radio path"]').isDisabled(),true,'Expiry cannot enable a Map action with no target');
  assert.deepEqual(errors,[]);
  console.log('Traceroute countdown shared by node actions and Map; MC and missing-target checks passed');
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
