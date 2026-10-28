const {chromium}=require('playwright');const assert=require('node:assert/strict');const {pathToFileURL}=require('node:url');const path=require('node:path');
(async()=>{const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});try{
 const page=await browser.newPage({viewport:{width:390,height:780}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{window.calls=[];window.AndroidBridge=new Proxy({},{get:(_,name)=>(...args)=>calls.push([name,...args])});});
 await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
 await page.evaluate(()=>{
  const r=id=>({name:id,key:id+'1',ownId:id+'own',ownName:id,connected:true,loading:false,scanProgress:0,channels:[{index:2,name:'Friends'}],nodes:[{id:'!abcdef12',name:'Alice',role:'Client'}],chats:{2:[]},directs:{},state:'Connected'});
  window.f={mt:r('mt'),mc:r('mc'),devices:[{radio:'mt',key:'mt1',id:'mtown'}],myNodes:[],sound:{mode:'shared',shared:{}},selectedMt:'mt1',selectedMc:'mc1'};
  f.mt.chats[2]=[{id:1,packetId:123,text:'Original',sender:'!abcdef12',time:'12:00'},{id:2,packetId:124,replyId:123,reaction:true,text:'❤️',sender:'!11223344',time:'12:01'},{id:3,packetId:125,replyId:123,reaction:true,text:'👨‍👩‍👧‍👦',sender:'!55667788',time:'12:02'}];
  onRadioState(f);selectChannel('mt',2);
 });

 await page.evaluate(()=>{f.mt.nodes.push({id:'!11223344',name:'Bob',role:'Client',hops:2});onRadioState(f);});
 await page.locator('#chat-messages .reaction-chip').first().click();
 assert.match(await page.locator('#dm-context').innerText(),/Bob.*!11223344/);
 await page.getByRole('button',{name:'Close',exact:true}).click();
 await page.evaluate(()=>switchTab('settings'));
 assert.equal(await page.getByRole('button',{name:/Export debug log/}).count(),1);
 assert.equal(await page.getByRole('button',{name:/Export debug log/}).isVisible(),true);
 await page.getByRole('button',{name:/Export debug log/}).click();assert.equal(await page.evaluate(()=>calls.at(-1)[0]),'exportDebugLog');
 await page.evaluate(()=>{showSettingsPage('sounds');});
 assert.equal(await page.locator('fieldset.sound-frame').count(),3);
 await page.locator('#disconnect-sound-choice button').first().click();assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['pickSound','disconnect']);
 await page.evaluate(()=>{
  f.mc.nodes=[{id:'bbbbbbbbbbbb',name:'Repeater B',role:'Repeater',hops:3},{id:'cccccccccccc',name:'Repeater C',role:'Repeater',hops:0},{id:'dddddddddddd',name:'Unknown',role:'Repeater',hops:-1}];
  f.mt.nodes=[{id:'!11223344',name:'Bob',role:'Client',hops:2}];onRadioState(f);switchTab('nodes');el('nodes-show-unknown').checked=true;el('nodes-hide-unmessageable').checked=false;setNodeSort('hops-asc');
 });
 assert.deepEqual(await page.locator('.android-node').evaluateAll(rows=>rows.map(r=>r.dataset.nodeKey)),['mc:cccccccccccc','mt:!11223344','mc:bbbbbbbbbbbb','mc:dddddddddddd']);
 await page.locator('#nodes-sort').selectOption('hops-desc');
 assert.deepEqual(await page.locator('.android-node').evaluateAll(rows=>rows.map(r=>r.dataset.nodeKey)),['mc:bbbbbbbbbbbb','mt:!11223344','mc:cccccccccccc','mc:dddddddddddd']);
 await page.evaluate(()=>{openedNode='mc:bbbbbbbbbbbb';renderNodes();});
 await page.getByRole('button',{name:'Ping',exact:true}).click();await page.getByRole('button',{name:'Ping · 2-byte ID',exact:true}).click();
 assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['nodeAction','mc1','bbbbbbbbbbbb','ping','2']);
 await page.evaluate(()=>onTraceResult({action:'ping',radio:'mc',name:'Repeater B',pending:true}));assert.match(await page.locator('#dm-context').innerText(),/Pinging/);
 await page.evaluate(()=>{el('dm-context').hidden=true;switchTab('settings');showSettingsPage('sounds');});
 await page.screenshot({path:'build/settings-099.png',fullPage:true});
 assert.deepEqual(errors,[]);console.log('Settings, reaction sender, Ping and hop sorting checks passed');
}finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});

