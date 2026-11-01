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
 assert.equal(await page.locator('#chat-messages .android-msg').count(),1);
 assert.deepEqual(await page.locator('#chat-messages .reaction-chip').allTextContents(),['❤️','👨‍👩‍👧‍👦']);
 const bubble=await page.locator('#chat-messages .android-msg').boundingBox(),reactions=await page.locator('.message-reactions').boundingBox();assert.ok(reactions.x<bubble.x+bubble.width/2&&reactions.y>bubble.y);
 await page.locator('#chat-messages .body').click();await page.getByRole('button',{name:'Emoji',exact:true}).click();assert.equal(await page.locator('.reaction-choices button').count(),9);
 assert.deepEqual(await page.locator('.reaction-choices button').allTextContents(),['👍','0','1','2','3','4','5','6','7']);
 await page.getByRole('button',{name:'React 0',exact:true}).click();assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['react','mt1',1,'0']);
 await page.locator('#chat-input').fill('Send and return');assert.equal(await page.locator('body').evaluate(e=>e.classList.contains('composing')),true);
 await page.locator('#chat-send').click();assert.equal(await page.locator('#chat-input').inputValue(),'Send and return');
 await page.evaluate(()=>onComposeQueued('mt1','','Send and return'));
 assert.equal(await page.locator('#chat-input').inputValue(),'');assert.equal(await page.locator('body').evaluate(e=>e.classList.contains('composing')),false);
 assert.equal(await page.locator('#channel-mt-side').isVisible(),true);assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['finishComposing']);
 assert.equal(await page.evaluate(()=>network==='right'&&channel.right===2&&!dmMode),true);
 await page.evaluate(()=>{f.mt.directs['!abcdef12']=f.mt.chats[2];onRadioState(f);selectDmPeer('mt','!abcdef12');});
 assert.equal(await page.locator('#dm-chat-history .android-msg').count(),1);assert.equal(await page.locator('#dm-chat-history .reaction-chip').count(),2);
 await page.locator('#dm-input').fill('DM return');await page.locator('#dm-reply').click();await page.evaluate(()=>onComposeQueued('mt1','!abcdef12','DM return'));
 assert.equal(await page.evaluate(()=>dmMode&&dmSelection.peer==='!abcdef12'&&!document.body.classList.contains('composing')),true);
 await page.locator('#dm-input').fill('Keep on error');await page.locator('#dm-reply').click();await page.evaluate(()=>onNodeActionResult('Radio busy; try again'));
 assert.equal(await page.locator('#dm-input').inputValue(),'Keep on error');
 await page.evaluate(()=>{pendingCompose=null;f.mt.connected=false;onRadioState(f);showMessageMenu('mt',f.mt.chats[2][0],{peer:'!abcdef12'});});await page.getByRole('button',{name:'Emoji',exact:true}).click();assert.equal(await page.locator('.reaction-choices button:disabled').count(),9);
 await page.evaluate(()=>{showMessageMenu('mc',{id:5,packetId:5,text:'MC',sender:'MeshCore'},{channel:2});});assert.equal(await page.locator('.reaction-choices').count(),0);
 await page.evaluate(()=>{showMessageMenu('mt',{id:5,text:'Old',sender:'!abcdef12'},{channel:2});});await page.getByRole('button',{name:'Emoji',exact:true}).click();assert.match(await page.locator('#dm-context').innerText(),/original packet reference was not saved/);
 await page.evaluate(()=>{f.mt.connected=true;f.mt.chats[2].push(
  {id:4,packetId:126,replyId:123,reaction:true,text:'❤️',sender:'!33445566',time:'12:03'},
  {id:5,packetId:127,text:'My message',sender:'You',own:true,time:'12:04'},
  {id:6,packetId:128,replyId:127,reaction:true,text:'👍',sender:'!abcdef12',time:'12:05'}
 );onRadioState(f);selectChannel('mt',2);});
 assert.equal(await page.locator('#chat-messages .reaction-chip').first().innerText(),'❤️ 2');
 assert.equal(await page.locator('#chat-messages .android-msg.own .reaction-chip').innerText(),'👍');
 await page.evaluate(()=>{f.mt.directs['!abcdef12']=f.mt.chats[2];onRadioState(f);selectDmPeer('mt','!abcdef12');});
 assert.equal(await page.locator('#dm-chat-history .android-msg.own .reaction-chip').innerText(),'👍');
 await page.evaluate(()=>{el('dm-context').hidden=true;switchTab('chat');selectChannel('mt',2);});await page.screenshot({path:'build/reactions-097.png'});
 assert.deepEqual(errors,[]);console.log('Reactions and Send-return UI checks passed');
}finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});

