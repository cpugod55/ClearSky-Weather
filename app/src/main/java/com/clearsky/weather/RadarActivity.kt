package com.clearsky.weather

import android.app.Activity
import android.os.Bundle
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

class RadarActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lat = intent.getDoubleExtra("lat", 44.0)
        val lon = intent.getDoubleExtra("lon", -89.0)
        val prefs = getSharedPreferences("prefs", 0)
        val source = prefs.getString("radar_source", "librewxr") ?: "librewxr"

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = WebViewClient()
        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                android.util.Log.d("ClearSkyRadar", "${m.message()} (${m.lineNumber()})")
                return true
            }
        }
        setContentView(web)

        val html = """<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'>
<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'>
<style>
html,body,#map{height:100%;margin:0;background:#111}#bar{position:absolute;z-index:9999;left:8px;right:8px;top:8px;background:#eaf3fff2;color:#102030;border-radius:14px;padding:7px;display:flex;align-items:center;gap:6px;font-family:sans-serif;box-shadow:0 2px 8px #0005}#meta{flex:1;min-width:0}#time{font-weight:700;font-size:14px;white-space:nowrap}#span{font-size:11px;opacity:.72;white-space:nowrap}button,select{height:36px;border:0;border-radius:9px;background:#fff;color:#102030}button{font-size:19px;min-width:38px}select{font-size:12px;max-width:145px;padding:0 4px}
</style></head><body><div id='map'></div><div id='bar'><select id='source'><option value='librewxr'>LibreWXR</option><option value='iemhist'>IEM Historical 5h</option><option value='iemnow'>IEM Current</option><option value='hrrr'>IEM Future HRRR 6h</option><option value='mrms'>MRMS Current + 2h</option><option value='hrrrp'>HRRR Future Precip Type 6h</option></select><div id='meta'><div id='time'>Loading radar…</div><div id='span'></div></div><button id='prev'>‹</button><button id='play'>Ⅱ</button><button id='next'>›</button></div>
<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script><script>
const map=L.map('map',{zoomControl:true}).setView([$lat,$lon],7);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:18,attribution:'© OpenStreetMap contributors'}).addTo(map);
L.circleMarker([$lat,$lon],{radius:6,color:'#1565c0',fillColor:'#fff',fillOpacity:1,weight:3}).addTo(map);
let frames=[],api=null,pos=0,current=null,pending=null,timer=null,serial=0,mode='$source';
const sel=document.getElementById('source'); if(!['librewxr','iemhist','iemnow','hrrr','mrms','hrrrp'].includes(mode)) mode='librewxr'; sel.value=mode;
function fmt(t){return new Date(t*1000).toLocaleTimeString([],{hour:'numeric',minute:'2-digit'})}
function updateMeta(f){
 let prefix=(mode==='hrrr'||mode==='hrrrp')?'FORECAST • ':mode==='iemnow'?'CURRENT • ':mode==='mrms'?'MRMS • ':'';
 document.getElementById('time').textContent=prefix+fmt(f.valid||f.time);
 if(mode==='hrrr') document.getElementById('span').textContent=frames.length+' frames • 15 min • +6h HRRR simulated radar';
 else if(mode==='hrrrp') document.getElementById('span').textContent=frames.length+' frames • 15 min • +6h HRRR rain/snow/ice';
 else if(mode==='mrms') document.getElementById('span').textContent=frames.length+' frames • 2 min • MRMS SeamlessHSR';
 else if(mode==='iemnow') document.getElementById('span').textContent='IEM live NEXRAD mosaic';
 else if(frames.length>1){const mins=Math.round((frames[frames.length-1].time-frames[0].time)/60);document.getElementById('span').textContent=frames.length+' frames • '+Math.floor(mins/60)+'h '+(mins%60)+'m';}
 else document.getElementById('span').textContent=frames.length+' frame';
}
function clearLayers(){serial++;if(current){map.removeLayer(current);current=null}if(pending){map.removeLayer(pending);pending=null}}
function utcStamp(t){const d=new Date(t*1000);return d.getUTCFullYear()+String(d.getUTCMonth()+1).padStart(2,'0')+String(d.getUTCDate()).padStart(2,'0')+String(d.getUTCHours()).padStart(2,'0')+String(d.getUTCMinutes()).padStart(2,'0')}
function makeLayer(f,opacity){
 if(mode==='librewxr') return L.tileLayer(api.host+f.path+'/256/{z}/{x}/{y}/2/1_1.png',{opacity,maxNativeZoom:7,maxZoom:18});
 if(mode==='iemnow') return L.tileLayer('https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/nexrad-n0q/{z}/{x}/{y}.png',{opacity,maxNativeZoom:7,maxZoom:18,updateWhenIdle:true,keepBuffer:2});
 if(mode==='hrrr'||mode==='hrrrp'){
   const mins=String(f.minute).padStart(4,'0');
   const prod=mode==='hrrrp'?'REFP':'REFD';
   return L.tileLayer('https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/hrrr::'+prod+'-F'+mins+'-0/{z}/{x}/{y}.png',{opacity,maxNativeZoom:7,maxZoom:18,errorTileUrl:'',updateWhenIdle:true,keepBuffer:2});
 }
 if(mode==='mrms'){
   const layer='mrms::lcref-'+utcStamp(f.time);
   return L.tileLayer('https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/'+layer+'/{z}/{x}/{y}.png',{opacity,maxNativeZoom:7,maxZoom:18,errorTileUrl:'',updateWhenIdle:true,keepBuffer:2});
 }
 const layer='ridge::USCOMP-N0Q-'+utcStamp(f.time);
 return L.tileLayer('https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/'+layer+'/{z}/{x}/{y}.png',{opacity,maxNativeZoom:7,maxZoom:18,errorTileUrl:'',updateWhenIdle:true,keepBuffer:2});
}
function show(n){
 if(!frames.length)return;pos=(n+frames.length)%frames.length;const f=frames[pos],my=++serial;
 if(!current){current=makeLayer(f,.72).addTo(map);current.once('load',()=>updateMeta(f));return}
 if(pending){map.removeLayer(pending);pending=null}
 pending=makeLayer(f,0);let loaded=0,done=false;
 const finish=()=>{if(done||my!==serial||!pending)return;done=true;const old=current;current=pending;pending=null;current.setOpacity(.72);updateMeta(f);if(old&&old!==current)map.removeLayer(old);};
 pending.on('tileload',()=>{loaded++;if(loaded>=1)finish()});
 pending.on('tileerror',()=>console.log('tileerror source='+mode+' frame='+(f.valid||f.time)));
 pending.once('load',()=>{if(loaded>0)finish()});pending.addTo(map);
 setTimeout(()=>{if(!done&&my===serial){console.log('skip unavailable frame source='+mode+' frame='+(f.valid||f.time));if(pending){map.removeLayer(pending);pending=null}if(timer)show(pos+1)}},2500);
}
function stop(){if(timer){clearInterval(timer);timer=null;document.getElementById('play').textContent='▶'}}
function play(){if(timer||frames.length<2)return;document.getElementById('play').textContent='Ⅱ';const speed=(mode==='hrrr'||mode==='hrrrp')?420:mode==='mrms'?180:mode==='iemhist'?300:360;timer=setInterval(()=>{if(!pending)show(pos+1)},speed)}
document.getElementById('prev').onclick=()=>{stop();show(pos-1)};document.getElementById('next').onclick=()=>{stop();show(pos+1)};document.getElementById('play').onclick=()=>timer?stop():play();
function iemHistory(){const now=Math.floor(Date.now()/1000);const newest=Math.floor((now-15*60)/300)*300;const out=[];for(let t=newest-5*3600;t<=newest;t+=300)out.push({time:t});return out}
function mrmsHistory(){const now=Math.floor(Date.now()/1000);const newest=Math.floor((now-6*60)/120)*120;const out=[];for(let t=newest-2*3600;t<=newest;t+=120)out.push({time:t});return out}
function hrrrFuture(){const now=Math.floor(Date.now()/1000);const base=Math.floor(now/3600)*3600;const out=[];for(let m=0;m<=360;m+=15)out.push({time:base,valid:base+m*60,minute:m});return out}
async function loadSource(next){
 stop();clearLayers();frames=[];api=null;pos=0;mode=next;localStorage.setItem('clearskyRadarSource',mode);document.getElementById('time').textContent='Loading…';document.getElementById('span').textContent='';
 try{
  if(mode==='librewxr'){const r=await fetch('https://api.librewxr.net/public/weather-maps.json');if(!r.ok)throw Error('HTTP '+r.status);api=await r.json();frames=[...(api.radar?.past||[]),...(api.radar?.nowcast||[])];}
  else if(mode==='iemhist') frames=iemHistory();
  else if(mode==='iemnow') frames=[{time:Math.floor(Date.now()/1000)}];
  else if(mode==='mrms') frames=mrmsHistory();
  else frames=hrrrFuture();
  if(!frames.length)throw Error('No radar frames');
  console.log('source='+mode+' frames='+frames.length);
  show(0);if(frames.length>1)setTimeout(play,500);
 }catch(e){console.error('radar '+mode+' '+e);document.getElementById('time').textContent='Radar unavailable';document.getElementById('span').textContent=String(e.message||e)}
}
sel.onchange=()=>loadSource(sel.value);
loadSource(mode);
</script></body></html>"""
        web.loadDataWithBaseURL("https://clearsky.local/", html, "text/html", "UTF-8", null)
    }

    override fun onPause() {
        if (::web.isInitialized) web.evaluateJavascript("if(typeof stop==='function')stop();", null)
        super.onPause()
    }

    override fun onDestroy() {
        if (::web.isInitialized) { web.loadUrl("about:blank"); web.stopLoading(); web.destroy() }
        super.onDestroy()
    }
}
