"""Browser smoke check for the offline Map UI, using installed Chrome only."""
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Thread
import os
import time
from io import BytesIO
from PIL import Image
from playwright.sync_api import sync_playwright

ASSETS = Path(__file__).resolve().parents[1] / "src" / "main" / "assets"
SAMPLE = Path(os.environ.get("MAP_SMOKE_PACK", "")) if os.environ.get("MAP_SMOKE_PACK") else None
satellite_image = BytesIO()
Image.new("RGB", (256, 256), (68, 92, 78)).save(satellite_image, format="JPEG")
satellite_requests = []
online_image = BytesIO()
Image.new("RGB", (256, 256), (45, 64, 75)).save(online_image, format="PNG")

class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ASSETS), **kwargs)
    def log_message(self, *_args):
        pass
    def do_GET(self):
        if self.path.startswith("/satellite/"):
            satellite_requests.append(self.path)
            data = satellite_image.getvalue()
            self.send_response(200)
            self.send_header("Content-Type", "image/jpeg")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        if self.path.startswith("/assets/"):
            self.path = self.path[7:]
        if self.path.startswith("/packs/") and SAMPLE and SAMPLE.is_file():
            size = SAMPLE.stat().st_size
            requested = self.headers.get("Range", "")
            start, end = 0, size - 1
            if requested.startswith("bytes="):
                before, _, after = requested[6:].partition("-")
                start = int(before)
                if after:
                    end = min(end, int(after))
            self.send_response(206 if requested else 200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Content-Length", str(end - start + 1))
            if requested:
                self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
            self.end_headers()
            with SAMPLE.open("rb") as file:
                file.seek(start)
                try:
                    remaining = end - start + 1
                    while remaining:
                        chunk = file.read(min(65536, remaining))
                        if not chunk:
                            break
                        self.wfile.write(chunk)
                        remaining -= len(chunk)
                except (BrokenPipeError, ConnectionAbortedError):
                    pass  # MapLibre cancels obsolete tile requests while changing style.
            return
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
    page.route("https://tile.openstreetmap.org/**", lambda route: route.fulfill(status=200, content_type="image/png", body=online_image.getvalue()))
    page.add_init_script("window.AndroidBridge = new Proxy({}, {get: () => () => {}})")
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(f"http://127.0.0.1:{server.server_port}/index.html")
    page.evaluate("switchTab('map')")
    page.evaluate("""onMapData({
      nodes:[{radio:'mt',device:'!11111111',id:'!22222222',name:'Test Node',role:'CLIENT',lat:37.98,lon:23.72,positionAt:1000,lastHeard:1000}],
      positions:[],heard:[],items:[],packs:[]})""")
    page.wait_for_timeout(1500)
    assert page.locator("#map-counts").inner_text().startswith("1 positioned")
    assert page.locator("#map-source-switch").inner_text() == "Online ⇄"
    assert page.evaluate("!!mapView.getSource('openstreetmap')")
    page.locator("#map-source-switch").click()
    assert page.locator("#map-empty-pack").is_visible()
    assert page.locator(".map-tool-nav").evaluate("nav=>nav.compareDocumentPosition(document.getElementById('offline-map')) & Node.DOCUMENT_POSITION_PRECEDING")
    assert page.locator("#map-empty-pack").evaluate("box=>getComputedStyle(box).position") == "absolute"
    assert page.locator("#map-empty-pack button").evaluate("button=>getComputedStyle(button).borderRadius") == "6px"
    zoom_color = page.locator(".maplibregl-ctrl-zoom-in").evaluate("button=>getComputedStyle(button).backgroundColor")
    tool_color = page.locator(".map-tool-nav button").nth(1).evaluate("button=>getComputedStyle(button).backgroundColor")
    assert zoom_color == tool_color
    assert page.locator(".map-node-dot.mt").count() == 1
    page.screenshot(path="build/map-empty-preview.png")
    assert page.locator(".map-tool-nav button").count() == 6
    page.locator("#map-empty-pack button").click()
    assert page.locator("#map-offline-dialog").evaluate("dialog=>dialog.open")
    page.locator("#map-offline-dialog .map-offline-choices button").nth(1).click()
    assert page.locator("#map-import-dialog").evaluate("dialog=>dialog.open")
    assert page.locator("#map-import-dialog button").first.evaluate("button=>getComputedStyle(button).borderRadius") == "6px"
    page.locator("#map-import-dialog .log-dialog-head button").first.click()
    if SAMPLE:
        page.evaluate("""onMapData({nodes:[],positions:[],heard:[],items:[],packs:[{id:'11111111-1111-1111-1111-111111111111',kind:'street',schema:'protomaps',title:'Test area',size:1,bounds:[11.1,43.6,11.5,43.9],minZoom:0,maxZoom:15}]})""")
        for _ in range(30):
            if page.evaluate("mapView.isStyleLoaded()"):
                break
            time.sleep(0.3)
        assert page.evaluate("mapView.isStyleLoaded()"), errors
        assert page.evaluate("!!mapView.getSource('protomaps')")
    page.evaluate("""onMapData({nodes:[],positions:[],heard:[],items:[],packs:[{id:'22222222-2222-2222-2222-222222222222',kind:'satellite',schema:'copernicus',title:'Test satellite',size:1,bounds:[11.1,43.6,11.5,43.9],minZoom:8,maxZoom:14}]})""")
    page.evaluate("mapLayerKind='satellite'; mapPackId='22222222-2222-2222-2222-222222222222'; mapStyleKey=''; setMapStyle()")
    for _ in range(30):
        if satellite_requests and page.evaluate("mapView.isStyleLoaded()"):
            break
        time.sleep(0.3)
    assert satellite_requests
    assert page.evaluate("!!mapView.getSource('imagery')")
    page.evaluate("onSatelliteEstimate({tiles:120,estimatedMB:18,maxZoom:14})")
    assert "120 tiles" in page.locator("#satellite-estimate").inner_text()
    page.evaluate("""window.AndroidBridge={estimateSatellite:bounds=>{window.testSatelliteBounds=JSON.parse(bounds);onSatelliteEstimate({tiles:30,estimatedMB:5,maxZoom:14});}};chooseSatelliteArea()""")
    assert page.evaluate("window.testSatelliteBounds"), (errors, page.locator("#map-status").inner_text())
    assert len(page.evaluate("window.testSatelliteBounds")) == 4
    assert "30 tiles" in page.locator("#satellite-estimate").inner_text()
    page.evaluate("document.getElementById('map-import-dialog').showModal();onSatelliteAccount(true,true,'…12345678')")
    assert page.locator("#satellite-logoff").is_visible()
    assert page.locator("#satellite-forget").is_visible()
    page.evaluate("""AndroidBridge.logOffSatelliteAccount=()=>{window.testLoggedOff=true};
        document.getElementById('satellite-client-secret').value='temporary secret';logOffSatelliteAccount()""")
    assert page.evaluate("window.testLoggedOff")
    assert page.locator("#satellite-client-secret").input_value() == ""
    page.evaluate("onSatelliteAccount(true,false,'…12345678')")
    assert page.locator("#satellite-login").is_visible()
    page.evaluate("""window.confirm=()=>true;AndroidBridge.forgetSatelliteAccount=()=>{window.testForgot=true};
        document.getElementById('satellite-client-secret').value='temporary secret';forgetSatelliteAccount()""")
    assert page.evaluate("window.testForgot")
    assert page.locator("#satellite-client-secret").input_value() == ""
    assert not errors, errors
    browser.close()
server.shutdown()
print("Map browser smoke passed")
