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

import { LAB_HTML } from "./lab";

export interface Env {
  ROOMS: DurableObjectNamespace;
  QUOTA: DurableObjectNamespace;
  /** עולמות קהילה: תמונות ב-R2 ואינדקס (SQLite) ב-Durable Object אחד */
  WORLDS: R2Bucket;
  WORLDS_INDEX: DurableObjectNamespace;
  /** מפתח Gemini (סוד בשרת בלבד, לא באפליקציה) */
  GEMINI_API_KEY?: string;
  /** מפתח OpenAI (סוד בשרת בלבד). אם קיים הוא ברירת המחדל לעיבוד */
  OPENAI_API_KEY?: string;
  OPENAI_IMAGE_MODEL?: string;
  OPENAI_IMAGE_QUALITY?: string;
  /** מפתח Muapi האמיתי (סוד בשרת בלבד) ומפתח ה-Sandbox שלהם (נתוני דמה, בלי עלות) */
  MUAPI_KEY?: string;
  MUAPI_KEY_TEST?: string;
  /** תקרת עיבודים יומית לכל המשתמשים יחד (ברירת מחדל 100) */
  DAILY_BUDGET?: string;
  /** כמה עיבודים חינם ביום לכל מכשיר (ברירת מחדל 20 בזמן בדיקות) */
  DAILY_PER_DEVICE?: string;
  /** מפתח הבעלים (סוד בשרת בלבד): מי ששולח אותו בכותרת x-owner עוקף את המכסות */
  OWNER_KEY?: string;
}

