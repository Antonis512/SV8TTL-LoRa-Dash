const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');
const {chromium}=require('playwright');
const {pathToFileURL}=require('node:url');
const path=require('node:path');
const ctx=vm.createContext({Date,Map,Number,Math});
vm.runInContext(fs.readFileSync('src/main/assets/map-tracks.js','utf8'),ctx);
const now=Date.now(),point=(lat,lon,time=now)=>({lat,lon,time});
const ordered=ctx.orderedTrackPoints([point(2,2,now),point(1,1,now-1000),point(2,2,now),point(3,3,now-31*86400000),point(4,4,now+600000)]);
assert.equal(ordered.length,2);assert.equal(ordered[0].lat,1);
assert.equal(ctx.trackSegments([point(1,1,now-100),point(1,1)]).length,0);
assert.equal(ctx.trackSegments([point(1,1,now-31*60000),point(2,2)]).length,0);
assert.equal(ctx.trackSegments([point(1,1),point(2,2)]).length,0);
const dateline=ctx.trackSegments([point(1,179,now-100),point(2,-179)]);
assert.equal(dateline[0][1][0],181);
for(const [dx,dy] of [[100,0],[-100,0],[0,100],[0,-100],[100,100]]){
 const arrow=ctx.trackArrow([[0,0],[dx,dy]],([x,y])=>({x,y}),([lng,lat])=>({lng,lat}));
 const tip=arrow[1],back=[(arrow[0][0]+arrow[2][0])/2,(arrow[0][1]+arrow[2][1])/2];
 assert((tip[0]-back[0])*dx+(tip[1]-back[1])*dy>0,'Arrow must face newer sample');
}
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH});
 try{
  const page=await browser.newPage({viewport:{width:390,height:780}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(()=>{window.AndroidBridge=new Proxy({},{get:()=>()=>{}});});
  await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
  await page.evaluate(()=>{
   maplibregl.Marker=class{constructor(o){this.element=o.element;}setLngLat(p){this.point=p;return this;}setPopup(p){this.popup=p;return this;}addTo(){document.getElementById('offline-map').append(this.element);return this;}remove(){this.element.remove();}togglePopup(){}};
   maplibregl.Popup=class{setDOMContent(e){this.el=e;return this;}};
   window.sources=new Map();window.layers=new Map();
   mapView={project:([x,y])=>({x:x*10000,y:-y*10000}),unproject:([x,y])=>({lng:x/10000,lat:-y/10000}),isStyleLoaded:()=>true,getSource:id=>sources.get(id),addSource:(id,s)=>sources.set(id,{...s,setData(data){this.data=data;}}),getLayer:id=>layers.get(id),addLayer:l=>layers.set(l.id,l),resize(){},setStyle(){}};
   state={devices:[],myNodes:[]};
   const now=Date.now(),base={radio:'mt',device:'receiver',stream:'track1',sender:'!12345678',label:'Test walker',channel:0,lat:38,lon:23,time:now};
   window.sample={nodes:[],sharedPositions:[base,{...base,lat:37.99,time:now-120000},{...base,lat:37.98,time:now-240000},{...base,lat:30,time:now-31*86400000}],items:[]};
   onMapData(sample);clearMapFilters();
  });
  // The passive panel exists before the asynchronous native snapshot arrives.
  assert.match(await page.locator('#map-passive-summary').textContent(),/^0 saved hearing events · 0 saved positions/);
  await page.evaluate(()=>{
   const time=Date.now();
   window.passiveSample={...sample,heard:[{radio:'mt',device:'receiver',node:'!12345678',time}],positions:[{radio:'mt',device:'receiver',node:'!12345678',lat:38,lon:23,time}]};
   onMapData(passiveSample);
  });
  assert.match(await page.locator('#map-passive-summary').textContent(),/^1 saved hearing events · 1 saved positions/);
  await page.evaluate(()=>onMapData({...passiveSample,heard:[...passiveSample.heard,{...passiveSample.heard[0],time:Date.now()}]}));
  assert.match(await page.locator('#map-passive-summary').textContent(),/^2 saved hearing events · 1 saved positions/);
  await page.evaluate(()=>onMapData(sample));
  assert.match(await page.locator('#map-passive-summary').textContent(),/^0 saved hearing events · 0 saved positions/);
  assert.equal(await page.evaluate(()=>mapData.sharedPositions.length),3);
  assert.equal(await page.evaluate(()=>mapMarkers.length),1);
  assert.deepEqual(await page.evaluate(()=>mapMarkers[0].point),[23,38]);
  assert.equal(await page.evaluate(()=>sources.get('map-location-tracks').data.features.length),2);
  assert.equal(await page.evaluate(()=>sources.get('map-location-arrows').data.features.length),2);
  assert.match(await page.evaluate(()=>mapMarkers[0].popup.el.textContent),/3 retained reports/);
  assert(await page.locator('#map-device option[value="mt:receiver"]').count());
  await page.evaluate(()=>onMapData({...sample,items:[{id:'track1',kind:'mark',radio:'mt',edited:Date.now(),enabled:false}]}));
  assert.equal(await page.evaluate(()=>mapMarkers.length),0);
  await page.evaluate(()=>onMapData({...sample,sharedPositions:[...sample.sharedPositions,...sample.sharedPositions.map(p=>({...p,device:'second receiver'}))]}));
  assert.equal(await page.evaluate(()=>mapMarkers.length),2);
  await page.evaluate(()=>{el('map-device').value='mt:receiver';renderMap();});
  assert.equal(await page.evaluate(()=>mapMarkers.length),1);
  await page.evaluate(()=>{el('map-network').value='mc';renderMap();});
  assert.equal(await page.evaluate(()=>mapMarkers.length),0);
  assert.equal(await page.evaluate(()=>sources.get('map-location-tracks').data.features.length),0);
  await page.evaluate(()=>{el('map-network').value='all';onMapData({nodes:[],sharedPositions:sample.sharedPositions.map(p=>({...p,time:p.time-31*86400000}))});});
  assert.equal(await page.evaluate(()=>mapMarkers.length),0);
  assert.equal(await page.evaluate(()=>sources.get('map-location-arrows').data.features.length),0);
  assert.deepEqual(errors,[]);
  console.log('Map tracks: arrow direction, ordering, deduplication, gap, dateline, filters, latest marker and expiry passed');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
