const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const {pathToFileURL}=require('node:url');
const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
 try {
 const page=await browser.newPage({viewport:{width:390,height:780},hasTouch:true});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
  window.calls=[];
  window.AndroidBridge=new Proxy({}, {get:(_,name)=>(...args)=>{
   calls.push([name,...args]);
   if(name==='getBackupChannels') window.onBackupChannels(args[0],[{index:0,name:'Local'},{index:2,name:'Friends'}]);
  }});
 });
 await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
 await page.evaluate(()=>{
  const radio=id=>({name:id,ownName:id+' Long name',ownId:id==='mt'?'!12345678':'ownmc',key:id+'1',rfLabel:id==='mt'?'Long Fast':'869.618 MHz · BW 62.5 kHz · SF8 · CR4/5',connected:true,channels:[{index:0,name:'Local'}],nodes:[{id:id==='mt'?'!abcdef12':'abcdefabcdef',name:id+' User',role:'User',favorite:false,hops:1}],directs:{},chats:{},state:'Connected',loading:false,scanning:false,scanProgress:0});
  window.fixture={mc:radio('mc'),mt:radio('mt'),selectedMc:'mc1',selectedMt:'mt1',devices:['mc','mt'].map(r=>({radio:r,key:r+'1',id:r+'own',name:r+' Long name'})),myNodes:['mc','mt','mt'].map((r,i)=>({radio:r,id:'saved'+i,name:'Saved '+i,connected:false})),sound:{mode:'shared',shared:{selected:false,repeats:1}},audioEnabled:true};
  fixture.mt.nodes[0].heardSettings='Long Fast';
  fixture.mc.nodes[0].heardSettings='869.618 MHz · BW 62.5 kHz · SF8 · CR4/5';
  onRadioState(fixture);
 });
 assert.match(await page.locator('#hdr-time').innerText(),/^\d\d:\d\d - \d\d\/\d\d\/\d{4}$/);
 await page.evaluate(()=>{switchTab('settings');showSettingsPage('nodes');});
 assert.equal(await page.getByRole('button',{name:'View messages',exact:true}).count(),3);
 assert.equal(await page.locator('#connect-selected').isDisabled(),true);
 // WebView input must update actions without waiting for a later change event.
 await page.locator('#my-nodes-list input').first().evaluate(input=>{input.checked=true;input.dispatchEvent(new Event('input',{bubbles:true}));});
 assert.equal(await page.locator('#connect-selected').isEnabled(),true);
 await page.evaluate(()=>onRadioState(fixture));
 assert.equal(await page.locator('#my-nodes-list input').first().isChecked(),true);
 assert.equal(await page.locator('#connect-selected').isEnabled(),true);
 await page.locator('#my-nodes-list input').first().uncheck();
 assert.equal(await page.locator('#connect-selected').isDisabled(),true);
 // Preserve the control state if a radio refresh arrives before its change notification.
 await page.locator('#my-nodes-list input').first().evaluate(input=>{input.checked=true;onRadioState(fixture);});
 assert.equal(await page.locator('#my-nodes-list input').first().isChecked(),true);
 assert.equal(await page.locator('#connect-selected').isEnabled(),true);
 await page.locator('#my-nodes-list input').first().uncheck();
 await page.locator('#my-nodes-list input').nth(0).check();
 await page.locator('#my-nodes-list input').nth(1).check();
 assert.equal(await page.evaluate(()=>calls.filter(c=>c[0]==='connectKnown').length),0);
 await page.evaluate(()=>onRadioState(fixture));
 assert.equal(await page.locator('#my-nodes-list input:checked').count(),2);
 await page.locator('#connect-selected').click();
 assert.equal(await page.evaluate(()=>calls.filter(c=>c[0]==='connectKnown').length),2);
 await page.evaluate(()=>{fixture.myNodes[0].connected=true;fixture.myNodes[1].connecting=true;fixture.myNodes[1].detectedRadio='mc';onRadioState(fixture);});
 assert.equal(await page.locator('#my-nodes-list progress').count(),1);
 assert.equal(await page.locator('.radio-type-badge').nth(1).innerText(),'MC');
 assert.equal(await page.locator('.connected-label').evaluate(e=>getComputedStyle(e).color),'rgb(73, 219, 131)');
 assert.equal(await page.locator('#connect-selected').isDisabled(),true);
 await page.locator('#my-nodes-list input').nth(2).check();
 assert.equal(await page.locator('#connect-selected').isEnabled(),true);
 await page.locator('#connect-selected').click();
 assert.deepEqual(await page.evaluate(()=>calls.filter(c=>c[0]==='connectKnown').slice(2)),[['connectKnown','mt','saved2']]);
 await page.locator('#my-nodes-list input').nth(2).uncheck();
 assert.equal(await page.locator('#connect-selected').isDisabled(),true);
 await page.evaluate(()=>{fixture.myNodes[0].connected=false;fixture.myNodes[1].connecting=false;onRadioState(fixture);});
 assert.equal(await page.locator('#connect-selected').isEnabled(),true);
 await page.evaluate(()=>{fixture.myNodes[0].connected=true;fixture.myNodes[1].connecting=true;onRadioState(fixture);});
 assert.equal(await page.locator('#my-nodes-list input').first().isEnabled(),true);
 await page.locator('#disconnect-selected').click();
 assert.deepEqual(await page.evaluate(()=>calls.filter(c=>c[0]==='disconnectKnown').slice(-2)),[['disconnectKnown','mc','saved0'],['disconnectKnown','mt','saved1']]);
 await page.locator('#disconnect-all').click();assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['disconnectAll']);

 assert.deepEqual(errors,[]); console.log('Saved-radio selection and connection actions passed');
 } finally { await browser.close(); }
})();