const GEMINI_MODEL = "gemini-2.5-flash-image";
const MAX_UPLOAD_BYTES = 1_200_000;
const PROMPTS: Record<string, string> = {
  tundra:
    "Transform this exact photo into a dramatic frozen tundra winter scene, photorealistic and highly detailed, like a still from a AAA game. " +
    "Heavy fresh snow settled on every upward-facing surface (floor, ledges, tops of objects), thick frost and ice crystals on walls and objects, " +
    "icicles hanging from overhead edges, a glossy icy reflective floor with patches of snow. " +
    "Cold cinematic lighting with deep blue shadows and soft cyan highlights, but keep natural color variety: warm lights and colored objects stay recognizable under the frost. " +
    "Keep the full depth of the scene: the far end must stay clearly visible and detailed, with only a light haze and no white fog wall. " +
    "Sharp, high contrast, fine detail, not blurry, not monochrome. " +
    "Keep the exact same camera, framing and composition, and the exact position, size and shape of every wall, pillar, object and opening. " +
    "Do not add, remove or move anything. No people, no text.",
  tundra_mid:
    "Transform this exact photo into a dramatic frozen tundra winter scene, photorealistic and highly detailed, like a still from a AAA game. " +
    "Heavy fresh snow settled on every upward-facing surface (floor, ledges, tops of objects), thick frost and ice crystals on walls and objects, " +
    "icicles hanging from overhead edges, a glossy icy reflective floor with patches of snow. " +
    "Cold cinematic lighting with deep blue shadows and soft cyan highlights, but keep natural color variety: warm lights and colored objects stay recognizable under the frost. " +
    "Keep the full depth of the scene: the far end must stay clearly visible and detailed, with only a light haze and no white fog wall. " +
    "Sharp, high contrast, fine detail, not blurry, not monochrome. " +
    "Keep the exact same camera, framing and composition, and the exact position, size and shape of every wall, pillar, object and opening. " +
    "Do not add, remove or move anything. No people, no text.",
  jungle: "This is the SAME place, years later, reclaimed by a lush jungle, photorealistic, AAA game look. Every object stays exactly what it is and where it is, just aged and overgrown: thick moss, ferns and vines covering floors, ledges, walls and the tops of objects, roots creeping over surfaces, big tropical leaves growing from cracks, damp stone and puddles. Warm golden light shafts, rich greens, keep original colors recognizable. Keep the far end clearly visible. Sharp, high contrast. Keep the exact same camera, framing and composition, and the exact position, size and shape of every wall, pillar, car, object and opening. Do not add, remove, move or replace any object. No people, no animals, no text. Keep exact image dimensions; no crop, zoom or rotate.",
  jungle_mid: "This is the SAME place, years later, reclaimed by a lush jungle, painterly game concept art, stylized realism, soft visible brushwork, slightly saturated colors, gentle glow and rim light. Every object stays exactly what it is and where it is, just aged and overgrown: thick moss, ferns and vines covering floors, ledges, walls and the tops of objects, roots creeping over surfaces, big tropical leaves growing from cracks. Warm golden light, rich greens. Keep the exact same camera, framing and composition, and the exact position, size and shape of every wall, pillar, car, object and opening. Do not add, remove, move or replace any object. No people, no animals, no text. Keep exact image dimensions; no crop, zoom or rotate.",
  cyber_mid: "This is the SAME place, transformed into a neon cyberpunk night city interior, painterly game concept art, stylized realism, soft visible brushwork, slightly saturated colors, gentle glow and rim light. Every object stays exactly what it is and where it is, just transformed by the theme: glowing neon tubes and light strips in magenta, purple and cyan along edges, ceilings and furniture, glossy wet reflective floor with colorful reflections, holographic accents on surfaces, soft fog haze with light glow, deep blue-violet shadows, small screens and signage glow without readable text. Keep the far end clearly visible. Keep the exact same camera, framing and composition, and the exact position, size and shape of every wall, pillar, car, object and opening. Do not add, remove, move or replace any object. No people, no animals, no text. Keep exact image dimensions; no crop, zoom or rotate.",
  military_mid: "This is the SAME place, transformed into an abandoned military base, painterly game concept art, stylized realism, soft visible brushwork, slightly saturated colors, gentle glow and rim light. Every object stays exactly what it is and where it is, just transformed by the theme: olive drab and khaki paint, scuffed steel plates, sandbags and camouflage netting draped over edges and tops of objects, stencil-free metal crates, rust and dust, hazard-stripe accents on ledges, cool overcast light with warm lamp spots. Keep the far end clearly visible. Keep the exact same camera, framing and composition, and the exact position, size and shape of every wall, pillar, car, object and opening. Do not add, remove, move or replace any object. No people, no animals, no text. Keep exact image dimensions; no crop, zoom or rotate.",
  ruins_mid: "This is the SAME place, transformed into ancient sun-baked ruins, painterly game concept art, stylized realism, soft visible brushwork, slightly saturated colors, gentle glow and rim light. Every object stays exactly what it is and where it is, just transformed by the theme: weathered sandstone and carved stone surfaces, cracked golden plaster, sand drifts on floors and ledges, faded carved patterns without readable text, small vines on stone, warm golden sunlight with dusty light shafts and soft haze. Keep the far end clearly visible. Keep the exact same camera, framing and composition, and the exact position, size and shape of every wall, pillar, car, object and opening. Do not add, remove, move or replace any object. No people, no animals, no text. Keep exact image dimensions; no crop, zoom or rotate.",
  market_mid: "This is the SAME place, transformed into a vibrant old-world bazaar, painterly game concept art, stylized realism, soft visible brushwork, slightly saturated colors, gentle glow and rim light. Every object stays exactly what it is and where it is, just transformed by the theme: colorful fabric awnings and draped cloth over edges and the tops of objects, woven rugs and baskets, string lights and paper lanterns hanging from overhead edges, warm spice-market glow in orange, red and gold, rich saturated colors. Keep the far end clearly visible. Keep the exact same camera, framing and composition, and the exact position, size and shape of every wall, pillar, car, object and opening. Do not add, remove, move or replace any object. No people, no animals, no text. Keep exact image dimensions; no crop, zoom or rotate.",
  industrial_mid: "This is the SAME place, transformed into a gritty abandoned factory, painterly game concept art, stylized realism, soft visible brushwork, slightly saturated colors, gentle glow and rim light. Every object stays exactly what it is and where it is, just transformed by the theme: rusted steel beams, riveted metal panels, pipes and cables along walls and ceiling edges, oil stains and puddles on a glossy dark concrete floor, warm orange furnace glow and sparks against cool grey shadows, steam haze. Keep the far end clearly visible. Keep the exact same camera, framing and composition, and the exact position, size and shape of every wall, pillar, car, object and opening. Do not add, remove, move or replace any object. No people, no animals, no text. Keep exact image dimensions; no crop, zoom or rotate.",
  jungle_toon: "This is the SAME place, years later, reclaimed by a lush jungle, stylized 3D cartoon game art, smooth glossy rounded shapes, bold saturated colors, like a polished mobile game level. Every object stays exactly what it is and where it is, just aged and overgrown: thick moss, ferns and vines covering floors, ledges, walls and the tops of objects, roots creeping over surfaces, big tropical leaves growing from cracks, damp stone and puddles. Warm golden light, rich greens. Keep the exact same camera, framing and composition, and the exact position, size and shape of every wall, pillar, car, object and opening. Do not add, remove, move or replace any object. No people, no animals, no text. Keep exact image dimensions; no crop, zoom or rotate.",
  rockforest: "This is the SAME place, years later, reclaimed by a mossy pine forest, photorealistic, AAA game look. Every object stays exactly what it is and where it is, just aged and overgrown: concrete turns weathered and cracked, covered in moss, lichen and small mushrooms, tree roots and a few thin trunks creeping along walls, fallen leaves and pebbles on the floor. Soft overcast light, cool green-grey tones, gentle mist, keep original colors recognizable. Keep the far end clearly visible. Sharp, high contrast. Keep the exact same camera, framing and composition, and the exact position, size and shape of every wall, pillar, car, object and opening. Do not add, remove, move or replace any object. No people, no animals, no text. Keep exact image dimensions; no crop, zoom or rotate.",
  rockforest_mid: "This is the SAME place, years later, reclaimed by a mossy pine forest, painterly game concept art, stylized realism, soft visible brushwork, slightly saturated colors, gentle glow and rim light. Every object stays exactly what it is and where it is, just aged and overgrown: concrete turns weathered and cracked, covered in moss, lichen and small mushrooms, tree roots and a few thin trunks creeping along walls, fallen leaves and pebbles on the floor. Soft overcast light, cool green-grey tones, gentle mist,. Keep the exact same camera, framing and composition, and the exact position, size and shape of every wall, pillar, car, object and opening. Do not add, remove, move or replace any object. No people, no animals, no text. Keep exact image dimensions; no crop, zoom or rotate.",
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
  if (!env.GEMINI_API_KEY && !env.OPENAI_API_KEY && !env.MUAPI_KEY && !env.MUAPI_KEY_TEST) return Response.json({ error: "not-configured" }, { status: 503 });
  const device = request.headers.get("x-device") ?? "";
  if (!/^[A-Za-z0-9_-]{8,64}$/.test(device)) return Response.json({ error: "bad-device" }, { status: 400 });
  const style = new URL(request.url).searchParams.get("style") ?? "tundra";
  const prompt = PROMPTS[style];
  if (!prompt) return Response.json({ error: "bad-style" }, { status: 400 });
  const body = new Uint8Array(await request.arrayBuffer());
  if (body.length < 1000 || body.length > MAX_UPLOAD_BYTES) return Response.json({ error: "bad-size" }, { status: 413 });

  // בדיקת כפילות: אותה תמונה ואותן הגדרות כבר עובדו (על ידי מישהו כלשהו)? מחזירים אותה, בלי מכסה ובלי עלות.
  const reqUrl = new URL(request.url);
  const dedupe = reqUrl.searchParams.get("sandbox") !== "1" && reqUrl.searchParams.get("nocache") !== "1";
  let cacheStub: DurableObjectStub | null = null;
  if (dedupe) {
    const keyText = `${await sha256Hex(body)}:${await sha256Hex(new TextEncoder().encode(reqUrl.search))}`;
    cacheStub = env.QUOTA.get(env.QUOTA.idFromName(`img:${keyText}`));
    for (let wait = 0; wait < 45; wait++) {
      const begin = await (await cacheStub.fetch("https://quota/img-begin", { method: "POST" })).text();
      if (begin === "have") {
        const hit = await cacheStub.fetch("https://quota/img-get");
        if (hit.status === 200) {
          return new Response(hit.body, { headers: { "content-type": hit.headers.get("content-type") ?? "image/jpeg", "cache-control": "no-store", "x-provider": "cache" } });
        }
        break;
      }
      if (begin === "ok") break;
      await new Promise((r) => setTimeout(r, 2000)); // מישהו כבר מעבד את אותה תמונה: מחכים לתוצאה שלו
    }
  }
  const releaseBegin = async () => { if (cacheStub) { try { await cacheStub.fetch("https://quota/img-end", { method: "POST" }); } catch { /* ignore */ } } };

  const perDevice = Number(env.DAILY_PER_DEVICE ?? "20");
  const budget = Number(env.DAILY_BUDGET ?? "100");
  const ownerHeader = request.headers.get("x-owner") ?? "";
  const isOwner = !!env.OWNER_KEY && ownerHeader.length === env.OWNER_KEY.length && ownerHeader === env.OWNER_KEY;
  if (!isOwner) {
    if (!(await takeQuota(env, `dev:${device}`, perDevice))) {
      await releaseBegin();
      return Response.json({ error: "daily-limit" }, { status: 429 });
    }
    if (!(await takeQuota(env, "global", budget))) {
      await refundQuota(env, `dev:${device}`);
      await releaseBegin();
      return Response.json({ error: "budget-reached" }, { status: 429 });
    }
  }
  const fail = async (status: number, error: string, url?: string) => {
    await releaseBegin();
    if (!isOwner) {
      await refundQuota(env, `dev:${device}`);
      await refundQuota(env, "global");
    }
    return Response.json({ error, url }, { status });
  };

  try {
    const wanted = new URL(request.url).searchParams.get("provider");
    const provider = wanted ?? (env.OPENAI_API_KEY ? "openai" : "gemini");
    const size = new URL(request.url).searchParams.get("size") === "portrait" ? "1024x1536" : "1536x1024";
    const q = new URL(request.url).searchParams;
    const quality = ["low", "medium", "high"].includes(q.get("quality") ?? "") ? (q.get("quality") as string) : undefined;
    const model = /^gpt-image-[a-z0-9.\-]{1,30}$/.test(q.get("model") ?? "") ? (q.get("model") as string) : undefined;
    const muModel = MUAPI_MODELS.includes(q.get("model") ?? "") ? (q.get("model") as string) : "qwen3-image-to-image";
    const result =
      provider === "muapi" ? await callMuapi(env, style, prompt, body, muModel, q.get("sandbox") === "1", wantedAspect(q, size === "1024x1536"))
      : provider === "openai" ? await callOpenAI(env, prompt, body, size, quality, model)
      : await callGemini(env, prompt, body);
    if ("error" in result) return fail(502, result.error, result.url);
    if (cacheStub) {
      try { await cacheStub.fetch(`https://quota/img-put?mime=${encodeURIComponent(result.mime)}`, { method: "POST", body: result.bytes }); } catch { await releaseBegin(); }
    }
    return new Response(result.bytes, { headers: { "content-type": result.mime, "cache-control": "no-store", "x-provider": provider } });
  } catch {
    return fail(502, "upstream-error");
  }
}

