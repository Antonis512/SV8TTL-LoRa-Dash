const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const {pathToFileURL}=require('node:url');
const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
 try {
  const page=await browser.newPage({viewport:{width:390,height:780}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(()=>{window.calls=[];window.AndroidBridge=new Proxy({},{get:(_,name)=>(...args)=>calls.push([name,...args])});});
  await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
  await page.evaluate(()=>{
   const now=Math.floor(Date.now()/1000),peer=(id,name,time,favorite=false)=>({id,name,role:'CLIENT',hops:1,lastHeard:time,favorite});
   const radio=(type,id,name,nodes,connected=true)=>({radio:type,key:id,ownId:id,name,ownName:name,connected,loading:false,channels:[],nodes,directs:{},chats:{},scanProgress:0});
   window.radios=[radio('mt','!11111111','Home receiver',[peer('!abcdef12','PASIXERA',now-100),peer('!cccccccc','PASIXERA',now-90)]),
    radio('mt','!22222222','Field receiver',[peer('!ABCDEF12','PASIXERA renamed',now-10,true)]),
    radio('mt','!33333333','Old receiver',[{...peer('!abcdef12','Older name',now-200),savedOnly:true}],false),
    radio('mc','aabbccddeeff','MC receiver',[peer('!abcdef12','Different network',now-50)])];
   window.fixture={mt:radios[0],mc:radios[3],devices:radios.filter(r=>r.connected).map(r=>({key:r.key,id:r.ownId,name:r.name,radio:r.radio})),myNodes:[],sound:{mode:'shared',shared:{}},nodeNames:{}};
   onRadioState(fixture);onContactNodes(radios);
   window.person={...newContact(),id:'person-test',name:'Test person',nodes:[{radio:'mt',id:'!abcdef12',name:'Old saved name',assignmentRole:'Owner'},{radio:'mt',id:'!cccccccc',name:'PASIXERA'},{radio:'mc',id:'!abcdef12',name:'Different network'}]};
   onContactsData({items:[person],view:{}});switchTab('contacts');openContact(person.id);
  });
  const cards=page.locator('#contact-detail .contact-node');
  assert.equal(await cards.count(),3,'one card per network and identity, not per receiver or display name');
  const shared=cards.filter({hasText:'PASIXERA renamed'});
  assert.equal(await shared.count(),1);
  assert.match(await shared.innerText(),/Home receiver/);assert.match(await shared.innerText(),/Field receiver/);assert.match(await shared.innerText(),/Old receiver/);
  await shared.locator(':scope > button').click();
  assert.equal(await shared.locator('.node-receivers p').count(),3,'selected-radio aliases do not duplicate receivers');
  assert.match(await shared.locator('.node-receivers').innerText(),/Offline/);
  assert.match(await shared.locator('.node-receivers').innerText(),/Favorite on this device/);
  const chooser=shared.getByLabel('Use device for node actions');
  const home=await chooser.locator('option').filter({hasText:'Home receiver'}).getAttribute('value');
  await chooser.selectOption(home);
  await shared.getByRole('button',{name:'Req info',exact:true}).click();
  assert.deepEqual(await page.evaluate(()=>calls.filter(c=>c[0]==='nodeAction').at(-1)),['nodeAction','!11111111','!abcdef12','req','']);
  await page.evaluate(()=>onContactNodes([...radios].reverse()));
  assert.match(await shared.getByLabel('Use device for node actions').locator('option:checked').innerText(),/Home receiver/,'action receiver survives snapshot reordering');
  await page.locator('#contact-detail summary').click();
  const receiverFilter=page.locator('#contact-detail').getByLabel('Receiving device',{exact:true});
  await receiverFilter.selectOption({label:'Field receiver'});
  assert.equal(await cards.count(),1,'receiver filter still works after grouping');
  await receiverFilter.selectOption('all');
  await page.locator('#contact-detail').getByLabel('Favorite nodes',{exact:true}).check();
  assert.equal(await cards.count(),1,'favorite on either receiver selects one node');
  await page.locator('#contact-detail').getByLabel('Favorite nodes',{exact:true}).uncheck();
  await page.evaluate(()=>{switchTab('nodes');el('nodes-show-unknown').checked=true;renderNodes();});
  await page.locator('#nodes-list .android-node[data-node-key="mt:!abcdef12"] .node-heading').click();
  const info=page.locator('#nodes-list .android-node[data-node-key="mt:!abcdef12"] .node-receivers');
  assert.equal(await info.locator('p').count(),3);
  assert.match(await info.innerText(),/Home receiver/);assert.match(await info.innerText(),/Field receiver/);assert.match(await info.innerText(),/Old receiver/);
  await page.evaluate(()=>onContactNodes(radios));
  assert.equal(await info.locator('p').count(),3,'expanded Nodes view refreshes receiver details');
  assert.equal(await page.evaluate(()=>calls.filter(c=>c[0]==='saveContacts').length),0,'grouping never rewrites personal contacts');
  assert.deepEqual(errors,[]);
  console.log('Unique contact nodes, receiver details, filters, action routing, and Nodes expansion passed');
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
