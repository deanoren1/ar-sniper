# SnapBoo VFX — Frozen Tundra

A reusable, photo-independent VFX package for the transition from the player's arena photo into the existing Frozen Tundra boss intro.

## Included source files

- `Blender/create_frozen_portal.py` — procedural icy portal/vortex scene and transparent PNG sequence renderer.
- `Blender/create_ice_crack_overlay.py` — procedural transparent crack overlay renderer.
- `Blender/pack_sprite_sheet.py` — packs numbered RGBA frames into a sprite sheet and JSON metadata.
- `ASSET_SOURCES.md` — external asset sources and licensing/acquisition rules.
- `ANDROID_INTEGRATION.md` — layer order, timing, mobile performance and acceptance criteria.

## Quick start

From the repository root, with Blender installed:

```bash
blender -b --python VFX/Frozen_Tundra/Blender/create_frozen_portal.py -- --output //VFX/Frozen_Tundra/Exports/Portal --frames 48 --size 1024
blender -b --python VFX/Frozen_Tundra/Blender/create_ice_crack_overlay.py -- --output //VFX/Frozen_Tundra/Exports/Ice_Cracks/ice_cracks.png --size 1024
python -m pip install Pillow
python VFX/Frozen_Tundra/Blender/pack_sprite_sheet.py VFX/Frozen_Tundra/Exports/Portal VFX/Frozen_Tundra/Exports/Portal/portal_sheet.png --columns 8
```

The Blender scripts render RGBA PNGs locally. The sprite packer creates an RGBA atlas and JSON manifest. Blender is not bundled, and the render outputs are generated on the machine running Blender.

## Design and integration

- Cyan/ice-blue energy, frost-white highlights and restrained cold violet.
- Transparent layers; the user's photo remains the live background.
- The portal's center remains transparent so the arena stays visible through it.
- Snow ambience, portal opening, cracks, shards and flash remain independent layers.
- Target transition: approximately 2 seconds, followed by the existing approximately 7-second boss intro. Do not modify the approved boss-intro video.

## Third-party assets

No third-party raw asset files are bundled. Use the direct sources and license notes in `ASSET_SOURCES.md`. Select only assets explicitly marked free, retain the license evidence locally, and do not redistribute raw source files.

## Validation status

Committed source scripts and documentation have been reviewed for consistency. Rendering, alpha-edge inspection, atlas generation and Android device profiling remain pending until run on a machine with Blender and tested in-game. Do not treat the generated assets as production-approved until the acceptance checklist in `ANDROID_INTEGRATION.md` passes.
