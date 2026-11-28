const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path');
const {pathToFileURL}=require('node:url');
(async()=>{const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});try{
 const page=await browser.newPage({viewport:{width:390,height:780},hasTouch:true}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{window.calls=[];window.AndroidBridge=new Proxy({},{get:(_,name)=>(...args)=>calls.push([name,...args])});});
 await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
 await page.evaluate(()=>{
  const radio=r=>({radio:r,key:r+'1',ownId:'own-'+r,ownName:r,connected:true,scanProgress:0,loading:false,channels:[{index:0,name:'Local'}],nodes:[{id:r==='mc'?'abcdefabcdef':'!12345678',name:'Alice',role:'User'}],directs:{},chats:{}});
  window.fixture={mc:radio('mc'),mt:radio('mt'),devices:[],myNodes:[],sound:{mode:'shared',shared:{}},persistentJobs:[]};onRadioState(fixture);switchTab('chat');selectDmPeer('mt','!12345678');
 });
 const row=page.locator('#dm-center .persistent-controls');assert.deepEqual(await row.locator('button').allTextContents(),['P. Msg','P. Location','Send position']);
 assert((await row.boundingBox()).y<(await page.locator('#dm-input').boundingBox()).y);
 await page.locator('#dm-input').fill('Check in');await page.evaluate(()=>reportPersistentComposer());
 assert.deepEqual(await page.evaluate(()=>calls.filter(c=>c[0]==='persistentComposer').at(-1)),['persistentComposer',true]);
  await row.getByRole('button',{name:'P. Msg',exact:true}).click();assert.equal(await page.locator('#persistent-text').inputValue(),'Check in');
 await page.locator('#persistent-interval').fill('1');await page.locator('#persistent-start').click();assert.equal(await page.evaluate(()=>calls.filter(c=>c[0]==='startPersistent').length),0);
 await page.locator('#persistent-interval').fill('2');await page.locator('#persistent-retries').fill('');await page.locator('#persistent-expiry').fill('');await page.locator('#persistent-start').click();
 const call=await page.evaluate(()=>calls.filter(c=>c[0]==='startPersistent').at(-1));assert.equal(call[2],'Check in');assert.deepEqual(call.slice(3),[false,2,0,-1]);assert.equal(JSON.parse(call[1]).peer,'!12345678');
 await page.evaluate(()=>onPersistentStarted());assert.equal(await page.locator('#dm-input').inputValue(),'Check in');await page.locator('#persistent-close').click();
 await row.getByRole('button',{name:'P. Location',exact:true}).click();assert(await page.locator('#persistent-text').isHidden());assert(await page.locator('#persistent-retries').isHidden());
 await page.locator('#persistent-start').click();assert.equal(await page.evaluate(()=>calls.filter(c=>c[0]==='startPersistent').at(-1)[3]),true);await page.locator('#persistent-close').click();
 await page.evaluate(()=>{fixture.persistentJobs=[{id:'j1',radio:'mt',nodeId:'own-mt',peer:'!12345678',channel:-1,label:'Alice',text:'Check in',location:false,state:'active',detail:'Waiting for confirmation',attempts:1,answerAvailable:true}];onRadioState(fixture);openPersistentJobs();});
 await page.getByRole('button',{name:'Mark answered / Stop',exact:true}).click();assert.deepEqual(await page.evaluate(()=>calls.filter(c=>c[0]==='persistentAction').at(-1)),['persistentAction','j1','answered']);
 await page.locator('#persistent-close').click();
 for(const size of [{width:320,height:640},{width:390,height:360},{width:600,height:400}]){
  await page.setViewportSize(size);await page.locator('#dm-input').focus();assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await page.screenshot({path:'build/previews/persistent-keyboard.png'});
  const nav=await page.locator('#bottom-nav').evaluate(e=>e.getBoundingClientRect().top);
  for(const selector of ['#dm-center [data-persistent-kind="message"]','#dm-center [data-persistent-kind="location"]','#dm-position','#dm-input','#dm-reply']){const box=await page.locator(selector).boundingBox();assert(box.y>=0&&box.y+box.height<=nav,selector+' fits above navigation');}
 }
 await page.setViewportSize({width:390,height:780});await page.screenshot({path:'build/previews/persistent-dm.png'});
 await page.evaluate(()=>{fixture.memberConfig={enabled:true,contacts:[],channels:{'mt:own-mt':[{index:0,name:'Local'}]}};fixture.memberLanguage='en';fixture.memberRadios=[fixture.mt];onRadioState(fixture);});
 assert.deepEqual(await page.locator('#member-composer .persistent-controls button').allTextContents(),['P. Msg','P. Location','Send my location']);
 assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:'build/previews/persistent-member.png'});
 assert.deepEqual(errors,[]);console.log('Persistent UI passed: order, destination, interval validation, optional limits, draft preservation, manual priority signal, answer action, narrow/keyboard layouts, member controls.');
}finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});
