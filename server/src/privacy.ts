/** עמוד מדיניות הפרטיות הציבורי (נדרש לגוגל פליי). כתובת הקשר נקבעת כאן. */
export const CONTACT_EMAIL = "__CONTACT_EMAIL__";

export const PRIVACY_HTML = `<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>SnapBoo – Privacy Policy</title>
<style>
body{margin:0;padding:24px 16px;background:#fff;color:#1b1f2a;font:16px/1.6 system-ui,sans-serif}
main{max-width:760px;margin:0 auto}h1{font-size:28px}h2{font-size:20px;margin-top:28px}
li{margin:4px 0}.mut{color:#5b6475}
@media(prefers-color-scheme:dark){body{background:#10141c;color:#e8edf7}.mut{color:#9aa5bb}a{color:#6db4ff}}
</style></head><body><main>
<h1>SnapBoo – Privacy Policy</h1>
<p class="mut"><b>Effective date:</b> November 8, 2026</p>
<p>SnapBoo ("the game", "we") turns photos you take into playable arenas. This policy explains what information the game uses, where it goes, and how you can control it.</p>

<h2>1. Summary</h2>
<ul>
<li>Photos you take stay on your device unless you choose an AI feature or share an arena.</li>
<li>Detecting and removing people from a photo happens on your device.</li>
<li>AI arena generation and community sharing send a photo to our server.</li>
<li>We do not sell personal information and we do not use advertising or analytics SDKs in this version.</li>
</ul>

<h2>2. Information the game uses</h2>
<p><b>Camera and photos.</b> The game asks for camera access so you can photograph a place. The photo becomes the background of an arena and is stored on your device. Finding and removing people in the photo is done on your device.</p>
<p><b>Microphone.</b> The microphone permission is used only for voice chat in co-op mode. Voice is relayed in real time to the other player in your room and is not recorded or stored by us. You can turn the microphone off in the game or in your device settings.</p>
<p><b>Notifications.</b> Used to show the status of an arena that is being generated in the background.</p>
<p><b>Random device identifier.</b> The game creates a random identifier on first use. It is not your advertising ID and is not linked to your name, email or phone number. We use it to count your AI creations (daily limits and credits), to prevent duplicate charges for the same photo and arena, and to recognize your own shared arenas.</p>
<p><b>Gameplay data.</b> Settings, scores, coins, diamonds, unlocked arenas and similar progress are stored on your device.</p>

<h2>3. Features that send data to our server</h2>
<p>Our server runs on Cloudflare.</p>
<p><b>AI arena generation.</b> When you create an AI arena, the photo is sent to our server and from there to an image-generation provider (currently Muapi, and in some cases OpenAI) that returns the themed image. We do not send your name, email or other identifiers with the photo. The generated image may be cached on our server for up to 60 days so that repeating the same request does not create a new charge. The providers process the image under their own terms and privacy policies.</p>
<p><b>Community arenas.</b> Nothing is published unless you choose to share an arena. Sharing uploads the finished arena image and the analysis data of the source photo (positions of objects used to place monsters) to our storage. Shared arenas can be played by other players, reported, and hidden or removed if they are inappropriate. Please do not share photos that show identifiable people, private places, license plates or other sensitive content, and do not share photos you do not have the right to share. You can remove an arena you shared at any time from the game.</p>
<p><b>Co-op.</b> Joining a room sends game state (such as positions, shots and room settings) and voice chat to the other player through our server for the duration of the session.</p>

<h2>4. How long we keep information</h2>
<ul>
<li>Photos and progress on your device stay until you delete them or uninstall the game.</li>
<li>Cached AI results: up to 60 days.</li>
<li>Shared community arenas: until you remove them or we remove them for violating our rules.</li>
<li>Co-op session data and voice: only while the session is active.</li>
</ul>

<h2>5. Sharing</h2>
<p>We share data only with the service providers that run the features above (Cloudflare for hosting and storage, and the image-generation providers for AI arenas), or when required by law. We do not sell personal information.</p>

<h2>6. Your choices</h2>
<ul>
<li>You can deny or revoke camera, microphone and notification permissions in your device settings. The related features will stop working.</li>
<li>You can delete local data by clearing the game's storage or uninstalling it.</li>
<li>You can remove arenas you shared from within the game.</li>
<li>To ask us to delete data associated with your device identifier, or for any question about this policy, contact us at the address below.</li>
</ul>

<h2>7. Children</h2>
<p>SnapBoo is not directed to children under 13, and we do not knowingly collect personal information from them. If you believe a child has sent us information, contact us and we will delete it.</p>

<h2>8. Security</h2>
<p>Data sent to our server is transmitted over HTTPS. No method of storage or transmission is completely secure.</p>

<h2>9. Changes</h2>
<p>If we add features such as advertising, purchases or analytics, we will update this policy before they are released and change the effective date above.</p>

<h2>10. Contact</h2>
<p><a href="mailto:${CONTACT_EMAIL}">${CONTACT_EMAIL}</a></p>
</main></body></html>`;
