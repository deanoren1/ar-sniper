# Frozen Tundra — external asset sources

Research checked 16 November 2026. These are source links and acquisition notes, not files bundled in this repository. Download assets directly from their creators, retain the original license evidence, and do not commit third-party raw files unless redistribution is explicitly permitted.

## 1. CGHEVEN — primary library to inspect

- Library: https://cgheven.com/assets
- Blender add-on: https://cgheven.com/addons/blender
- License: https://cgheven.com/licensing
- The provider states that its free tier is CC0 and includes VFX, flipbooks, 3D models, VDBs, HDRIs and shaders at 1K/2K.
- Its Blender add-on page lists Blender 4.0, 4.1 and 4.2 support; check the current version before installing.
- Useful search terms: `Magic Portal`, `Portal`, `Energy`, `Ice`, `Snow`, `Vortex`.
- Candidate named by the provider's magic-VFX article: **Magic Portal 04 – Front**, described there as free/CC0. Confirm that the specific item is still marked FREE on the download page before acquiring it.
- The provider's licensing page says finished projects such as games may use assets, but raw files may not be shared, resold or re-uploaded. This repository therefore stores source links and notes only.

## 2. SEAMPROOF — free ice impact sample

- Pack/sample page: https://seamproof.itch.io/ice-frost-vfx
- The creator states that the free sample **Ice Shatter in Glacier** includes 48 native 4K frames, sprite sheets, loose frames, preview video, metadata, viewer and engine helpers.
- The page states commercial use is included and attribution is optional; redistribution of the raw asset pack is prohibited.
- Download directly from the creator. Do not mirror the raw files in this repository.

## 3. Acquisition checklist

For each downloaded item, save a local record of:
- Exact asset title and creator
- Source URL and download date
- License name and a copy/screenshot of the applicable license
- Whether commercial use is allowed
- Whether attribution is required
- Whether redistribution is prohibited
- Format, dimensions, frame count, alpha support and intended blend mode

## Integration rules

- Prefer RGBA/alpha assets. Verify alpha in the actual downloaded file; a page preview is not proof that the downloadable format has alpha.
- For alpha video, confirm the codec and alpha channel survive the Android decode path before adopting it.
- If only a green-screen MP4 is available, treat it as a fallback and test chroma-key edges against bright snow and cyan portal highlights.
- Keep all effects independent of the user's arena photo.
- Select only assets explicitly marked FREE. Do not assume every asset in a free library is free.
