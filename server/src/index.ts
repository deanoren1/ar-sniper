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
