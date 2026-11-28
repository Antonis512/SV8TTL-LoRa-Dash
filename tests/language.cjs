const {chromium}=require('playwright'),assert=require('node:assert/strict');
const {pathToFileURL}=require('node:url');const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||'C:/Program Files/Google/Chrome/Application/chrome.exe'});
 try{
  const page=await browser.newPage({viewport:{width:390,height:780}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(()=>{window.calls=[];window.AndroidBridge=new Proxy({setAppLanguage(language){calls.push(['setAppLanguage',language]);fixture.appLanguage=language;fixture.memberLanguage=language;onRadioState(fixture);}},{get:(o,k)=>o[k]||((...a)=>calls.push([k,...a]))});});
  await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
  await page.evaluate(()=>{
   const radio=id=>({name:'Map',ownName:'Send',ownId:id==='mt'?'!12345678':'abcdef012345',key:id+'1',connected:true,channels:[{index:2,name:'Map'}],nodes:[{id:'!abcdef12',name:'Send',role:'User',lastHeard:Math.floor(Date.now()/1000)}],directs:{},chats:{2:[{id:1,sender:'!abcdef12',text:'Send',time:'12:00'}]},state:'Connected',loading:false,scanProgress:0});
   window.fixture={mc:radio('mc'),mt:radio('mt'),appLanguage:'en',memberLanguage:'en',devices:[],myNodes:[],memberConfig:{enabled:false},sound:{mode:'shared',shared:{}}};
   onRadioState(fixture);onContactsData({items:[{id:'group',kind:'category',title:'Map'},{id:'person',kind:'contact',name:'Send',categories:['group'],nodes:[]}]});
   document.getElementById('chat-input').value='Map Send ${test}';switchTab('settings');showSettingsPage('home');
  });
  await page.locator('#select-language').click();
  await page.getByRole('radio',{name:'Ελληνικά',exact:true}).check();
  await page.getByRole('button',{name:'Apply / Εφαρμογή',exact:true}).click();
  assert.equal(await page.locator('html').getAttribute('lang'),'el');
  assert.equal(await page.locator('#current-language').textContent(),'Ελληνικά');
  assert.equal(await page.locator('#config-connect-all strong').textContent(),'Σύνδεση γνωστών κόμβων');
  assert.equal(await page.locator('#connect-all-available').textContent(),'Σύνδεση όλων των διαθέσιμων');
  assert.equal(await page.locator('#new-multi-send').textContent(),'Μαζική αποστολή');
  assert.equal(await page.locator('[data-persistent-kind="message"]').first().textContent(),'Εμμένον Μήνυμα');
  assert.equal(await page.locator('[data-persistent-kind="location"]').first().textContent(),'Εμμένουσα αποστολή θέσης');
  for(const width of [320,390]){
   await page.setViewportSize({width,height:780});await page.evaluate(()=>{switchTab('chat');selectDmPeer('mt','!abcdef12');});
   const row=await page.locator('#dm-center .persistent-chat-controls').evaluate(e=>[...e.children].map(b=>{const r=b.getBoundingClientRect();return {top:r.top,left:r.left,right:r.right,width:r.width};}));
   assert.equal(row.length,3);assert(row.every(r=>r.width>0&&r.top===row[0].top&&r.left>=0&&r.right<=width));
  }
  await page.evaluate(()=>{switchTab('settings');showSettingsPage('home');});

  assert.equal(await page.locator('#chat-input').inputValue(),'Map Send ${test}');
  assert.equal(await page.locator('[data-tab="settings"] span').last().textContent(),'Ρυθμίσεις');
  assert.equal(await page.locator('#settings-home button[onclick="checkForUpdates()"] strong').textContent(),'Έλεγχος ενημερώσεων');
  assert.equal(await page.evaluate(()=>trTemplate('Message ${index+1}',[7])),await page.evaluate(()=>greekTranslations['Message ${index+1}'].replace('${index+1}','7')));
  assert.equal(await page.evaluate(()=>tr('constructor')),'constructor');
  await page.evaluate(()=>document.getElementById('language-frame').scrollIntoView({block:'center'}));
  await page.screenshot({path:'build/previews/config-language-implemented-el.png'});
  await page.evaluate(()=>switchTab('contacts'));
  assert.equal(await page.locator('.contact-person strong').textContent(),'Send');
  assert.equal(await page.locator('#contacts-categories option[value="group"]').textContent(),'Map');
  assert.equal(await page.locator('#contacts-categories option[value=""]').textContent(),await page.evaluate(()=>tr('All groups')));
  await page.evaluate(()=>openPersistent(false,'chat-input'));
  await page.evaluate(()=>document.getElementById('persistent-dialog').close());
  for(const [width,height] of [[320,640],[390,780],[390,430],[780,390]]){
   await page.setViewportSize({width,height});await page.evaluate(()=>{switchTab('settings');showSettingsPage('home');openLanguagePicker();});
   const r=await page.locator('#language-picker').boundingBox();assert(r.x>=0&&r.y>=0&&r.x+r.width<=width&&r.y+r.height<=height);
   await page.getByRole('button',{name:'Cancel / Ακύρωση',exact:true}).click();
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  }
  await page.evaluate(()=>openLanguagePicker());await page.getByRole('radio',{name:'English',exact:true}).check();await page.getByRole('button',{name:'Apply / Εφαρμογή',exact:true}).click();
  assert.equal(await page.locator('#settings-home button[onclick="checkForUpdates()"] strong').textContent(),'Check for Updates');
  assert.equal(await page.locator('[data-tab="settings"] span').last().textContent(),'Config');
  assert.deepEqual(await page.evaluate(()=>calls.filter(c=>c[0]==='setAppLanguage')),[['setAppLanguage','el'],['setAppLanguage','en']]);
  assert.deepEqual(errors,[]);console.log('Language: bilingual selector, Greek/English round trip, placeholders, user names, drafts and narrow layouts passed');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
