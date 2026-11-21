const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path');
const {pathToFileURL}=require('node:url');
(async()=>{
  const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
  try{
    const page=await browser.newPage({viewport:{width:390,height:780}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.addInitScript(()=>{window.calls=[];window.AndroidBridge=new Proxy({},{get:(_,name)=>(...args)=>calls.push([name,...args])});});
    await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
    await page.evaluate(()=>{
      state={mt:{key:'mt-live',ownId:'!00000001',connected:true},mc:{key:'mc-live',ownId:'own',connected:true}};
      window.showSettingsPage('home');openIgnoredNodes();
      onIgnoredNodes({radios:[{id:'!00000001',name:'MT One',ready:true,busy:false,nodes:[{id:'!00000002',name:'Ignored user'}]},{id:'!00000003',name:'MT Offline',ready:false,nodes:[{id:'!00000002',name:'Same node on other radio'}]}]});
    });
    const dialog=page.locator('#settings-window-ignored');assert.equal(await dialog.isVisible(),true);
    const buttons=dialog.getByRole('button',{name:'Unignore',exact:true});assert.equal(await buttons.count(),2);
    assert.equal(await buttons.nth(1).isDisabled(),true);await buttons.nth(0).click();
    assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['setNodeIgnored','!00000001','!00000002',false]);
    await page.evaluate(()=>onIgnoredNodes({message:'Waiting for radio confirmation',radios:[{id:'!00000001',name:'MT One',ready:true,busy:true,nodes:[{id:'!00000002',name:'Ignored user'}]}]}));
    assert.equal(await dialog.getByRole('button',{name:'Unignore',exact:true}).isDisabled(),true);
    await page.evaluate(()=>onIgnoredNodes({message:'Node unignored on radio.',radios:[{id:'!00000001',name:'MT One',ready:true,busy:false,nodes:[]}]}));
    assert.match(await dialog.innerText(),/No ignored nodes on this radio/);
    await dialog.getByRole('button',{name:'Close',exact:true}).click();
    await page.evaluate(()=>{
      const host=document.createElement('div');host.id='ignore-test';document.body.append(host);
      host.append(nodeDetails('mt',{id:'!00000002',name:'Node',role:'Client',ignored:false}));
    });
    await page.locator('#ignore-test').getByRole('button',{name:'Ignore',exact:true}).dispatchEvent('click');
    assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['nodeAction','mt-live','!00000002','ignore','']);
    await page.evaluate(()=>{el('ignore-test').replaceChildren(nodeDetails('mt',{id:'!00000002',role:'Client',ignored:true}));});
    await page.locator('#ignore-test').getByRole('button',{name:'Unignore',exact:true}).dispatchEvent('click');
    assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['nodeAction','mt-live','!00000002','unignore','']);
    await page.evaluate(()=>{el('ignore-test').replaceChildren(nodeDetails('mc',{id:'abcdefabcdef',role:'Chat',ignored:false}));});
    assert.equal(await page.locator('#ignore-test').getByRole('button',{name:'Ignore',exact:true}).count(),0);
    assert.deepEqual(errors,[]);console.log('Ignored-node UI checks passed: MT actions, MC exclusion, per-radio Unignore, offline and pending states.');
  }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
