# Frozen Tundra — external asset sources

Checked 16 November 2026. These are source links and acquisition notes, not files bundled in this repository. Download and retain the original license/receipt with each acquired asset. Never commit third-party raw files unless the license explicitly permits redistribution.

## Recommended first source: CGHEVEN

- Library: https://cgheven.com/assets
- Blender add-on: https://cgheven.com/addons/blender
- License: https://cgheven.com/licensing
- The free tier is described by the provider as CC0 and includes free 1K/2K VFX, 3D models, VDBs, HDRIs and flipbooks. The add-on supports Blender 4.0, 4.1 and 4.2 according to its add-on page.
- Search terms to use in the library: `Magic Portal`, `Portal`, `Energy`, `Ice`, `Snow`, `Lightning`, `Vortex`.
- Candidate asset named by the provider: **Magic Portal 04 – Front** (listed as free/CC0 in its magic VFX collection).
- Important: select only assets marked FREE in the library. Do not assume that every asset on the site is free. The provider's licensing page prohibits re-uploading raw files; use them locally in the finished game and keep proof of the license.

## Recommended ice impact sample: SEAMPROOF

- Pack/sample page: https://seamproof.itch.io/ice-frost-vfx
- The page states the free sample **Ice Shatter in Glacier** includes 48 native 4K frames, sprite sheets, loose frames, preview video, metadata and engine helpers.
- The page states commercial use is included and attribution is optional; redistribution of the raw asset pack is prohibited.
- Download it directly from the creator's page. Do not mirror the raw files in this repository.

## Integration rules

- Prefer RGBA/alpha assets. For an alpha video, confirm the codec and alpha channel survive the full Android decode path before adopting it.
- If a source supplies only a green-screen MP4, treat it as a fallback and test chroma-key edge quality against bright snow and cyan portal highlights.
- Keep effects independent of the user's arena photo. Never bake a player photo into a reusable effect.
- Record the exact asset title, source URL, date downloaded, license name, and a local copy of the license text in the game's private asset ledger.
