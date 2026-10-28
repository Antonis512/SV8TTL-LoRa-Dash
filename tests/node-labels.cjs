const {chromium}=require('playwright');const assert=require('node:assert/strict');const path=require('node:path');const {pathToFileURL}=require('node:url');
(async()=>{const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});try{
const page=await browser.newPage({viewport:{width:390,height:780}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
await page.addInitScript(()=>{window.calls=[];window.AndroidBridge=new Proxy({},{get:(_,name)=>(...args)=>{calls.push([name,...args]);if(name==='setNodeName'){f.nodeNames[args[0]+':'+args[1]]=args[2];onRadioState(f);}}});});
await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
await page.evaluate(()=>{
 const radio=r=>({key:r+'1',radio:r,name:r,ownId:'own-'+r,ownName:r,connected:true,loading:false,scanProgress:0,channels:[{index:0,name:'Local'}],nodes:[{id:'abcdefabcdef',name:'Alice',role:'User',hops:1,lastHeard:Math.floor(Date.now()/1000)}],directs:{abcdefabcdef:[{id:7,sender:'Alice',text:'Private history must stay in Chat',time:'12:00',own:false}]},chats:{0:[]}});
 window.f={mc:radio('mc'),mt:radio('mt'),devices:[],myNodes:[],sound:{mode:'shared',shared:{}},nodeNames:{}};onRadioState(f);switchTab('nodes');
});
assert.equal(await page.title(),'SV8TTL LoRa Dash');
const row=page.locator('.android-node').first();
await row.locator('.node-subtitle').click();assert.equal(await row.locator('.node-details').count(),1,'Whole card subtitle opens actions');
assert.equal(await page.locator('.node-dm-history,.node-direct').count(),0);
assert.equal(await row.getByText('Private history must stay in Chat',{exact:true}).count(),0);
await row.getByRole('button',{name:'Enter Name',exact:true}).click();
await page.getByRole('textbox',{name:'Node name',exact:true}).fill('Αντώνης <friend>');
await page.locator('#dm-context').getByRole('button',{name:'Save',exact:true}).click();
assert.equal(await row.locator('.node-name').innerText(),'Alice · Αντώνης <friend>');
assert.equal(await page.locator('.android-node').nth(1).locator('.node-name').innerText(),'Alice','Label scoped to radio network');
assert.equal(await row.locator('.node-name friend').count(),0,'Name is plain text');
await page.evaluate(()=>showMessageMenu('mc',{id:1,sender:'MeshCore',text:'Alice: Hello',own:false,time:'12:00'},{peer:'',channel:0}));
assert.equal(await page.locator('#dm-context strong').first().innerText(),'Message actions · Alice · Αντώνης <friend>');
await page.locator('#dm-context').getByRole('button',{name:'Node actions',exact:true}).click();
assert.equal(await page.locator('#dm-context .node-direct').count(),0);
await page.locator('#dm-context').getByRole('button',{name:'Enter Name',exact:true}).click();
assert.equal(await page.getByRole('textbox',{name:'Node name',exact:true}).inputValue(),'Αντώνης <friend>');
await page.getByRole('textbox',{name:'Node name',exact:true}).fill('');await page.locator('#dm-context').getByRole('button',{name:'Save',exact:true}).click();
assert.equal(await row.locator('.node-name').innerText(),'Alice');
await row.locator('.node-subtitle').click();assert.equal(await row.locator('.node-details').count(),0,'Whole card also collapses actions');
for(const width of [320,390,780]){await page.setViewportSize({width,height:640});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Header must fit viewport');}
await page.setViewportSize({width:390,height:780});await page.screenshot({path:'build/node-labels.png'});
assert.deepEqual(errors,[]);console.log('Node labels, clear/edit, message menu, whole-card taps and hidden DM history passed');
}finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});
