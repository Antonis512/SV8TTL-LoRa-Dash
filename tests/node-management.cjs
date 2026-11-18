const {chromium}=require('playwright');
const assert=require('node:assert/strict'),path=require('node:path');
const {pathToFileURL}=require('node:url');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROME_PATH||'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
 try{
  const page=await browser.newPage({viewport:{width:390,height:780}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(()=>{window.calls=[];window.AndroidBridge=new Proxy({},{get:(_,name)=>(...args)=>{calls.push([name,...args]);if(name==='generateChannelKey')return args[0]?'01020304050607080910111213141516':'AQIDBAUGBwgJCgsMDQ4PEA==';}});});
  await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
  await page.evaluate(()=>{openNodeManagement({radio:'mt',id:'!123',name:'My MT'});onNodeManagement({radio:'mt',id:'!123',ready:true,channels:[{index:0,name:'LongFast'}],freeSlot:1,busy:false,blocked:false,message:''});});
  await page.getByRole('button',{name:'Channels Add a channel to this radio'}).click();
  assert.equal(await page.locator('#node-channel-list').innerText(),'LongFast');
  await page.locator('#node-channel-name').fill('Rescue');
  await page.getByRole('button',{name:'Generate key',exact:true}).click();
  await page.getByRole('button',{name:'Add to this radio',exact:true}).click();
  assert.deepEqual(await page.evaluate(()=>calls.filter(c=>c[0]==='addRadioChannel').at(-1)),['addRadioChannel','mt','!123','Rescue','private','AQIDBAUGBwgJCgsMDQ4PEA==']);
  await page.evaluate(()=>onNodeManagement({radio:'mt',id:'!123',ready:true,channels:[{index:0,name:'LongFast'}],freeSlot:1,busy:true,blocked:true,message:'Adding channel…'}));
  assert(await page.locator('#node-channel-add').isDisabled());
  assert(await page.locator('#node-channel-key').isDisabled());
  await page.evaluate(()=>{closeSettingsWindow('node-management');});await page.waitForTimeout(30);
  assert.equal(await page.locator('#node-channel-key').inputValue(),'');
  await page.evaluate(()=>{openNodeManagement({radio:'mc',id:'abcdef',name:'My MC'});showNodeManagementSection('channels');onNodeManagement({radio:'mc',id:'abcdef',ready:true,channels:[{index:0,name:'Public'}],freeSlot:3,busy:false,blocked:false,message:''});});
  assert.equal(await page.locator('#node-channel-mode').inputValue(),'hashtag');
  assert(await page.locator('#node-channel-key-fields').isHidden());
  await page.locator('#node-channel-name').fill('#local');await page.locator('#node-channel-add').click();
  assert.deepEqual(await page.evaluate(()=>calls.filter(c=>c[0]==='addRadioChannel').at(-1)),['addRadioChannel','mc','abcdef','#local','hashtag','']);
  await page.evaluate(()=>onNodeManagement({radio:'mt',id:'!123',ready:true,channels:[{index:0,name:'Wrong radio'}],freeSlot:1,busy:false,blocked:false,message:''}));
  assert.equal(await page.locator('#node-channel-list').innerText(),'Public');
  await page.evaluate(()=>onNodeManagement({radio:'mc',id:'abcdef',ready:false,channels:[],freeSlot:-1,busy:false,blocked:false,message:'Connect this radio to manage its channels.'}));
  assert(await page.locator('#node-channel-add').isDisabled());
  for(const [width,height]of [[320,640],[390,780],[780,390]]){await page.setViewportSize({width,height});assert(await page.locator('#settings-window-node-management').evaluate(e=>e.scrollWidth<=e.clientWidth+1),'Dialog fits viewport');}
  await page.setViewportSize({width:390,height:780});
  await page.evaluate(()=>onNodeManagement({radio:'mc',id:'abcdef',ready:true,channels:[{index:0,name:'Public'}],freeSlot:3,busy:false,blocked:false,message:'Channel added and verified on the radio.'}));
  await page.screenshot({path:'build/previews/node-management.png'});
  assert.deepEqual(errors,[]);console.log('Node management UI checks passed');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
