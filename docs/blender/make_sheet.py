"""מרכיב את הפריימים שרונדרו (frames/<angle>_<row>_<col>.png) לגיליונות 6x4. הרצה: python make_sheet.py <outdir>"""
import glob
import os
import sys

from PIL import Image

out = sys.argv[1] if len(sys.argv) > 1 else "out"
frames = glob.glob(os.path.join(out, "frames", "*.png"))
angles = sorted({os.path.basename(f).split("_")[0] for f in frames})
for angle in angles:
    cell = None
    sheet = None
    for f in frames:
        base = os.path.basename(f)[:-4].split("_")
        if base[0] != angle:
            continue
        img = Image.open(f).convert("RGBA")
        if sheet is None:
            cell = img.width
            sheet = Image.new("RGBA", (6 * cell, 4 * cell), (0, 0, 0, 0))
        sheet.paste(img, (int(base[2]) * cell, int(base[1]) * cell))
    path = os.path.join(out, f"octo_{angle}.png")
    sheet.save(path)
    print("saved", path, sheet.size)
