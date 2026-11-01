/**
 * SnapBoo: שרת חדרים למשחק משותף.
 *
 * השרת הוא ממסר בלבד: הוא לא מכיר את המשחק. שחקן אחד (המארח) מריץ את המנוע ושולח מצב, האחרים שולחים קלט.
 * כל הודעה (טקסט או בינארי) מועברת לשאר החדר כמו שהיא. כך השרת זול: בלי מסד נתונים, ובלי לפענח הודעות.
 *
 * נקודות קצה:
 *   GET /create        -> {"code":"K7M2P"}  (קוד חדר חדש)
 *   GET /room/<CODE>   -> WebSocket (Upgrade). הראשון בחדר הוא host, האחרים guest.
 *
 * הודעות שהשרת עצמו שולח (טקסט JSON):
 *   {"t":"hello","id":<מספר>,"role":"host"|"guest","peers":<כמה בחדר>}   אליך, בחיבור
 *   {"t":"join","id":<מספר>,"role":"guest","peers":<n>}                 לאחרים כשמישהו הצטרף
 *   {"t":"leave","id":<מספר>,"peers":<n>}                               כשמישהו עזב
 *   {"t":"closed","reason":"host-left"}                                 כשהמארח עזב (החדר נסגר)
 * שליחת הטקסט "ping" מחזירה "pong".
 */

export interface Env {
  ROOMS: DurableObjectNamespace;
  QUOTA: DurableObjectNamespace;
  /** מפתח Gemini (סוד בשרת בלבד, לא באפליקציה) */
  GEMINI_API_KEY?: string;
  /** תקרת עיבודים יומית לכל המשתמשים יחד (ברירת מחדל 300) */
  DAILY_BUDGET?: string;
  /** כמה עיבודים חינם ביום לכל מכשיר (ברירת מחדל 1) */
  DAILY_PER_DEVICE?: string;
}

const GEMINI_MODEL = "gemini-2.5-flash-image";
const MAX_UPLOAD_BYTES = 1_200_000;
const PROMPTS: Record<string, string> = {
  tundra:
    "Restyle this photo as a frozen tundra winter scene: fresh snow settled on every upward-facing surface, " +
    "frost and ice on edges and vertical surfaces, cold blue light and soft haze in the distance. " +
    "Keep the exact same camera view, composition, and the exact position, size and shape of every object, " +
    "the floor and the walls. Do not add, remove or move any object. No people, no text, no borders.",
};

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

async function takeQuota(env: Env, name: string, limit: number): Promise<boolean> {
  const stub = env.QUOTA.get(env.QUOTA.idFromName(name));
  const res = await stub.fetch(`https://quota/take?limit=${limit}&day=${today()}`, { method: "POST" });
  return res.status === 200;
}

async function refundQuota(env: Env, name: string): Promise<void> {
  const stub = env.QUOTA.get(env.QUOTA.idFromName(name));
  await stub.fetch(`https://quota/refund?day=${today()}`, { method: "POST" });
}

function toBase64(bytes: Uint8Array): string {
  let bin = "";
  for (let i = 0; i < bytes.length; i += 0x8000) {
    bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  }
  return btoa(bin);
}

