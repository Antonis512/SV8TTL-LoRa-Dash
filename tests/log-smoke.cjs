const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const {pathToFileURL}=require('node:url');
const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
 try{
  const page=await browser.newPage({viewport:{width:390,height:780},hasTouch:true});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(()=>{
   window.savedLog=[];window.savedTemplates=[{title:'Status Update',text:'Status:\nLocation:\nNext update:'}];
   window.AndroidBridge=new Proxy({}, {get:(_,name)=>(...args)=>{
    if(name==='loadLogItems')setTimeout(()=>window.onLogItems(window.savedLog),0);
    if(name==='saveLogItem'){const item=JSON.parse(args[0]);window.savedLog=window.savedLog.filter(x=>x.id!==item.id).concat(item);setTimeout(()=>window.onLogItems(window.savedLog),0);}
    if(name==='deleteLogItems'){const ids=JSON.parse(args[0]);window.savedLog=window.savedLog.filter(x=>!ids.includes(x.id));setTimeout(()=>window.onLogItems(window.savedLog),0);}
    if(name==='savePresetMessages'){window.savedTemplates=JSON.parse(args[0]);setTimeout(()=>{state.presetMessages=window.savedTemplates;window.onPresetMessagesSaved(true,'Saved');},0);}
   }});
  });
  await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
  await page.evaluate(()=>{state={presetMessages:window.savedTemplates,mt:{channelTextLimit:200},mc:{directTextLimit:138,channelTextLimit:120}};switchTab('log');});
  await page.getByRole('button',{name:'+ New'}).click();
  await page.locator('#log-title').fill('Bridge directions');
  await page.locator('#log-body').fill('Turn left at the bridge.');
  await page.locator('#log-new-category').fill('Travel');
  await page.locator('#log-editor').getByRole('button',{name:'Add category'}).click();
  await page.locator('#log-editor-categories input:checked').waitFor();
  await page.locator('#log-editor').getByRole('button',{name:'Save',exact:true}).click();
  await page.getByRole('button',{name:/Bridge directions/}).waitFor();
  await page.screenshot({path:'build/log-list-preview.png'});
  await page.setViewportSize({width:360,height:640});
  await page.screenshot({path:'build/log-list-compact-preview.png'});
  await page.setViewportSize({width:390,height:780});
  assert.equal(await page.evaluate(()=>savedLog.filter(x=>x.kind==='note')[0].categories.length),1);
  await page.getByRole('button',{name:/Bridge directions/}).click();
  await page.locator('#log-detail-categories button').click();
  assert.equal(await page.locator('#log-list .log-item').count(),1);
  await page.evaluate(()=>addMessageToLog({own:false,sender:'Maria',time:'31/10/2026 14:20',network:'MC',device:'Field Radio',conversation:'Team',text:'Take the north road.'}));
  await page.screenshot({path:'build/log-editor-preview.png'});
  assert.match(await page.locator('#log-body').inputValue(),/Device: Field Radio[\s\S]*Take the north road/);
  await page.locator('#log-editor').getByRole('button',{name:'Cancel'}).click();
  await page.evaluate(()=>openMessageTemplateEditor('Check in at the bridge.'));
  await page.screenshot({path:'build/message-template-preview.png'});
  assert.match(await page.locator('#message-template-budget').innerText(),/MT.*MC DM.*MC channel/);
  await page.locator('#message-template-title').fill('Check In');
  await page.locator('#message-template-editor').getByRole('button',{name:'Save template'}).click();
  await page.waitForFunction(()=>savedTemplates.length===2);
  assert.deepEqual(errors,[]);
  console.log('Log smoke passed: note, category, Add to Log, message template and byte counter.');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
