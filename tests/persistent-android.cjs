// Existing debug emulator only. Connect its WebView socket to localhost:9228 first.
const {chromium}=require('playwright'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const adb='C:/Users/Antonis/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const shell=(...args)=>execFileSync(adb,['-s','emulator-5554','shell',...args],{encoding:'utf8'});
(async()=>{
 const browser=await chromium.connectOverCDP('http://127.0.0.1:9228',{noDefaults:true});
 try{
  const page=browser.contexts()[0].pages().find(p=>p.url().includes('appassets'));assert(page,'App WebView available');
  for(const mode of ['threebutton','gestural']){
   shell('cmd','overlay','enable-exclusive','--category','com.android.internal.systemui.navbar.'+mode);
   await page.waitForTimeout(700);
   await page.evaluate(()=>{
    const radio=r=>({radio:r,key:r+'1',ownId:'own-'+r,ownName:r,connected:true,scanProgress:0,loading:false,channels:[{index:0,name:'Local'}],nodes:[{id:r==='mc'?'abcdefabcdef':'!12345678',name:'Alice',role:'User'}],directs:{},chats:{}});
    onRadioState({mc:radio('mc'),mt:radio('mt'),devices:[],myNodes:[],sound:{mode:'shared',shared:{}},persistentJobs:[]});switchTab('chat');selectDmPeer('mt','!12345678');
   });
   for(const keyboard of [false,true]){
    if(keyboard){await page.locator('#dm-input').click();await page.locator('#dm-input').fill('Inset check');await page.waitForTimeout(800);}
    const result=await page.evaluate(()=>{
     const nav=el('bottom-nav').getBoundingClientRect();
     return {height:innerHeight,width:innerWidth,scroll:document.documentElement.scrollWidth,navBottom:nav.bottom,controls:[...document.querySelectorAll('#dm-center [data-persistent-kind],#dm-position,#dm-input,#dm-reply')].map(e=>{const b=e.getBoundingClientRect();return {id:e.id||e.textContent,top:b.top,bottom:b.bottom,visible:b.width>0&&b.height>0,fits:b.top>=0&&b.bottom<=nav.top};})};
    });
    assert(result.scroll<=result.width,'No horizontal overflow');assert(result.navBottom<=result.height+1,'Navigation inside WebView');
    for(const control of result.controls)assert(control.visible&&control.fits,mode+' keyboard='+keyboard+' '+JSON.stringify(control));
    shell('screencap','-p','/sdcard/persistent-'+mode+'-'+keyboard+'.png');
    execFileSync(adb,['-s','emulator-5554','pull','/sdcard/persistent-'+mode+'-'+keyboard+'.png','build/previews/persistent-android-'+mode+'-'+keyboard+'.png'],{stdio:'ignore'});
    console.log(mode+' keyboard='+keyboard+' '+JSON.stringify(result));
   }
   shell('input','keyevent','4');await page.waitForTimeout(300);
  }
 }finally{shell('cmd','overlay','enable-exclusive','--category','com.android.internal.systemui.navbar.threebutton');await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
