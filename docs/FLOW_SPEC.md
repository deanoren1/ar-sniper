# SnapBoo: game flow, progression and economy (agreed 2026-11-13)

Design source: the mock-up set "Main / Choose your game / Journey / World detail / Classic / Create AI world / Cards / My Worlds / Multiplayer / Armory / Settings".
The mock-up is a visual direction; this file holds the rules we agreed. Where they differ, this file wins.

## 1. Screen flow
1. **MAIN**: big PLAY, below it four icon buttons with no labels (Multiplayer, Armory, Settings, Stats), top right: coins, gems and a green + that opens the shop.
   - First launch: PLAY opens CHOOSE YOUR GAME.
   - After a journey was started: PLAY continues the journey and shows the current world's name on the button ("CONTINUE JOURNEY - World 3: Ice"); it opens that world's detail screen (section 1.4). A GAME MODES button below it opens CHOOSE YOUR GAME.
2. **CHOOSE YOUR GAME** (modes): WORLD JOURNEY, CLASSIC, RANDOM WORLD, MY WORLDS.
3. **WORLD JOURNEY** map (17 motifs; launch with 5-6, the rest "coming soon"). World 1 = CLASSIC ("Home Base", the player's real environment), world 2 = ICE (Tundra), then the rest. World 1 uses a ready-made official sample room by default so a new player can start without taking a photo (camera permission, no suitable place); the player can switch to their own photo. Stars rules (section 2) are the same for every world.
4. **WORLD DETAIL** (a motif): PLAY (ready-made official world, free), CREATE MY <motif> (uses 1 AI card), MY <motif> WORLDS (saved worlds).
5. **CLASSIC**: TAKE PHOTO / FROM GALLERY / MY PHOTOS, then PLAY on the real environment (no AI). After a Classic win: offer "Play this room as a <motif>?" (uses 1 AI card).
6. **CREATE AI WORLD**: selected photo, choose theme, CREATE WORLD (1 card). Shows AI NEW / AI READY.
7. **RANDOM WORLD** (community worlds; coins only), **MY WORLDS** (all saved worlds, filter by theme).
8. **MULTIPLAYER**: Co-op as built (2 players, host picks RANDOM WORLD / MY WORLDS / MY PHOTOS, camera/gallery inside). PVP: hidden or "coming soon".
9. Armory, Settings, Stats as today.

## 2. Progression (journey)
- Stars come from performance only, never from the world: 1 star = all waves cleared, 2 = at least 2 hearts left, 3 = all hearts left (hearts-based, as decided earlier).
- Stars belong to the motif (best result kept), earned in the journey on the official world, a world the player created, or a saved world.
- **1 star unlocks the next world.**
- **Chapter gate**: the journey is divided into chapters; moving to the next chapter also needs a total number of stars. Each gate is about 60% of the maximum stars available before it (3 per world), so it is reachable without replaying everything. With 3 chapters of 6 worlds: chapter 2 needs **11 stars** (of 18), chapter 3 needs **22 stars** (of 36). Journey order: section 10.
- RANDOM WORLD: coins/gems only, no stars. Classic is journey world 1 and earns stars like any other world (stars come from hearts left, so the player's room does not change the star rules). Panorama games (section 5) earn rewards only, no journey stars.
- Replays always pay coins, so replaying for stars is worth it.

## 3. Resources
- **Coins**: earned in play; weapons and upgrades.
- **Gems**: rare, premium: revive, premium weapons and skins. Never used for AI worlds.
- **AI cards** (separate resource): 1 card = 1 AI world creation. Every new player gets cards for 2 worlds. More: bought in the shop (packs of 1 / 3 / 10; prices come from Google Play, not hard-coded). Classic, Random and My Worlds never consume cards. No ads yet.
- Server must hold card balances and verify purchases (purchase token), tied to a player identity that survives reinstall (to be designed: Google sign-in). Starter cards are granted once per identity, server side.

## 4. Audience and policy
- Store audience: 13+.
- Community worlds: explicit opt-in sharing, EXIF stripped, 3 reports hide a world, report and block creator, creator can unshare, admin page.

## 5. Panoramas (180 / 360)
- Panoramas are a capture option of CLASSIC (the real environment): TAKE PHOTO, PANORAMA 180, PANORAMA 360, FROM GALLERY, MY PHOTOS. They are not part of the journey and give rewards only.
- Panoramas exist only in CLASSIC (solo) and CO-OP; never in the journey or PVP.
- 180: Classic works today, also as an AI world (made in tiles; costs 1 card). 360: Classic only for now (no AI world yet).
- Co-op with a panorama is built later: it needs a partner view and weapon sync, tested on two phones (not built yet).
- No PVP.

## 6. Gyro aiming in the normal arena
- Panorama modes (180/360) use the gyro for the view direction (already).
- Normal arena: optional "Gyro aiming" (default OFF), with sensitivity and a re-center button.
- Where: Settings (toggle + sensitivity), pause menu (quick toggle), and a one-time tip chip on the Classic "ready to play" screen.
- Built: Settings toggle + LOW/MID/HIGH sensitivity, pause menu toggle + RE-CENTER AIM. Joystick keeps working alongside. Tip chip not built yet.

## 7. Build order
1. Shared world picker (also used by Co-op): returns a world; solo/co-op continue from it.
2. World detail screen (motif) with its three options.
3. Journey map and saved progress (stars, unlocks, chapter gates).
4. Arena clear screen: stars, rewards, PLAY AGAIN, NEXT WORLD.
5. AI cards: server balance, purchase verification, starter grant, shop screen.
6. Gyro aiming option.

## 8. Open items
- Where NEXT WORLD leads after a Classic win.
- XP/levels: not in the first version.

## 9. The 17 motifs (list from Dean, 2026-11-13)
Industrial Warehouse, Jungle, Military Base, Frozen Tundra, Ruins, Urban Bazaar, Cyberpunk, Rocky Forest, Candy World, Volcano, Desert, Underwater, Space, Haunted World, Toy Factory, Cloud Kingdom, Crystal Cave.
- Already in code (8): Industrial, Jungle, Military, Tundra, Ruins, Bazaar, Cyber, Forest (shown as "Rocky Forest"). Classic is journey world 1 ("Home Base") and is not one of the 17.
- Not in code yet (9): Candy, Volcano, Desert, Underwater, Space, Haunted, Toy Factory, Cloud Kingdom, Crystal Cave (each needs an ArenaStyle, server AI style, tint/particles, map position).

## 10. Journey order (agreed 2026-11-16)
18 worlds (Classic + the 17 motifs), 3 chapters of 6. From familiar to fantasy; warm/cold and nature/city alternate.
- **Chapter 1 - Real World** (all already in code; launch content): 1 Classic (Home Base), 2 Frozen Tundra, 3 Jungle, 4 Industrial Warehouse, 5 Cyberpunk, 6 Military Base.
- **Chapter 2 - Lost Lands** (gate: 11 stars): 7 Ruins, 8 Rocky Forest, 9 Urban Bazaar, 10 Desert (new), 11 Volcano (new), 12 Underwater (new).
- **Chapter 3 - Fantasy** (gate: 22 stars): 13 Haunted World, 14 Crystal Cave, 15 Candy World, 16 Toy Factory, 17 Cloud Kingdom, 18 Space (finale). All new.
- Worlds not in code yet appear on the map as "coming soon" (locked).