/** POST /restyle?style=tundra, גוף: JPEG, כותרת x-device. עיבוד אחד ביום לכל מכשיר + תקרה יומית כללית. */
async function restyle(request: Request, env: Env): Promise<Response> {
  if (request.method !== "POST") return new Response("POST only", { status: 405 });
  if (!env.GEMINI_API_KEY) return Response.json({ error: "not-configured" }, { status: 503 });
  const device = request.headers.get("x-device") ?? "";
  if (!/^[A-Za-z0-9_-]{8,64}$/.test(device)) return Response.json({ error: "bad-device" }, { status: 400 });
  const style = new URL(request.url).searchParams.get("style") ?? "tundra";
  const prompt = PROMPTS[style];
  if (!prompt) return Response.json({ error: "bad-style" }, { status: 400 });
  const body = new Uint8Array(await request.arrayBuffer());
  if (body.length < 1000 || body.length > MAX_UPLOAD_BYTES) return Response.json({ error: "bad-size" }, { status: 413 });

  const perDevice = Number(env.DAILY_PER_DEVICE ?? "1");
  const budget = Number(env.DAILY_BUDGET ?? "300");
  if (!(await takeQuota(env, `dev:${device}`, perDevice))) {
    return Response.json({ error: "daily-limit" }, { status: 429 });
  }
  if (!(await takeQuota(env, "global", budget))) {
    await refundQuota(env, `dev:${device}`);
    return Response.json({ error: "budget-reached" }, { status: 429 });
  }
  const fail = async (status: number, error: string) => {
    await refundQuota(env, `dev:${device}`);
    await refundQuota(env, "global");
    return Response.json({ error }, { status });
  };

  try {
    const gemini = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`, {
      method: "POST",
      headers: { "content-type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
      body: JSON.stringify({
        contents: [{ parts: [{ text: prompt }, { inline_data: { mime_type: "image/jpeg", data: toBase64(body) } }] }],
        generationConfig: { responseModalities: ["TEXT", "IMAGE"] },
      }),
    });
    if (!gemini.ok) return fail(502, `upstream-${gemini.status}`);
    const json = (await gemini.json()) as any;
    const parts: any[] = json?.candidates?.[0]?.content?.parts ?? [];
    const image = parts.find((p) => p.inlineData?.data || p.inline_data?.data);
    const data: string | undefined = image?.inlineData?.data ?? image?.inline_data?.data;
    if (!data) return fail(502, "no-image");
    const bin = atob(data);
    const out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    const mime: string = image?.inlineData?.mimeType ?? image?.inline_data?.mime_type ?? "image/png";
    return new Response(out, { headers: { "content-type": mime, "cache-control": "no-store" } });
  } catch {
    return fail(502, "upstream-error");
  }
}

/** מונה יומי פשוט: מפתח אחד לכל יום. אובייקט לכל מכשיר ואחד גלובלי. */
export class Quota implements DurableObject {
  constructor(private ctx: DurableObjectState) {}

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const day = url.searchParams.get("day") ?? "";
    const key = `day:${day}`;
    const used = (await this.ctx.storage.get<number>(key)) ?? 0;
    if (url.pathname === "/take") {
      const limit = Number(url.searchParams.get("limit") ?? "0");
      if (used >= limit) return new Response("full", { status: 429 });
      await this.ctx.storage.put(key, used + 1);
      return new Response("ok");
    }
    if (url.pathname === "/refund") {
      await this.ctx.storage.put(key, Math.max(0, used - 1));
      return new Response("ok");
    }
    return new Response("?", { status: 404 });
  }
}

const CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // בלי O,0,I,1,L שמתבלבלים
const CODE_LENGTH = 5;
const MAX_PEERS = 4;
const MAX_MESSAGE_BYTES = 900_000;

function newCode(): string {
  const bytes = new Uint8Array(CODE_LENGTH);
  crypto.getRandomValues(bytes);
  let out = "";
  for (const b of bytes) out += CODE_ALPHABET[b % CODE_ALPHABET.length];
  return out;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (url.pathname === "/create") {
      return Response.json({ code: newCode() }, { headers: { "cache-control": "no-store" } });
    }

    if (url.pathname === "/restyle") return restyle(request, env);

    const match = url.pathname.match(/^\/room\/([A-Za-z0-9]{4,8})$/);
    if (match) {
      if (request.headers.get("Upgrade") !== "websocket") {
        return new Response("Expected WebSocket", { status: 426 });
      }
      const code = match[1].toUpperCase();
      const stub = env.ROOMS.get(env.ROOMS.idFromName(code));
      return stub.fetch(request);
    }

    return new Response("SnapBoo rooms server", { status: 200 });
  },
};

interface Attachment {
  id: number;
  role: "host" | "guest";
}

export class Room implements DurableObject {
  constructor(private ctx: DurableObjectState, private env: Env) {}

  private peers(): WebSocket[] {
    return this.ctx.getWebSockets();
  }

  async fetch(request: Request): Promise<Response> {
    const existing = this.peers();
    if (existing.length >= MAX_PEERS) {
      return new Response("Room full", { status: 409 });
    }

    const pair = new WebSocketPair();
    const client = pair[0];
    const server = pair[1];

    // המארח הוא הראשון בחדר. מספרים רצים לפי מי שכבר מחובר (נשמרים ב-attachment)
    const taken = new Set(existing.map((ws) => (ws.deserializeAttachment() as Attachment | null)?.id ?? 0));
    let id = 1;
    while (taken.has(id)) id++;
    const hasHost = existing.some((ws) => (ws.deserializeAttachment() as Attachment | null)?.role === "host");
    const role: Attachment["role"] = hasHost ? "guest" : "host";

    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({ id, role } satisfies Attachment);

    const count = existing.length + 1;
    server.send(JSON.stringify({ t: "hello", id, role, peers: count }));
    for (const ws of existing) {
      ws.send(JSON.stringify({ t: "join", id, role, peers: count }));
    }

    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(ws: WebSocket, message: string | ArrayBuffer): Promise<void> {
    if (message === "ping") {
      ws.send("pong");
      return;
    }
    const size = typeof message === "string" ? message.length : message.byteLength;
    if (size > MAX_MESSAGE_BYTES) {
      ws.send(JSON.stringify({ t: "error", reason: "too-large" }));
      return;
    }
    for (const other of this.peers()) {
      if (other !== ws) other.send(message);
    }
  }

  async webSocketClose(ws: WebSocket, code: number, reason: string, wasClean: boolean): Promise<void> {
    this.handleLeave(ws);
    try { ws.close(code, reason); } catch { /* כבר סגור */ }
  }

  async webSocketError(ws: WebSocket): Promise<void> {
    this.handleLeave(ws);
  }

  private handleLeave(ws: WebSocket): void {
    const info = ws.deserializeAttachment() as Attachment | null;
    const rest = this.peers().filter((p) => p !== ws);
    if (info?.role === "host") {
      for (const p of rest) {
        try {
          p.send(JSON.stringify({ t: "closed", reason: "host-left" }));
          p.close(1000, "host-left");
        } catch { /* ignore */ }
      }
      return;
    }
    for (const p of rest) {
      p.send(JSON.stringify({ t: "leave", id: info?.id ?? 0, peers: rest.length }));
    }
  }
}
