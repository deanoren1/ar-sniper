# Frozen Tundra — reference asset drop

This folder contains 12 original PNG reference assets copied from the CC0 sources listed in [THIRD_PARTY_LICENSES.md](./THIRD_PARTY_LICENSES.md). They are unmodified source images; use them as visual building blocks and prototypes, not as a finished Frozen Tundra transition.

## Uploaded files

| File | Suggested use |
|---|---|
| particle-circle-01.png | portal/ring composition, circular energy mask |
| particle-magic-01.png | rune/energy overlay reference |
| particle-magic-02.png | alternate magic layer |
| particle-twirl-01.png | vortex/swirl motion layer |
| particle-flare-01.png | portal center flash/glint |
| particle-light-01.png | glow and illumination layer |
| particle-smoke-01.png | transition mist / atmospheric layer |
| particle-spark-01.png | ice sparkle and particle accents |
| particle-trace-01.png | streaks moving inward toward portal |
| particle-star-01.png | small glints / snow sparkle accents |
| thick-fog-01.png | full-screen fog / cold haze layer |
| cracked-tiles-sd.png | crack pattern reference; adapt/crop to a photo-independent overlay |

## Important art-direction note

Most of these are generic particle/flare assets rather than finished ice VFX. They are useful for compositing and prototyping. They do not replace the hero 3D ice portal, faceted ice shards, snow particle system, or photo-independent crack overlay. Some are stylized 2D assets and may need recoloring, masking, scale changes, and blending-mode tests to fit SnapBoo's polished 3D cartoon look.

## Android notes

- Keep source PNGs as separate layers; don't bake them into the user's arena photo.
- Use transparent PNG alpha and test both alpha and additive blending.
- Downscale oversized assets to 512–1024 px for mobile where appropriate.
- Avoid using the crack tileset directly over an arbitrary photo floor; derive a neutral overlay mask instead.
