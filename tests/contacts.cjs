const {chromium}=require('playwright');const assert=require('node:assert/strict');const path=require('node:path');const {pathToFileURL}=require('node:url');
(async()=>{const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});try{
const page=await browser.newPage({viewport:{width:390,height:780}}),errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
await page.addInitScript(()=>{
  window.calls=[];window.people=[];window.savedView={};
  window.AndroidBridge=new Proxy({},{get:(_,name)=>(...args)=>{
    calls.push([name,...args]);
    if(name==='loadContacts'&&window.onContactsData){onContactsData({items:people,view:savedView});if(window.radios)onContactNodes(radios);}
    if(name==='saveContacts'){
      if(window.failSave){onContactError('Test storage failure');return;}
      for(const item of JSON.parse(args[0])){const i=people.findIndex(c=>c.id===item.id);if(i<0)people.push(item);else people[i]=item;}
      onContactsData({items:people,view:savedView});onContactsSaved();
    }
    if(name==='contactsView')savedView=JSON.parse(args[0]);
  }});
});
await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
await page.evaluate(()=>{
  const radio=(r,id)=>({key:r+'-device',radio:r,name:r+' radio',ownId:'own-'+r,ownName:'Me',connected:true,loading:false,scanProgress:0,channels:[{index:0,name:'Local'}],nodes:[{id,name:r==='mc'?'Portable':'Base',role:r==='mc'?'User':'CLIENT',hops:1,lastHeard:Math.floor(Date.now()/1000)}],directs:{},chats:{0:[]}});
  window.f={mc:radio('mc','aabbccddeeff'),mt:radio('mt','!12345678'),devices:[],myNodes:[],sound:{mode:'shared',shared:{}},nodeNames:{}};window.radios=[f.mc,f.mt];onRadioState(f);switchTab('contacts');
});
await page.getByRole('button',{name:'+ Add Contact',exact:true}).click();
const modal=page.locator('#contacts-dialog');
await modal.getByLabel('Name',{exact:true}).fill('Νίκος');await modal.getByLabel('Surname',{exact:true}).fill('Παππάς');await modal.getByLabel('Alias',{exact:true}).fill('Electrician');
await modal.getByRole('button',{name:'Add callsigns',exact:true}).click();assert.equal(await modal.getByLabel('Callsigns label',{exact:true}).inputValue(),'Radio Amateur');await modal.getByLabel('Callsigns value',{exact:true}).fill('SV8ABC');
await modal.getByRole('button',{name:'Add phone numbers',exact:true}).click();await modal.getByLabel('Phone numbers value',{exact:true}).fill('+30 690 123 4567');
await modal.getByRole('button',{name:'Add email addresses',exact:true}).click();await modal.getByLabel('Email addresses value',{exact:true}).fill('nikos@example.com');
await modal.getByRole('button',{name:'Add custom fields',exact:true}).click();await modal.getByLabel('Custom fields label',{exact:true}).fill('Workshop');await modal.getByLabel('Custom fields value',{exact:true}).fill('Mountain station');
await modal.getByRole('button',{name:'Add note',exact:true}).click();await modal.getByLabel('Note',{exact:true}).fill('Repairs repeaters near Volos');
await modal.getByLabel('New category',{exact:true}).fill('Club');await modal.getByRole('button',{name:'Add category',exact:true}).click();
await modal.getByRole('button',{name:'Assign Nodes',exact:true}).click();await modal.getByLabel('Portable · MC · aabbccddeeff',{exact:true}).check();await modal.getByLabel('Base · MT · !12345678',{exact:true}).check();await modal.getByRole('button',{name:'Done',exact:true}).click();
await page.evaluate(()=>{const c=document.createElement('canvas');c.width=c.height=2;onContactPhoto({token:contactEdit.token,photo:c.toDataURL('image/jpeg')});});
await page.evaluate(()=>{window.failSave=true;});await modal.getByRole('button',{name:'Save Contact',exact:true}).click();assert.equal(await modal.isVisible(),true);assert.match(await modal.innerText(),/Test storage failure/);
await page.evaluate(()=>window.failSave=false);await modal.getByRole('button',{name:'Save Contact',exact:true}).click();assert.equal(await modal.isVisible(),false);
const person=await page.evaluate(()=>people.find(c=>c.kind==='contact'));assert.equal(person.nodes.length,2);assert.equal(person.categories.length,1);assert.ok(person.photo.startsWith('data:image/jpeg;base64,'));assert.equal(person.notes.length,1);
await page.locator('#contact-detail').getByRole('button',{name:'Call',exact:true}).click();assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['contactCommunicate','call','+30 690 123 4567']);
await page.locator('#contact-detail').getByRole('button',{name:'SMS',exact:true}).click();assert.equal(await page.evaluate(()=>calls.at(-1)[1]),'sms');
await page.locator('#contact-detail').getByRole('button',{name:'Email',exact:true}).click();assert.equal(await page.evaluate(()=>calls.at(-1)[1]),'email');
await page.locator('#contact-detail').getByRole('button',{name:'Portable · MC',exact:true}).click();await page.locator('#contact-detail').getByRole('button',{name:'☆ Add to Favorites',exact:true}).click();assert.deepEqual(await page.evaluate(()=>calls.at(-1)),['nodeAction','mc-device','aabbccddeeff','favorite','']);
await page.evaluate(()=>onRadioState(f));assert.equal(await page.locator('#contact-detail').getByRole('button',{name:'☆ Add to Favorites',exact:true}).isVisible(),true);
await page.locator('#contact-detail').getByRole('button',{name:'← Contacts',exact:true}).click();
for(const query of ['νικος','690123','mountain','repeater','electrcian','SV8ABC']){await page.getByRole('searchbox',{name:'Search contacts',exact:true}).fill(query);assert.equal(await page.locator('.contact-person').count(),1,query);assert.ok(await page.locator('.contact-match').innerText());}
await page.locator('#contacts-favorites').check();assert.equal(await page.locator('.contact-person').count(),0);assert.match(await page.locator('#contacts-count').innerText(),/1 matching contacts hidden/);await page.locator('#contacts-favorites').uncheck();await page.locator('#contacts-search').fill('');
await page.locator('.contact-person').click();await page.locator('#contact-detail').getByRole('button',{name:'Share',exact:true}).click();assert.equal(await modal.getByLabel('Private notes',{exact:true}).isChecked(),false);await modal.getByRole('button',{name:'Share selected fields',exact:true}).click();assert.ok(!(await page.evaluate(()=>calls.at(-1)[1])).includes('Repairs'));await modal.getByRole('button',{name:'Cancel',exact:true}).click();
await page.locator('#contact-detail').getByRole('button',{name:'Export',exact:true}).click();await modal.getByRole('button',{name:'Choose export file',exact:true}).click();const exported=await page.evaluate(()=>JSON.parse(calls.at(-1)[1]));assert.equal(exported.items[0].photo,person.photo);assert.equal(exported.items[0].nodes.length,2);
await page.evaluate(id=>onAndroidContact({target:id,contact:{name:'New Android name',phones:[{label:'Home',value:'12345'}],androidLink:{uri:'content://com.android.contacts/contacts/123',lookup:'123',refreshed:Date.now()}}}),person.id);
assert.equal(await modal.getByLabel('Name · New Android name',{exact:true}).isChecked(),false);await modal.getByLabel('Name · New Android name',{exact:true}).check();await modal.getByRole('button',{name:'Review profile',exact:true}).click();assert.equal(await modal.getByLabel('Name',{exact:true}).inputValue(),'New Android name');await modal.getByRole('button',{name:'Save Contact',exact:true}).click();assert.equal(await page.evaluate(()=>people.find(c=>c.kind==='contact').phones[0].value),'+30 690 123 4567');
await page.locator('#contact-detail').getByRole('button',{name:'Delete',exact:true}).click();await modal.getByRole('button',{name:'Delete Contact',exact:true}).click();assert.equal(await page.locator('.contact-person').count(),0);
await page.locator('#contacts-tools summary').click();await page.getByRole('button',{name:'Deleted contacts / Undo',exact:true}).click();await modal.getByRole('button',{name:'Restore',exact:true}).click();await modal.getByRole('button',{name:'Close',exact:true}).click();assert.equal(await page.locator('.contact-person').count(),1);
await page.evaluate(()=>{const original=people.find(c=>c.kind==='contact');const duplicate={...structuredClone(original),id:'person-duplicate',alias:'Other alias',notes:[{id:'n2',date:Date.now(),text:'Second note'}]};people.push(duplicate);onContactsData({items:people,view:savedView});});
await page.getByRole('button',{name:'Find duplicates',exact:true}).click();await modal.locator('button').first().click();await modal.getByRole('button',{name:'Save Contact',exact:true}).click();assert.equal(await page.evaluate(()=>contactPeople().length),1);assert.equal(await page.evaluate(()=>contactPeople()[0].notes.length),2);assert.ok(await page.evaluate(()=>contactPeople()[0].custom.some(v=>v.value==='Other alias')));
await page.evaluate(()=>{contactDetailBack();onContactImport([{id:'imported-person',kind:'contact',alias:'Imported',edited:1,nodes:[],notes:[]}]);});await modal.getByRole('button',{name:'Import selected',exact:true}).click();assert.equal(await page.evaluate(()=>contactPeople().length),2);
await page.evaluate(()=>assignNodeToContact('mc',{id:'heard-name:Guest',name:'Guest',role:'Heard sender',savedOnly:true}));
await modal.getByRole('button',{name:'Imported',exact:true}).click();await modal.getByRole('button',{name:'Save Contact',exact:true}).click();assert.equal(await page.evaluate(()=>contactById('imported-person').nodes[0].id),'heard-name:Guest');assert.match(await page.locator('#contact-detail').innerText(),/Unverified association/);
await page.evaluate(()=>{onLogItems([{id:'related-note',kind:'note',title:'Field work',body:'Test',categories:[],created:1,edited:1}]);editContact(contactById('imported-person'));});
await modal.getByLabel('Field work',{exact:true}).check();await modal.getByRole('button',{name:'Save Contact',exact:true}).click();assert.deepEqual(await page.evaluate(()=>contactById('imported-person').logs),['related-note']);
await page.evaluate(()=>openContact(contactPeople().find(c=>c.id!=='imported-person').id));await page.locator('#contact-detail summary').click();await page.locator('#contact-detail').getByLabel('Network',{exact:true}).selectOption('mt');assert.equal(await page.locator('#contact-detail .contact-node').count(),1);assert.match(await page.locator('#contact-detail .contact-node').innerText(),/Base/);await page.locator('#contact-detail').getByLabel('Network',{exact:true}).selectOption('all');
await page.evaluate(()=>deleteContact(contactById('imported-person')));await modal.getByRole('button',{name:'Delete Contact',exact:true}).click();await page.evaluate(()=>contactTrash());
await modal.locator('.contact-toolbar').filter({hasText:'Imported'}).getByRole('button',{name:'Delete permanently',exact:true}).click();assert.equal(await page.evaluate(()=>contactById('imported-person').purged),true);assert.equal(await page.evaluate(()=>contactById('imported-person').photo),undefined);assert.equal(await page.evaluate(()=>contactById('imported-person').nodes),undefined);await modal.getByRole('button',{name:'Close',exact:true}).click();
// Back and bottom-bar overflow at phone sizes, with reduced space for system bars/keyboard.
for(const [width,height] of [[320,640],[390,756],[390,732],[390,430],[780,640]]){await page.setViewportSize({width,height});await page.evaluate(()=>switchTab('contacts'));assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));assert.ok(await page.locator('#bottom-nav').evaluate(e=>e.getBoundingClientRect().bottom<=innerHeight));}
await page.setViewportSize({width:390,height:780});await page.locator('#contacts-tools').evaluate(e=>e.open=false);await page.screenshot({path:'build/contacts-list.png'});
await page.evaluate(()=>editContact(contactPeople()[0]));await page.screenshot({path:'build/contacts-editor.png'});assert.deepEqual(errors,[]);console.log('Contacts CRUD, search, filters, photo, node actions, import review, selective sharing, export, merge, undo and responsive layout passed');
}finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});
