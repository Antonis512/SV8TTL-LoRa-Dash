const {chromium}=require('playwright');const assert=require('node:assert/strict');const path=require('node:path');const {pathToFileURL}=require('node:url');
(async()=>{const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});try{
 const page=await browser.newPage({viewport:{width:390,height:780},locale:'en-US'}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{window.calls=[];window.AndroidBridge=new Proxy({},{get:(_,name)=>(...args)=>calls.push([name,...args])});});
 await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
 await page.evaluate(()=>{
  const r=(radio,key,id)=>({radio,key,ownId:id,ownName:key,name:key,connected:true,loading:false,scanProgress:0,channels:[{index:0,name:'Local'}],nodes:[],directs:{},chats:{0:[]},state:'Connected'});
  window.mc1=r('mc','MC-Device1','abcdef012345'+'0'.repeat(52));window.mc2=r('mc','MC-Device2','abcdef543210'+'0'.repeat(52));window.mt1=r('mt','MT-Device1','!12345678');
  mc1.nodes=[{id:mc1.ownId.slice(0,12),name:'Self',role:'User'},{id:mc2.ownId.slice(0,12),name:'Other own radio',role:'User'},{id:'ffffffffffff',name:'MC-Node1',role:'Repeater'}];
  mt1.nodes=[{id:mt1.ownId,name:'Self',role:'Client'},{id:'!11111111',name:'MT-Node32',role:'Client',lastHeard:Math.floor(Date.now()/1000)}];
  window.f={mc:mc1,mt:mt1,left:mc1,right:mt1,devices:[mc1,mc2,mt1].map(r=>({key:r.key,radio:r.radio,id:r.ownId,name:r.name})),myNodes:[{radio:'mc',id:mc2.ownId,connected:false}],sound:{mode:'shared',shared:{}},selectedMc:mc1.key,selectedMt:mt1.key};
  onRadioState(f);switchTab('chat');
 });
 await page.evaluate(()=>{f.left={...mc1,ownId:'',key:'',connected:false};onRadioState(f);});
 assert.equal(await page.locator('#chat-advert-left').isVisible(),false,'No advert for an empty MC slot');
 await page.evaluate(()=>{f.left=mc1;onRadioState(f);});
 assert.deepEqual(await page.locator('#settings-home .settings-shortcuts strong').allTextContents().then(x=>x.slice(0,2)),['My Nodes','Add Radio Connections']);
 assert.equal(await page.locator('#settings-adverts-page').count(),0);
 assert.equal(await page.locator('#chat-advert-left').isVisible(),true);assert.equal(await page.locator('#chat-advert-right').isVisible(),false);
 await page.locator('#chat-advert-left').click();await page.getByRole('button',{name:'Zero Hop',exact:true}).click();assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['sendAdvert','MC-Device1','zero']);
 await page.evaluate(()=>{el('dm-context').hidden=true;f.left=mt1;f.right=mc2;onRadioState(f);});
 assert.equal(await page.locator('#chat-advert-left').isVisible(),false);assert.equal(await page.locator('#chat-advert-right').isVisible(),true);
 await page.locator('#chat-advert-right').click();await page.getByRole('button',{name:'Flood Routed',exact:true}).click();assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['sendAdvert','MC-Device2','flood']);
 await page.evaluate(()=>{el('dm-context').hidden=true;f.left=mc1;onRadioState(f);dmMode=false;renderChat();});
 for(const viewport of [{width:360,height:740},{width:390,height:780},{width:740,height:360}]){
  await page.setViewportSize(viewport);
  const boxes=await page.locator('#chat-font-bar button').evaluateAll(bs=>bs.map(b=>({id:b.id,x:b.getBoundingClientRect().x,right:b.getBoundingClientRect().right})));
  assert(boxes[0].x<boxes[1].x&&boxes[2].right<=boxes[3].x,'Adverts flank font controls');
  const bar=await page.locator('#chat-font-bar').boundingBox();
  assert(Math.abs(boxes[0].x-bar.x)<2&&Math.abs(boxes[3].right-(bar.x+bar.width))<2,'Adverts align with column edges');
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'No page overflow');
 }
 await page.setViewportSize({width:390,height:780});await page.screenshot({path:'build/chat-0109.png'});
 await page.evaluate(()=>{switchTab('nodes');el('nodes-show-unknown').checked=true;el('nodes-hide-unmessageable').checked=false;renderNodes();});
 assert.deepEqual(await page.locator('.android-node').evaluateAll(ns=>ns.map(n=>n.dataset.nodeKey).sort()),['mc:ffffffffffff','mt:!11111111']);
 await page.evaluate(()=>{openedNode='mt:!11111111';renderNodes();});await page.getByRole('button',{name:'Signal Quality',exact:true}).click();assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['nodeAction','mt','!11111111','signal','']);
 assert(!/\b(AM|PM)\b/.test(await page.locator('#nodes-list').innerText()),'24-hour node timestamps even in US locale');
 await page.evaluate(()=>{openedNode='mc:ffffffffffff';renderNodes();});await page.getByRole('button',{name:'Ping',exact:true}).click();await page.getByRole('button',{name:'Ping · 1-byte ID',exact:true}).click();assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['nodeAction','MC-Device1','ffffffffffff','ping','1']);
 assert.deepEqual(errors,[]);console.log('24h nodes, own-radio filtering, Signal Quality, Ping and per-column adverts passed');
}finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});
