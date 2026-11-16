"""Static validation for the VFX Python source files (no Blender install needed)."""
import ast
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SCRIPTS = [
    ROOT / "Blender" / "create_frozen_portal.py",
    ROOT / "Blender" / "create_ice_crack_overlay.py",
    ROOT / "Blender" / "pack_sprite_sheet.py",
]
required = {
    "create_frozen_portal.py": ["film_transparent", '"RGBA"', "render(animation=True)"],
    "create_ice_crack_overlay.py": ["film_transparent", '"RGBA"', "write_still=True"],
    "pack_sprite_sheet.py": ["Image.open", "alpha_composite", "json.dumps"],
}
errors = []
for path in SCRIPTS:
    if not path.is_file():
        errors.append(f"Missing source file: {path}")
        continue
    source = path.read_text(encoding="utf-8")
    try:
        ast.parse(source, filename=str(path))
    except SyntaxError as exc:
        errors.append(f"Syntax error in {path.name}:{exc.lineno}: {exc.msg}")
        continue
    for token in required[path.name]:
        if token not in source:
            errors.append(f"{path.name}: missing expected feature marker {token!r}")

if errors:
    print("\n".join("FAIL: " + e for e in errors))
    raise SystemExit(1)
print(f"PASS: parsed {len(SCRIPTS)} Python scripts; expected alpha/render/packing markers found.")
