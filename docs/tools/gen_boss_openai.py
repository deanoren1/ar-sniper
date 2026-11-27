"""Generate a portal-boss animation sheet through the OpenAI image API (runs in GitHub Actions).

usage: gen_boss_openai.py <design.jpg> <boss name> <out_dir>
needs OPENAI_API_KEY in the environment.

Makes four 1536x1024 sheets, each a 3x2 grid of poses on a transparent background:
  idle.png     6 frames: breathing loop
  prepare.png  6 frames: attack wind-up (the game uses the first 5)
  firehit.png  frames 1-2 attack release, frames 3-6 hit reactions
  death.png    6 frames: collapse and vanish
Then assembles out_dir/boss_anim_sheet.png in the game's layout (6 columns x 5 rows:
idle 6, prepare 5, fire 2, hit 4, death 6).
"""
import base64
import json
import mimetypes
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

from PIL import Image

API = "https://api.openai.com/v1/images/edits"
MODELS = ["gpt-image-1.5", "gpt-image-1"]

STYLE = (
    "Game sprite sheet for a mobile shooter. Exactly 6 poses of the SAME character from the reference image, "
    "arranged in a clean grid of 3 columns by 2 rows, every pose centred in its own equal cell with empty margin "
    "around it, nothing crossing the cell borders, no grid lines, no text, no labels, no numbers. "
    "Fully transparent background, no floor, no shadow, no portal, no frame. "
    "Keep the design identical in every pose: same proportions, colours, armour plates, spikes, claws, eyes, "
    "huge toothed mouth with the glowing magenta core, glossy chunky 3D-cartoon brawler style. "
    "Front three-quarter view facing the viewer, same size and same foot line in every cell, feet fully visible. "
)

SHEETS = {
    "idle": "Poses: a heavy breathing idle loop. Tiny changes only between poses: chest rising and falling, "
            "shoulders lifting slightly, claws flexing, eyes blinking once, the mouth glow pulsing. Order them as a smooth loop.",
    "prepare": "Poses: winding up an attack, getting stronger each pose: 1 crouches lower, 2 spreads its claws, "
               "3 rears back with mouth opening wider, 4 mouth wide open with the magenta core glowing bright, "
               "5 peak charge with a swirling magenta energy ball forming inside the mouth, 6 same as 5 but even brighter.",
    "firehit": "Poses: 1 lunges forward and spits a big glowing magenta energy orb out of its mouth toward the viewer, "
               "2 follow-through right after the shot with the mouth still open and a small magenta flash. "
               "3 recoils from a hit on the head, eyes squeezed, 4 staggers back with a small impact spark on the chest, "
               "5 flinches to the side, angry, 6 dazed with small stars circling its head.",
    "death": "Poses: defeat sequence: 1 roars in pain with cracks of light on the armour, 2 drops to its knees, "
             "3 slumps forward with armour plates breaking off, 4 collapses into a pile of broken armour and spikes "
             "with purple smoke, 5 the pile crumbles into small rubble with fading magenta glow, "
             "6 only a few small stones and a wisp of smoke remain.",
}


def call(model, prompt, images, fidelity=True):
    boundary = uuid.uuid4().hex
    parts = []

    def field(name, value):
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())

    field("model", model)
    field("prompt", prompt)
    field("size", "1536x1024")
    field("quality", "high")
    field("background", "transparent")
    field("output_format", "png")
    if fidelity:
        field("input_fidelity", "high")
    for path in images:
        mime = mimetypes.guess_type(path)[0] or "image/png"
        with open(path, "rb") as f:
            data = f.read()
        parts.append(
            f'--{boundary}\r\nContent-Disposition: form-data; name="image[]"; filename="{os.path.basename(path)}"\r\n'
            f"Content-Type: {mime}\r\n\r\n".encode() + data + b"\r\n"
        )
    parts.append(f"--{boundary}--\r\n".encode())
    req = urllib.request.Request(API, data=b"".join(parts), method="POST", headers={
        "Authorization": "Bearer " + os.environ["OPENAI_API_KEY"],
        "Content-Type": f"multipart/form-data; boundary={boundary}",
    })
    with urllib.request.urlopen(req, timeout=600) as r:
        return base64.b64decode(json.load(r)["data"][0]["b64_json"])


def generate(prompt, images, out):
    last = None
    for model in MODELS:
        for fidelity in (True, False):
            for attempt in range(2):
                try:
                    png = call(model, prompt, images, fidelity)
                    with open(out, "wb") as f:
                        f.write(png)
                    print(f"ok {out} model={model} fidelity={fidelity}", flush=True)
                    return
                except urllib.error.HTTPError as e:
                    last = f"{e.code} {e.read()[:400]!r}"
                    print(f"fail model={model} fidelity={fidelity}: {last}", flush=True)
                    if e.code in (429, 500, 502, 503):
                        time.sleep(20)
                        continue
                    break
                except Exception as e:  # timeouts
                    last = repr(e)
                    print(f"fail {last}", flush=True)
                    time.sleep(10)
    raise SystemExit(f"could not generate {out}: {last}")


def cells(path):
    im = Image.open(path).convert("RGBA")
    w, h = im.size
    return [im.crop((c * w // 3, r * h // 2, (c + 1) * w // 3, (r + 1) * h // 2)) for r in range(2) for c in range(3)]


def main():
    design, name, out = sys.argv[1], sys.argv[2], sys.argv[3]
    os.makedirs(out, exist_ok=True)
    base = f"The character is the boss '{name}' from the reference image. " + STYLE
    generate(base + SHEETS["idle"], [design], f"{out}/idle.png")
    for k in ("prepare", "firehit", "death"):
        generate(base + "The second reference image shows the exact character as already drawn: match it precisely. "
                 + SHEETS[k], [design, f"{out}/idle.png"], f"{out}/{k}.png")

    idle, prep, fh, death = (cells(f"{out}/{k}.png") for k in ("idle", "prepare", "firehit", "death"))
    rows = [idle, prep[:5], fh[:2], fh[2:], death]
    cw, ch = idle[0].size
    sheet = Image.new("RGBA", (cw * 6, ch * 5), (0, 0, 0, 0))
    for r, row in enumerate(rows):
        for c, cell in enumerate(row):
            sheet.paste(cell.resize((cw, ch)), (c * cw, r * ch))
    sheet.save(f"{out}/boss_anim_sheet.png")
    preview = Image.new("RGBA", sheet.size, (40, 40, 60, 255))
    preview.alpha_composite(sheet)
    preview.convert("RGB").save(f"{out}/preview.jpg", quality=88)
    print("sheet done", sheet.size)


if __name__ == "__main__":
    main()
