/** עמוד בדיקה פשוט לעולמות מדווחים: מציג אותם עם תמונה, ומאפשר להחזיר, למחוק או לחסום את היוצר. נשען על מפתח ADMIN_KEY של השרת. */
export const ADMIN_HTML = `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>SnapBoo admin</title>
<style>
:root{color-scheme:light dark}body{font:15px system-ui,sans-serif;max-width:900px;margin:24px auto;padding:0 16px}
.card{border:1px solid #8885;border-radius:10px;padding:12px;margin:12px 0;display:flex;gap:12px;flex-wrap:wrap}
.card img{width:240px;max-width:100%;border-radius:8px;background:#8883}
button{padding:8px 12px;border-radius:8px;border:1px solid #8886;cursor:pointer;margin:2px}
.mut{opacity:.7;font-size:13px}.bad{color:#c33}
</style></head><body>
<h1>Reported worlds</h1>
<p><input id="key" type="password" placeholder="Admin key" size="30"> <button id="go">Load</button></p>
<div id="msg" class="mut"></div><div id="list"></div>
<script>
const $=id=>document.getElementById(id);
const hdr=()=>({"x-admin-key":$("key").value});
async function api(path,method){const r=await fetch(path,{method:method||"GET",headers:hdr()});if(!r.ok)throw new Error(r.status);return r}
async function load(){
  $("msg").textContent="Loading...";$("list").innerHTML="";
  try{
    const items=await (await api("/admin/api/reported")).json();
    $("msg").textContent=items.length?items.length+" worlds":"Nothing to review";
    sessionStorage.setItem("k",$("key").value);
    for(const w of items){
      const card=document.createElement("div");card.className="card";
      const img=document.createElement("img");
      api("/admin/api/img/"+w.id).then(r=>r.blob()).then(b=>{img.src=URL.createObjectURL(b)}).catch(()=>{});
      const box=document.createElement("div");
      box.innerHTML="<b>"+w.style+"</b><br><span class='mut'>reports: "+w.n+" · "+(w.hidden?"<span class='bad'>hidden</span>":"visible")+"<br>creator: "+w.device.slice(0,8)+"…</span><br>";
      const act=(label,path)=>{const b=document.createElement("button");b.textContent=label;b.onclick=async()=>{if(!confirm(label+"?"))return;try{await api(path,"POST");load()}catch(e){alert("Failed "+e.message)}};box.appendChild(b)};
      act("RESTORE","/admin/api/restore?id="+w.id);
      act("DELETE","/admin/api/remove?id="+w.id);
      act("BAN CREATOR","/admin/api/ban?device="+encodeURIComponent(w.device));
      card.append(img,box);$("list").appendChild(card);
    }
  }catch(e){$("msg").textContent="Error: "+e.message}
}
$("go").onclick=load;$("key").value=sessionStorage.getItem("k")||"";
</script></body></html>`;
