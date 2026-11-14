const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const {pathToFileURL}=require('node:url');
const path=require('node:path');
(async()=>{
  const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH,args:['--enable-unsafe-swiftshader']});
  try{
    const page=await browser.newPage({viewport:{width:390,height:780}});
    await page.addInitScript(()=>{
      window.AndroidBridge=new Proxy({saveMapItem(json){window.saved=JSON.parse(json);onMapData({items:[saved]});}},{
        get:(target,key)=>target[key]||(()=>{})
      });
    });
    await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
    await page.evaluate(()=>{mapMode='offline';switchTab('map');onMapData({});mapTool('geofence');});
    await page.waitForFunction(()=>mapStyleReady);
    const panel=page.locator('#map-tool-panel');
    await panel.getByLabel('Name',{exact:true}).fill('Test area');
    await panel.getByText('Start drawing').click();
    const canvas=page.locator('#offline-map canvas');
    for(const [x,y] of [[65,55],[240,55],[170,140]])await canvas.click({position:{x,y}});
    assert.equal(await page.locator('.map-draft-handle').count(),3);
    await page.getByLabel('Close fence at first corner').click();
    // Tap off-centre on the top edge and on the closing edge.
    await canvas.click({position:{x:107,y:58}});
    await canvas.click({position:{x:137,y:113}});
    const corners=await page.evaluate(()=>mapDraft.map(p=>{const {x,y}=mapView.project(p);return [x,y];}));
    assert.equal(corners.length,5);
    for(const [index,start,end] of [[1,0,2],[4,3,0]]){
      for(const axis of [0,1])assert(Math.abs(corners[index][axis]-(corners[start][axis]+corners[end][axis])/2)<0.01);
    }
    await canvas.click({position:{x:268,y:126}});
    assert.equal(await page.locator('.map-draft-handle').count(),5);
    const corner=page.getByLabel('Corner 2',{exact:true});
    const original=await page.evaluate(()=>mapDraft[1].slice());
    const box=await corner.boundingBox();
    await page.mouse.move(box.x+box.width/2,box.y+box.height/2);await page.mouse.down();
    await page.mouse.move(box.x+45,box.y+32,{steps:6});await page.mouse.up();
    assert.notDeepEqual(await page.evaluate(()=>mapDraft[1]),original);
    await panel.getByText('Save',{exact:true}).click();
    assert.equal(await page.evaluate(()=>saved.geometry.coordinates[0].length),6);
    assert.deepEqual(await page.evaluate(()=>saved.geometry.coordinates[0][0]),await page.evaluate(()=>saved.geometry.coordinates[0].at(-1)));
    await page.waitForFunction(()=>mapView.getLayer('map-shapes-fill')&&!draftHandles.length);
    await page.getByText('Hide on map',{exact:true}).click();
    assert.equal(await page.evaluate(()=>!!mapView.getLayer('map-shapes-fill')),false);
    await page.getByText('View',{exact:true}).click();
    await page.waitForFunction(()=>!mapView.isMoving()&&!!mapView.getLayer('map-shapes-fill'));
    assert(await page.evaluate(()=>saved.geometry.coordinates[0].every(point=>mapView.getBounds().contains(point))));
    console.log('Passed MapLibre fence creation, closure, corner drag, save, visibility and full-boundary View.');
  }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
