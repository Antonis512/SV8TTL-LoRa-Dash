const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const {pathToFileURL}=require('node:url');const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.CHROME_PATH?{executablePath:process.env.CHROME_PATH}:{})});
 try{
  const page=await browser.newPage({viewport:{width:390,height:780}}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(()=>{window.AndroidBridge=new Proxy({},{get:()=>()=>{}});});
  await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
  await page.evaluate(()=>{
   window.maplibregl.Marker=class{constructor(options){this.element=options.element;}setLngLat(point){this.point=point;return this;}setPopup(p){this.popup=p;return this;}addTo(){document.getElementById('offline-map').appendChild(this.element);return this;}remove(){this.element.remove();}togglePopup(){}getPopup(){return this.popup;}};
   window.maplibregl.Popup=class{setDOMContent(el){this.el=el;return this;}addTo(){return this;}};
   window.sources=new Map();window.layers=new Map();
   mapView={project:([x,y])=>({x:x*10000,y:y*10000}),flyTo:value=>window.flight=value,getZoom:()=>15,resize(){},setStyle(){},
    isStyleLoaded:()=>true,getSource:id=>sources.get(id),addSource:(id,value)=>sources.set(id,{...value,setData(data){this.data=data;}}),removeSource:id=>sources.delete(id),
    getLayer:id=>layers.get(id),addLayer:value=>layers.set(value.id,value),removeLayer:id=>layers.delete(id)};
   state={devices:[{radio:'mt',id:'active',connected:true}],myNodes:[]};
   window.rows=[['!9ea1a414','Hermesh 0'],['!ea8ee2b8','Keep Chili'],['!291b406e','Klif Aegina']].map(([id,name])=>({id,name,radio:'mt',device:'active',lat:37.9846656,lon:23.724032,positionTime:1700000010000,fixTime:1700000000000,receivedAt:1700000020000,precisionBits:13,positionSource:'mt_snapshot'}));
   onMapData({nodes:rows});clearMapFilters();
  });
  assert.equal(await page.evaluate(()=>mapMarkers.length),1);
  assert.equal(await page.evaluate(()=>mapMarkers[0].element.textContent),'3');
  assert.equal(await page.evaluate(()=>mapMarkers[0].element.getAttribute('aria-label')),'3 nodes at this location');
  assert.equal(await page.evaluate(()=>getComputedStyle(mapMarkers[0].element).width),'28px');
  assert.equal(await page.evaluate(()=>sources.get('map-position-precision').data.features.length),1);
  const text=await page.evaluate(()=>mapMarkers[0].getPopup().el.textContent);
  assert.match(text,/Coordinates: 37\.9846656, 23\.724032/);
  assert.match(text,/Position report:.*GPS fix:.*Received by this phone:/);
  assert.match(text,/2\.92 km/);
  assert.match(text,/Klif Aegina/);assert.match(text,/Keep Chili/);assert.match(text,/Hermesh 0/);
  const distances=await page.evaluate(()=>{
   const center=mapData.nodes[0];return mapPrecisionFeatures([center])[0].geometry.coordinates[0].slice(0,-1).map(([lon,lat])=>mapDistanceMeters(center,{lon,lat}));
  });
  assert(distances.every(d=>Math.abs(d-2918.18)<0.1));
  await page.evaluate(()=>{document.getElementById('map-show-precision').checked=false;renderMap();});
  assert.equal(await page.evaluate(()=>sources.get('map-position-precision').data.features.length),0);
  await page.evaluate(()=>{document.getElementById('map-show-precision').checked=true;openMapNode('mt','!ea8ee2b8','active');onMapData({nodes:rows});});
  assert.equal(await page.evaluate(()=>mapSelected.id),'!ea8ee2b8');
  assert.deepEqual(await page.evaluate(()=>flight.center),[23.724032,37.9846656]);
  const policy=await page.evaluate(()=>{
   const base={radio:'mt',id:'one',lat:38,lon:23.74};
   const old={...base,device:'old',fixTime:1000,receivedAt:9000},newer={...base,device:'active',lat:38.1,fixTime:2000,receivedAt:3000};
   const unknownOld={...base,device:'old',receivedAt:100},unknownActive={...base,device:'active',lat:38.1,receivedAt:100};
   const precise={...base,device:'old',precisionBits:32,fixTime:1000};
   const coarse={...base,device:'active',lat:37.9846656,lon:23.724032,precisionBits:13,fixTime:2000};
   return {
    newer:mapPreferredObservation(old,newer).device,active:mapPreferredObservation(unknownOld,unknownActive).device,
    precise:mapPreferredObservation(precise,coarse).device,moved:mapPreferredObservation(precise,{...coarse,lat:39}).device,
    missing:mapPreferredObservation(precise,{...coarse,lat:undefined}).device,
    mcRadius:mapPrecisionRadius({...coarse,radio:'mc'}),fullRadius:mapPrecisionRadius({...precise}),
    near:mapPositionGroups([{...base,id:'a'},{...base,id:'b',lat:38.00003}]).length,
    separate:mapPositionGroups([{...base,id:'a'},{...base,id:'b',lat:38.0001}]).length,
    dateline:mapPrecisionFeatures([{...coarse,lon:179.999}])[0].geometry.coordinates[0].every(([lon,lat])=>Number.isFinite(lon)&&Number.isFinite(lat))
   };
  });
  assert.deepEqual(policy,{newer:'active',active:'active',precise:'old',moved:'active',missing:'old',mcRadius:0,fullRadius:0,near:1,separate:2,dateline:true});
  await page.evaluate(()=>{onMapData({nodes:[...rows,{...rows[0],device:'other',lat:39,lon:24,fixTime:1700000030000}]});document.getElementById('map-device').value='mt:active';renderMap();});
  assert(await page.evaluate(()=>mapFilterRows().every(row=>row.device==='active')));
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  assert.deepEqual(errors,[]);
  console.log('Map position UI passed: geographic stacks, exact node/receiver selection, precision radii/toggle, timestamps, stale caches, signed/date-line geometry and no MC uncertainty inference');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
