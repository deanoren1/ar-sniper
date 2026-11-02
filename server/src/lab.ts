/** דף בדיקה בדפדפן: מעלים תמונה, בוחרים דגמים ורואים את כל התוצאות זו ליד זו (בלי להתקין APK). המפתחות נשארים בשרת. */
export const LAB_HTML = `<!doctype html>
<html lang="he" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>SnapBoo AI Lab</title>
<style>
:root{--bg:#10141c;--card:#1a2130;--fg:#e8edf7;--mut:#8b97ad;--acc:#4aa3ff;--bad:#ff6b6b}
*{box-sizing:border-box}body{margin:0;padding:16px;background:var(--bg);color:var(--fg);font:15px system-ui,sans-serif}
h1{font-size:20px;margin:0 0 12px}.card{background:var(--card);border-radius:12px;padding:12px;margin-bottom:12px}
label.m{display:inline-flex;align-items:center;gap:6px;margin:4px 10px 4px 0;direction:ltr}
button{background:var(--acc);color:#001;border:0;border-radius:8px;padding:10px 16px;font-weight:600;font-size:15px}
button:disabled{opacity:.5}.mut{color:var(--mut);font-size:13px}.bad{color:var(--bad)}
#grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:12px}
.res{background:var(--card);border-radius:12px;padding:8px;direction:ltr}.res img{width:100%;border-radius:8px;display:block}
.res b{display:block;margin:4px 0}#pre{max-width:100%;max-height:220px;border-radius:8px;margin-top:8px}
</style></head><body>
<h1>SnapBoo AI Lab</h1>
<div class="card">
  <input type="file" id="file" accept="image/*"><br>
  <img id="pre" hidden>
  <p class="mut">התמונה מוקטנת ל-1280 בצלע הארוכה לפני השליחה. כל תוצאה נספרת במכסה היומית.</p>
</div>
<div class="card" id="models"></div>
<div class="card">
  <label class="m"><input type="checkbox" id="sandbox"> Sandbox (נתוני דמה, בלי עלות)</label><br>
  <button id="run">הרץ</button> <span id="msg" class="mut"></span>
</div>
<div class="card"><button id="diag">בדיקת חיבור</button> <pre id="dout" class="mut" style="white-space:pre-wrap;direction:ltr"></pre></div>
<div id="grid"></div>
<script>
var MODELS=["qwen3-image-to-image","qwen-image-edit-plus","qwen-image-edit","seedream-5.0-flash-edit","kling-o3-image-edit","flux-kontext-pro-i2i","flux-2-klein-9b-edit","muse-image-edit"];
var box=document.getElementById("models");
MODELS.forEach(function(m,i){var l=document.createElement("label");l.className="m";
 l.innerHTML='<input type="checkbox" value="'+m+'"'+(i<4?" checked":"")+'> '+m;box.appendChild(l)});
var oa=document.createElement("label");oa.className="m";oa.innerHTML='<input type="checkbox" value="openai:gpt-image-1-mini"> openai gpt-image-1-mini';box.appendChild(oa);
var blob=null,file=document.getElementById("file");
function device(){try{var d=localStorage.getItem("dev");if(!d){d=(crypto.randomUUID?crypto.randomUUID():String(Math.random())).replace(/-/g,"").slice(0,32);localStorage.setItem("dev",d)}return d}catch(e){return "lab"+Math.random().toString(36).slice(2,14)}}
file.onchange=function(){var f=file.files[0];if(!f)return;var img=new Image();img.onload=function(){
 var s=Math.min(1,1280/Math.max(img.width,img.height)),c=document.createElement("canvas");c.width=Math.round(img.width*s);c.height=Math.round(img.height*s);
 c.getContext("2d").drawImage(img,0,0,c.width,c.height);c.toBlob(function(b){blob=b;var p=document.getElementById("pre");p.src=URL.createObjectURL(b);p.hidden=false;
 document.getElementById("msg").textContent=c.width+"x"+c.height+", "+Math.round(b.size/1024)+" KB"},"image/jpeg",0.9)};img.src=URL.createObjectURL(f)};
document.getElementById("diag").onclick=async function(){
 var o=document.getElementById("dout");o.textContent="";
 function log(s){o.textContent+=s+"\\n"}
 async function step(name,fn){var t0=Date.now();try{var r=await fn();log(name+": "+r+" ("+(Date.now()-t0)+"ms)")}catch(e){log(name+": FAIL "+e+" ("+(Date.now()-t0)+"ms)")}}
 await step("GET /create",async function(){var r=await fetch("/create");return r.status});
 await step("POST tiny /restyle",async function(){var r=await fetch("/restyle?style=tundra&provider=muapi&sandbox=1",{method:"POST",headers:{"x-device":device()},body:"x"});return r.status+" "+(await r.text()).slice(0,80)});
 if(blob){await step("POST photo "+Math.round(blob.size/1024)+"KB",async function(){var r=await fetch("/lab",{method:"POST",body:blob});return r.status})}
 else log("בחר תמונה כדי לבדוק גם העלאה");
};
document.getElementById("run").onclick=async function(){
 if(!blob){document.getElementById("msg").textContent="בחר תמונה קודם";return}
 var sel=[].slice.call(box.querySelectorAll("input:checked")).map(function(i){return i.value});
 if(!sel.length)return;var btn=this;btn.disabled=true;var sandbox=document.getElementById("sandbox").checked;var grid=document.getElementById("grid");
 document.getElementById("msg").textContent="רץ... עד כדקה";
 await Promise.all(sel.map(async function(m){
  var card=document.createElement("div");card.className="res";card.innerHTML="<b>"+m+"</b><span class=mut>מעבד...</span>";grid.prepend(card);
  var t=Date.now(),q="style=tundra",label=m;
  if(m.indexOf("openai:")===0){q+="&provider=openai&model="+m.slice(7)}else{q+="&provider=muapi&model="+m+(sandbox?"&sandbox=1":"")}
  try{var r=await fetch("/restyle?"+q,{method:"POST",headers:{"x-device":device()},body:blob});
   if(!r.ok){var tx=await r.text();card.innerHTML="<b>"+label+"</b><span class=bad>"+r.status+" "+tx.slice(0,200).replace(/</g,"&lt;")+"</span>";return}
   var b=await r.blob(),u=URL.createObjectURL(b),secs=((Date.now()-t)/1000).toFixed(0);
   if(b.type.indexOf("image")!==0){card.innerHTML="<b>"+label+"</b><span class=bad>not an image: "+b.type+"</span>";return}
   var im=new Image();im.onload=function(){card.innerHTML='<b>'+label+'</b><span class="mut">'+im.naturalWidth+"x"+im.naturalHeight+" · "+secs+"s</span>";card.appendChild(im)};im.src=u;
   var a=document.createElement("a");a.href=u;a.download=label.replace(/[^\\w.-]/g,"_")+".jpg";a.textContent=" שמור";a.style.color="#4aa3ff";card.appendChild(a);
  }catch(e){card.innerHTML="<b>"+label+"</b><span class=bad>"+e+" אחרי "+((Date.now()-t)/1000).toFixed(1)+" שניות</span>"}
 }));
 btn.disabled=false;document.getElementById("msg").textContent="סיום";
};
</script></body></html>`;
