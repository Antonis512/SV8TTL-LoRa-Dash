const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path');
const {pathToFileURL}=require('node:url');
(async()=>{const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});try{
 const page=await browser.newPage({viewport:{width:390,height:780}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{window.calls=[];window.AndroidBridge=new Proxy({},{get:(_,name)=>(...args)=>calls.push([name,...args])});});
 await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
 await page.evaluate(()=>{
  const radio=r=>({radio:r,key:r+'1',ownId:'own-'+r,ownName:r,connected:true,scanProgress:0,loading:false,channels:[{index:0,name:'Local'}],nodes:[{id:r==='mc'?'abcdef':'!12345678',name:'Alice',role:'User'}],directs:{},chats:{}});
  onRadioState({mc:radio('mc'),mt:radio('mt'),devices:[],myNodes:[],sound:{mode:'shared',shared:{}}});
  onContactsData({items:[{id:'alice',name:'Alice',nodes:[{radio:'mc',id:'abcdef'},{radio:'mt',id:'!12345678'}]},{id:'deleted',name:'Deleted',deletedAt:1}],view:{}});
  switchTab('settings');showSettingsPage('sounds');
 });
 assert.equal(await page.locator('#silence-target-label').isVisible(),false);
 await page.getByRole('button',{name:'Silence notifications',exact:true}).click();
 assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['setNotificationSilence','all','','All notifications',0]);
 await page.locator('#silence-duration').selectOption('timed');await page.locator('#silence-minutes').fill('15');
 await page.getByRole('button',{name:'Silence notifications',exact:true}).click();
 assert.equal((await page.evaluate(()=>calls.at(-1)))[4],15);
 await page.locator('#silence-minutes').fill('0');const before=await page.evaluate(()=>calls.filter(c=>c[0]==='setNotificationSilence').length);
 await page.getByRole('button',{name:'Silence notifications',exact:true}).click();assert.equal(await page.evaluate(()=>calls.filter(c=>c[0]==='setNotificationSilence').length),before);
 await page.locator('#silence-minutes').fill('30');await page.locator('#silence-scope').selectOption('contact');
 assert.deepEqual(await page.locator('#silence-target option').allTextContents(),['Alice']);
 await page.getByRole('button',{name:'Silence notifications',exact:true}).click();
 assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['setNotificationSilence','contact','alice','Alice',30]);
 await page.locator('#silence-duration').selectOption('forever');await page.getByRole('button',{name:'Silence notifications',exact:true}).click();assert.equal((await page.evaluate(()=>calls.at(-1)))[4],0);
 await page.locator('#silence-scope').selectOption('node');assert.equal(await page.locator('#silence-target option').count(),2);
 await page.locator('#silence-target').selectOption('mt:!12345678');await page.getByRole('button',{name:'Silence notifications',exact:true}).click();
 assert.deepEqual((await page.evaluate(()=>calls.at(-1))).slice(0,3),['setNotificationSilence','node','mt:!12345678']);
 await page.locator('#silence-duration').selectOption('timed');await page.getByRole('button',{name:'Silence notifications',exact:true}).click();assert.equal((await page.evaluate(()=>calls.at(-1)))[4],30);
 await page.evaluate(()=>renderSilenceRules({all:{label:'All',until:-1},'contact:alice':{label:'Alice',until:Date.now()+60000},expired:{label:'Expired',until:Date.now()-1}}));
 assert.equal(await page.locator('#silence-rules button').count(),2);await page.locator('#silence-rules button').first().click();assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['clearNotificationSilence','all']);
 for(const width of [320,390,780]){await page.setViewportSize({width,height:780});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));}
 await page.setViewportSize({width:390,height:780});
 await page.screenshot({path:'build/previews/notification-silence.png'});
 await page.evaluate(()=>openMapImport());assert.doesNotMatch(await page.locator('#map-import-dialog').innerText(),/Copernicus|OAuth|Sentinel/);
 assert.equal(await page.evaluate(()=>calls.some(c=>c[0]==='loadSatelliteAccount')),false);
 await page.getByRole('button',{name:'Import BBBike PMTiles or ZIP'}).click();assert.equal((await page.evaluate(()=>calls.at(-1)))[0],'importMapPack');
 assert.deepEqual(errors,[]);console.log('Silence UI: all six choices, validation, target identity, restore, expiry display, widths, and map import passed');
}finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});
