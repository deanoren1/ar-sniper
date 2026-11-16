# Android integration notes — Frozen Tundra transition

## Runtime sequence (target: about 2 seconds)

The user's arena photo remains the background for the entire transition. Freeze aiming/shooting input and optionally fade the HUD.

| Relative time | Layer/action |
|---|---|
| 0.00–0.35 s | Blue/cyan color grade and sparse frost particles |
| 0.20–0.80 s | Localized ice-crack overlay; do not assume the photo has a floor plane |
| 0.35–1.30 s | Snow/frost motes move inward toward the portal center |
| 0.45–1.65 s | Portal sprite/flipbook grows from small to full size |
| 1.35–1.90 s | Ice shards, short energy pulse and controlled white/cyan flash |
| 1.90–2.00 s | Match-cut to the existing boss-intro video's first frame |

The existing ~7-second boss intro video is retained unchanged.

## Layer order

1. Player photo (unchanged source image)
2. Optional blue tint/vignette
3. Ice cracks (alpha overlay, positioned by gameplay logic; optional if no reliable surface anchor)
4. Ambient snow particles (runtime particles preferred)
5. Portal loop/one-shot flipbook (alpha blend)
6. Ice shards (alpha blend)
7. Flash (brief, avoid full-white overexposure)

## Performance

- Start with 512px or 1024px atlases; do not assume 4K flipbooks are suitable for mobile.
- Use premultiplied-alpha consistently or straight-alpha consistently; do not mix without conversion.
- Use linear filtering for smooth edges and clamp atlas sampling to each frame's UV bounds to prevent bleeding.
- Pool particle objects; avoid allocating per frame.
- Pause/stop one-shot animations after completion.
- Profile on the Galaxy A54: frame time, memory, texture upload, and thermal behavior.
- Keep a reduced particle count and smaller atlas as a fallback quality tier.

## Arbitrary-photo safety

- Never place cracks as if every photograph contains a flat ground plane.
- Treat the portal as a screen-space or scene-anchor effect unless Scene Analysis supplies a trustworthy position/depth.
- Do not hide people/objects by painting over the source image.
- Keep the original photo untouched; effects are composited at runtime.

## Acceptance checklist

- [ ] PNG frames have alpha (transparent corners verified).
- [ ] First frame starts small; last frame reaches the boss-intro match size.
- [ ] No green fringe, black fringe, or dark premultiplication halo.
- [ ] One-shot ends cleanly; loopable ambience loops without a visible pop.
- [ ] Atlas metadata matches the actual grid and frame dimensions.
- [ ] Test on at least one bright, one dark, and one cluttered player photo.
- [ ] Test at native device resolution and reduced quality.
