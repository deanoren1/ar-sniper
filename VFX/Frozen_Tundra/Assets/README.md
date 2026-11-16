# Frozen Tundra — reference asset drop

This folder contains 12 third-party CC0 PNG reference assets and 6 original vector overlays created for SnapBoo. They are building blocks and prototypes, not a finished 2-second transition.

## Original ice overlays added for SnapBoo

These six SVGs are authored specifically for the Frozen Tundra transition and have transparent backgrounds:

| File | Intended use |
|---|---|
| ice-shards-burst.svg | Radial crystalline burst / shards around the portal |
| frost-nova-ring.svg | Expanding frost ring; keep the centre transparent for the player photo |
| snowflake-particles.svg | Floating snowflake accents and particle emitters |
| icicle-fall-overlay.svg | Top-edge icicle layer for a short transition beat |
| frost-crack-overlay.svg | Screen-space frost fracture overlay; not tied to a floor plane |
| blizzard-vortex.svg | Spiral streaks and vortex movement toward the portal |

These are vector source assets, not raster sprite sheets or animated sequences. Android integration should rasterize them to appropriately sized transparent PNGs at build time or convert them to vector drawables where supported. Animate transform/alpha/rotation separately in Compose; do not bake the photo into them.

## Third-party PNG reference assets

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

## Candidate ice-specific packs — not downloaded into this repository

The following source packs were researched, but their ZIP binaries are not present here yet:

- [Free Winter Pixel Effects](https://cs5475.itch.io/free-winter-pixel-effects) — 16 snow/ice effects, CC0, 275 kB.
- [PVFX Foundry](https://nerijs.itch.io/pvfx-foundry) — 101 effects, CC0, transparent PNG sheets + JSON, 8.8 MB.
- [SEAMPROOF Ice & Frost VFX](https://seamproof.itch.io/ice-frost-vfx) — free Ice Shatter sample; full ten-effect pack is paid and prohibits redistribution of the asset files.
- [Free Spell VFX Sampler](https://gegx.itch.io/free-spell-vfx-sampler) — three transparent spell loops; commercial use allowed, no credit required.

## Important art-direction note

Most third-party PNGs are generic particle/flare assets rather than finished ice VFX. The six original SVGs add photo-independent frost/crystal shapes, but they are still static overlays. We still need animated 3D hero portal/shards and a tested snow particle system to reach the intended polished 3D cartoon look.

## Android notes

- Keep every layer separate; never bake these effects into the user's arena photo.
- Preserve alpha. Test alpha blending for solid ice and additive blending only for glow/spark accents.
- Rasterize SVGs to 512–1024 px transparent PNGs for mobile; profile memory and frame time on the Galaxy A54.
- Do not use the crack overlay as a floor decal: it is screen-space and photo-independent.
- The SVGs are static art. Animate position, scale, rotation and alpha independently in Compose.
