const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const {pathToFileURL}=require('node:url');
const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
 try{
  const page=await browser.newPage({viewport:{width:390,height:756}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(()=>{window.AndroidBridge=new Proxy({},{get:()=>()=>{}});});
  await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
  await page.evaluate(()=>{
   switchTab('contacts');window.person={...newContact(),id:'scroll-person',name:'Scroll check',
    phones:Array.from({length:15},(_,i)=>({label:'Phone '+i,value:'123456'})),
    nodes:[{radio:'mt',id:'!12345678',name:'Test node'}],
    notes:Array.from({length:8},(_,i)=>({id:'note'+i,date:1,text:'Note '+i}))};
   onContactsData({items:[person],view:{}});editContact(person);
  });
  const dialog=page.locator('#contacts-dialog');
  const add=dialog.getByRole('button',{name:'Add note',exact:true});
  await add.scrollIntoViewIfNeeded();
  const before=await dialog.evaluate(e=>e.scrollTop);assert.ok(before>500);
  await add.click();
  assert.ok(Math.abs(await dialog.evaluate(e=>e.scrollTop)-before)<5,'Add note preserves editor scroll');
  const remove=dialog.getByRole('button',{name:'Remove note',exact:true}).nth(4);
  await remove.scrollIntoViewIfNeeded();const beforeRemove=await dialog.evaluate(e=>e.scrollTop);
  await remove.click();
  assert.ok(Math.abs(await dialog.evaluate(e=>e.scrollTop)-beforeRemove)<5,'Remove note preserves editor scroll');
  await page.evaluate(()=>{chooseContactNodes();});
  assert.equal(await dialog.evaluate(e=>e.scrollTop),0,'New dialog screen starts at top');
  await dialog.getByRole('button',{name:'Done',exact:true}).click();
  assert.equal(await dialog.evaluate(e=>e.scrollTop),0,'Return from picker does not reuse picker scroll');
  await page.evaluate(()=>{contactEdit=null;el('contacts-dialog').close();openContact(person.id);});
  const nodeButton=page.locator('#contact-detail .contact-node > button').first();
  await nodeButton.scrollIntoViewIfNeeded();const panel=page.locator('#tab-contacts');
  const beforeExpand=await panel.evaluate(e=>e.scrollTop);assert.ok(beforeExpand>300);
  await nodeButton.click();
  assert.ok(Math.abs(await panel.evaluate(e=>e.scrollTop)-beforeExpand)<5,'Node expansion preserves scroll');
  const beforeRefresh=await panel.evaluate(e=>e.scrollTop);
  await page.evaluate(()=>onContactsData({items:[person],view:{}}));
  assert.ok(Math.abs(await panel.evaluate(e=>e.scrollTop)-beforeRefresh)<5,'Background refresh preserves scroll');
  assert.deepEqual(errors,[]);
  console.log('Contact editor add/remove, picker transitions, node expansion and background refresh scroll passed');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
