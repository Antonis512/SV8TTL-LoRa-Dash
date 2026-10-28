const {chromium}=require('playwright');
const assert=require('node:assert/strict');const {pathToFileURL}=require('node:url');const path=require('node:path');
(async()=>{const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});try{
 const page=await browser.newPage({viewport:{width:390,height:780}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{window.calls=[];window.AndroidBridge=new Proxy({},{get:(_,name)=>(...args)=>calls.push([name,...args])});});
 await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
 await page.evaluate(()=>{switchTab('settings');showSettingsPage('sending');});
 assert.equal(await page.locator('#settings-sending-page .settings-shortcuts > button').count(),3);
 assert.equal(await page.locator('#settings-connections-page #mc-direct-attempts').count(),0);
 await page.getByRole('button',{name:'Message Split Split long messages and set the interval',exact:true}).click();
 assert.equal(await page.locator('#message-split-interval').isVisible(),true);
 await page.getByRole('button',{name:'Close Message Split',exact:true}).click();
 assert.equal(await page.locator('#settings-window-split').isVisible(),false);
 await page.evaluate(()=>openSettingsWindow('attempts'));await page.evaluate(()=>navigateBack());
 assert.equal(await page.locator('#settings-window-attempts').isVisible(),false);
 await page.evaluate(()=>{
  showSettingsPage('nodes');viewSavedMessages('mc','radio-a');
  window.savedFixture={radio:'mc',id:'radio-a',channels:[{index:2,name:'Friends'}],messages:Array.from({length:30},(_,i)=>({channel:2,sender:'Alice',text:'Saved message '+i,time:'12:00',own:false}))};
  onSavedNode(savedFixture);
 });
 assert.equal(await page.locator('#settings-window-history').isVisible(),true);
 assert.equal(await page.locator('#my-node-history .android-msg').count(),30);
 await page.getByRole('button',{name:'Close Saved messages',exact:true}).click();
 await page.evaluate(()=>onSavedNode(savedFixture));
 assert.equal(await page.locator('#settings-window-history').isVisible(),false,'Late response cannot reopen closed window');
 await page.evaluate(()=>{viewSavedMessages('mc','radio-a');onSavedNode(savedFixture);});
 await page.locator('#settings-window-history .settings-window-footer button').click();
 assert.equal(await page.locator('#settings-window-history').isVisible(),false);
 // Available WebView viewport models gesture, three-button and keyboard-reduced content areas.
 for(const viewport of [{width:390,height:740},{width:390,height:708},{width:320,height:460},{width:780,height:340}]){
  await page.setViewportSize(viewport);
  for(const name of ['multi','split','attempts','history']){
   await page.evaluate(name=>{if(name==='history'){viewSavedMessages('mc','radio-a');onSavedNode(savedFixture);}else openSettingsWindow(name);},name);
   const bounds=await page.locator('#settings-window-'+name).evaluate(d=>{const r=d.getBoundingClientRect(),x=d.querySelector('.window-x').getBoundingClientRect(),b=d.querySelector('.settings-window-footer button').getBoundingClientRect();return {left:r.left,right:r.right,top:r.top,bottom:r.bottom,xTop:x.top,xBottom:x.bottom,closeTop:b.top,closeBottom:b.bottom};});
   assert.ok(bounds.left>=0&&bounds.right<=viewport.width&&bounds.top>=0&&bounds.bottom<=viewport.height,JSON.stringify({name,viewport,bounds}));
   assert.ok(bounds.xTop>=0&&bounds.xBottom<=viewport.height&&bounds.closeTop>=0&&bounds.closeBottom<=viewport.height,'Both close buttons stay reachable');
   await page.evaluate(name=>closeSettingsWindow(name),name);
  }
 }
 await page.setViewportSize({width:390,height:780});await page.evaluate(()=>{showSettingsPage('sending');});await page.screenshot({path:'build/sending-0108.png'});
 await page.evaluate(()=>openSettingsWindow('split'));await page.screenshot({path:'build/split-settings-0108.png'});
 await page.evaluate(()=>{closeSettingsWindow('split');viewSavedMessages('mc','radio-a');onSavedNode(savedFixture);});await page.screenshot({path:'build/saved-messages-0108.png'});
 assert.deepEqual(errors,[]);console.log('Settings windows, saved-message dismissal, late responses and viewport checks passed');
}finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});
