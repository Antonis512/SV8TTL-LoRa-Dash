'use strict';
const mapHistoryAge=30*86400000,mapTrackGap=30*60000;
let selectedMapTrail=null;
function orderedTrackPoints(points,now=Date.now()){
 const sorted=points.filter(p=>Number.isFinite(p.lat)&&Number.isFinite(p.lon)&&Math.abs(p.lat)<=90&&Math.abs(p.lon)<=180&&p.time>=now-mapHistoryAge&&p.time<=now+300000).slice().sort((a,b)=>a.time-b.time||a.lat-b.lat||a.lon-b.lon);
 const out=[];for(const p of sorted){const last=out.at(-1);if(last&&last.time===p.time&&last.lat===p.lat&&last.lon===p.lon)continue;out.push(p);}return out;
}
function trackSegments(points){
 const lines=[];
 for(let i=1;i<points.length;i++){
  const a=points[i-1],b=points[i];if(b.time<=a.time||b.time-a.time>mapTrackGap||a.lat===b.lat&&a.lon===b.lon)continue;
  let lon=b.lon;while(lon-a.lon>180)lon-=360;while(lon-a.lon< -180)lon+=360;
  lines.push([[a.lon,a.lat],[lon,b.lat]]);
 }return lines;
}
// Screen-sized arrowheads face from the older sample toward the newer sample.
function trackArrow(segment,project,unproject){
 const a=project(segment[0]),b=project(segment[1]),dx=b.x-a.x,dy=b.y-a.y,length=Math.hypot(dx,dy);
 if(length<18)return null;
 const ux=dx/length,uy=dy/length,size=Math.min(9,length*.18),x=a.x+dx*.65,y=a.y+dy*.65;
 return [[x-ux*size-uy*size*.55,y-uy*size+ux*size*.55],[x,y],[x-ux*size+uy*size*.55,y-uy*size-ux*size*.55]].map(p=>{const q=unproject(p);return [q.lng,q.lat];});
}
function visibleSharedTracks(){
 const grouped=new Map(),network=el('map-network').value,device=el('map-device').value;
 const from=mapMoment(el('map-from').value),to=mapMoment(el('map-to').value),contact=el('map-contact').value;
 const role=el('map-role').value,rf=el('map-rf').value,people=typeof contactPeople==='function'?contactPeople():[];
 for(const p of mapData.sharedPositions||[]){
  if(mapData.items.some(i=>i.id===p.stream&&i.enabled===false))continue;
  if(network!=='all'&&network!==p.radio||device!=='all'&&device!==p.radio+':'+p.device||from&&p.time<from||to&&p.time>to)continue;
  const node=mapData.nodes.find(n=>n.radio===p.radio&&n.device===p.device&&geofenceNodeKey(n.radio,n.id)===geofenceNodeKey(p.radio,p.sender));
  if(role!=='all'&&(!node||node.radio+':'+node.role!==role)||rf!=='all'&&(!node||heardProfile(node.radio,node.receivingRf)!==rf))continue;
  if(contact!=='all'||mapContactGroups.length){const linked=people.filter(c=>(c.nodes||[]).some(n=>n.radio===p.radio&&geofenceNodeKey(n.radio,n.id)===geofenceNodeKey(p.radio,p.sender)));if(!linked.some(c=>(contact==='all'||c.id===contact)&&(!mapContactGroups.length||mapContactGroups.some(id=>(c.categories||[]).includes(id)))))continue;}
  const key=JSON.stringify([p.radio,p.device,p.stream]);if(!grouped.has(key))grouped.set(key,[]);grouped.get(key).push(p);
 }
 return [...grouped.values()].map(points=>orderedTrackPoints(points)).filter(points=>points.length);
}
function setTrackLayer(id,features,color,width){
 const data={type:'FeatureCollection',features};
 if(mapView.getSource(id))mapView.getSource(id).setData(data);else mapView.addSource(id,{type:'geojson',data});
 if(!mapView.getLayer(id+'-line'))mapView.addLayer({id:id+'-line',type:'line',source:id,layout:{'line-cap':'round','line-join':'round'},paint:{'line-color':color,'line-width':width,'line-opacity':.85}});
}
function drawDirectionalTracks(){
 if(!mapView||(!mapStyleReady&&!mapView.isStyleLoaded()))return;
 const features=[],arrows=[],tracks=visibleSharedTracks();
 if(selectedMapTrail&&mapFilterRows().some(n=>sameMapNode(n,selectedMapTrail))){const n=selectedMapTrail,from=mapMoment(el('map-from').value),to=mapMoment(el('map-to').value);tracks.push(orderedTrackPoints(mapData.positions.filter(p=>p.radio===n.radio&&p.device===n.device&&p.node===n.id&&(!from||p.time>=from)&&(!to||p.time<=to))));}
 for(const points of tracks)for(const segment of trackSegments(points)){
  features.push({type:'Feature',properties:{},geometry:{type:'LineString',coordinates:segment}});
  const arrow=trackArrow(segment,p=>mapView.project(p),p=>mapView.unproject(p));
  if(arrow)arrows.push({type:'Feature',properties:{},geometry:{type:'LineString',coordinates:arrow}});
 }
 setTrackLayer('map-location-tracks',features,'#e5ad43',2);setTrackLayer('map-location-arrows',arrows,'#e5ad43',3);
}
function renderSharedTrackMarkers(){
 const tracks=visibleSharedTracks();
 for(const points of tracks){
  const last=points.at(-1),pin=mapText('button','','map-shared-location '+last.radio);pin.type='button';
  pin.append(mapText('span','●'),mapText('span',last.label||last.sender,'map-shared-label'));
  pin.setAttribute('aria-label',tr('Shared phone location: ')+(last.label||last.sender));
  const popup=document.createElement('div');popup.append(mapText('strong',last.label||last.sender),mapText('div',last.radio.toUpperCase()+tr(' · Shared phone location')),mapText('div',tr('Last report: ')+mapDate(last.time)),mapText('div',last.lat.toFixed(6)+', '+last.lon.toFixed(6)),mapText('div','Receiver: '+last.device+(last.channel>=0?tr(' · channel ')+last.channel:'')),mapText('div',points.length+tr(' retained reports · arrows point toward newer reports')));
  if(last.radio==='mc'&&last.channel>=0)popup.append(mapText('div',tr('Node identity supplied in the shared message')));
  const marker=new maplibregl.Marker({element:pin,anchor:'bottom'}).setLngLat([last.lon,last.lat]).setPopup(new maplibregl.Popup({offset:18}).setDOMContent(popup)).addTo(mapView);
  pin.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();marker.togglePopup();}};
  mapMarkers.push(marker);
 }
 return tracks.length;
}
