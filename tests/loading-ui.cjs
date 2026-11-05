const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const {pathToFileURL}=require('node:url');
const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.CHROME_PATH?{executablePath:process.env.CHROME_PATH}:{})});
 try{
  const page=await browser.newPage({viewport:{width:390,height:780}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(()=>{window.AndroidBridge=new Proxy({},{get:()=>()=>{}});});
  await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
  await page.evaluate(()=>{
   const radio=type=>({radio:type,key:type+'-device',ownId:type==='mt'?'!12345678':'mc-own',ownName:type,name:type,connected:true,channels:[{index:0,name:'Primary'}],nodes:[],directs:{},chats:{0:[]},state:'Connected',scanProgress:0,loading:false});
   const mc=radio('mc'),mt=radio('mt');
   window.fixture={mc,mt,left:mc,right:mt,selectedMc:mc.key,selectedMt:mt.key,devices:[mc,mt].map(r=>({key:r.key,radio:r.radio,id:r.ownId,name:r.name})),myNodes:[],sound:{mode:'shared',shared:{}}};
   onRadioState(fixture);switchTab('log');
   window.counts={chat:0,nodes:0,myNodes:0,sounds:0};
   for(const [key,name] of [['chat','renderChat'],['nodes','renderNodes'],['myNodes','renderMyNodes'],['sounds','renderSoundConfig']]){
    const original=window[name];window[name]=(...args)=>{counts[key]++;return original(...args);};
   }
   fixture.mt.loading=true;fixture.mt.loadingStage='Loading nodes';
   for(let i=1;i<=100;i++){fixture.mt.loadingNodes=i*15;onRadioState(fixture);}
  });
  assert.deepEqual(await page.evaluate(()=>counts),{chat:0,nodes:0,myNodes:0,sounds:0});
  await page.evaluate(()=>switchTab('chat'));
  assert.match(await page.locator('#chat-sync-text').innerText(),/Loading nodes.*1500 nodes/);
  assert.equal(await page.evaluate(()=>counts.chat),1);
  await page.evaluate(()=>{
   fixture.mt.loadingStage='Saving nodes';onRadioState(fixture);
  });
  assert.match(await page.locator('#chat-sync-text').innerText(),/Saving nodes/);
  assert.equal(await page.evaluate(()=>counts.nodes+counts.myNodes+counts.sounds),0);
  await page.evaluate(()=>{
   fixture.mt.loading=false;
   fixture.mt.nodes=[{id:'!abcdef12',name:'Newest node',role:'Client',hops:1,lastHeard:Math.floor(Date.now()/1000)}];
   onRadioState(fixture);switchTab('nodes');
  });
  assert.match(await page.locator('#nodes-list').innerText(),/Newest node/);
  assert.equal(await page.evaluate(()=>counts.nodes),1);
  await page.evaluate(()=>{switchTab('settings');showSettingsPage('nodes');});
  assert.equal(await page.evaluate(()=>counts.myNodes),1);
  assert.deepEqual(errors,[]);
  console.log('Loading UI passed: 100 hidden-screen updates avoid rebuilds; progress, saving phase and latest data render on navigation');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
