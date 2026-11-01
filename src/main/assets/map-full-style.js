/* Locally authored colorful rendering of BBBike Shortbread vector layers. */
function fullShortbreadLayers(){
  const source='shortbread',kind=['get','kind'];
  const fill=(id,layer,color)=>({id,type:'fill',source,'source-layer':layer,paint:{'fill-color':color}});
  const line=(id,layer,color,width)=>({id,type:'line',source,'source-layer':layer,layout:{'line-join':'round','line-cap':'round'},paint:{'line-color':color,'line-width':width}});
  const label=(id,layer,size,color,minzoom=0,placement='point')=>({id,type:'symbol',source,'source-layer':layer,minzoom,
    layout:{'symbol-placement':placement,'text-field':['coalesce',['get','name'],['get','name_en'],''],'text-font':['Noto Sans Regular'],'text-size':size,'text-max-width':9},
    paint:{'text-color':color,'text-halo-color':'#fffdf5','text-halo-width':1.3}});
  const roadColor=['match',kind,'motorway','#e9a0a9','trunk','#efba8d','primary','#f6cf83','secondary','#f7e6a4','tertiary','#fff2c5',
    'track','#c8b390','path','#d0bfb1','footway','#d0bfb1','cycleway','#9cc5c5','#ffffff'];
  const width=['interpolate',['linear'],['zoom'],5,.4,10,1.3,14,3,18,9];
  const layers=[
    {id:'land',type:'background',paint:{'background-color':'#f2efe9'}},
    fill('land-use','land',['match',kind,['forest','wood'],'#add19e',['grass','meadow','grassland','village_green'],'#cdebb0',
      ['park','garden','recreation_ground'],'#c8e7b5',['farmland','farm','orchard','vineyard'],'#e6e8bd',
      ['residential'],'#e4dfdc',['industrial','commercial','retail'],'#e5d7e3',['sand','beach'],'#f5e8bc',
      ['scrub','heath'],'#d4dfb9',['cemetery'],'#bed2b4','#e6e4d9']),
    fill('sites','sites',['match',kind,['park','garden','pitch','golf_course','sports_centre'],'#c8e7b5',
      ['hospital'],'#f3d7d7',['school','university','college'],'#f3e7c5',['industrial'],'#e5d7e3','#e8e2d7']),
    fill('ocean','ocean','#a8cfe0'),fill('water','water_polygons','#a8cfe0'),
    line('waterways','water_lines','#91bfd3',['interpolate',['linear'],['zoom'],8,.6,16,2.5]),
    line('boundaries','boundaries','#ad91b0',1.2),
    fill('street-areas','street_polygons','#faf8f4'),
    fill('buildings','buildings','#d7c8bb'),
    line('ferries','ferries','#6eabc7',1),
    line('road-casing','streets','#b5aaa0',['interpolate',['linear'],['zoom'],5,.8,10,2,14,4.5,18,12]),
    line('streets','streets',roadColor,width),
    line('railways','streets','#9a9390',1),
    line('aerialways','aerialways','#a09999',1),
    line('piers','pier_lines','#ddd3c4',2),fill('pier-areas','pier_polygons','#ddd3c4'),
    label('water-labels','water_polygons_labels',12,'#397e9f',10),
    label('road-labels','street_labels',11,'#57534d',12,'line'),
    label('poi-labels','pois',11,'#775e50',15),
    label('transport-labels','public_transport',11,'#426c9c',13),
    label('address-labels','addresses',10,'#807367',17),
    label('place-labels','place_labels',['interpolate',['linear'],['zoom'],4,11,10,14,16,17],'#49443f',3)
  ];
  layers.find(l=>l.id==='railways').filter=['==',['get','rail'],true];
  layers.find(l=>l.id==='boundaries').paint['line-dasharray']=[4,3];
  layers.find(l=>l.id==='ferries').paint['line-dasharray']=[3,3];
  layers.find(l=>l.id==='address-labels').layout['text-field']=['coalesce',['get','housenumber'],''];
  return layers;
}
