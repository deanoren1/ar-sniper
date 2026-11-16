# SnapBoo VFX — Frozen Tundra

This folder contains the visual-effects work for the Frozen Tundra boss transition.

## Planned asset groups

- `Portal/` — animated icy portal and vortex
- `Snow_Particles/` — snow, frost dust, and inward particle motion
- `Ice_Cracks/` — transparent overlays for the arena photo
- `Ice_Shards/` — one-shot ice bursts and fragments
- `Flash/` — transition flash and energy accents
- `Blender/` — Blender source files and Python render scripts
- `Exports/` — transparent PNG sequences and sprite sheets

## Technical targets

- Preserve alpha transparency wherever possible.
- Keep effects as separate layers for runtime positioning and timing.
- Design for arbitrary player-captured arena photos; do not bake in a fixed background.
- Target Android performance, starting with 1024px assets and profiling on the Galaxy A54.
- Keep loopable ambience separate from one-shot transition effects.

## Initial transition timing

Target approximately 2 seconds from the player's photo arena into the portal, followed by the existing approximately 7-second boss intro video. The boss intro video is not to be modified.

## Licensing

Record the source, license, attribution requirements, and commercial-use permissions for every third-party asset before adding it to the project. Do not commit assets until their license is verified.
