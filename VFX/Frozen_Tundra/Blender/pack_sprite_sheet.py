"""
Pack a numbered RGBA PNG sequence into a sprite sheet.
Requires Pillow: python -m pip install Pillow
Example:
  python pack_sprite_sheet.py Exports/Portal Exports/Portal/portal.png --columns 8
Writes a JSON manifest with frame count, dimensions, and grid.
"""
from PIL import Image
import argparse, json, math
from pathlib import Path

def main():
    p=argparse.ArgumentParser()
    p.add_argument("input_dir"); p.add_argument("output")
    p.add_argument("--columns",type=int,default=8)
    p.add_argument("--pattern",default="*.png")
    a=p.parse_args()
    files=sorted(Path(a.input_dir).glob(a.pattern))
    if not files: raise SystemExit("ERROR: no PNG frames found")
    frames=[Image.open(f).convert("RGBA") for f in files]
    size=frames[0].size
    if any(im.size!=size for im in frames): raise SystemExit("ERROR: inconsistent frame sizes")
    cols=max(1,a.columns); rows=math.ceil(len(frames)/cols)
    sheet=Image.new("RGBA",(cols*size[0],rows*size[1]),(0,0,0,0))
    for i,im in enumerate(frames):
        sheet.alpha_composite(im,((i%cols)*size[0],(i//cols)*size[1]))
    out=Path(a.output); out.parent.mkdir(parents=True,exist_ok=True)
    sheet.save(out,optimize=True)
    manifest={"image":out.name,"frames":len(frames),"columns":cols,"rows":rows,
              "frame_width":size[0],"frame_height":size[1],"sheet_width":sheet.width,
              "sheet_height":sheet.height,"format":"RGBA PNG","frame_order":"lexicographic"}
    out.with_suffix(".json").write_text(json.dumps(manifest,indent=2),encoding="utf-8")
    print(json.dumps(manifest,indent=2))
if __name__=="__main__": main()
