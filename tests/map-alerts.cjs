const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const {pathToFileURL}=require('node:url');
const path=require('node:path');
(async()=>{
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
try{
const page=await browser.newPage({viewport:{width:390,height:780}});
await page.addInitScript(()=>{window.calls=[];window.AndroidBridge=new Proxy({},{get:(_,n)=>(...args)=>calls.push([n,...args])});});
await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
await page.evaluate(()=>{
 window.fixture={nodes:[{radio:'mt',device:'rx',id:'one',name:'First',lat:38,lon:23,positionAt:1},{radio:'mt',device:'rx',id:'two',name:'Second',lat:38,lon:23,positionAt:1},{radio:'mt',device:'other',id:'one',name:'Stale name',lat:39,lon:24,positionAt:2}],alerts:[{id:'alert',radio:'mt',device:'rx',node:'one',name:'First',event:'entered',title:'Home',lat:38,lon:23,time:1000}]};
 // Exercise selection and marker contents without network tiles or GPU requirements.
 window.maplibregl.Marker=class{constructor(){this.popup=null;}setLngLat(){return this;}setPopup(p){this.popup=p;return this;}addTo(){return this;}remove(){}togglePopup(){}getPopup(){return this.popup;}};
 window.maplibregl.Popup=class{setDOMContent(el){this.el=el;return this;}addTo(){return this;}};
 mapView={project:([x,y])=>({x:x*100,y:y*100}),flyTo:o=>window.lastFlight=o,getZoom:()=>15,getSource:()=>null,isStyleLoaded:()=>false,resize(){},setStyle(){}};
 onMapData(fixture);
 openMapNode('mt','one','rx');
 window.waitingFocus=mapFocusWaiting&&mapFocus.id==='one';
 onMapData(fixture);
});
assert.equal(await page.evaluate(()=>waitingFocus),true);
assert.equal(await page.locator('#map-device').inputValue(),'mt:rx');
assert.equal(await page.evaluate(()=>mapSelected.name),'First');
assert.match(await page.evaluate(()=>mapMarkers[0].getPopup().el.textContent),/Second.*two/);
assert.deepEqual(await page.evaluate(()=>lastFlight.center),[23,38]);
await page.evaluate(()=>{fixture.nodes[0].name='Renamed';onMapData(fixture);openGeofenceHistory();});
assert.match(await page.locator('#map-alerts-list').innerText(),/First entered Home/);
assert.match(await page.locator('#map-alerts-list').innerText(),/Receiver: rx/);
await page.getByRole('button',{name:'Alert location',exact:true}).click();
assert.deepEqual(await page.evaluate(()=>lastFlight.center),[23,38]);
await page.evaluate(()=>{openMapNode('mt','missing','rx');onMapData(fixture);});
assert.match(await page.locator('#map-status').innerText(),/No saved position for missing/);
await page.evaluate(()=>openGeofenceHistory());
page.on('dialog',d=>d.accept());
await page.getByRole('button',{name:'Delete alert',exact:true}).click();
assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['deleteGeofenceAlert','alert']);
assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
await page.evaluate(()=>{
 fixture.nodes=[
  {radio:'mt',device:'a',id:'equator',lat:0,lon:23,positionAt:2},
  {radio:'mt',device:'b',id:'equator',positionAt:9},
  {radio:'mt',device:'a',id:'newest',lat:38,lon:23,positionAt:1},
  {radio:'mt',device:'b',id:'newest',lat:39,lon:24,positionAt:3},
  {radio:'mc',device:'a',id:'invalid',lat:99,lon:23,positionAt:4}
 ];onMapData(fixture);clearMapFilters();
});
assert.equal(await page.evaluate(()=>mapFilterRows().find(n=>n.id==='equator').device),'a');
assert.equal(await page.evaluate(()=>mapFilterRows().find(n=>n.id==='newest').device),'b');
assert.equal(await page.locator('#map-counts').innerText(),'2 positioned · 1 without position');
await page.selectOption('#map-device','mt:b');await page.evaluate(()=>renderMap());
assert.equal(await page.locator('#map-counts').innerText(),'1 positioned · 1 without position');
console.log('Geofence history, historical location, overlapping chooser and exact receiver/node navigation passed');
}finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
