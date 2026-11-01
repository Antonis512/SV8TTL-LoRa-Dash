const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const {chromium} = require(process.env.PLAYWRIGHT_PATH || 'playwright');

const assets = path.resolve(__dirname, '../src/main/assets');
const types = {'.html':'text/html','.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml','.pbf':'application/x-protobuf'};
const server = http.createServer((request,response) => {
  if(request.url.startsWith('/packs/')){
    const file=process.env.MAP_SMOKE_PACK,size=fs.statSync(file).size;
    assert.equal(request.headers.range,undefined,'WebView must not receive an HTTP Range header');
    const params=new URL(request.url,'http://localhost').searchParams;
    const offset=Number(params.get('offset')),length=Number(params.get('length'));
    const range=['',String(offset),String(offset+length-1)];
    const start=range?Number(range[1]):0,end=range&&range[2]?Math.min(size-1,Number(range[2])):size-1;
    response.writeHead(range?206:200,{'Content-Type':'application/octet-stream','Content-Length':end-start+1,'Accept-Ranges':'bytes',...(range?{'Content-Range':`bytes ${start}-${end}/${size}`}:{})});
    fs.createReadStream(file,{start,end}).pipe(response);return;
  }
  const name = decodeURIComponent(new URL(request.url,'http://localhost').pathname).replace(/^\/assets\//,'/');
  const target = path.resolve(assets,'.'+name);
  if (!target.startsWith(assets+path.sep) || !fs.existsSync(target)) { response.writeHead(404).end(); return; }
  response.writeHead(200,{'Content-Type':types[path.extname(target)]||'application/octet-stream'});
  fs.createReadStream(target).pipe(response);
});

(async () => {
  assert.ok(process.env.MAP_SMOKE_PACK,'Set MAP_SMOKE_PACK to a Shortbread PMTiles file');
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true,args:['--enable-webgl','--use-gl=angle','--use-angle=swiftshader']});
  try {
    const page=await browser.newPage({viewport:{width:390,height:844}});
    await page.addInitScript(()=>{window.AndroidBridge=new Proxy({}, {get:()=>()=>{}});});
    await page.route('https://tile.openstreetmap.org/**',r=>r.abort());
    await page.goto(`http://127.0.0.1:${server.address().port}/index.html`);
    await page.evaluate(()=>{
      switchTab('map'); window.mapErrors=[];mapView.on('error',e=>mapErrors.push(e.error.message));
      const pack={id:'11111111-1111-1111-1111-111111111111',title:'Greece',kind:'street',schema:'shortbread',bounds:[18.97064,34.59111,29.65683,41.74954],minZoom:0,maxZoom:14};
      onMapData({packs:[pack]});viewMapPack(pack,false);
    });
    await page.waitForTimeout(8000);
    console.log('Overview',await page.evaluate(()=>({errors:mapErrors,loaded:mapView.isStyleLoaded(),features:mapView.queryRenderedFeatures().length,zoom:mapView.getZoom()})));
    await page.screenshot({path:'build/previews/greece-overview.png'});
    await page.evaluate(()=>mapView.jumpTo({center:[23.7275,37.9838],zoom:14}));
    await page.waitForTimeout(8000);
    console.log('Athens',await page.evaluate(()=>({errors:mapErrors,loaded:mapView.isStyleLoaded(),features:mapView.queryRenderedFeatures().length})));
    await page.screenshot({path:'build/previews/greece-athens.png'});
    assert.ok(await page.evaluate(()=>mapView.queryRenderedFeatures({layers:['streets','buildings']}).length)>0,'Real Shortbread streets/buildings must render');
    assert.deepEqual(await page.evaluate(()=>mapErrors),[]);
    assert.equal(await page.evaluate(()=>mapDisplayStyle),'full');
    assert.equal(await page.evaluate(()=>mapView.getPaintProperty('land','background-color')),'#f2efe9');
    await page.evaluate(()=>openOfflineMaps());
    await page.getByRole('button',{name:'Map Display Style'}).click();
    assert.equal(await page.getByRole('radio',{name:/Full/}).isChecked(),true);
    const before=await page.evaluate(()=>[mapView.getCenter().lng,mapView.getCenter().lat,mapView.getZoom()]);
    await page.getByRole('radio',{name:/Dark/}).check();
    await page.waitForTimeout(400);
    assert.equal(await page.evaluate(()=>mapView.getPaintProperty('land','background-color')),'#18232d');
    assert.deepEqual(await page.evaluate(()=>[mapView.getCenter().lng,mapView.getCenter().lat,mapView.getZoom()]),before);
    await page.getByRole('radio',{name:/Full/}).check();
    await page.getByRole('button',{name:'Close Map Display Style',exact:true}).click();
    for(const tool of ['passive','active','route','mark','overlay','geofence']){
      await page.evaluate(t=>mapTool(t),tool);
      assert.ok(await page.locator('#map-tool-panel').evaluate(e=>e.scrollHeight<=e.clientHeight+1),'Tool content must fit: '+tool);
    }
    assert.ok(await page.locator('.map-tool-nav').evaluate(e=>!!(e.compareDocumentPosition(document.getElementById('offline-map'))&Node.DOCUMENT_POSITION_PRECEDING)));
    assert.ok(await page.locator('.map-tool-nav').evaluate(e=>e.nextElementSibling.id==='map-tool-panel'));
    assert.equal(await page.locator('#channel-search-toggle svg').count(),1);
    await page.evaluate(()=>{ensureMessageSearch('channel','mt','radio','',0);toggleMessageSearch('channel');});
    assert.equal(await page.locator('#channel-search-toggle').getAttribute('aria-expanded'),'false');
    assert.equal(await page.locator('#channel-search-toggle svg').count(),1);
    await page.evaluate(()=>toggleMessageSearch('channel'));
    assert.equal(await page.locator('#channel-search-toggle').getAttribute('aria-expanded'),'true');
    // Shorter usable height represents space taken by three-button navigation.
    await page.setViewportSize({width:390,height:760});
    await page.evaluate(()=>mapTool('mark'));
    assert.ok(await page.locator('#map-tool-panel').evaluate(e=>e.scrollHeight<=e.clientHeight+1));
    await page.locator('#map-tool-panel').scrollIntoViewIfNeeded();
    await page.screenshot({path:'build/previews/map-tool-panel.png'});

    await page.evaluate(()=>{mapTool('passive');onMapData({...mapData,displayStyle:'dark'});});
    assert.equal(await page.evaluate(()=>mapDisplayStyle),'dark');
    await page.evaluate(()=>setMapDisplayStyle('full'));

    await page.evaluate(()=>mapView.fire('error',{error:new Error('Cannot read offline tile bytes')}));
    assert.match(await page.locator('#map-status').innerText(),/Offline map could not load: Cannot read offline tile bytes/);
    assert.equal(await page.locator('#map-status').getAttribute('role'),'alert');
    await page.screenshot({path:'build/previews/map-error-highlight.png'});

  } finally {await browser.close();server.close();}
})().catch(e=>{console.error(e);process.exitCode=1;server.close();});
