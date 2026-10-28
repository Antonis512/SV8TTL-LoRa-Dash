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
  const radio=id=>({name:id,ownName:id+' Long name',ownId:id==='mt'?'!12345678':'ownmc',key:id+'1',connected:true,channels:[{index:0,name:'Local'}],nodes:[{id:id==='mt'?'!abcdef12':'abcdefabcdef',name:id+' User',role:'User',favorite:false,hops:1}],directs:{},chats:{},state:'Connected',loading:false,scanning:false,scanProgress:0});
  window.fixture={mc:radio('mc'),mt:radio('mt'),selectedMc:'mc1',selectedMt:'mt1',devices:['mc','mt'].map(r=>({radio:r,key:r+'1',id:r+'own',name:r+' Long name'})),myNodes:['mc','mt','mt'].map((r,i)=>({radio:r,id:'saved'+i,name:'Saved '+i,connected:false})),sound:{mode:'shared',shared:{selected:false,repeats:1}},audioEnabled:true};
  onRadioState(fixture);
 });
 assert.match(await page.locator('#hdr-time').innerText(),/^\d\d:\d\d - \d\d\/\d\d\/\d{4}$/);
 await page.evaluate(()=>{switchTab('settings');showSettingsPage('nodes');});
 assert.equal(await page.getByRole('button',{name:'View messages',exact:true}).count(),3);
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
 assert.equal(await page.locator('#my-nodes-list input').first().isEnabled(),true);
 await page.locator('#disconnect-selected').click();
 assert.deepEqual(await page.evaluate(()=>calls.filter(c=>c[0]==='disconnectKnown').slice(-2)),[['disconnectKnown','mc','saved0'],['disconnectKnown','mt','saved1']]);
 await page.locator('#disconnect-all').click();assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['disconnectAll']);
 await page.evaluate(()=>showSettingsPage('home'));
 await page.getByRole('button',{name:/Export debug log/}).click();assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['exportDebugLog']);
 await page.evaluate(()=>navigateBack());
 await page.evaluate(()=>{window.savedTestNavigation=[...navigationStack];switchTab('nodes');});
 for(const [width,height] of [[360,640],[390,780],[780,390]]) {
   await page.setViewportSize({width,height});
   const filters=await page.locator('.node-filter-options').boundingBox();
   const selector=await page.locator('#nodes-mt-device').boundingBox();
   assert.ok(filters.y>=selector.y+selector.height);
   assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 }
 await page.setViewportSize({width:390,height:780});
 await page.screenshot({path:'build/nodes-filters.png'});
 await page.evaluate(()=>{switchTab('settings');showSettingsPage('nodes');});
 await page.screenshot({path:'build/my-nodes-debug.png'});
 await page.evaluate(()=>{navigationStack.splice(0,navigationStack.length,...savedTestNavigation);});
 await page.evaluate(()=>navigateBack());
 assert.equal(await page.locator('#settings-home').isVisible(),true);
 await page.evaluate(()=>navigateBack());
 assert.equal(await page.locator('#tab-chat').isVisible(),true);
 await page.evaluate(()=>selectChannel('mt',0));
 assert.match(await page.locator('#channel-chat-title').innerText(),/mt Long name/);
 await page.locator('#channel-mt-side .dm-contact').dispatchEvent('contextmenu');
 assert.equal(await page.locator('#dm-context').isVisible(),true);
 assert.equal(await page.locator('#dm-context button').filter({hasText:/^TR$/}).count(),1);
 assert.equal(await page.locator('#dm-context button').filter({hasText:'Export chat as CSV'}).count(),0);
 assert.equal(await page.locator('#channel-mt-side .dm-contact').evaluate(e=>getComputedStyle(e).userSelect),'none');
 await page.locator('#dm-context').click({position:{x:3,y:100}});
 assert.equal(await page.locator('#dm-context').isVisible(),false);
 await page.locator('#channel-mc-side .dm-contact').dispatchEvent('contextmenu');
 assert.equal(await page.locator('#dm-context button').filter({hasText:/^TR$/}).count(),1);
 await page.evaluate(()=>navigateBack());
 await page.evaluate(()=>selectDmPeer('mt','!abcdef12'));
 await page.locator('#dm-mt-side .dm-contact').dispatchEvent('contextmenu');
 assert.equal(await page.locator('#dm-context button').filter({hasText:'Export chat as CSV'}).count(),1);
 await page.evaluate(()=>navigateBack());
 await page.evaluate(()=>{switchTab('settings');showSettingsPage('channel-backup');});
 await page.locator('#backup-device').selectOption('mt1');
 await page.locator('#backup-channel').selectOption('2');
 await page.locator('#backup-channel-export').click();
 assert.deepEqual(await page.evaluate(()=>calls.find(c=>c[0]==='exportChannelCsv')),['exportChannelCsv','mt1',2]);
 await page.evaluate(()=>{fixture.devices=[];onRadioState(fixture);});
 assert.equal(await page.locator('#backup-channel-export').isDisabled(),true);
 assert.equal(await page.locator('#message-banner').count(),0);
 await page.evaluate(()=>{showSettingsPage('sounds');fixture.backgroundMessageNotifications=false;fixture.messageNotificationsAllowed=true;onRadioState(fixture);});
 assert.equal(await page.locator('#background-message-notifications').isChecked(),false);
 await page.locator('#background-message-notifications').check();
 assert.deepEqual(await page.evaluate(()=>calls.find(c=>c[0]==='setBackgroundMessageNotifications')),['setBackgroundMessageNotifications',true]);
 await page.evaluate(()=>{fixture.backgroundMessageNotifications=true;onRadioState(fixture);});
 assert.match(await page.locator('#message-notifications-status').innerText(),/are on/);
 await page.evaluate(()=>{fixture.messageNotificationsAllowed=false;onRadioState(fixture);});
 assert.match(await page.locator('#message-notifications-status').innerText(),/blocked/);
 await page.getByRole('button',{name:'Android notification settings',exact:true}).click();
 assert.ok(await page.evaluate(()=>calls.some(c=>c[0]==='openMessageNotificationSettings')));
 await page.locator('#background-message-notifications').uncheck();
 assert.ok(await page.evaluate(()=>calls.some(c=>c[0]==='setBackgroundMessageNotifications'&&c[1]===false)));
 await page.evaluate(()=>onActionNotice('Bluetooth','Enable Bluetooth first'));
 assert.ok(await page.evaluate(()=>calls.some(c=>c[0]==='showActionNotice'&&c[1]==='Bluetooth')));
 for(const height of [780,752,420]) {
  await page.setViewportSize({width:360,height});
  await page.evaluate(()=>{switchTab('chat');});
  const close=await page.locator('#app-close').boundingBox();
  assert.ok(close.x>=0&&close.x+close.width<=360,'Close button fits narrow viewport');
  const nav=await page.locator('#bottom-nav').boundingBox();assert.ok(nav.y+nav.height<=height+1);
 }
 await page.setViewportSize({width:390,height:780});
 await page.evaluate(()=>{fixture.devices=[{radio:'mt',key:'mt1',id:'mtown',name:'MT Long name'}];onRadioState(fixture);switchTab('settings');showSettingsPage('channel-backup');});
 await page.screenshot({path:'build/channel-backup-preview.png'});
 await page.evaluate(()=>{switchTab('chat');selectChannel('mt',0);showMessageNodeActions('mt','!abcdef12');});
 await page.screenshot({path:'build/contact-actions-preview.png'});
 await page.evaluate(()=>navigateBack());
 await page.locator('#app-close').click();
 assert.equal(await page.evaluate(()=>calls.some(c=>c[0]==='closeApp')),true);
 assert.deepEqual(errors,[]);
 console.log('UI smoke passed: navigation, contacts, selection, backup, notification controls, viewport and close.');
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});

