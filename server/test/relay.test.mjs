// בדיקת אינטגרציה לשרת החדרים. מריצים מול `wrangler dev` על פורט 8787.
const BASE = process.env.BASE || "127.0.0.1:8787";

function assert(cond, msg) {
  if (!cond) {
    console.error("FAIL:", msg);
    process.exit(1);
  }
  console.log("ok:", msg);
}

function connect(code) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`ws://${BASE}/room/${code}`);
    ws.binaryType = "arraybuffer";
    ws.inbox = [];
    ws.waiters = [];
    ws.addEventListener("message", (e) => {
      const waiter = ws.waiters.shift();
      if (waiter) waiter(e.data);
      else ws.inbox.push(e.data);
    });
    ws.addEventListener("open", () => resolve(ws));
    ws.addEventListener("error", () => reject(new Error("connect failed")));
  });
}

function next(ws, ms = 5000) {
  if (ws.inbox.length) return Promise.resolve(ws.inbox.shift());
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error("timeout")), ms);
    ws.waiters.push((d) => { clearTimeout(t); resolve(d); });
  });
}

const res = await fetch(`http://${BASE}/create`);
const { code } = await res.json();
assert(/^[A-Z2-9]{5}$/.test(code), `create returns a 5 char code (${code})`);

const host = await connect(code);
const hello1 = JSON.parse(await next(host));
assert(hello1.t === "hello" && hello1.role === "host" && hello1.peers === 1, "first player is host");

const guest = await connect(code);
const hello2 = JSON.parse(await next(guest));
assert(hello2.role === "guest" && hello2.peers === 2, "second player is guest");
const join = JSON.parse(await next(host));
assert(join.t === "join" && join.peers === 2, "host is told about the join");

host.send("state-1");
assert((await next(guest)) === "state-1", "text relays host -> guest");

const bin = new Uint8Array([1, 2, 3, 4]).buffer;
guest.send(bin);
const got = await next(host);
assert(got instanceof ArrayBuffer && got.byteLength === 4, "binary relays guest -> host");

guest.send("ping");
assert((await next(guest)) === "pong", "ping returns pong");

const g3 = await connect(code); await next(g3);
const g4 = await connect(code); await next(g4);
let rejected = false;
try { await connect(code); } catch { rejected = true; }
assert(rejected, "fifth player is rejected (room full)");

host.close();
let closed = JSON.parse(await next(guest));
while (closed.t === "join") closed = JSON.parse(await next(guest)); // קודם מגיעות הודעות ההצטרפות של השחקנים 3 ו-4
assert(closed.t === "closed" && closed.reason === "host-left", "guests are told when the host leaves");

console.log("ALL OK");
process.exit(0);
