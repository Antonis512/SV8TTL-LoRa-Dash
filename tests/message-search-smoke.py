"""Browser check for saved-history window and shared message-search controls."""
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Thread
import os
from playwright.sync_api import sync_playwright

ASSETS = Path(__file__).resolve().parents[1] / "src" / "main" / "assets"

class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ASSETS), **kwargs)
    def log_message(self, *_args):
        pass
    def do_GET(self):
        if self.path.startswith("/assets/"):
            self.path = self.path[7:]
        super().do_GET()

server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
Thread(target=server.serve_forever, daemon=True).start()
errors = []
with sync_playwright() as playwright:
    browser = playwright.chromium.launch(
        executable_path=r"C:\Program Files\Google\Chrome\Application\chrome.exe",
        headless=True,
        args=["--enable-webgl", "--use-gl=angle", "--use-angle=swiftshader"],
    )
    page = browser.new_page(viewport={"width": 390, "height": 844})
    page.add_init_script("window.AndroidBridge = new Proxy({}, {get: () => () => {}})")
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(f"http://127.0.0.1:{server.server_port}/index.html")
    page.evaluate("""window.AndroidBridge={
      messageSearchSenders:(radio,id,peer,ch,scope)=>{window.testScope=scope},
      searchStoredMessages:(radio,id,peer,ch,query,selected,time,before,scope,request)=>{
        window.testSearch={radio,id,peer,ch,query,selected:JSON.parse(selected),scope,request}
      }};
      savedNode={radio:'mt',id:'!11111111',channels:[{index:0,name:'Primary'}],messages:[]};
      savedChannel=0;pendingSavedView={radio:'mt',id:'!11111111'};
      document.getElementById('settings-window-history').showModal();renderSavedHistory();""")
    dialog = page.locator("#settings-window-history")
    box = dialog.bounding_box()
    assert box and box["width"] >= 385 and box["height"] >= 839
    assert page.locator("#settings-window-history .window-x").is_visible()
    page.evaluate("""onMessageSearchSenders(window.testScope,[
      {key:'own',name:'Me',count:5},{key:'sender:Alice',name:'Alice',count:8}])""")
    request = page.evaluate("window.testSearch")
    assert request["selected"] == ["own", "sender:Alice"]
    page.evaluate("""onMessageSearchPage({scope:window.testScope,request:window.testSearch.request,
      messages:[{id:55,sender:'Alice',displaySender:'Alice',text:'Directions here',displayText:'Directions here',
        time:'12:00',receivedAt:123,own:false,peer:'',channel:0}],more:false,beforeTime:123,beforeId:55})""")
    assert "Directions here" in page.locator(".my-node-messages").inner_text()
    page.locator("#saved-message-search .message-search-row button").first.click()
    page.locator("#saved-message-search .message-search-senders input").first.uncheck()
    assert page.evaluate("window.testSearch.selected") == ["sender:Alice"]
    page.locator("#saved-message-search input[type=search]").fill("directions")
    page.wait_for_timeout(300)
    assert page.evaluate("window.testSearch.query") == "directions"
    page.locator("#settings-window-history .window-x").click()
    assert not dialog.evaluate("element => element.open")
    page.evaluate("""state={
      left:{radio:'mt',ownId:'!11111111',key:'device1',ownName:'Me',channels:[{index:0,name:'Primary'}],
        chats:{'0':[]},directs:{},nodes:[],connected:false,loading:false},
      right:{radio:'mc',ownId:'',key:'',channels:[],chats:{},directs:{},nodes:[],connected:false,loading:false}};
      network='left';channel.left=0;dmMode=false;document.getElementById('channels-screen').hidden=false;
      renderChannelsScreen();onMessageSearchSenders(window.testScope,[
        {key:'own',name:'Me',count:0},{key:'sender:Alice',name:'Alice',count:8}]);""")
    assert page.locator("#channel-message-search input[type=search]").is_visible()
    page.locator("#channel-message-search input[type=search]").fill("meeting")
    page.wait_for_timeout(300)
    assert page.evaluate("window.testSearch.query") == "meeting"
    assert page.evaluate("window.testSearch.selected") == ["own", "sender:Alice"]
    page.evaluate("""state.left.directs={'!alice':[]};dmSelection={radio:'left',peer:'!alice'};
      document.getElementById('dm-screen').hidden=false;renderDmScreen();
      onMessageSearchSenders(window.testScope,[{key:'own',name:'Me',count:0},{key:'sender:!alice',name:'!alice',count:3}]);""")
    assert page.locator("#dm-message-search input[type=search]").is_visible()
    page.locator("#dm-message-search input[type=search]").fill("directions")
    page.wait_for_timeout(300)
    assert page.evaluate("window.testSearch.query") == "directions"
    assert not errors, errors
    browser.close()
server.shutdown()
print("Message search browser smoke passed")
