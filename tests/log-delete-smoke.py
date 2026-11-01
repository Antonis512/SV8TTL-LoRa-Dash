"""Check the Log delete controls and their confirmation paths in the browser UI."""
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Thread
from playwright.sync_api import sync_playwright

ASSETS = Path(__file__).resolve().parents[1] / "src" / "main" / "assets"


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ASSETS), **kwargs)

    def log_message(self, *_args):
        pass


server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
Thread(target=server.serve_forever, daemon=True).start()
try:
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(
            executable_path=r"C:\Program Files\Google\Chrome\Application\chrome.exe",
            headless=True,
        )
        try:
            page = browser.new_page(viewport={"width": 390, "height": 844})
            errors = []
            page.on("pageerror", lambda error: errors.append(str(error)))
            page.add_init_script("""window.AndroidBridge=new Proxy({}, {get:(_,name)=>(...args)=>{
              if(name==='deleteLogItems'){
                const ids=JSON.parse(args[0]);window.deleted.push(ids);
                window.savedLog=window.savedLog.filter(item=>!ids.includes(item.id)).map(item=>({
                  ...item,categories:(item.categories||[]).filter(id=>!ids.includes(id))
                }));onLogItems(window.savedLog);
              }
            }});""")
            page.goto(f"http://127.0.0.1:{server.server_port}/index.html")
            page.evaluate("""window.deleted=[];window.savedLog=[
              {id:'cat1',kind:'category',title:'Travel',body:'',categories:[],created:1,edited:1},
              {id:'tcat1',kind:'templateCategory',title:'Forms',body:'',categories:[],created:1,edited:1},
              {id:'n1',kind:'note',title:'Directions',body:'Turn left',categories:['cat1'],created:2,edited:2},
              {id:'n2',kind:'note',title:'Packing',body:'Water',categories:['cat1'],created:3,edited:3},
              {id:'n3',kind:'note',title:'Weather',body:'Clear',categories:[],created:4,edited:4},
              {id:'t1',kind:'template',title:'Checklist',body:'Item:',categories:['tcat1'],created:5,edited:5}
            ];switchTab('log');onLogItems(savedLog);""")

            accept_dialog = [False]
            page.on("dialog", lambda dialog: dialog.accept() if accept_dialog[0] else dialog.dismiss())
            page.get_by_role("button", name="Categories", exact=True).click()
            page.locator("#log-list .log-small-action", has_text="Delete").click()
            assert page.evaluate("deleted.length") == 0

            accept_dialog[0] = True
            page.locator("#log-list .log-small-action", has_text="Delete").click()
            assert page.evaluate("deleted.at(-1)") == ["cat1"]
            assert page.evaluate("savedLog.filter(x=>x.kind==='note').every(x=>x.categories.length===0)")

            page.get_by_role("button", name="Template categories", exact=True).click()
            page.locator("#log-list .log-small-action", has_text="Delete").click()
            assert page.evaluate("deleted.at(-1)") == ["tcat1"]
            assert page.evaluate("savedLog.find(x=>x.id==='t1').categories") == []

            page.get_by_role("button", name="Notes", exact=True).click()
            page.locator("#log-list input[aria-label='Select Directions']").check()
            page.locator("#log-list input[aria-label='Select Packing']").check()
            page.get_by_role("button", name="Delete selected").click()
            assert set(page.evaluate("deleted.at(-1)")) == {"n1", "n2"}
            assert page.evaluate("savedLog.some(x=>x.id==='n3')")

            page.get_by_role("button", name="Weather").click()
            page.locator("#log-detail").get_by_role("button", name="Delete").click()
            assert page.evaluate("deleted.at(-1)") == ["n3"]
            assert not page.locator("#log-detail").evaluate("dialog=>dialog.open")

            page.evaluate("""savedLog.push(
              {id:'n4',kind:'note',title:'First',body:'',categories:[],created:6,edited:6},
              {id:'n5',kind:'note',title:'Second',body:'',categories:[],created:7,edited:7});
              onLogItems(savedLog);""")
            page.get_by_role("button", name="Delete all notes").click()
            assert set(page.evaluate("deleted.at(-1)")) == {"n4", "n5"}
            assert page.evaluate("savedLog.filter(x=>x.kind==='note').length") == 0

            page.get_by_role("button", name="Note templates", exact=True).click()
            page.locator("#log-list .log-small-action", has_text="Delete").click()
            assert page.evaluate("deleted.at(-1)") == ["t1"]
            assert errors == [], errors
            print("Log deletion smoke passed: cancel, categories, selected, single, all, and template.")
        finally:
            browser.close()
finally:
    server.shutdown()
