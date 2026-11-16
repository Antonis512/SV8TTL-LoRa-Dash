const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const {chromium} = require(process.env.PLAYWRIGHT_PATH || 'playwright');

const assets = path.resolve(__dirname, '../src/main/assets');
const types = {'.html':'text/html','.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml','.pbf':'application/x-protobuf'};
const server = http.createServer((request,response) => {
  const name = decodeURIComponent(new URL(request.url,'http://localhost').pathname).replace(/^\/assets\//,'/');
  const target = path.resolve(assets,'.'+name);
  if (!target.startsWith(assets+path.sep) || !fs.existsSync(target)) { response.writeHead(404).end(); return; }
  response.writeHead(200,{'Content-Type':types[path.extname(target)]||'application/octet-stream'});
  fs.createReadStream(target).pipe(response);
});

(async () => {
  await new Promise(resolve => server.listen(0,'127.0.0.1',resolve));
  const browser = await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true,args:['--enable-webgl','--use-gl=angle','--use-angle=swiftshader']});
  try {
    const page = await browser.newPage({viewport:{width:390,height:844}});
    const errors=[];page.on('pageerror',error=>errors.push(error.message));
    await page.addInitScript(() => {
      localStorage.setItem('map-show-precision','false');
      window.AndroidBridge = new Proxy({}, {get:() => () => {}});
    });
    const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg==','base64');
    let onlineRequests=0;
    await page.route('https://tile.openstreetmap.org/**',route=>{onlineRequests++;return route.fulfill({status:200,contentType:'image/png',body:png});});
    await page.goto(`http://127.0.0.1:${server.address().port}/index.html`);
    await page.evaluate(() => { switchTab('map'); onMapData({nodes:[
      {radio:'mt',device:'!11111111',id:'!22222222',name:'A',role:'Client',lat:37.98,lon:23.72,receivingRf:'Long Fast',precisionBits:13,positionTime:1700000000000},
      {radio:'mc',device:'abcdef',id:'123456',name:'B',role:'User',lat:38,lon:24,receivingRf:'869.618 MHz'}
    ],positions:[],heard:[],items:[],packs:[]}); });
    await page.waitForFunction(() => !!mapView?.getSource('openstreetmap'));
    await page.waitForFunction(() => !!mapView?.getLayer('map-position-precision-fill'));
    // Measure the rendered fill boundary for the same 2.92 km report at two zooms.
    const widths=[];
    for(const zoom of [10,11]){
      await page.evaluate(z=>mapView.jumpTo({center:[23.72,37.98],zoom:z}),zoom);
      await page.waitForFunction(()=>mapView.queryRenderedFeatures(mapView.project([23.72,37.98]),{layers:['map-position-precision-fill']}).length>0);
      const edge=await page.evaluate(()=>{
        const center=mapView.project([23.72,37.98]);
        for(let dx=1;dx<190;dx++){
          const point=[center.x+dx,center.y];
          if(!mapView.queryRenderedFeatures(point,{layers:['map-position-precision-fill']}).length){
            const boundary=mapView.unproject(point);
            return {dx,meters:mapDistanceMeters({lat:37.98,lon:23.72},{lat:boundary.lat,lon:boundary.lng})};
          }
        }
      });
      assert(edge&&Math.abs(edge.meters-2918)<100,JSON.stringify(edge));
      widths.push(edge.dx);
    }
    assert(Math.abs(widths[1]-2*widths[0])<=2,JSON.stringify(widths));
    // Legacy MT snapshots can carry the shared cell centre without field 23.
    const original=await page.evaluate(()=>mapData.nodes);
    await page.evaluate(()=>{
      const centre=v=>(Math.floor(v*1e7/524288)*524288+262144)/1e7;
      const first={...mapData.nodes[0],lat:centre(37.71),lon:centre(23.35),precisionBits:0};
      onMapData({nodes:[first,{...first,id:'second'},{...first,id:'third',precisionBits:32}]});
      mapView.jumpTo({center:[first.lon,first.lat],zoom:12});
    });
    await page.waitForFunction(()=>mapView.queryRenderedFeatures(mapView.project(mapView.getCenter()),{layers:['map-position-precision-fill']}).length===1);
    assert.equal(await page.locator('.map-precision-stack').textContent(),'3');
    const mapBox=()=>page.locator('#offline-map').boundingBox();
    const before=await mapBox();
    await page.getByRole('button',{name:'Maximize map'}).click();
    const expanded=await mapBox();
    assert(expanded.height>=843 && expanded.width>=389 && expanded.y===0,JSON.stringify(expanded));
    await page.getByRole('button',{name:'Restore map'}).click();
    assert.equal((await mapBox()).height,before.height);
    await page.evaluate(nodes=>onMapData({nodes}),original);
    // A full-precision stack representative shares coordinates with two MT
    // nodes whose distinct, smaller rings used to sit under its count badge.
    await page.evaluate(()=>{
      window.originalMapRows=mapData.nodes;
      const first=mapData.nodes[0];
      onMapData({nodes:[{...first,precisionBits:32},{...first,id:'!33333333',precisionBits:18},{...first,id:'!44444444',precisionBits:19}]});
      mapView.jumpTo({center:[first.lon,first.lat],zoom:12});
    });
    await page.waitForFunction(()=>mapView.queryRenderedFeatures(mapView.project([23.72,37.98]),{layers:['map-position-precision-fill']}).length===2);
    const stack=page.locator('.map-precision-stack');
    assert.equal(await stack.textContent(),'3');
    assert(await stack.evaluate(marker=>{
      const center=mapView.project([23.72,37.98]);
      return marker.getBoundingClientRect().bottom<mapView.getContainer().getBoundingClientRect().top+center.y;
    }));
    await page.evaluate(()=>el('map-show-precision').click());
    await page.waitForFunction(()=>mapView.queryRenderedFeatures(mapView.project([23.72,37.98]),{layers:['map-position-precision-fill']}).length===0);
    await page.evaluate(()=>{switchTab('nodes');switchTab('map');});
    await page.waitForFunction(()=>mapView.queryRenderedFeatures(mapView.project([23.72,37.98]),{layers:['map-position-precision-fill']}).length===2);
    await stack.click();
    const member=page.locator('#map-node-group details').filter({hasText:'!44444444'});
    await member.locator('summary').click();
    await member.getByRole('button',{name:'Zoom to proximity circle'}).click();
    await page.waitForFunction(()=>!el('map-node-dialog').open && mapView.getZoom()>14);
    await page.evaluate(()=>{mapView.stop();onMapData({nodes:originalMapRows});});
    assert.equal(await page.locator('#map-role optgroup').count(),2);
    assert.equal(await page.locator('#map-role option[value="mc:User"]').innerText(),'MC · Companion');
    await page.locator('#map-role').selectOption('mc:User');
    assert.match(await page.locator('#map-counts').innerText(),/^1 positioned/);
    await page.locator('#map-role').selectOption('all');
    await page.evaluate(()=>{const menu=document.createElement('select');populateRoleMenu(menu,[{radio:'mc',role:'Sensor'},{radio:'mt',role:'Sensor'}]);window.testRoleValues=[...menu.options].map(o=>o.value);});
    assert.deepEqual(await page.evaluate(()=>testRoleValues),['all','mc:Sensor','mt:Sensor']);

    assert.equal(await page.locator('#map-source-switch').innerText(),'Online ⇄');
    assert.equal(await page.locator('#map-offline-dialog').evaluate(dialog=>dialog.open),false);
    assert.equal(await page.locator('#map-empty-pack').isVisible(),false);
    await page.locator('#map-rf').selectOption('Preset: Long Fast');
    assert.match(await page.locator('#map-counts').innerText(),/^1 positioned/);
    await page.locator('#map-rf').selectOption('all');
    await page.waitForTimeout(1000);
    assert.ok(onlineRequests>0,'online mode requests visible tiles');
    await page.screenshot({path:path.resolve(__dirname,'../build/previews/map-online-mode.png')});
    await page.locator('#map-source-switch').click();
    assert.equal(await page.locator('#map-source-switch').innerText(),'Offline ⇄');
    assert.equal(await page.locator('#map-empty-pack').isVisible(),true);
    await page.screenshot({path:path.resolve(__dirname,'../build/previews/map-offline-mode.png')});
    await page.getByRole('button',{name:'Offline maps',exact:true}).first().click();
    assert.equal(await page.locator('#map-offline-dialog').evaluate(dialog=>dialog.open),true);
    await page.screenshot({path:path.resolve(__dirname,'../build/previews/map-offline-menu.png')});
    await page.locator('#map-offline-dialog .map-offline-choices button').first().click();
    assert.equal(await page.locator('#map-installed-dialog').evaluate(dialog=>dialog.open),true);
    await page.locator('#map-installed-dialog [aria-label="Back to Offline maps"]').click();
    assert.equal(await page.locator('#map-offline-dialog').evaluate(dialog=>dialog.open),true);
    await page.locator('#map-offline-dialog .map-offline-choices button').nth(1).click();
    assert.equal(await page.locator('#map-import-dialog').evaluate(dialog=>dialog.open),true);
    await page.locator('#map-import-dialog [aria-label="Back to Offline maps"]').click();
    await page.locator('#map-offline-dialog [aria-label="Close Offline maps"]').click();
    await page.locator('#map-source-switch').click();
    assert.equal(await page.locator('#map-source-switch').innerText(),'Online ⇄');
    // A picker result after page recreation must surface its status without a dialog open.
    await page.evaluate(() => onMapPackImportStatus('Opening selected map file…',true));
    assert.equal(await page.locator('#map-pack-import-status').isVisible(),true);
    assert.equal(await page.locator('#map-pack-import-progress').getAttribute('value'),null);
    await page.evaluate(() => onMapPackImportStatus('Copying map: 25 of 100 MB (25%)',true,25));
    assert.equal(await page.locator('#map-pack-import-progress').evaluate(p=>p.value),25);
    await page.screenshot({path:path.resolve(__dirname,'../build/previews/map-import-progress.png')});
    await page.evaluate(() => onMapPackImportStatus('Checking and installing map…',true));
    assert.equal(await page.locator('#map-pack-import-progress').getAttribute('value'),null);
    await page.evaluate(() => {el('map-import-dialog').close();onMapPackImportStatus('Map import failed: Cannot read map pack');});
    assert.equal(await page.locator('#map-pack-import-status').isVisible(),true);
    assert.equal(await page.locator('#map-pack-import-progress').isVisible(),false);
    assert.match(await page.locator('#map-pack-import-status').innerText(),/Cannot read/);
    assert.deepEqual(errors,[]);
    console.log('Map mode browser smoke passed');
  } finally { await browser.close(); server.close(); }
})().catch(error=>{console.error(error);process.exitCode=1;server.close();});