async function sha256Hex(data: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", data);
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** יחס רוחב/גובה מבוקש (aspect=1.333), או לפי כיוון התמונה */
function wantedAspect(q: URLSearchParams, portrait: boolean): number {
  const a = parseFloat(q.get("aspect") ?? "");
  if (a >= 0.4 && a <= 2.5) return a;
  return portrait ? 9 / 16 : 16 / 9;
}

type ImageResult = { bytes: Uint8Array; mime: string } | { error: string; url?: string };

function fromBase64(data: string): Uint8Array {
  const bin = atob(data);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

async function callGemini(env: Env, prompt: string, body: Uint8Array): Promise<ImageResult> {
  if (!env.GEMINI_API_KEY) return { error: "gemini-not-configured" };
  const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`, {
    method: "POST",
    headers: { "content-type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
    body: JSON.stringify({
      contents: [{ parts: [{ text: prompt }, { inline_data: { mime_type: "image/jpeg", data: toBase64(body) } }] }],
      generationConfig: { responseModalities: ["TEXT", "IMAGE"] },
    }),
  });
  if (!res.ok) return { error: `gemini-${res.status}` };
  const json = (await res.json()) as any;
  const parts: any[] = json?.candidates?.[0]?.content?.parts ?? [];
  const image = parts.find((p) => p.inlineData?.data || p.inline_data?.data);
  const data: string | undefined = image?.inlineData?.data ?? image?.inline_data?.data;
  if (!data) return { error: "gemini-no-image" };
  return { bytes: fromBase64(data), mime: image?.inlineData?.mimeType ?? image?.inline_data?.mime_type ?? "image/png" };
}

/** OpenAI Images API (edits). הדגם נקבע במשתנה OPENAI_IMAGE_MODEL; ברירת מחדל: הקטן והזול */
async function callOpenAI(env: Env, prompt: string, body: Uint8Array, size: string, quality?: string, model?: string): Promise<ImageResult> {
  if (!env.OPENAI_API_KEY) return { error: "openai-not-configured" };
  const form = new FormData();
  form.append("model", model || env.OPENAI_IMAGE_MODEL || "gpt-image-1-mini");
  form.append("prompt", prompt);
  form.append("size", size);
  form.append("quality", quality || env.OPENAI_IMAGE_QUALITY || "medium");
  form.append("output_format", "jpeg");
  form.append("image", new Blob([body], { type: "image/jpeg" }), "photo.jpg");
  const res = await fetch("https://api.openai.com/v1/images/edits", {
    method: "POST",
    headers: { authorization: `Bearer ${env.OPENAI_API_KEY}` },
    body: form,
  });
  if (!res.ok) {
    const text = (await res.text()).slice(0, 160).replace(/[^\w .:,-]/g, "");
    return { error: `openai-${res.status}-${text}` };
  }
  const json = (await res.json()) as any;
  const data: string | undefined = json?.data?.[0]?.b64_json;
  if (!data) return { error: "openai-no-image" };
  return { bytes: fromBase64(data), mime: "image/jpeg" };
}

/** רשימה לבנה של דגמי עריכה ב-Muapi (כדי שאי אפשר יהיה להפעיל דגם יקר אחר דרך השרת) */
const MUAPI_MODELS = [
  "qwen3-image-to-image",
  "qwen-image-edit-plus",
  "qwen-image-edit",
  "qwen-image-edit-2511",
  "qwen2-1-image-to-image",
  "seedream-5.0-flash-edit",
  "kling-o3-image-edit",
  "flux-kontext-pro-i2i",
  "flux-2-klein-9b-edit",
  "muse-image-edit",
  "bytedance-seededit-v3",
];
/** גרסה קצרה של הפרומפט (ל-Muapi יש תקרה של 800 תווים לפרומפט) */
const SHORT_PROMPTS: Record<string, string> = {
  tundra:
    "Transform this exact photo into a dramatic frozen tundra winter scene, photorealistic, AAA game look. " +
    "Heavy fresh snow on every upward-facing surface, frost and ice crystals on walls and objects, glossy icy reflective floor with snow patches. " +
    "Cold cinematic light, deep blue shadows, soft cyan highlights, but keep natural color variety: warm lights and colored objects stay recognizable. " +
    "Keep the far end of the scene clearly visible and detailed, only a light haze, no white fog wall. Sharp, high contrast, not monochrome. " +
    "Keep the exact same camera, framing and composition, and the exact position, size and shape of every wall, pillar, object and opening. " +
    "Do not add, remove or move anything. No people, no text. Keep exact image dimensions and aspect ratio; no crop, zoom or rotate.",
  tundra_mid:
    "Transform this exact photo into a frozen tundra winter scene, painterly game concept art, stylized realism, soft brushwork, slightly saturated colors, gentle glow. " +
    "Heavy fresh snow on every upward-facing surface, frost and ice crystals on walls and objects, glossy icy floor with snow patches. " +
    "Cold light, deep blue shadows, soft cyan highlights, warm lights stay recognizable. Keep the far end clearly visible. " +
    "Keep the exact camera, framing and composition and the position of every wall, pillar, object and opening. " +
    "Do not move or remove anything; add only a few small snow-covered crates and barrels on the floor, spaced apart, low, never blocking the view. No people, no text. Keep exact dimensions; no crop.",
  jungle: "This is the SAME place, years later, reclaimed by a lush jungle, photorealistic, AAA game look. Every object stays exactly what it is and where it is, just aged and overgrown: thick moss, ferns and vines covering floors, ledges, walls and the tops of objects, roots creeping over surfaces, big tropical leaves growing from cracks, damp stone and puddles. Warm golden light shafts, rich greens, keep original colors recognizable. Keep the far end clearly visible. Sharp, high contrast. Keep the exact same camera, framing and composition, and the exact position, size and shape of every wall, pillar, car, object and opening. Do not add, remove, move or replace any object. No people, no animals, no text. Keep exact image dimensions; no crop, zoom or rotate.",
  jungle_mid: "This is the SAME place, years later, reclaimed by a lush jungle, painterly game concept art, stylized realism, soft visible brushwork, slightly saturated colors, gentle glow and rim light. Every object stays exactly what it is and where it is, just aged and overgrown: thick moss, ferns and vines covering floors, ledges, walls and the tops of objects, roots creeping over surfaces, big tropical leaves growing from cracks. Warm golden light, rich greens. Keep the exact same camera, framing and composition, and the exact position, size and shape of every wall, pillar, car, object and opening. Do not add, remove, move or replace any object. No people, no animals, no text. Keep exact image dimensions; no crop, zoom or rotate.",
  jungle_toon: "This is the SAME place, years later, reclaimed by a lush jungle, stylized 3D cartoon game art, smooth glossy rounded shapes, bold saturated colors, like a polished mobile game level. Every object stays exactly what it is and where it is, just aged and overgrown: thick moss, ferns and vines covering floors, ledges, walls and the tops of objects, roots creeping over surfaces, big tropical leaves growing from cracks, damp stone and puddles. Warm golden light, rich greens. Keep the exact same camera, framing and composition, and the exact position, size and shape of every wall, pillar, car, object and opening. Do not add, remove, move or replace any object. No people, no animals, no text. Keep exact image dimensions; no crop, zoom or rotate.",
  rockforest: "This is the SAME place, years later, reclaimed by a mossy pine forest, photorealistic, AAA game look. Every object stays exactly what it is and where it is, just aged and overgrown: concrete turns weathered and cracked, covered in moss, lichen and small mushrooms, tree roots and a few thin trunks creeping along walls, fallen leaves and pebbles on the floor. Soft overcast light, cool green-grey tones, gentle mist, keep original colors recognizable. Keep the far end clearly visible. Sharp, high contrast. Keep the exact same camera, framing and composition, and the exact position, size and shape of every wall, pillar, car, object and opening. Do not add, remove, move or replace any object. No people, no animals, no text. Keep exact image dimensions; no crop, zoom or rotate.",
  rockforest_mid: "This is the SAME place, years later, reclaimed by a mossy pine forest, painterly game concept art, stylized realism, soft visible brushwork, slightly saturated colors, gentle glow and rim light. Every object stays exactly what it is and where it is, just aged and overgrown: concrete turns weathered and cracked, covered in moss, lichen and small mushrooms, tree roots and a few thin trunks creeping along walls, fallen leaves and pebbles on the floor. Soft overcast light, cool green-grey tones, gentle mist,. Keep the exact same camera, framing and composition, and the exact position, size and shape of every wall, pillar, car, object and opening. Do not add, remove, move or replace any object. No people, no animals, no text. Keep exact image dimensions; no crop, zoom or rotate.",
};
const MUAPI_BASE = "https://api.muapi.ai/api/v1";

/** Muapi: מעלים את התמונה, שולחים בקשה לדגם ובודקים את התוצאה עד שהיא מוכנה (עד כדקה) */
async function callMuapi(env: Env, style: string, prompt: string, body: Uint8Array, model: string, sandbox: boolean, aspect: number): Promise<ImageResult> {
  // יחס התמונה הקרוב ביותר מהרשימה שהדגמים תומכים בה. כך המבנה נשמר והניתוח של המקור מתאים לתמונה החדשה
  const presets = [
    { r: 16 / 9, a: "16:9", w: 1536, h: 864 },
    { r: 3 / 2, a: "3:2", w: 1440, h: 960 },
    { r: 4 / 3, a: "4:3", w: 1376, h: 1032 },
    { r: 1, a: "1:1", w: 1024, h: 1024 },
    { r: 3 / 4, a: "3:4", w: 1032, h: 1376 },
    { r: 2 / 3, a: "2:3", w: 960, h: 1440 },
    { r: 9 / 16, a: "9:16", w: 864, h: 1536 },
  ];
  const pick = presets.reduce((best, p) => (Math.abs(Math.log(p.r / aspect)) < Math.abs(Math.log(best.r / aspect)) ? p : best));
  const key = sandbox ? env.MUAPI_KEY_TEST : env.MUAPI_KEY;
  if (!key) return { error: "muapi-not-configured" };
  const clip = async (res: Response) => (await res.text()).slice(0, 160).replace(/[^\w .:,-]/g, "");
  const form = new FormData();
  form.append("file", new Blob([body], { type: "image/jpeg" }), "photo.jpg");
  const up = await fetch(`${MUAPI_BASE}/upload_file`, { method: "POST", headers: { "x-api-key": key }, body: form });
  if (!up.ok) return { error: `muapi-upload-${up.status}-${await clip(up)}` };
  const imageUrl: string | undefined = ((await up.json()) as any)?.url;
  if (!imageUrl) return { error: "muapi-no-upload-url" };

  const submit = await fetch(`${MUAPI_BASE}/${model}`, {
    method: "POST",
    headers: { "x-api-key": key, "content-type": "application/json" },
    // לדגמים שונים שמות שדות שונים לתמונה ולגודל: שולחים את כולם (שדות מיותרים מתעלמים מהם)
    body: JSON.stringify({
      prompt: (SHORT_PROMPTS[style] ?? prompt).slice(0, 790),
      image_url: imageUrl,
      images_list: [imageUrl],
      aspect_ratio: pick.a,
      width: pick.w,
      height: pick.h,
    }),
  });
  if (!submit.ok) return { error: `muapi-submit-${submit.status}-${await clip(submit)}` };
  const id: string | undefined = ((await submit.json()) as any)?.request_id;
  if (!id) return { error: "muapi-no-request-id" };

  for (let i = 0; i < 40; i++) {
    await new Promise((r) => setTimeout(r, 2000));
    const res = await fetch(`${MUAPI_BASE}/predictions/${id}/result`, { headers: { "x-api-key": key } });
    if (!res.ok) return { error: `muapi-result-${res.status}-${await clip(res)}` };
    const json = (await res.json()) as any;
    const status = String(json?.status ?? "").toLowerCase();
    if (status === "completed") {
      const first = json?.outputs?.[0];
      const url: string | undefined = typeof first === "string" ? first : first?.url;
      if (!url) return { error: "muapi-no-output" };
      // ה-CDN של Muapi חוסם בקשות בלי כותרות של דפדפן (403), לכן מזדהים כדפדפן
      const browserHeaders = {
        "user-agent": "Mozilla/5.0 (Linux; Android 14; SM-A546B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
        accept: "image/avif,image/webp,image/png,image/jpeg,image/*;q=0.8",
        referer: "https://muapi.ai/",
      };
      let img = await fetch(url, { headers: browserHeaders });
      if (img.status === 403 || img.status === 401) img = await fetch(url, { headers: { ...browserHeaders, "x-api-key": key } });
      if (!img.ok) return { error: `muapi-download-${img.status}-${new URL(url).host}${sandbox ? "-sandbox" : ""}`, url };
      return { bytes: new Uint8Array(await img.arrayBuffer()), mime: img.headers.get("content-type") ?? "image/jpeg" };
    }
    if (status === "failed" || status === "error") return { error: `muapi-failed-${String(json?.error ?? "").slice(0, 100).replace(/[^\w .:,-]/g, "")}` };
  }
  return { error: "muapi-timeout" };
}

/** מונה יומי פשוט: מפתח אחד לכל יום. אובייקט לכל מכשיר ואחד גלובלי. */
export class Quota implements DurableObject {
  constructor(private ctx: DurableObjectState) {}

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname.startsWith("/img-")) return this.imageCache(url, request);
    const day = url.searchParams.get("day") ?? "";
    const key = `day:${day}`;
    const used = (await this.ctx.storage.get<number>(key)) ?? 0;
    if (url.pathname === "/take") {
      const limit = Number(url.searchParams.get("limit") ?? "0");
      if (used >= limit) return new Response("full", { status: 429 });
      await this.ctx.storage.put(key, used + 1);
      return new Response("ok");
    }
    if (url.pathname === "/peek") return Response.json({ used });
    if (url.pathname === "/refund") {
      await this.ctx.storage.put(key, Math.max(0, used - 1));
      return new Response("ok");
    }
    return new Response("?", { status: 404 });
  }

  /**
   * מטמון תוצאות העיבוד (בדיקת כפילות): אותה תמונה + אותו סגנון = אותה תוצאה, בלי לשלם שוב.
   * נשמר בחלקים של עד מגה (מגבלת ערך באחסון), ופג אחרי 60 יום. "begin" מסמן עיבוד שבתהליך, כדי שבקשות מקבילות לא ישלמו פעמיים.
   */
  private async imageCache(url: URL, request: Request): Promise<Response> {
    const st = this.ctx.storage;
    const meta = await st.get<{ at: number; chunks: number; mime: string }>("meta");
    const fresh = !!meta && Date.now() - meta.at < IMG_TTL_MS;
    if (url.pathname === "/img-get") {
      if (!fresh || !meta) return new Response("none", { status: 404 });
      const parts: Uint8Array[] = [];
      let total = 0;
      for (let i = 0; i < meta.chunks; i++) {
        const c = await st.get<ArrayBuffer>(`c${i}`);
        if (!c) return new Response("none", { status: 404 });
        const u = new Uint8Array(c);
        parts.push(u);
        total += u.length;
      }
      const out = new Uint8Array(total);
      let o = 0;
      for (const u of parts) { out.set(u, o); o += u.length; }
      return new Response(out, { headers: { "content-type": meta.mime } });
    }
    if (url.pathname === "/img-begin") {
      if (fresh) return new Response("have");
      const busy = await st.get<number>("busy");
      if (busy && Date.now() - busy < 150_000) return new Response("busy");
      await st.put("busy", Date.now());
      return new Response("ok");
    }
    if (url.pathname === "/img-end") {
      await st.delete("busy");
      return new Response("ok");
    }
    if (url.pathname === "/img-put") {
      const bytes = new Uint8Array(await request.arrayBuffer());
      if (bytes.length === 0 || bytes.length > 8_000_000) return new Response("bad", { status: 400 });
      const chunks = Math.ceil(bytes.length / IMG_CHUNK);
      for (let i = 0; i < chunks; i++) {
        const part = bytes.slice(i * IMG_CHUNK, (i + 1) * IMG_CHUNK);
        await st.put(`c${i}`, part.buffer.slice(part.byteOffset, part.byteOffset + part.byteLength));
      }
      await st.put("meta", { at: Date.now(), chunks, mime: url.searchParams.get("mime") ?? "image/jpeg" });
      await st.delete("busy");
      return new Response("ok");
    }
    return new Response("?", { status: 404 });
  }
}

const IMG_CHUNK = 1_000_000;
const IMG_TTL_MS = 60 * 24 * 3600 * 1000;


// ───────────── עולמות קהילה ─────────────

const WORLD_STYLES = new Set(["tundra_mid", "jungle_mid", "rockforest_mid", "cyber_mid", "military_mid", "ruins_mid", "market_mid", "industrial_mid"]);
const MAX_WORLD_BYTES = 1_800_000;
const MAX_ANALYSIS_BYTES = 2_500_000;
const SHARES_PER_DEVICE_PER_DAY = 10;
const REPORTS_TO_HIDE = 3;

function validDevice(request: Request): string | null {
  const device = request.headers.get("x-device") ?? "";
  return /^[A-Za-z0-9_-]{8,64}$/.test(device) ? device : null;
}

/** POST /worlds/share?style=  (גוף: JPEG של העולם)   GET /worlds/random?style=&exclude=id,id
 *  GET /worlds/img/<id>   POST /worlds/report?id=   POST /worlds/unshare?id=   כולם עם x-device */
async function worldsApi(request: Request, env: Env, url: URL): Promise<Response> {
  if (!env.WORLDS) return Response.json({ error: "not-configured" }, { status: 503 });
  const index = env.WORLDS_INDEX.get(env.WORLDS_INDEX.idFromName("main"));
  const json = (body: unknown, status = 200) => Response.json(body, { status, headers: { "cache-control": "no-store" } });

  const imgMatch = url.pathname.match(/^\/worlds\/(img|analysis)\/([a-f0-9]{16,64})$/);
  if (imgMatch && request.method === "GET") {
    const isImg = imgMatch[1] === "img";
    const meta = await (await index.fetch(`https://idx/status?id=${imgMatch[2]}`)).json<{ ok: boolean }>();
    if (!meta.ok) return json({ error: "not-found" }, 404);
    const obj = await env.WORLDS.get(isImg ? `worlds/${imgMatch[2]}.jpg` : `worlds/${imgMatch[2]}.bin.gz`);
    if (!obj) return json({ error: "not-found" }, 404);
    return new Response(obj.body, { headers: { "content-type": isImg ? "image/jpeg" : "application/gzip", "cache-control": "public, max-age=86400" } });
  }

  const device = validDevice(request);
  if (!device) return json({ error: "bad-device" }, 400);

  if (url.pathname === "/worlds/share" && request.method === "POST") {
    const style = url.searchParams.get("style") ?? "";
    if (!WORLD_STYLES.has(style)) return json({ error: "bad-style" }, 400);
    // גוף: [אורך העולם: 4 בתים, big-endian][JPEG של העולם][ניתוח הסצנה בדחיסת gzip]
    const all = new Uint8Array(await request.arrayBuffer());
    if (all.length < 24_000 || all.length > MAX_WORLD_BYTES + MAX_ANALYSIS_BYTES + 4) return json({ error: "bad-size" }, 413);
    const worldLen = ((all[0] << 24) | (all[1] << 16) | (all[2] << 8) | all[3]) >>> 0;
    const world = all.subarray(4, 4 + worldLen);
    const analysis = all.subarray(4 + worldLen);
    if (worldLen < 20_000 || worldLen > MAX_WORLD_BYTES) return json({ error: "bad-size" }, 413);
    if (analysis.length < 200 || analysis.length > MAX_ANALYSIS_BYTES) return json({ error: "bad-analysis" }, 413);
    if (!(world[0] === 0xff && world[1] === 0xd8 && world[2] === 0xff)) return json({ error: "not-jpeg" }, 415);
    if (!(analysis[0] === 0x1f && analysis[1] === 0x8b)) return json({ error: "bad-analysis" }, 415);
    if (!(await takeQuota(env, `share:${device}`, SHARES_PER_DEVICE_PER_DAY))) return json({ error: "share-limit" }, 429);
    const id = (await sha256Hex(world)).slice(0, 32);
    const res = await index.fetch(`https://idx/add?id=${id}&style=${style}&device=${device}`, { method: "POST" });
    const info = await res.json<{ status: string }>();
    if (info.status === "banned") { await refundQuota(env, `share:${device}`); return json({ error: "hidden" }, 403); }
    if (info.status === "new") {
      await env.WORLDS.put(`worlds/${id}.jpg`, world, { httpMetadata: { contentType: "image/jpeg" } });
      await env.WORLDS.put(`worlds/${id}.bin.gz`, analysis, { httpMetadata: { contentType: "application/gzip" } });
    }
    return json({ id, status: info.status });
  }

  if (url.pathname === "/worlds/random" && request.method === "GET") {
    const style = url.searchParams.get("style") ?? "";
    if (!WORLD_STYLES.has(style)) return json({ error: "bad-style" }, 400);
    const exclude = (url.searchParams.get("exclude") ?? "").split(",").filter((x) => /^[a-f0-9]{16,64}$/.test(x)).slice(0, 50).join(",");
    const res = await index.fetch(`https://idx/random?style=${style}&exclude=${exclude}&device=${device}`);
    return new Response(res.body, { status: res.status, headers: { "content-type": "application/json", "cache-control": "no-store" } });
  }

  if ((url.pathname === "/worlds/report" || url.pathname === "/worlds/unshare") && request.method === "POST") {
    const id = url.searchParams.get("id") ?? "";
    if (!/^[a-f0-9]{16,64}$/.test(id)) return json({ error: "bad-id" }, 400);
    const op = url.pathname === "/worlds/report" ? "report" : "unshare";
    const res = await index.fetch(`https://idx/${op}?id=${id}&device=${device}`, { method: "POST" });
    const info = await res.json<{ status: string }>();
    if (op === "unshare" && info.status === "deleted") { await env.WORLDS.delete(`worlds/${id}.jpg`); await env.WORLDS.delete(`worlds/${id}.bin.gz`); }
    return json(info, res.status);
  }
  return json({ error: "not-found" }, 404);
}

/** אינדקס העולמות המשותפים. SQLite: worlds + reports (דיווח אחד לכל מכשיר לכל עולם; 3 דיווחים מסתירים) */
export class WorldsIndex implements DurableObject {
  private sql: SqlStorage;
  constructor(ctx: DurableObjectState) {
    this.sql = ctx.storage.sql;
    this.sql.exec("CREATE TABLE IF NOT EXISTS worlds (id TEXT PRIMARY KEY, style TEXT NOT NULL, device TEXT NOT NULL, at INTEGER NOT NULL, hidden INTEGER NOT NULL DEFAULT 0)");
    this.sql.exec("CREATE TABLE IF NOT EXISTS reports (id TEXT NOT NULL, device TEXT NOT NULL, PRIMARY KEY (id, device))");
    this.sql.exec("CREATE INDEX IF NOT EXISTS worlds_style ON worlds (style, hidden)");
  }

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const q = url.searchParams;
    const id = q.get("id") ?? "";
    const device = q.get("device") ?? "";
    const reply = (body: unknown, status = 200) => Response.json(body, { status });

    if (url.pathname === "/status") {
      const row = this.sql.exec("SELECT hidden FROM worlds WHERE id = ?", id).toArray()[0];
      return reply({ ok: !!row && Number(row.hidden) === 0 });
    }
    if (url.pathname === "/add") {
      const row = this.sql.exec("SELECT hidden FROM worlds WHERE id = ?", id).toArray()[0];
      if (row) return reply({ status: Number(row.hidden) ? "banned" : "exists" });
      this.sql.exec("INSERT INTO worlds (id, style, device, at) VALUES (?, ?, ?, ?)", id, q.get("style") ?? "", device, Date.now());
      return reply({ status: "new" });
    }
    if (url.pathname === "/random") {
      const style = q.get("style") ?? "";
      const exclude = (q.get("exclude") ?? "").split(",").filter(Boolean);
      const marks = exclude.map(() => "?").join(",");
      const rows = this.sql.exec(
        `SELECT id FROM worlds WHERE style = ? AND hidden = 0 AND device != ?${exclude.length ? ` AND id NOT IN (${marks})` : ""} ORDER BY RANDOM() LIMIT 1`,
        style, device, ...exclude
      ).toArray();
      if (!rows.length) return reply({ error: "none" }, 404);
      return reply({ id: rows[0].id });
    }
    if (url.pathname === "/report") {
      const row = this.sql.exec("SELECT device FROM worlds WHERE id = ?", id).toArray()[0];
      if (!row) return reply({ status: "not-found" }, 404);
      this.sql.exec("INSERT OR IGNORE INTO reports (id, device) VALUES (?, ?)", id, device);
      const count = Number(this.sql.exec("SELECT COUNT(*) AS n FROM reports WHERE id = ?", id).toArray()[0].n);
      if (count >= REPORTS_TO_HIDE) this.sql.exec("UPDATE worlds SET hidden = 1 WHERE id = ?", id);
      return reply({ status: count >= REPORTS_TO_HIDE ? "hidden" : "reported" });
    }
    if (url.pathname === "/unshare") {
      const row = this.sql.exec("SELECT device, hidden FROM worlds WHERE id = ?", id).toArray()[0];
      if (!row) return reply({ status: "not-found" }, 404);
      if (row.device !== device) return reply({ status: "forbidden" }, 403);
      // עולם שהוסתר בדיווחים נשאר ברשומה (כדי שלא יועלה שוב); רק עולם תקין נמחק
      if (Number(row.hidden)) return reply({ status: "hidden" });
      this.sql.exec("DELETE FROM worlds WHERE id = ?", id);
      return reply({ status: "deleted" });
    }
    return reply({ error: "not-found" }, 404);
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
    if (url.pathname.startsWith("/worlds/")) return worldsApi(request, env, url);
    if (url.pathname === "/quota") {
      const device = request.headers.get("x-device") ?? "";
      if (!/^[A-Za-z0-9_-]{8,64}$/.test(device)) return Response.json({ error: "bad-device" }, { status: 400 });
      const limit = Number(env.DAILY_PER_DEVICE ?? "20");
      const stub = env.QUOTA.get(env.QUOTA.idFromName(`dev:${device}`));
      const { used } = await (await stub.fetch(`https://quota/peek?day=${today()}`)).json<{ used: number }>();
      return Response.json({ limit, left: Math.max(0, limit - used) }, { headers: { "cache-control": "no-store" } });
    }
    if (url.pathname === "/lab") return new Response(LAB_HTML, { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" } });

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
