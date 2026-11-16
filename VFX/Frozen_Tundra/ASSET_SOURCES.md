# Frozen Tundra — asset acquisition catalogue

Research checked 16 November 2026. This catalogue prioritizes effects that can help us build a polished portal transition. **These are download sources, not binaries stored in this repository yet.** The repository is private, but each source's license still governs copying and storage. For each download, preserve the included license and source attribution where required.

## Priority A — best match for the transition

| Source | Relevant contents | Format / style | License notes | Link |
|---|---|---|---|---|
| SEAMPROOF — Ice & Frost VFX | Ice Shatter, Frost Nova, Ice Wall, Ice Spike Eruption, Icicle Fall, Snow Burst, Ice Shield, Blizzard | Transparent PNG sprite sheets and individual frames; high-resolution hand-painted VFX | Creator states commercial use allowed and attribution optional; raw asset-pack redistribution prohibited. Download locally; do not mirror raw files unless the included license expressly allows it. | https://seamproof.itch.io/ice-frost-vfx |
| CGHEVEN | Portal, magic portal, energy, elemental VFX, flipbooks, VDBs | 3D/VFX library, 1K/2K; useful for Blender-based look development | Provider describes its free tier as CC0; verify each item's FREE status and license. Site terms restrict re-uploading raw assets. | https://cgheven.com/assets |
| CGHEVEN Blender add-on | Browse/import supported assets in Blender | Blender add-on | Check current Blender version compatibility and per-asset license before download. | https://cgheven.com/addons/blender |

## Priority B — broad libraries for finding useful pieces

| Source | Relevant contents | Format / style | License notes | Link |
|---|---|---|---|---|
| Pixel VFX Library — 260 effects | Portals, cosmic magic, ice, ground cracks, crystal shards, shields, shockwaves, transitions | 520 transparent PNG sheets (glow/no-glow), JSON metadata, 23,988 frames; pixel art | Download page says free; inspect the included LICENSE.txt before use and retain it. | https://breathinghuman.itch.io/pixel-vfx-library-260 |
| PVFX Foundry — 101 effects | Ice, lightning, impact, smoke, auras, projectiles and pickups | Transparent sprite sheets + exact JSON metadata; pixel art | CC0 1.0 according to creator; commercial use and modification allowed, no credit required. | https://nerijs.itch.io/pvfx-foundry |
| Free Winter Pixel Effects | Snowfall, blizzard, ice trail, aurora swirl, frost aura, ice shatter, frost burst, icicle strike | 16 transparent PNG sheets, JSON frame maps; pixel art | CC0 according to creator; commercial use, no attribution required. | https://cs5475.itch.io/free-winter-pixel-effects |
| pixelfx.io | Blizzard vortex, freeze, frost breath, ice crystal, ice lance, ice shatter, ice spikes, icicle fall, snowflake burst, portal and more | 244 transparent PNG sprite sheets + animation JSON; pixel art | Upstream pixelfx effects are MIT; keep required copyright/license notice. Verify individual downloadable content and include the MIT notice. | https://pixelfx.io/effects/ |
| Tiddybub 2D assets | Particle pack, flare, smoke particles, thick fog, light masks and other effect packs | Large curated repository of 2D art | Repository claims CC0 for included packs and retains per-pack SOURCE.md; inspect each source record. | https://github.com/Tiddybub/2d-assets |

## Priority C — additional elemental / magic effects

| Source | Relevant contents | Format / style | License notes | Link |
|---|---|---|---|---|
| Spell FX Free | Ice bolt and ice impact, plus fire and arcane | Transparent PNG sheets in 256px, 128px HD and 64px pixel styles; JSON metadata | CC BY 4.0; credit required: “Spell FX Free by heyheythere - https://heyheythere.itch.io/spell-fx-free - CC BY 4.0”. | https://heyheythere.itch.io/spell-fx-free |
| Free Spell VFX Sampler | Frost Rune Circle, Flame Spiral, Storm Spiral | 128px transparent PNG loops, 8 frames each | Creator states personal and commercial use, modification allowed, no credit required. | https://gegx.itch.io/free-spell-vfx-sampler |
| Elemental Magic VFX LITE | Water shield, arcane circle, electric burst, wind buff and more | HD 192px and pixel 64px transparent sheets; individual frames | Creator states royalty-free commercial/non-commercial use; no standalone resale or redistribution. | https://walrussoundqueen.itch.io/elemental-magic-vfx-lite |
| FreePixel Effects & Particles | Ice shards, lightning, smoke, impact bursts, magic | 100 transparent 200×200 PNG assets | Site states commercial use and no attribution required. Confirm page/download terms when acquiring. | https://freepixel.art/browse/effects |
| KanaWorks SAKUGA VFX | Hand-drawn elemental effects; potentially useful energy and magic layers | Straight-alpha PNG flipbook atlases, 2048/1024, JSON metadata | Free for individuals and companies under USD 1M annual revenue with credit, per project license. | https://github.com/kanaworksai-hue/kanaworks-sakuga-vfx |

## Style warning for SnapBoo

SnapBoo's approved look is saturated, glossy, stylized 3D/cartoon with strong blue/purple/orange accents. Most of the sprite-sheet packs above are pixel art. They are useful for prototypes, small particles, sparks, snowflakes and secondary accents, but **should not automatically become the main portal**. The hero portal should be selected from a suitable 3D/VFX source or built procedurally in Blender, then color-matched to the game.

## Download and import checklist

For each candidate we actually download:
1. Save the original ZIP and its license file.
2. Record title, creator, URL, download date, license, commercial-use permission, attribution and redistribution limits.
3. Inspect the actual PNG alpha channel; a transparent-looking preview is not proof of alpha.
4. Check frame dimensions, frame count, pivot/origin and frame rate.
5. Preview on both dark and bright arena photos.
6. Convert only after keeping an untouched original.
7. Keep source assets separate from optimized game exports.

## GitHub storage rule

Do not commit a source pack merely because the repository is private. Commit raw assets only when their license permits storage/copying in a private repository. If a creator prohibits redistribution, keep the original locally and commit only our own derived exports if the license permits that use, plus the source/license record.

## Current status

- This file is a curated source shortlist; it does not mean the listed ZIPs have been downloaded.
- No third-party binaries are currently included in this repository.
- Blender renders and Android runtime tests are still pending.
