# SnapBoo: game flow, progression and economy (agreed 2026-11-13)

Design source: the mock-up set "Main / Choose your game / Journey / World detail / Classic / Create AI world / Cards / My Worlds / Multiplayer / Armory / Settings".
The mock-up is a visual direction; this file holds the rules we agreed. Where they differ, this file wins.

## 1. Screen flow
1. **MAIN**: big PLAY, below it four icon buttons with no labels (Multiplayer, Armory, Settings, Stats), top right: coins, gems and a green + that opens the shop.
   - First launch: PLAY opens CHOOSE YOUR GAME.
   - After a journey was started: PLAY continues the journey; a GAME MODES button below it opens CHOOSE YOUR GAME.
2. **CHOOSE YOUR GAME** (modes): WORLD JOURNEY, CLASSIC, RANDOM WORLD, MY WORLDS.
3. **WORLD JOURNEY** map (17 motifs; launch with 5-6, the rest "coming soon").
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
- **Chapter gate**: the journey is divided into chapters; moving to the next chapter also needs a total number of stars (example: 20). Set each gate at about 60% of the maximum stars available before it (3 per world), so it is reachable without replaying everything.
- RANDOM WORLD: coins/gems only, no stars. Classic: rewards only, no journey stars.
- Replays always pay coins, so replaying for stars is worth it.

## 3. Resources
- **Coins**: earned in play; weapons and upgrades.
- **Gems**: rare, premium: revive, premium weapons and skins. Never used for AI worlds.
- **AI cards** (separate resource): 1 card = 1 AI world creation. Every new player gets cards for 2 worlds. More: bought in the shop (packs of 1 / 3 / 10; prices come from Google Play, not hard-coded). Classic, Random and My Worlds never consume cards. No ads yet.
- Server must hold card balances and verify purchases (purchase token), tied to a player identity that survives reinstall (to be designed: Google sign-in). Starter cards are granted once per identity, server side.

## 4. Audience and policy
- Store audience: 13+.
- Community worlds: explicit opt-in sharing, EXIF stripped, 3 reports hide a world, report and block creator, creator can unshare, admin page.

## 5. Gyro aiming in the normal arena
- Panorama modes (180/360) use the gyro for the view direction (already).
- Normal arena: optional "Gyro aiming" (default OFF), with sensitivity and a re-center button.
- Where: Settings (toggle + sensitivity), pause menu (quick toggle), and a one-time tip chip on the Classic "ready to play" screen.

## 6. Build order
1. Shared world picker (also used by Co-op): returns a world; solo/co-op continue from it.
2. World detail screen (motif) with its three options.
3. Journey map and saved progress (stars, unlocks, chapter gates).
4. Arena clear screen: stars, rewards, PLAY AGAIN, NEXT WORLD.
5. AI cards: server balance, purchase verification, starter grant, shop screen.
6. Gyro aiming option.

## 7. Open items
- What exactly "CONTINUE JOURNEY" opens: the arena directly (official world) or the motif screen. Proposal: directly into the arena.
- Where NEXT WORLD leads after a Classic win.
- Number of chapters and each gate's star count (after the launch motif list is fixed).
- XP/levels: not in the first version.
