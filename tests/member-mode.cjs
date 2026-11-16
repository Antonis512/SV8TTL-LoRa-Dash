const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const {pathToFileURL}=require('node:url');
const path=require('node:path');
(async()=>{
  const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH});
  try{
    const page=await browser.newPage({viewport:{width:390,height:780}});
    const errors=[];page.on('pageerror',e=>errors.push(e.message));page.setDefaultTimeout(4000);
    await page.addInitScript(()=>{
      window.AndroidBridge=new Proxy({
        connectAllAvailable(){window.connectCalls=(window.connectCalls||0)+1;},
        messagePartCount:(text,limit)=>Math.ceil(new TextEncoder().encode(text).length/limit),
        fetchMarkPosition(token){window.locationToken=token;},
        sendMemberLocation(json,lat,lon){window.shared=[JSON.parse(json)[0].peer,lat,lon];onMemberLocationResult(true);},
        sendMemberMessage(json){window.sent=JSON.parse(json);onMemberSendResult(true);},
        disableMemberMode(){fixture.memberConfig.enabled=false;onRadioState(fixture);},
        setMemberLanguage(lang){fixture.memberLanguage=lang;onRadioState(fixture);},
        saveMemberConfig(json){fixture.memberConfig=JSON.parse(json);onRadioState(fixture);}
      },{get:(obj,key)=>obj[key]||(()=>{})});
    });
    await page.goto(pathToFileURL(path.resolve('src/main/assets/index.html')).href);
    await page.evaluate(()=>{
      const radios=['mc','mt','mt'].map((radio,i)=>({
        radio,key:'r'+i,ownId:'self'+i,name:'Radio '+i,ownName:'Radio '+i,connected:true,loading:false,scanProgress:0,
        state:'Connected',channels:[{index:2,name:'Team'},{index:3,name:'Other'}],
        nodes:[{name:'Portable',role:'User',id:radio==='mc'?'abcdef123456':'!11111111'}],
        directs:{},chats:{},channelTextLimit:10,directTextLimit:10
      }));
      radios[1].nodes.push({id:'!22222222',name:'Spare',role:'User'});
      radios[2].directs['!33333333']=[{id:40,text:'Unknown DM',sender:'!33333333',time:'09:00'}];
      radios[2].chats[3]=[{id:41,text:'Unknown channel',sender:'!33333333',packetId:500,time:'09:01'},
        {id:42,text:'My message',sender:'You',own:true,time:'09:02'}];
      radios[1].directs['!11111111']=[{id:1,sender:'!11111111',text:'Incoming DM',time:'10:00'}];
      radios[0].chats[2]=[{id:2,sender:'MeshCore',text:'Pat: Incoming MC',time:'10:02'}];
      radios[1].chats[2]=[{id:3,sender:'!11111111',text:'Incoming MT',time:'10:03',packetId:365}];
      window.fixture={mc:radios[0],mt:radios[1],left:radios[0],right:radios[1],memberRadios:radios,
        selectedMc:'r0',selectedMt:'r1',devices:radios.map(r=>({...r,id:r.ownId,pathNodes:r.nodes})),myNodes:[],
        presetMessages:[{title:'Ready',text:'Ready now'}],sound:{mode:'shared',shared:{}},
        memberConfig:{enabled:true,channels:{'mc:self0':[radios[0].channels[0]],'mt:self1':radios[1].channels},contacts:['p']}};
      onRadioState(fixture);
      onContactsData({items:[{id:'p',displayName:'Pat',nodes:[
        {radio:'mc',id:'abcdef123456',name:'Base'},{radio:'mt',id:'!11111111',name:'Portable'},
        {radio:'mt',id:'!22222222',name:'Spare'}]}]});
    });
    assert.equal(await page.locator('#member-chat').isVisible(),true);
    assert.equal(await page.locator('#chat-header-bar').isVisible(),false);
    await page.evaluate(()=>{
      fixture.memberRadios[0].chats[2].push({id:4,sender:'MeshCore',text:'Pat: m:38.123456,24.654321|Location|poi',time:'10:04'});
      renderMemberChat();
    });
    await page.getByRole('button',{name:'View on map'}).click();
    assert.match(await page.locator('#member-map-label').innerText(),/Pat/);
    assert(await page.locator('#member-map-body .maplibregl-marker').count()>0);
    await page.evaluate(()=>navigateBack());
    await page.waitForSelector('.map-canvas-wrap #offline-map',{state:'attached'});
    await page.locator('#member-radio').selectOption('mt:self1');
    await page.locator('#member-use-dm').check();
    const dialog=page.locator('#member-recipients');
    assert.match(await dialog.locator('#member-recipient-help').innerText(),/replaces the previous choice/);
    await dialog.getByRole('button',{name:'OK',exact:true}).click();
    assert.equal(await dialog.getByRole('alert').innerText(),'Choose a contact before continuing.');
    await dialog.getByRole('button',{name:'Cancel',exact:true}).click();
    assert.equal(await page.locator('#member-use-dm').isChecked(),false);
    await page.getByRole('button',{name:'Ελληνικά'}).click();
    await page.locator('#member-use-dm').check();
    assert.match(await dialog.locator('#member-recipient-help').innerText(),/αντικαθιστά την προηγούμενη/);
    await dialog.getByRole('button',{name:'Εντάξει'}).click();
    assert.equal(await dialog.getByRole('alert').innerText(),'Επιλέξτε μια επαφή για να συνεχίσετε.');
    await dialog.getByLabel('Pat',{exact:true}).check();
    await dialog.getByRole('button',{name:'Εντάξει'}).click();
    assert.equal(await dialog.getByRole('alert').innerText(),'Επιλέξτε έναν διαθέσιμο κόμβο για αυτή την επαφή.');
    await page.evaluate(()=>navigateBack());
    assert.equal(await page.locator('#member-use-dm').isChecked(),false);
    await page.getByRole('button',{name:'English'}).click();
    await page.locator('#member-use-dm').check();
    await dialog.getByLabel('Pat',{exact:true}).check();
    await dialog.getByLabel(/Spare/).check();
    assert(await dialog.getByLabel(/Base/).isDisabled());
    await dialog.getByLabel(/Portable/).check();
    await dialog.getByText('OK',{exact:true}).click();
    await page.locator('#member-input').fill('Keep my draft');
    const share=page.locator('#member-location');
    await share.click();
    assert(await page.locator('#member-location-confirm').isDisabled());
    await page.evaluate(()=>navigateBack());
    await page.evaluate(()=>onMarkPhonePosition(locationToken,38,24,'Phone position'));
    assert.equal(await page.evaluate(()=>window.shared),undefined);
    await share.click();
    await page.evaluate(()=>onMarkPhonePosition(locationToken,38,24,'Phone position'));
    assert.match(await page.locator('#member-location-target').innerText(),/Pat.*!11111111/);
    await page.locator('#member-location-confirm').click();
    assert.deepEqual(await page.evaluate(()=>shared),['!11111111',38,24]);
    assert.equal(await page.locator('#member-input').inputValue(),'Keep my draft');
    await page.locator('#member-template').selectOption('0');
    await page.locator('#member-send').click();
    assert.deepEqual(await page.evaluate(()=>sent.map(t=>[t.nodeId,t.peer])),[['self1','!11111111']]);
    await page.locator('#member-use-channel').check();
    const channels=page.locator('#member-channel-list');
    await channels.getByLabel('Other').check();
    await channels.getByLabel('Team').check();
    assert.equal(await channels.locator(':checked').count(),1);
    await page.locator('#member-input').fill('x'.repeat(30));
    assert.match(await page.locator('#member-budget').textContent(),/Split limit reached/);
    await page.locator('#member-input').fill('x'.repeat(31));
    assert(await page.locator('#member-send').isDisabled());
    await page.locator('#member-input').fill('Hello');
    await page.locator('#member-send').click();
    assert.deepEqual(await page.evaluate(()=>sent.map(t=>[t.nodeId,t.channel,t.peer])),[['self1',2,'']]);
    for(const [message,id,peer,source] of [['Incoming DM','self1','!11111111',1],['Incoming MT','self1','',3],['Incoming MC','self0','',2],['Unknown DM','self2','!33333333',40],['Unknown channel','self2','',41]]){
      const article=page.locator('#member-history article').filter({hasText:message});
      await article.getByRole('button',{name:'Reply'}).click();
      if(message==='Incoming MC')assert.match(await page.locator('#member-input').inputValue(),/^@\[Pat\]/);
      await page.locator('#member-input').pressSequentially('OK');await page.locator('#member-send').click();
      assert.deepEqual(await page.evaluate(()=>sent.map(t=>[t.nodeId,t.peer,t.sourceId])),[[id,peer,source]]);
    }
    assert.equal(await page.locator('#member-history .member-own').innerText().then(t=>t.includes('My message')),true);
    await page.locator('#member-input').fill('Second draft');
    assert(await page.locator('#member-send').isDisabled());
    for(const height of [780,420,340]){
      await page.setViewportSize({width:390,height});
      await page.locator('#member-input').focus();
      assert(await page.evaluate(()=>{
        const nav=document.querySelector('#bottom-nav').getBoundingClientRect();
        return ['member-input','member-template','member-budget','member-send','member-result'].every(id=>{
          const r=document.getElementById(id).getBoundingClientRect();
          return r.top>=0&&r.bottom<=nav.top;
        });
      }));
    }
    await page.setViewportSize({width:390,height:780});
    await page.evaluate(()=>{
      const history=document.querySelector("#member-history");
      history.scrollTop=history.scrollHeight;
      const messages=fixture.memberRadios[0].chats[2];
      for(let i=0;i<40;i++)messages.push({id:100+i,sender:'Pat',text:'Update '+i,receivedAt:200+i,time:'12:00'});
      renderMemberChat();
    });
    const history=page.locator('#member-history');
    assert(await history.evaluate(e=>e.scrollHeight>e.clientHeight&&e.scrollHeight-e.clientHeight-e.scrollTop<=1));
    assert.match(await history.locator('article').last().innerText(),/Update 39/);
    assert(await history.evaluate(e=>e.getBoundingClientRect().bottom<=document.querySelector('#member-input').getBoundingClientRect().top));
    await history.evaluate(e=>{e.scrollTop=140;});
    await page.evaluate(()=>{
      fixture.memberRadios[0].chats[2].push({id:300,sender:'Pat',text:'Newest',receivedAt:900,time:'12:04'});
      renderMemberChat();
    });
    assert.equal(await history.evaluate(e=>Math.round(e.scrollTop)),140);
    await page.getByRole('button',{name:'Ελληνικά'}).click();
    assert.equal(await page.locator('#member-send').innerText(),'Αποστολή');
    const before=await page.evaluate(()=>JSON.stringify(fixture.memberConfig.channels));
    await page.evaluate(()=>{switchTab('settings');showSettingsPage('sending');openSettingsWindow('split');});
    assert.equal(await page.locator('#tab-settings button:visible').count(),2);
    const shortcut=page.locator('#config-connect-all');
    assert.equal(await page.locator('#settings-home button:visible').first().getAttribute('id'),'config-connect-all');
    assert.equal(await shortcut.innerText(),'Σύνδεση όλων των διαθέσιμων συσκευών');
    await shortcut.click();
    assert(await page.locator('#settings-my-nodes-page').isVisible());
    assert.equal(await page.evaluate(()=>window.connectCalls||0),0);
    assert.equal(await page.locator('#disconnect-all').isVisible(),false);
    await page.locator('#connect-all-available').click();
    assert.equal(await page.evaluate(()=>connectCalls),1);
    assert(await page.locator('#connect-all-available').isDisabled());
    await page.locator('#nodes-back').click();
    assert(await shortcut.isEnabled());
    assert.equal(await page.locator('dialog[open]').count(),0);
    await page.getByRole('button',{name:'Απενεργοποίηση λειτουργίας για μη εκπαιδευμένα μέλη'}).click();
    assert.equal(await page.locator('body').evaluate(e=>e.classList.contains('member-mode')),false);
    assert.equal(await page.evaluate(()=>JSON.stringify(fixture.memberConfig.channels)),before);
    assert(await page.getByRole('button',{name:/My Nodes/}).isVisible());
    await shortcut.click();
    assert(await page.locator('#disconnect-all').isVisible());
    assert.equal(await page.evaluate(()=>connectCalls),1);
    await page.locator('#nodes-back').click();
    assert.deepEqual(await page.locator('#settings-home h2:visible').allTextContents(),['Radios','Messaging','Map & alerts','Data & app']);
    await page.locator('#settings-home').getByRole('button',{name:/Geofence alerts/}).click();
    assert(await page.locator('#map-alerts-dialog').isVisible());
    assert.deepEqual(errors,[]);
    console.log('Passed member-mode single destinations, channel selection, replies, templates, split limit, Greek and exit.');
  }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
