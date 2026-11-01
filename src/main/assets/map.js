'use strict';
let mapData={nodes:[],positions:[],heard:[],items:[],packs:[]};
let mapView=null,mapMarkers=[],mapToolName='passive',mapDrawing=null,mapDraft=[],mapMode='online',mapLayerKind='street',mapPackId='',mapFocus=null,mapStyleKey='';
let mapProtocol=null,mapDisplayStyle='full';
const mapUrl=id=>location.origin+'/packs/'+id+'.pmtiles';
const mapAsset=path=>location.origin+'/assets/'+path;
const mapDate=t=>t?new Date(t).toLocaleString(undefined,{hourCycle:'h23'}):'Unknown';
const mapMoment=value=>value?new Date(value).getTime():0;
const mapText=(tag,content,cls='')=>{const e=document.createElement(tag);e.textContent=content;if(cls)e.className=cls;return e;};
function mapButton(parent,label,action){const b=mapText('button',label);b.type='button';b.onclick=action;parent.appendChild(b);return b;}
function mapLabel(parent,title,control){const l=mapText('label',title);l.appendChild(control);parent.appendChild(l);return control;}
function mapInput(parent,title,value='',type='text'){const e=document.createElement('input');e.className='form-input';e.type=type;e.value=value;return mapLabel(parent,title,e);}
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
  mapData={nodes:data.nodes||[],positions:data.positions||[],heard:data.heard||[],items:data.items||[],packs:data.packs||[]};
  if(!mapData.packs.some(p=>p.id===mapPackId)){
    const saved=mapData.packs.find(p=>p.id===data.selectedPackId);
    mapPackId=saved?.id||'';if(saved)mapLayerKind=saved.kind;
  }
  if(!mapData.packs.some(p=>p.kind===mapLayerKind))mapLayerKind=mapData.packs.some(p=>p.kind==='street')?'street':mapData.packs.some(p=>p.kind==='satellite')?'satellite':'street';
  initMap();setMapMode(mapMode);renderMap();renderMapItems();
};
function mapFilterRows(){
  const network=el('map-network').value,device=el('map-device').value,role=el('map-role').value,rf=el('map-rf').value;
  const from=mapMoment(el('map-from').value),to=mapMoment(el('map-to').value);
  const matching=mapData.nodes.filter(n=>{
    if(network!=='all'&&n.radio!==network)return false;if(device!=='all'&&n.radio+':'+n.device!==device)return false;if(role!=='all'&&n.radio+':'+n.role!==role)return false;if(rf!=='all'&&(n.receivingRf||'')!==rf)return false;
    if(from||to)return mapData.heard.some(h=>h.radio===n.radio&&h.device===n.device&&h.node===n.id&&(!from||h.time>=from)&&(!to||h.time<=to));
    return true;
  });
  // The same radio node may be known through several receiving devices; show its newest position once in All.
  const byNode=new Map();for(const n of matching){const key=n.radio+':'+n.id,old=byNode.get(key);if(!old||n.positionAt>old.positionAt||(!old.lat&&n.lat))byNode.set(key,n);}
  return [...byNode.values()];
}
function renderMapFilters(){
  const device=el('map-device'),old=device.value;device.replaceChildren();const all=mapText('option','All devices');all.value='all';device.appendChild(all);
  for(const d of state?.devices||[]){const option=mapText('option',(d.radio||'').toUpperCase()+' · '+(d.name||d.id));option.value=d.radio+':'+d.id;device.appendChild(option);}
  if([...device.options].some(o=>o.value===old))device.value=old;
  const roles=el('map-role');populateRoleMenu(roles,mapData.nodes,roles.value);
  const rf=el('map-rf'),oldRf=rf.value;rf.replaceChildren();const anyRf=mapText('option','All settings');anyRf.value='all';rf.appendChild(anyRf);
  for(const value of [...new Set(mapData.nodes.map(n=>n.receivingRf||''))].sort()){const option=mapText('option',value||'Unknown');option.value=value;rf.appendChild(option);}
  if([...rf.options].some(o=>o.value===oldRf))rf.value=oldRf;
}
function clearMapFilters(){for(const id of ['map-network','map-device','map-role','map-rf'])el(id).value='all';el('map-from').value='';el('map-to').value='';renderMap();}
function renderMap(){
  if(!mapView)return;renderMapFilters();
  for(const m of mapMarkers)m.remove();mapMarkers=[];
  const rows=mapFilterRows(),placed=rows.filter(n=>Number.isFinite(n.lat)&&Number.isFinite(n.lon));
  for(const n of placed){
    const dot=mapText('span','',`map-node-dot ${n.radio}${n.positionAt&&Date.now()-n.positionAt>86400000?' old':''}`);
    const popup=document.createElement('div');popup.appendChild(mapText('strong',n.name||n.id));popup.appendChild(mapText('div',n.radio.toUpperCase()+' · '+displayRole(n.radio,n.role)));popup.appendChild(mapText('div','Receiving radio RF (last known): '+(n.receivingRf||'Unknown')));popup.appendChild(mapText('div','Last heard: '+mapDate(n.lastHeard)));popup.appendChild(mapText('div','Position reported: '+mapDate(n.positionAt)));
    mapButton(popup,'Show saved positions',()=>showMapTrail(n));
    const marker=new maplibregl.Marker({element:dot}).setLngLat([n.lon,n.lat]).setPopup(new maplibregl.Popup({offset:15}).setDOMContent(popup)).addTo(mapView);mapMarkers.push(marker);
  }
  const missing=rows.filter(n=>!Number.isFinite(n.lat)||!Number.isFinite(n.lon));el('map-counts').textContent=placed.length+' positioned · '+missing.length+' without position';
  const list=el('map-no-position-list');list.replaceChildren();for(const n of missing)list.appendChild(mapText('div',n.radio.toUpperCase()+' · '+(n.name||n.id)+' · '+n.id));
  if(mapFocus){const n=placed.find(x=>x.radio===mapFocus.radio&&x.id===mapFocus.id&&(!mapFocus.device||x.device===mapFocus.device));if(n){mapView.flyTo({center:[n.lon,n.lat],zoom:Math.max(mapView.getZoom(),13)});const marker=mapMarkers[placed.indexOf(n)];marker?.togglePopup();mapFocus=null;}}
  for(const item of mapData.items.filter(i=>i.kind==='mark'&&i.enabled)){
    const [lon,lat]=item.geometry.coordinates;if(!Number.isFinite(lon)||!Number.isFinite(lat))continue;
    const pin=mapText('span','📍');pin.style.fontSize='23px';const popup=document.createElement('div');popup.appendChild(mapText('strong',item.title));popup.appendChild(mapText('div',item.description||'Saved mark'));
    mapMarkers.push(new maplibregl.Marker({element:pin}).setLngLat([lon,lat]).setPopup(new maplibregl.Popup({offset:15}).setDOMContent(popup)).addTo(mapView));
  }
  drawMapItems();
  if(mapToolName==='route')mapTool('route');
}
function mapFitVisible(){if(!mapView)return;const rows=mapFilterRows().filter(n=>Number.isFinite(n.lat)&&Number.isFinite(n.lon));if(!rows.length){mapNotice('No positioned nodes match these filters.');return;}const bounds=new maplibregl.LngLatBounds();for(const n of rows)bounds.extend([n.lon,n.lat]);mapView.fitBounds(bounds,{padding:45,maxZoom:15});}
window.openMapNode=(radio,id,device='')=>{mapFocus={radio,id,device};clearMapFilters();el('map-network').value=radio;switchTab('map');AndroidBridge.loadMap();};
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
    const segments=[];for(const radio of ['mc','mt']){
      const trace=state?.[radio]?.lastTrace;if(!trace||trace.pending)continue;const hops=trace.forward||[];
      const coordinate=hop=>{if(hop.id===state[radio].ownId&&Number.isFinite(state[radio].ownLongitude))return [state[radio].ownLongitude,state[radio].ownLatitude];
        const known=mapData.nodes.find(n=>n.radio===radio&&n.id===hop.id&&Number.isFinite(n.lon)&&Number.isFinite(n.lat));return known?[known.lon,known.lat]:null;};
      for(let i=1;i<hops.length;i++){const a=coordinate(hops[i-1]),b=coordinate(hops[i]);if(a&&b)segments.push({type:'Feature',properties:{radio},geometry:{type:'LineString',coordinates:[a,b]}});}
    }
    if(segments.length){mapView.addSource('map-radio-path',{type:'geojson',data:{type:'FeatureCollection',features:segments}});mapView.addLayer({id:'map-radio-path-line',type:'line',source:'map-radio-path',paint:{'line-color':'#b49eff','line-width':3,'line-dasharray':[2,3]}});}
  }
}
function renderMapItems(){const host=el('map-items');host.replaceChildren();if(!mapData.items.length)return;host.appendChild(mapText('h3','Saved map items'));
  for(const item of mapData.items){const row=document.createElement('div');row.className='map-item';row.appendChild(mapText('strong',item.title));row.appendChild(mapText('small',' · '+item.kind));
    const actions=mapText('div','','map-item-actions');mapButton(actions,'View',()=>{const c=item.kind==='mark'?item.geometry.coordinates:item.geometry.type==='Polygon'?item.geometry.coordinates[0][0]:item.geometry.coordinates[0];mapView.flyTo({center:c,zoom:Math.max(mapView.getZoom(),13)});});
    if(item.kind==='mark')mapButton(actions,'Send by MT',()=>{mapTool('mark');el('map-send-mark').value=item.id;});
    if(item.kind!=='mark')mapButton(actions,item.enabled?'Turn off':'Turn on',()=>AndroidBridge.toggleMapItem(item.id,!item.enabled));
    mapButton(actions,'Share file',()=>AndroidBridge.exportMapItem(item.id));
    mapButton(actions,'Delete',()=>{if(confirm('Delete '+item.title+'?'))AndroidBridge.deleteMapItem(item.id);});row.appendChild(actions);host.appendChild(row);
  }
}
function mapTool(name){
  mapToolName=name;mapDrawing=null;mapDraft=[];drawMapItems();for(const b of document.querySelectorAll('.map-tool-nav button'))b.classList.toggle('active',b.textContent===({passive:'Passive activity',active:'Ask positions',route:'Radio path',mark:'Marks',overlay:'Overlays',geofence:'Geofences'}[name]));
  const host=el('map-tool-panel');host.replaceChildren();const title={passive:'Passive activity',active:'Ask nodes for positions',route:'Radio path',mark:'Marks',overlay:'Overlays',geofence:'Geofences'}[name];host.appendChild(mapText('h3',title));
  const instruction=text=>host.appendChild(mapText('p',text));
  if(name==='passive'){
    instruction('1. Connect a radio. 2. Leave it listening. 3. Incoming messages and position reports build this phone’s history without transmitting. Use Heard from/to above to review it.');
    host.appendChild(mapText('p',mapData.heard.length+' saved hearing events · '+mapData.positions.length+' saved positions shown. Position updates can be less frequent than radio contact.'));
  }else if(name==='active'){
    instruction('1. Choose your connected Meshtastic device and a channel it reported. 2. Tap Ask once. 3. Watch for replies. This broadcasts one position request and uses shared radio airtime.');
    const s=state?.mt,choices=(s?.channels||[]).map(c=>[String(c.index),c.name]);const select=mapSelect(host,'Channel',choices.length?choices:[['','Connect an MT device']]);
    mapButton(host,'Ask for positions',()=>AndroidBridge.senseMesh(s?.key||'',Number(select.value))).disabled=!s?.connected||!choices.length;
    host.appendChild(mapText('p','The app allows one mesh-wide request every three minutes. Nodes may decline or be out of range.'));
  }else if(name==='route'){
    instruction('1. Choose a node on your connected radio. 2. Request a radio trace. 3. Read the hops. A radio path is not the node’s physical travel route.');
    const candidates=['mc','mt'].flatMap(r=>(state?.[r]?.nodes||[]).filter(n=>state[r].connected).map(n=>[state[r].key+'|'+n.id,r.toUpperCase()+' · '+(n.name||n.id)]));
    const select=mapSelect(host,'Node',candidates.length?candidates:[['','Connect a radio']]);mapButton(host,'Request radio path',()=>{const [key,id]=select.value.split('|');if(key&&id)AndroidBridge.mapNodeAction(key,id,'tr');}).disabled=!candidates.length;
    for(const r of ['mc','mt']){const trace=state?.[r]?.lastTrace;if(!trace)continue;host.appendChild(mapText('p',r.toUpperCase()+' · '+(trace.name||trace.target)+' · '+(trace.note||'Latest path')+' · '+(trace.forward||[]).map(h=>h.name).join(' → ')));}
  }else if(name==='mark'){
    instruction('1. Tap a point on Map or enter coordinates. 2. Give it a name and save. 3. Optionally send it over a Meshtastic channel or share a file.');
    const title=mapInput(host,'Name');const desc=mapInput(host,'Description');const lat=mapInput(host,'Latitude','','number'),lon=mapInput(host,'Longitude','','number');lat.step=lon.step='any';
    mapButton(host,'Tap map to choose position',()=>{mapDrawing='mark';mapDraft=[];mapNotice('Tap a position on the map.');mapView.once('click',e=>{lat.value=e.lngLat.lat.toFixed(6);lon.value=e.lngLat.lng.toFixed(6);mapDrawing=null;mapDraft=[];drawMapItems();});});
    mapButton(host,'Save mark',()=>{const latitude=Number(lat.value),longitude=Number(lon.value);if(!title.value.trim()||!lat.value||!lon.value||!Number.isFinite(latitude)||!Number.isFinite(longitude)||latitude<-90||latitude>90||longitude<-180||longitude>180){mapNotice('Enter a name and valid latitude/longitude.');return;}
      AndroidBridge.saveMapItem(JSON.stringify({id:crypto.randomUUID(),kind:'mark',title:title.value.trim(),description:desc.value.trim(),geometry:{type:'Point',coordinates:[longitude,latitude]},enabled:true,created:Date.now(),edited:Date.now()}));});
    const marks=mapData.items.filter(i=>i.kind==='mark');const send=mapSelect(host,'Saved mark',marks.length?marks.map(i=>[i.id,i.title]):[['','Save a mark first']]);send.id='map-send-mark';
    const channels=(state?.mt?.channels||[]).map(c=>[String(c.index),c.name]);const channel=mapSelect(host,'Meshtastic channel',channels.length?channels:[['','Connect MT first']]);
    mapButton(host,'Send mark by radio',()=>AndroidBridge.sendMapMark(send.value,state?.mt?.key||'',Number(channel.value))).disabled=!state?.mt?.connected||!marks.length||!channels.length;
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
