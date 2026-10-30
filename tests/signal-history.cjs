const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const {pathToFileURL}=require('node:url');
const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROME_PATH||'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
 try {
  const page=await browser.newPage({viewport:{width:390,height:780},locale:'en-US'}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(()=>{window.calls=[];window.AndroidBridge=new Proxy({},{get:(_,n)=>(...args)=>calls.push([n,...args])});});
  await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
  await page.evaluate(()=>{
   openSignalHistory('MT-Device1','!12345678');const now=Date.now();
   window.fixture={key:'MT-Device1',peer:'!12345678',name:'MT-Node32',receiver:'MT-Device1',connected:true,rows:[
    {time:now,snr:0,rssi:-98,hops:0,stats:'',phoneTime:false},
    {time:now-60000,snr:-8,rssi:-112,hops:0,stats:''},
    {time:now-120000,snr:15,rssi:-55,hops:2,stats:'Node statistics\nNoise floor: -110 dBm\nPackets received: 18',requested:true},
    {time:now-180000,snr:9,hops:-1,stats:''},
    {time:now-86400000*2,snr:2,rssi:-100,hops:0,stats:''}
   ]};onSignalHistory(fixture);
  });
  assert.equal(await page.locator('.signal-plot svg circle').count(),4,'Graphs include only recent direct receptions');
  assert((await page.locator('#signal-history').innerText()).includes('Last-hop reception · 2 hops'));
  assert(!/\b(?:AM|PM)\b/.test(await page.locator('#signal-history').innerText()));
  await page.getByRole('button',{name:'Request node statistics',exact:true}).click();
  assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['nodeAction','MT-Device1','!12345678','signal','']);
  assert(await page.getByRole('button',{name:'Request node statistics',exact:true}).isDisabled());
  await page.evaluate(()=>onSignalRequestResult('MT-Device1','!12345678','Request sent to radio. Waiting for response…',true));
  assert((await page.locator('.signal-request-status').innerText()).includes('Waiting'));
  await page.evaluate(()=>onSignalRequestResult('MT-Device1','!12345678','Error sending request: USB disconnected',false));
  assert((await page.locator('.signal-request-status').innerText()).includes('Error'));
  assert(!(await page.getByRole('button',{name:'Request node statistics',exact:true}).isDisabled()));
  await page.getByLabel('Signal history period').selectOption('0');
  assert.equal(await page.locator('.signal-plot svg circle').count(),6);
  for(const width of [320,390,780]){await page.setViewportSize({width,height:780});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));}
  await page.setViewportSize({width:390,height:780});await page.screenshot({path:'build/signal-history.png'});
  await page.getByRole('button',{name:'Clear this signal history',exact:true}).click();
  assert.notDeepEqual(await page.evaluate(()=>calls.at(-1)),['signalHistory','MT-Device1','!12345678',true]);
  await page.getByRole('button',{name:'Delete saved readings',exact:true}).click();
  assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['signalHistory','MT-Device1','!12345678',true]);
  await page.evaluate(()=>onSignalHistory({...fixture,connected:false,rows:[]}));
  assert(await page.getByRole('button',{name:'Request node statistics',exact:true}).isDisabled());
  assert.equal(await page.locator('.signal-plot svg').count(),0);
  await page.getByRole('button',{name:'Close',exact:true}).click();
  await page.evaluate(()=>onSignalSampleSaved('MT-Device1','!12345678'));
  assert(await page.locator('#dm-context').isHidden());assert.deepEqual(errors,[]);
  await page.evaluate(()=>{switchTab('settings');showSettingsPage('timeouts');});
  for(const kind of ['signal','ping','trace'])assert.equal(await page.locator('#timeout-'+kind).inputValue(),'60');
  await page.locator('#timeout-ping').fill('45');await page.locator('#timeout-ping').dispatchEvent('change');
  assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['setRequestTimeout','ping',45]);
  const before=await page.evaluate(()=>calls.filter(c=>c[0]==='setRequestTimeout'&&c[1]==='trace').length);
  await page.locator('#timeout-trace').fill('0');await page.locator('#timeout-trace').dispatchEvent('change');
  assert.equal(await page.evaluate(()=>calls.filter(c=>c[0]==='setRequestTimeout'&&c[1]==='trace').length),before,'Invalid timeout rejected');
  const labels=await page.locator('#settings-home strong').allTextContents();assert.equal(labels[labels.indexOf('Sending options')+1],'Request timeouts');
  console.log('Signal history: graph filtering, zero SNR, receiver selection, offline history, clear confirmation and responsive layout passed');
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
