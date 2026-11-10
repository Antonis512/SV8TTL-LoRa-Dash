'use strict';
let mapData={nodes:[],positions:[],heard:[],items:[],packs:[]};
let mapView=null,mapMarkers=[],mapToolName='passive',mapDrawing=null,mapDraft=[],mapMode='online',mapLayerKind='street',mapPackId='',mapFocus=null,mapStyleKey='';
let mapSelected=null,mapFocusWaiting=false,mapAlertPin=null;
let mapProtocol=null,mapDisplayStyle='full';
const mapUrl=id=>location.origin+'/packs/'+id+'.pmtiles';
const mapAsset=path=>location.origin+'/assets/'+path;
const mapDate=t=>t?new Date(t).toLocaleString(undefined,{hourCycle:'h23'}):'Unknown';
const mapMoment=value=>value?new Date(value).getTime():0;
const mapText=(tag,content,cls='')=>{const e=document.createElement(tag);e.textContent=content;if(cls)e.className=cls;return e;};
function mapButton(parent,label,action){const b=mapText('button',label);b.type='button';b.onclick=action;parent.appendChild(b);return b;}
function mapLabel(parent,title,control){const l=mapText('label',title);l.appendChild(control);parent.appendChild(l);return control;}
function mapInput(parent,title,value='',type='text'){const e=document.createElement('input');e.className='form-input';e.type=type;e.value=value;return mapLabel(parent,title,e);}
function mapChoices(menu,entries){
  const previous=menu.value;
  if(menu.dataset.choices===JSON.stringify(entries))return;
  menu.dataset.choices=JSON.stringify(entries);
  menu.replaceChildren(...entries.map(([value,label])=>new Option(label,value)));
  if(entries.some(([value])=>value===previous))menu.value=previous;
}
function mapSelect(parent,title,choices){const e=document.createElement('select');e.className='form-input';for(const [value,label] of choices){const o=mapText('option',label);o.value=value;e.appendChild(o);}return mapLabel(parent,title,e);}
function mapNotice(message,error=false){const status=el('map-status');status.textContent=message;status.classList.toggle('map-error',error);status.setAttribute('role',error?'alert':'status');}
function mapPackSource(url){return {
      getKey:()=>url,
      getBytes:async(offset,length,signal)=>{
        // WebView applies HTTP Range itself; pass local slice coordinates in the URL instead.
        const response=await fetch(url+'?offset='+offset+'&length='+length,{signal});
        if(!response.ok)throw new Error('Cannot read installed map (HTTP '+response.status+').');
        const data=await response.arrayBuffer();
        if(!data.byteLength)throw new Error('Installed map returned no tile data.');
        return {data};
      }
};}
function initMap(){
  if(mapView||!window.maplibregl)return;
  mapProtocol=new pmtiles.Protocol();maplibregl.addProtocol('pmtiles',mapProtocol.tile);
  mapView=new maplibregl.Map({container:'offline-map',style:{version:8,sources:{},layers:[{id:'blank',type:'background',paint:{'background-color':'#172330'}}]},center:[20,37],zoom:3,attributionControl:false});
  mapView.addControl(new maplibregl.NavigationControl({showCompass:false}),'top-right');
  mapView.addControl(new maplibregl.AttributionControl({compact:true}),'bottom-right');
  mapView.on('error',event=>{
    const detail=event.error?.message||'Unknown map loading error';
    mapNotice((mapMode==='offline'?'Offline':'Online')+' map could not load: '+detail,true);
  });
  mapView.on('load',()=>renderMap());
  mapView.on('zoomend',()=>renderMap());
  mapView.on('style.load',()=>{drawMapItems();renderMap();});
  mapView.on('click',event=>{if(!mapDrawing)return;mapDraft.push([Number(event.lngLat.lng.toFixed(6)),Number(event.lngLat.lat.toFixed(6))]);drawMapItems();mapNotice(mapDraft.length+' point'+(mapDraft.length===1?'':'s')+' chosen. Tap again, then Save.');});
}
window.mapShown=()=>{initMap();setTimeout(()=>mapView?.resize(),40);};
function activePack(){return mapData.packs.find(p=>p.id===mapPackId&&p.kind===mapLayerKind)||mapData.packs.find(p=>p.kind===mapLayerKind)||null;}
function setMapMode(mode){
  if(mode!=='online'&&mode!=='offline')return;
  if(mapMode!==mode)mapStyleKey='';
  mapMode=mode;
  const button=el('map-source-switch');button.textContent=mode==='online'?'Online ⇄':'Offline ⇄';
  button.setAttribute('aria-label','Switch to '+(mode==='online'?'offline':'online')+' map');
  setMapStyle();renderMapPacks();mapView?.resize();
}
function toggleMapMode(){setMapMode(mapMode==='online'?'offline':'online');}
function setMapStyle(preserveView=false){
  if(!mapView)return;const pack=mapMode==='offline'?activePack():null,key=mapMode==='online'?'online':(pack?.id||'blank')+':'+mapDisplayStyle;if(key===mapStyleKey)return;mapStyleKey=key;
  if(pack&&pack.schema!=='copernicus'&&!mapProtocol.get(mapUrl(pack.id))){
    const url=mapUrl(pack.id);
    mapProtocol.add(new pmtiles.PMTiles(mapPackSource(url)));
  }
  let style={version:8,sources:{},layers:[{id:'blank',type:'background',paint:{'background-color':'#172330'}}]};
  if(mapMode==='online'){
    style.sources.openstreetmap={type:'raster',tiles:['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],tileSize:256,maxzoom:19,attribution:'© OpenStreetMap contributors'};
    style.layers.push({id:'online-streets',type:'raster',source:'openstreetmap'});
  }else if(pack?.kind==='street'&&pack.schema==='shortbread'){
    const dark='#18232d',road='#53677a';style={version:8,glyphs:mapAsset('map-fonts/{fontstack}/{range}.pbf'),sources:{shortbread:{type:'vector',url:'pmtiles://'+mapUrl(pack.id),attribution:'© OpenStreetMap contributors · BBBike'}},layers:[
      {id:'land',type:'background',paint:{'background-color':dark}},
      {id:'ocean',type:'fill',source:'shortbread','source-layer':'ocean',paint:{'fill-color':'#172f46'}},
      {id:'water',type:'fill',source:'shortbread','source-layer':'water_polygons',paint:{'fill-color':'#1b4960'}},
      {id:'parks',type:'fill',source:'shortbread','source-layer':'land',filter:['==',['get','kind'],'park'],paint:{'fill-color':'#244537'}},
      {id:'buildings',type:'fill',source:'shortbread','source-layer':'buildings',paint:{'fill-color':'#37434b'}},
      {id:'streets',type:'line',source:'shortbread','source-layer':'streets',paint:{'line-color':road,'line-width':['interpolate',['linear'],['zoom'],5,.5,15,3]}},
      {id:'place-labels',type:'symbol',source:'shortbread','source-layer':'place_labels',layout:{'text-field':['get','name'],'text-font':['Noto Sans Regular'],'text-size':13},paint:{'text-color':'#e6edf2','text-halo-color':dark,'text-halo-width':1.5}}
    ]};
  }else if(pack?.kind==='street'){
    style={version:8,glyphs:mapAsset('map-fonts/{fontstack}/{range}.pbf'),sprite:mapAsset('map-sprites/v4/dark'),
      sources:{protomaps:{type:'vector',url:'pmtiles://'+mapUrl(pack.id),attribution:'© OpenStreetMap contributors · Protomaps'}},
      layers:basemaps.layers('protomaps',basemaps.namedFlavor(mapDisplayStyle==='dark'?'dark':'light'),{lang:'en'})};
  }else if(pack?.schema==='copernicus'){
    style.sources.imagery={type:'raster',tiles:[location.origin+'/satellite/'+pack.id+'/{z}/{x}/{y}.jpg'],minzoom:pack.minZoom,maxzoom:pack.maxZoom,tileSize:256,attribution:'Copernicus Sentinel-2 L2A'};
    style.layers.push({id:'satellite',type:'raster',source:'imagery'});
  }else if(pack?.kind==='satellite'){
    style.sources.imagery={type:'raster',url:'pmtiles://'+mapUrl(pack.id),tileSize:256,attribution:'Satellite imagery: see installed pack source'};
    style.layers.push({id:'satellite',type:'raster',source:'imagery'});
  }
  if(pack?.kind==='street'&&pack.schema==='shortbread'&&mapDisplayStyle==='full')style.layers=fullShortbreadLayers();
  mapView.setStyle(style);
  el('map-empty-pack').hidden=mapMode==='online'||!!pack;
  el('map-source').textContent=mapMode==='online'?'Online map · © OpenStreetMap contributors':pack?'Offline map: '+pack.title+' · '+(pack.kind==='street'?'© OpenStreetMap contributors · ':'Imagery: ')+(pack.source||pack.schema)+(pack.captureDate?' · captured '+pack.captureDate:'')+(pack.requestedRange?' · requested '+pack.requestedRange:'')+' · native zoom '+pack.maxZoom:'No offline map selected';
  if(!preserveView&&mapMode==='offline'&&pack?.bounds?.length===4&&!mapFocus){const [west,south,east,north]=pack.bounds;if(east>west&&north>south)setTimeout(()=>mapView?.fitBounds([[west,south],[east,north]],{padding:35,maxZoom:Math.min(11,pack.maxZoom||11)}),80);}
}
window.onMapData=data=>{
  if(data.displayStyle==='dark'||data.displayStyle==='full')mapDisplayStyle=data.displayStyle;
  mapData={nodes:data.nodes||[],positions:data.positions||[],heard:data.heard||[],items:data.items||[],packs:data.packs||[],alerts:data.alerts||[]};
  if(!mapData.packs.some(p=>p.id===mapPackId)){
    const saved=mapData.packs.find(p=>p.id===data.selectedPackId);
    mapPackId=saved?.id||'';if(saved)mapLayerKind=saved.kind;
  }
  if(!mapData.packs.some(p=>p.kind===mapLayerKind))mapLayerKind=mapData.packs.some(p=>p.kind==='street')?'street':mapData.packs.some(p=>p.kind==='satellite')?'satellite':'street';
  if(mapFocusWaiting){
    mapFocusWaiting=false;renderMapFilters();
    for(const id of ['map-network','map-device','map-role','map-rf'])el(id).value='all';
    el('map-from').value='';el('map-to').value='';
    el('map-network').value=mapFocus.radio;
    if(mapFocus.device)el('map-device').value=mapFocus.radio+':'+mapFocus.device;
  }
  renderGeofenceHistory();
  initMap();setMapMode(mapMode);renderMap();renderMapItems();el('map-send-mark')?.refreshTargets?.();
};
function mapFilterRows(){
  const network=el('map-network').value,device=el('map-device').value,role=el('map-role').value,rf=el('map-rf').value;
  const from=mapMoment(el('map-from').value),to=mapMoment(el('map-to').value);
  const matching=mapData.nodes.filter(n=>{
    if(network!=='all'&&n.radio!==network)return false;if(device!=='all'&&n.radio+':'+n.device!==device)return false;if(role!=='all'&&n.radio+':'+n.role!==role)return false;if(rf!=='all'&&heardProfile(n.radio,n.receivingRf)!==rf)return false;
    if(from||to)return mapData.heard.some(h=>h.radio===n.radio&&h.device===n.device&&h.node===n.id&&(!from||h.time>=from)&&(!to||h.time<=to));
    return true;
  });
  const byNode=new Map();for(const n of matching){const key=n.radio+':'+n.id,old=byNode.get(key);
    byNode.set(key,old?mapPreferredObservation(old,n):n);
  }
  return [...byNode.values()];
}
const mapPositionTime=n=>Number(n.fixTime)||Number(n.positionTime)||0;
function mapPrecisionRadius(n){
  const bits=Number(n.precisionBits);
  return n.radio==='mt'&&bits>=10&&bits<=19?23905787.925008/2**bits:0;
}
function mapSameCoarseCell(precise,coarse){
  const bits=Number(coarse.precisionBits),better=Number(precise.precisionBits)||32;
  if(coarse.radio!=='mt'||!mapHasPosition(precise)||!mapHasPosition(coarse)||
    !(Number(precise.precisionBits)>0||String(precise.positionSource||'').startsWith('mt'))||!(bits>0&&bits<32&&better>bits))return false;
  const width=2**(32-bits),center=x=>Math.floor(Math.round(x*1e7)/width)*width+width/2;
  return center(precise.lat)===Math.round(coarse.lat*1e7)&&center(precise.lon)===Math.round(coarse.lon*1e7);
}
function mapPreferredObservation(a,b){
  if(mapHasPosition(a)!==mapHasPosition(b))return mapHasPosition(b)?b:a;
  if(mapSameCoarseCell(a,b))return a;
  if(mapSameCoarseCell(b,a))return b;
  const at=mapPositionTime(a),bt=mapPositionTime(b);
  if(at!==bt)return bt>at?b:a;
  const active=n=>(state?.devices||[]).some(d=>d.radio===n.radio&&(d.id===n.device||d.ownId===n.device)&&d.connected!==false);
  if(active(a)!==active(b))return active(b)?b:a;
  const received=n=>Number(n.receivedAt)||Number(n.positionAt)||0;
  if(received(a)!==received(b))return received(b)>received(a)?b:a;
  return String(b.device).localeCompare(String(a.device))<0?b:a;
}
function mapHasPosition(n){return Number.isFinite(n.lat)&&Number.isFinite(n.lon)&&Math.abs(n.lat)<=90&&Math.abs(n.lon)<=180&&!(n.lat===0&&n.lon===0);}
function renderMapFilters(){
  const device=el('map-device'),old=device.value;device.replaceChildren();const all=mapText('option','All devices');all.value='all';device.appendChild(all);
  const devices=new Map();
  for(const n of mapData.nodes)devices.set(n.radio+':'+n.device,{radio:n.radio,id:n.device,connected:false});
  for(const d of state?.myNodes||[])devices.set(d.radio+':'+d.id,d);
  for(const d of state?.devices||[])devices.set(d.radio+':'+d.id,{...d,connected:true});
  for(const d of devices.values()){const option=mapText('option',(d.radio||'').toUpperCase()+' · '+(d.name||d.id)+(d.connected?'':' · offline'));option.value=d.radio+':'+d.id;device.appendChild(option);}
  if([...device.options].some(o=>o.value===old))device.value=old;
  const roles=el('map-role');populateRoleMenu(roles,mapData.nodes,roles.value);
  const rf=el('map-rf'),oldRf=rf.value;rf.replaceChildren();const anyRf=mapText('option','All settings');anyRf.value='all';rf.appendChild(anyRf);
  for(const value of [...new Set(mapData.nodes.map(n=>heardProfile(n.radio,n.receivingRf)))].sort()){const option=mapText('option',value||'Unknown');option.value=value;rf.appendChild(option);}
  if([...rf.options].some(o=>o.value===oldRf))rf.value=oldRf;
}
function clearMapFilters(){for(const id of ['map-network','map-device','map-role','map-rf'])el(id).value='all';el('map-from').value='';el('map-to').value='';renderMap();}
function mapDistanceMeters(a,b){
  const rad=Math.PI/180,lat=(b.lat-a.lat)*rad,lon=(b.lon-a.lon)*rad;
  const h=Math.sin(lat/2)**2+Math.cos(a.lat*rad)*Math.cos(b.lat*rad)*Math.sin(lon/2)**2;
  return 12742000*Math.asin(Math.sqrt(Math.max(0,Math.min(1,h))));
}
function mapPositionGroups(rows){
  // Five-metre geographic stacks stay stable as the user zooms. Different networks remain separate.
  const groups=[],bands=new Map();
  for(const n of [...rows].sort((a,b)=>(a.radio+':'+a.id).localeCompare(b.radio+':'+b.id))){
    const band=Math.floor(n.lat*111320/5);let group;
    for(let delta=-1;delta<=1&&!group;delta++)for(const candidate of bands.get(n.radio+':'+(band+delta))||[])
      if(mapDistanceMeters(n,candidate[0])<5){group=candidate;break;}
    if(group)group.push(n);else {group=[n];groups.push(group);const key=n.radio+':'+band;if(!bands.has(key))bands.set(key,[]);bands.get(key).push(group);}
  }
  return groups;
}
function mapPrecisionFeatures(rows){
  const unique=new Map();for(const n of rows)if(mapPrecisionRadius(n)>0)unique.set(n.lat+':'+n.lon+':'+n.precisionBits,n);
  return [...unique.values()].map(n=>{
    const angular=mapPrecisionRadius(n)/6371000,lat=n.lat*Math.PI/180,lon=n.lon*Math.PI/180,ring=[];
    for(let step=0;step<=64;step++){
      const bearing=step/64*2*Math.PI,y=Math.asin(Math.max(-1,Math.min(1,Math.sin(lat)*Math.cos(angular)+Math.cos(lat)*Math.sin(angular)*Math.cos(bearing))));
      let x=(lon+Math.atan2(Math.sin(bearing)*Math.sin(angular)*Math.cos(lat),Math.cos(angular)-Math.sin(lat)*Math.sin(y)))*180/Math.PI;
      // Keep the ring continuous across the international date line.
      while(x-n.lon>180)x-=360;while(x-n.lon< -180)x+=360;
      ring.push([x,y*180/Math.PI]);
    }
    ring[ring.length-1]=ring[0].slice();
    return {type:'Feature',properties:{id:n.radio+':'+n.id},geometry:{type:'Polygon',coordinates:[ring]}};
  });
}
function drawMapPrecision(rows){
  if(!mapView?.isStyleLoaded())return;
  const data={type:'FeatureCollection',features:el('map-show-precision')?.checked===false?[]:mapPrecisionFeatures(rows)};
  const source=mapView.getSource('map-position-precision');
  if(source)source.setData(data);else mapView.addSource('map-position-precision',{type:'geojson',data});
  if(!mapView.getLayer('map-position-precision-fill'))mapView.addLayer({id:'map-position-precision-fill',type:'fill',source:'map-position-precision',paint:{'fill-color':'#36c5f0','fill-opacity':0.12}});
  if(!mapView.getLayer('map-position-precision-line'))mapView.addLayer({id:'map-position-precision-line',type:'line',source:'map-position-precision',paint:{'line-color':'#36c5f0','line-width':1.5}});
}
function renderMap(){
  if(!mapView)return;renderMapFilters();
  for(const m of mapMarkers)m.remove();mapMarkers=[];
  const rows=mapFilterRows(),placed=rows.filter(mapHasPosition);
  const groups=mapPositionGroups(placed);
  for(const members of groups){
    const n=members.find(item=>mapSelected&&sameMapNode(item,mapSelected))||members.find(item=>item.id===item.device)||members[0];
    const dot=mapText('span',members.length>1?String(members.length):'',`map-node-dot ${n.radio}${members.length>1?' map-node-stack':''}${!mapPositionTime(n)?' undated':Date.now()-mapPositionTime(n)>86400000?' old':''}`);
    dot.setAttribute('aria-label',members.length>1?members.length+' nodes at this location':n.name||n.id);
    const popup=mapNodePopup(n,placed,members);
    const marker=new maplibregl.Marker({element:dot}).setLngLat([n.lon,n.lat]).setPopup(new maplibregl.Popup({offset:15}).setDOMContent(popup)).addTo(mapView);mapMarkers.push(marker);
    if(mapSelected&&members.some(item=>sameMapNode(item,mapSelected)))marker.togglePopup();
  }
  drawMapPrecision(placed);
  const missing=rows.filter(n=>!mapHasPosition(n));el('map-counts').textContent=placed.length+' positioned · '+missing.length+' without position';
  const list=el('map-no-position-list');list.replaceChildren();for(const n of missing)list.appendChild(mapText('div',n.radio.toUpperCase()+' · '+(n.name||n.id)+' · '+n.id));
  if(mapFocus&&!mapFocusWaiting){
    const requested=mapFocus;mapFocus=null;
    const index=placed.findIndex(n=>sameMapNode(n,requested));
    if(index>=0){const n=placed[index];mapSelected=n;const markerIndex=groups.findIndex(group=>group.some(item=>sameMapNode(item,n)));mapMarkers[markerIndex].setLngLat([n.lon,n.lat]).setPopup(new maplibregl.Popup({offset:15}).setDOMContent(mapNodePopup(n,placed,groups[markerIndex])));mapMarkers[markerIndex].getPopup().addTo(mapView);mapView.flyTo({center:[n.lon,n.lat],zoom:Math.max(mapView.getZoom(),13)});mapNotice('Selected '+(n.name||n.id)+' · '+n.id);}
    else {mapSelected=null;mapNotice('No saved position for '+requested.id+' on this receiving device.',true);}
  }
  for(const item of mapData.items.filter(i=>i.kind==='mark'&&i.enabled)){
    const [lon,lat]=item.geometry.coordinates;if(!Number.isFinite(lon)||!Number.isFinite(lat))continue;
    const pin=mapText('span','📍');pin.style.fontSize='23px';const popup=document.createElement('div');popup.appendChild(mapText('strong',item.title));popup.appendChild(mapText('div',item.description||'Saved mark'));
    mapMarkers.push(new maplibregl.Marker({element:pin}).setLngLat([lon,lat]).setPopup(new maplibregl.Popup({offset:15}).setDOMContent(popup)).addTo(mapView));
  }
  drawMapItems();
}
function mapFitVisible(){if(!mapView)return;const rows=mapFilterRows().filter(mapHasPosition);if(!rows.length){mapNotice('No positioned nodes match these filters.');return;}const bounds=new maplibregl.LngLatBounds();for(const n of rows)bounds.extend([n.lon,n.lat]);mapView.fitBounds(bounds,{padding:45,maxZoom:15});}
window.openMapNode=(radio,id,device='')=>{mapSelected=null;mapFocus={radio,id,device};mapFocusWaiting=true;mapAlertPin?.remove();mapAlertPin=null;switchTab('map');AndroidBridge.loadMap();};
window.onMapAlert=message=>mapNotice('Geofence: '+message);
function showMapTrail(n){
  const points=mapData.positions.filter(p=>p.radio===n.radio&&p.node===n.id&&p.device===n.device);
  mapNotice(points.length+' saved position reports for '+(n.name||n.id)+(points.length?' · newest '+mapDate(points.at(-1).time):'')+'. Gaps over 30 minutes are not connected.');
  if(mapView.getSource('map-trail')){mapView.removeLayer('map-trail-line');mapView.removeSource('map-trail');}
  const lines=[];let segment=[];for(const p of points){if(segment.length&&p.time-segment.at(-1).time>1800000){if(segment.length>1)lines.push(segment.map(v=>[v.lon,v.lat]));segment=[];}segment.push(p);}if(segment.length>1)lines.push(segment.map(v=>[v.lon,v.lat]));
  if(lines.length){mapView.addSource('map-trail',{type:'geojson',data:{type:'Feature',geometry:{type:'MultiLineString',coordinates:lines}}});mapView.addLayer({id:'map-trail-line',type:'line',source:'map-trail',paint:{'line-color':'#fbbf24','line-width':3,'line-dasharray':[2,2]}});}
}
function openMapDisplayStyle(){
  el('map-offline-dialog').close();
  for(const radio of document.querySelectorAll('[name="map-display-style"]'))radio.checked=radio.value===mapDisplayStyle;
  el('map-style-dialog').showModal();
}
function setMapDisplayStyle(value){
  if(value!=='dark'&&value!=='full')return;
  mapDisplayStyle=value;AndroidBridge.setMapDisplayStyle(value);setMapStyle(true);
}
function openOfflineMaps(){el('map-offline-dialog').showModal();}
function backToOfflineMaps(from){el(from).close();openOfflineMaps();}
function openInstalledMaps(){if(el('map-offline-dialog').open)el('map-offline-dialog').close();renderMapPacks();el('map-installed-dialog').showModal();}
function openMapImport(){if(!el('satellite-to').value)el('satellite-to').value=new Date().toISOString().slice(0,10);
  if(!el('satellite-from').value)el('satellite-from').value=new Date(Date.now()-30*86400000).toISOString().slice(0,10);
  if(el('map-offline-dialog').open)el('map-offline-dialog').close();AndroidBridge.loadSatelliteAccount();el('map-import-dialog').showModal();}
function importShortbreadMap(){
  if(!el('map-import-dialog').open)openMapImport();
  AndroidBridge.importMapPack('street','shortbread','','BBBike / OpenStreetMap','');
}
function showCopernicusMapSetup(){
  if(!el('map-import-dialog').open)openMapImport();
  el('copernicus-map-heading').scrollIntoView({block:'start'});
}
let satelliteBounds=null,satellitePlan=null;
let satelliteAccountSaved=false,satelliteSignedIn=false;
window.onSatelliteAccount=(saved,signedIn,label)=>{
  satelliteAccountSaved=!!saved;satelliteSignedIn=!!signedIn;
  el('satellite-account-status').textContent=saved?(signedIn?'Copernicus access ready · '+label:'Logged off · credentials remembered for '+label):'No account remembered on this phone';
  el('satellite-login').hidden=!saved||signedIn;el('satellite-logoff').hidden=!signedIn;el('satellite-forget').hidden=!saved;
  el('satellite-remember').checked=!!saved;
};
function logOffSatelliteAccount(){el('satellite-client-id').value='';el('satellite-client-secret').value='';satelliteSignedIn=false;AndroidBridge.logOffSatelliteAccount();}
function forgetSatelliteAccount(){if(!confirm('Forget saved Copernicus credentials from this phone? Offline map packs will stay.'))return;
  el('satellite-client-id').value='';el('satellite-client-secret').value='';satelliteSignedIn=false;satelliteAccountSaved=false;AndroidBridge.forgetSatelliteAccount();}
function chooseSatelliteArea(){
  if(!mapView){mapNotice('Open Map first to choose an area.');return;}
  const b=mapView.getBounds();satelliteBounds=[b.getWest(),b.getSouth(),b.getEast(),b.getNorth()];
  satellitePlan=null;el('satellite-estimate').textContent='Checking area…';AndroidBridge.estimateSatellite(JSON.stringify(satelliteBounds));
}
window.onSatelliteEstimate=plan=>{satellitePlan=plan;el('satellite-estimate').textContent=plan.tiles+' tiles · native zoom '+plan.maxZoom+' · about '+plan.estimatedMB+' MB (rough estimate; actual size may vary).';};
window.onSatelliteProgress=(done,total)=>{el('satellite-estimate').textContent='Downloading '+done+' of '+total+' tiles. Keep the app open.';};
function downloadSatelliteArea(){
  const from=el('satellite-from').value,to=el('satellite-to').value,id=el('satellite-client-id').value.trim(),secret=el('satellite-client-secret').value;
  if(!satellitePlan||!satelliteBounds){mapNotice('Choose the visible area first.');return;}
  if(!from||!to||from>to){mapNotice('Choose a valid imagery date range.');return;}
  if((!id||!secret)&&!(satelliteSignedIn&&!id&&!secret)){mapNotice('Enter both Copernicus credentials or log in with the remembered account.');return;}
  if(!confirm('Download '+satellitePlan.tiles+' tiles for roughly '+satellitePlan.estimatedMB+' MB? Copernicus account quotas apply.'))return;
  AndroidBridge.downloadSatellite(JSON.stringify(satelliteBounds),from,to,el('satellite-title').value,id,secret,el('satellite-remember').checked);
  el('satellite-client-id').value='';el('satellite-client-secret').value='';el('satellite-estimate').textContent='Starting download…';
}
function downloadMapPackFromForm(){const url=el('map-pack-url').value.trim();if(!/^https:\/\//i.test(url)){mapNotice('Enter an HTTPS address for a PMTiles file.');return;}AndroidBridge.downloadMapPack(url,el('map-pack-title').value,el('map-pack-kind').value,el('map-pack-schema').value,el('map-pack-source').value,el('map-pack-date').value);}
window.onMapPackImportStatus=(message,busy=false,percent=-1)=>{
  // Android may recreate the page while its document picker is in front.
  if(message&&!el('map-import-dialog').open){
    for(const dialog of document.querySelectorAll('dialog[open]'))dialog.close();
    el('map-import-dialog').showModal();
  }
  el('map-pack-import-feedback').hidden=!message;
  el('map-pack-import-status').textContent=message;
  const progress=el('map-pack-import-progress');
  progress.hidden=!busy;
  progress.max=100;
  if(percent>=0)progress.value=Math.min(100,percent);else progress.removeAttribute('value');
};
function viewMapPack(pack,persist=true){
  mapLayerKind=pack.kind;mapPackId=pack.id;mapFocus=null;mapStyleKey='';
  if(persist)AndroidBridge.selectMapPack(pack.id);
  setMapMode('offline');
  if(el('map-installed-dialog').open)el('map-installed-dialog').close();
  if(el('map-import-dialog').open)el('map-import-dialog').close();
  mapView?.resize();
}
window.onMapPackInstalled=pack=>{
  viewMapPack(pack,false);
  onMapPackImportStatus('');
  mapNotice('Offline map installed: '+pack.title);
};
function renderMapPacks(){
  const host=el('map-pack-list'),activeId=mapMode==='offline'?activePack()?.id:null;host.replaceChildren();
  if(!mapData.packs.length)host.appendChild(mapText('p','No maps installed yet. Open Import maps to add a BBBike file or download a Copernicus area.'));
  for(const p of mapData.packs){const row=document.createElement('div');if(p.id===activeId)row.className='map-pack-selected';row.appendChild(mapText('strong',p.title));
    if(p.id===activeId)row.appendChild(mapText('span','Currently shown on Map','map-pack-active'));
    row.appendChild(mapText('small',p.kind+' · '+(p.size/1048576).toFixed(1)+' MB · zoom '+p.minZoom+'–'+p.maxZoom+(p.source?' · '+p.source:'')+(p.captureDate?' · imagery '+p.captureDate:'')+(p.requestedRange?' · requested '+p.requestedRange:'')));
    if(p.id!==activeId)mapButton(row,'Use on Map',()=>viewMapPack(p));
    mapButton(row,'Remove',()=>{if(confirm('Remove '+p.title+' from this phone?'))AndroidBridge.removeMapPack(p.id);});host.appendChild(row);
  }
}
function drawMapItems(){
  if(!mapView||!mapView.isStyleLoaded())return;
  for(const id of ['map-shapes-line','map-shapes-fill','map-shapes','map-draft-line','map-draft-fill','map-draft','map-radio-path-line','map-radio-path']){
    if(mapView.getLayer(id))mapView.removeLayer(id);else if(mapView.getSource(id))mapView.removeSource(id);
  }
  const shapes=mapData.items.filter(i=>i.kind!=='mark'&&i.enabled).map(i=>({type:'Feature',properties:{title:i.title,kind:i.kind},geometry:i.geometry}));
  if(shapes.length){mapView.addSource('map-shapes',{type:'geojson',data:{type:'FeatureCollection',features:shapes}});mapView.addLayer({id:'map-shapes-fill',type:'fill',source:'map-shapes',filter:['==',['geometry-type'],'Polygon'],paint:{'fill-color':'#f59e0b','fill-opacity':.15}});mapView.addLayer({id:'map-shapes-line',type:'line',source:'map-shapes',paint:{'line-color':'#f59e0b','line-width':2}});}
  if(mapDraft.length){let coordinates=mapDraft.slice();if(mapDrawing==='geofence'&&coordinates.length>=3)coordinates=[coordinates.concat([coordinates[0]])];
    const geometry={type:mapDrawing==='geofence'&&mapDraft.length>=3?'Polygon':mapDraft.length===1?'Point':'LineString',coordinates:mapDrawing==='geofence'&&mapDraft.length>=3?coordinates:mapDraft.length===1?coordinates[0]:coordinates};
    mapView.addSource('map-draft',{type:'geojson',data:{type:'Feature',geometry}});
    mapView.addLayer({id:'map-draft-fill',type:'fill',source:'map-draft',filter:['==',['geometry-type'],'Polygon'],paint:{'fill-color':'#36c5f0','fill-opacity':.2}});
    mapView.addLayer({id:'map-draft-line',type:'line',source:'map-draft',paint:{'line-color':'#36c5f0','line-width':3,'line-dasharray':[2,2]}});
  }
  if(mapToolName==='route'){
    const segments=[];
    const device=state?.devices?.find(d=>d.key===el('map-route-device')?.value);
    const trace=device?.lastTrace;
    if(trace&&!trace.pending){
      const hops=trace.forward||[];
      const coordinate=hop=>{
        if(hop.id===device.id&&Number.isFinite(device.ownLongitude)&&Number.isFinite(device.ownLatitude))return [device.ownLongitude,device.ownLatitude];
        const known=mapData.nodes.find(n=>n.device===device.id&&n.radio===device.radio&&n.id===hop.id&&Number.isFinite(n.lon)&&Number.isFinite(n.lat));
        return known?[known.lon,known.lat]:null;
      };
      for(let i=1;i<hops.length;i++){const a=coordinate(hops[i-1]),b=coordinate(hops[i]);if(a&&b)segments.push({type:'Feature',properties:{radio:device.radio},geometry:{type:'LineString',coordinates:[a,b]}});}
    }
    if(segments.length){mapView.addSource('map-radio-path',{type:'geojson',data:{type:'FeatureCollection',features:segments}});mapView.addLayer({id:'map-radio-path-line',type:'line',source:'map-radio-path',paint:{'line-color':'#b49eff','line-width':3,'line-dasharray':[2,3]}});}
  }
}
function renderMapItems(){const host=el('map-items');host.replaceChildren();if(!mapData.items.length)return;host.appendChild(mapText('h3','Saved map items'));
  for(const item of mapData.items){const row=document.createElement('div');row.className='map-item';row.appendChild(mapText('strong',item.title));row.appendChild(mapText('small',' · '+item.kind));
    const actions=mapText('div','','map-item-actions');mapButton(actions,'View',()=>{const c=item.kind==='mark'?item.geometry.coordinates:item.geometry.type==='Polygon'?item.geometry.coordinates[0][0]:item.geometry.coordinates[0];mapView.flyTo({center:c,zoom:Math.max(mapView.getZoom(),13)});});
    if(item.kind==='mark')mapButton(actions,'Send by radio',()=>{mapTool('mark');el('map-send-mark').value=item.id;});
    if(item.kind!=='mark')mapButton(actions,item.enabled?'Turn off':'Turn on',()=>AndroidBridge.toggleMapItem(item.id,!item.enabled));
    mapButton(actions,'Save file',()=>AndroidBridge.exportMapItem(item.id));
    mapButton(actions,'Delete',()=>{if(confirm('Delete '+item.title+'?'))AndroidBridge.deleteMapItem(item.id);});row.appendChild(actions);host.appendChild(row);
  }
}
let markPhoneSequence=0, markPhonePending=0, markMapClick=null;
function cancelMarkPosition(){
  if(markPhonePending){AndroidBridge.cancelMarkPosition(markPhonePending);markPhonePending=0;}
  if(markMapClick){mapView?.off('click',markMapClick);markMapClick=null;}
}
window.onMarkPhonePosition=(token,latitude,longitude,message)=>{
  if(token!==markPhonePending)return;
  markPhonePending=0;
  const form=el('map-mark-position');
  if(!form)return;
  if(Number.isFinite(latitude)&&Number.isFinite(longitude))form.fill(latitude,longitude,message);
  else form.textContent=message;
};
function markInput(host,label,type){
  const input=mapInput(host,label,'',type);
  const wrapper=mapText('div','','mark-input');
  input.parentElement.after(wrapper);
  wrapper.appendChild(input.parentElement);
  const clear=mapButton(wrapper,'×',()=>{
    input.value='';input.dispatchEvent(new Event('input'));input.focus();
  });
  clear.setAttribute('aria-label','Clear '+label.toLowerCase());
  return input;
}
function mapTool(name){
  cancelMarkPosition();
  mapToolName=name;mapDrawing=null;mapDraft=[];drawMapItems();for(const b of document.querySelectorAll('.map-tool-nav button'))b.classList.toggle('active',b.textContent===({passive:'Passive activity',active:'Ask positions',route:'Radio path',mark:'Marks',overlay:'Overlays',geofence:'Geofences'}[name]));
  const host=el('map-tool-panel');host.replaceChildren();const title={passive:'Passive activity',active:'Ask nodes for positions',route:'Radio path',mark:'Marks',overlay:'Overlays',geofence:'Geofences'}[name];host.appendChild(mapText('h3',title));
  const instruction=text=>host.appendChild(mapText('p',text));
  if(name==='passive'){
    instruction('1. Connect a radio. 2. Leave it listening. 3. Incoming messages and position reports build this phone’s history without transmitting. Use Heard from/to above to review it.');
    host.appendChild(mapText('p',mapData.heard.length+' saved hearing events · '+mapData.positions.length+' saved positions shown. Position updates can be less frequent than radio contact.'));
  }else if(name==='active'){
    instruction('Choose a connected MT device and one of its channels, then tap Ask for positions. The radio broadcasts one position request with a hop limit of 3.');
    const device=mapSelect(host,'Connected MT device',[]);device.id='map-sense-device';
    const channel=mapSelect(host,'Channel',[]);
    const button=mapButton(host,'Ask for positions',()=>AndroidBridge.senseMesh(device.value,Number(channel.value)));
    function refresh(){
      const devices=(state?.devices||[]).filter(d=>d.radio==='mt');
      mapChoices(device,[['',devices.length?'Choose an MT device':'No connected MT devices'],...devices.map(d=>[d.key,(d.name||'MT')+' · '+d.id])]);
      const selected=devices.find(d=>d.key===device.value);
      const channels=selected&&!selected.loading?selected.channels||[]:[];
      const prompt=selected?.loading?'Loading channels…':selected&&!channels.length?'No reported channels':'Choose a channel';
      mapChoices(channel,[['',prompt],...channels.map(c=>[String(c.index),c.name])]);
      if(channel.dataset.device!==device.value){channel.value='';channel.dataset.device=device.value;}
      channel.disabled=!channels.length;
      button.disabled=!selected||channel.value==='';
    }
    device.refreshTargets=refresh;
    device.onchange=channel.onchange=refresh;
    refresh();
    host.appendChild(mapText('p','The app allows one mesh-wide request every three minutes. Nodes may decline or be out of range.'));
  }else if(name==='route'){
    instruction('Choose a connected device, then one of its nodes. MT uses traceroute. MC uses path discovery.');
    const device=mapSelect(host,'Connected device',[]);device.id='map-route-device';
    const target=mapSelect(host,'Destination node',[]);
    const button=mapButton(host,'Request radio path',()=>AndroidBridge.mapNodeAction(device.value,target.value,'tr'));
    const result=mapText('p','');host.appendChild(result);
    function refresh(){
      const devices=state?.devices||[];
      mapChoices(device,[['','Choose a connected device'],...devices.map(d=>[d.key,d.radio.toUpperCase()+' · '+(d.name||d.id)+' · '+d.id])]);
      const selected=devices.find(d=>d.key===device.value);
      const nodes=selected?.pathNodes||[];
      const prompt=selected?.loading?'Loading nodes…':selected&&!nodes.length?'No nodes available':'Choose a node';
      mapChoices(target,[['',prompt],...nodes.map(n=>[n.id,n.name&&n.name!==n.id?n.name+' · '+n.id:n.id])]);
      if(target.dataset.device!==device.value){target.value='';target.dataset.device=device.value;}
      target.disabled=!nodes.length;
      button.disabled=!selected||!target.value;
      button.textContent='Request radio path';button.title='';
      if(selected?.radio==='mt'){
        button.dataset.mtTraceroute='Request radio path';button.dataset.traceUnavailable=String(!selected||!target.value);updateTracerouteButtons();
      }else {delete button.dataset.mtTraceroute;delete button.dataset.traceUnavailable;}
      const trace=selected?.lastTrace;
      result.textContent=trace?[(trace.name||trace.target),trace.note,(trace.forward||[]).map(h=>h.name).join(' → ')].filter(Boolean).join(' · '):'';
    }
    device.refreshTargets=refresh;
    device.onchange=()=>{refresh();drawMapItems();};
    target.onchange=refresh;
    refresh();drawMapItems();
  }else if(name==='mark'){
    instruction('1. Tap a point on Map or enter coordinates. 2. Give it a name and save. 3. Optionally send an MT waypoint or an MC marker message to a channel or directly to a contact.');
    const title=markInput(host,'Name','text'),desc=markInput(host,'Description','text');
    const lat=markInput(host,'Latitude','number'),lon=markInput(host,'Longitude','number');
    lat.step=lon.step='any';
    const position=mapText('p','');position.id='map-mark-position';position.setAttribute('role','status');
    position.fill=(latitude,longitude,source)=>{
      cancelMarkPosition();lat.value=latitude.toFixed(6);lon.value=longitude.toFixed(6);position.textContent=source;
    };
    lat.oninput=lon.oninput=()=>{cancelMarkPosition();position.textContent='';};
    mapButton(host,'Tap map to choose position',()=>{
      cancelMarkPosition();position.textContent='Tap a position on the map.';
      markMapClick=e=>position.fill(e.lngLat.lat,e.lngLat.lng,'Position selected on map');
      mapView.on('click',markMapClick);
    });
    mapButton(host,'Fetch my position from phone',()=>{
      cancelMarkPosition();markPhonePending=++markPhoneSequence;
      position.textContent='Waiting for phone location…';
      AndroidBridge.fetchMarkPosition(markPhonePending);
    });
    host.appendChild(position);
    mapButton(host,'Save mark',()=>{const latitude=Number(lat.value),longitude=Number(lon.value);if(!title.value.trim()||!lat.value||!lon.value||!Number.isFinite(latitude)||!Number.isFinite(longitude)||latitude<-90||latitude>90||longitude<-180||longitude>180){mapNotice('Enter a name and valid latitude/longitude.');return;}
      AndroidBridge.saveMapItem(JSON.stringify({id:crypto.randomUUID(),kind:'mark',title:title.value.trim(),description:desc.value.trim(),geometry:{type:'Point',coordinates:[longitude,latitude]},enabled:true,created:Date.now(),edited:Date.now()}));});
    const mark=mapSelect(host,'Saved mark',[]);mark.id='map-send-mark';
    const radio=mapSelect(host,'Sending radio',[]);
    const mode=mapSelect(host,'Destination',[['channel','Channel'],['direct','Direct message']]);
    const channel=mapSelect(host,'Channel',[]);
    const peer=mapSelect(host,'Recipient',[]);
    const button=mapButton(host,'Send mark',()=>{
      const direct=mode.value==='direct';
      AndroidBridge.sendMapMark(mark.value,radio.value,direct?-1:Number(channel.value),direct?peer.value:'');
    });
    function updateTargets(){
      const radios=[state?.mt,state?.mc].filter(r=>r?.connected&&!r.loading);
      mapChoices(mark,mapData.items.filter(i=>i.kind==='mark').map(i=>[i.id,i.title]));
      mapChoices(radio,radios.map(r=>[r.key,r.radio.toUpperCase()+' · '+(r.ownName||r.name)]));
      const selected=radios.find(r=>r.key===radio.value);
      if(channel.dataset.radio!==radio.value){
        for(const menu of [channel,peer]){menu.replaceChildren();delete menu.dataset.choices;}
        channel.dataset.radio=radio.value;
      }
      mapChoices(channel,(selected?.channels||[]).map(c=>[String(c.index),c.name]));
      const direct=mode.value==='direct';
      channel.parentElement.hidden=direct;
      peer.parentElement.hidden=!direct;
      const contacts=direct&&selected?selected.nodes.filter(n=>nodeMessageable(selected.radio,n)):[];
      mapChoices(peer,[['','Choose a recipient'],...contacts.map(n=>[n.id,(n.name||n.id)+(n.name&&n.name!==n.id?' · '+n.id:'')])]);
      button.textContent=selected?.radio==='mc'?'Send MC marker message':'Send waypoint by radio';
      button.disabled=!mark.value||!selected||(direct?!peer.value:channel.value==='');
    }
    radio.onchange=mode.onchange=peer.onchange=updateTargets;
    mark.refreshTargets=updateTargets;
    updateTargets();
  }else if(name==='overlay'||name==='geofence'){
    instruction(name==='overlay'?'1. Choose a line or area. 2. Tap points on the map. 3. Name and save it. Share it as a file when needed.':'1. Tap at least three corners to draw an area. 2. Name and save it. 3. This phone alerts when a node reports entering or leaving. Share the area file with other phones if they need their own alerts.');
    const title=mapInput(host,'Name');const desc=mapInput(host,'Description');const shape=name==='overlay'?mapSelect(host,'Shape',[['LineString','Line'],['Polygon','Area']]):null;
    mapButton(host,'Start drawing',()=>{mapDrawing=name==='geofence'?'geofence':shape.value==='Polygon'?'geofence':'overlay';mapDraft=[];mapNotice('Tap corners on the map, then Save.');});
    mapButton(host,'Undo last point',()=>{mapDraft.pop();drawMapItems();});
    mapButton(host,'Save',()=>{const polygon=name==='geofence'||shape.value==='Polygon',needed=polygon?3:2;if(!title.value.trim()||mapDraft.length<needed){mapNotice('Enter a name and tap at least '+needed+' points.');return;}
      const geometry=polygon?{type:'Polygon',coordinates:[mapDraft.concat([mapDraft[0]])]}:{type:'LineString',coordinates:mapDraft};
      AndroidBridge.saveMapItem(JSON.stringify({id:crypto.randomUUID(),kind:name,title:title.value.trim(),description:desc.value.trim(),geometry,enabled:true,created:Date.now(),edited:Date.now()}));mapDrawing=null;mapDraft=[];drawMapItems();});
    mapButton(host,'Import shared map item',()=>AndroidBridge.importMapItem());
  }
}
mapTool('passive');
try{el('map-show-precision').checked=localStorage.getItem('map-show-precision')!=='false';}catch(unavailable){}

function sameMapNode(a,b){return a.radio===b.radio&&a.id===b.id&&(!b.device||a.device===b.device);}
function mapNodePopup(n,placed,members=[]){
  const popup=document.createElement('div');
  popup.appendChild(mapText('strong',n.name||n.id));
  popup.appendChild(mapText('div',n.radio.toUpperCase()+' · '+n.id));
  popup.appendChild(mapText('div','Receiver: '+n.device));
  popup.appendChild(mapText('div',displayRole(n.radio,n.role)+' · '+heardDescription(n.radio,n.receivingRf)));
  popup.appendChild(mapText('div','Last heard: '+mapDate(n.lastHeard)));
  popup.appendChild(mapText('div','Coordinates: '+n.lat.toFixed(7)+', '+n.lon.toFixed(7)));
  const time=mapPositionTime(n),received=Number(n.receivedAt)||Number(n.positionAt)||0;
  if(!time)popup.appendChild(mapText('div','Position time: unknown (saved coordinates)'));
  if(n.positionTime)popup.appendChild(mapText('div',(n.radio==='mc'?'Advertised: ':'Position report: ')+mapDate(n.positionTime)));
  if(n.fixTime)popup.appendChild(mapText('div','GPS fix: '+mapDate(n.fixTime)));
  if(received)popup.appendChild(mapText('div','Received by this phone: '+mapDate(received)));
  popup.appendChild(mapText('div','Position source: '+({mt_live:'live position packet',mt_snapshot:'radio node list',mt_preserved:'previously known precise position',mc_advert:'received advertisement',mc_snapshot:'radio contact list',mc_self:'connected radio configuration',legacy:'saved coordinates'}[n.positionSource]||'saved coordinates')));
  const radius=mapPrecisionRadius(n);
  popup.appendChild(mapText('div',radius?'Shared position precision: ± '+(radius>=1000?(radius/1000).toFixed(2)+' km':Math.round(radius)+' m'):
    n.radio==='mc'?'Advertised location (may be manually configured)':n.precisionBits===32?'Full shared precision':n.precisionBits>0?'Reported sharing precision: '+n.precisionBits+' bits':'Position precision not reported'));
  if(n.positionSource==='mt_preserved')popup.appendChild(mapText('div','Previously known precise position retained within the newly reported approximate area.'));
  if(!time)popup.appendChild(mapText('div','The age of these coordinates is unknown.'));
  mapButton(popup,'Show saved positions',()=>showMapTrail(n));
  const point=mapView.project([n.lon,n.lat]);
  const nearby=members.length>1?members:placed.filter(other=>{const p=mapView.project([other.lon,other.lat]);return Math.hypot(p.x-point.x,p.y-point.y)<32;});
  if(nearby.length>1){
    popup.appendChild(mapText('div',nearby.length+' nodes at or near this point:'));
    for(const other of nearby)mapButton(popup,(other.name||other.id)+' · '+other.id,()=>openMapNode(other.radio,other.id,other.device));
  }
  return popup;
}
window.openGeofenceHistory=()=>{
  switchTab('map');renderGeofenceHistory();
  if(!el('map-alerts-dialog').open)el('map-alerts-dialog').showModal();
  AndroidBridge.loadMap();
};
function renderGeofenceHistory(){
  const alerts=mapData.alerts||[],host=el('map-alerts-list');host.replaceChildren();
  el('map-alerts-button').textContent='Geofence alerts ('+alerts.length+')';
  if(!alerts.length)host.appendChild(mapText('p','No saved geofence alerts. New alerts stay here until you delete them.'));
  for(const a of alerts){
    const row=mapText('article','','map-alert-record');
    row.appendChild(mapText('strong',(a.name||a.node)+' '+a.event+' '+a.title));
    row.appendChild(mapText('div',mapDate(a.time)));
    row.appendChild(mapText('div',a.radio.toUpperCase()+' · '+a.node+' · Receiver: '+a.device));
    row.appendChild(mapText('div','Reported location: '+a.lat+', '+a.lon));
    mapButton(row,'Alert location',()=>{
      el('map-alerts-dialog').close();mapFocus=null;mapFocusWaiting=false;mapSelected=null;initMap();
      if(!mapView){mapNotice('Map is unavailable. The reported coordinates are saved in the alert.',true);return;}
      mapAlertPin?.remove();
      const popup=mapText('div',(a.name||a.node)+' · '+a.node+' · '+a.event+' '+a.title+' · '+mapDate(a.time));
      mapAlertPin=new maplibregl.Marker({color:'#fbbf24'}).setLngLat([a.lon,a.lat]).setPopup(new maplibregl.Popup().setDOMContent(popup)).addTo(mapView);
      mapAlertPin.togglePopup();mapView.flyTo({center:[a.lon,a.lat],zoom:Math.max(mapView.getZoom(),15)});
      mapNotice('Historical alert location · '+mapDate(a.time));
    });
    mapButton(row,'Latest node position',()=>{el('map-alerts-dialog').close();openMapNode(a.radio,a.node,a.device);});
    mapButton(row,'Delete alert',()=>{if(confirm('Delete this saved geofence alert?'))AndroidBridge.deleteGeofenceAlert(a.id);});
    host.appendChild(row);
  }
}
