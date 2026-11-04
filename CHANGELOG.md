# Changelog

All notable changes to Arcanic Visuals are listed here, newest first.

## 1.6

- Studio for wings, backpacks and headwear (right click in the wardrobe): size, offsets, tilt and turn.
- Hands in the knife and weapon studio: where the hands sit and how large they are, the left hand on its own.
- The menu moves when dragged by its top bar and keeps its place.
- Trust mode is started from the launcher: modules that act for the player and Lua are off for that game.

## 1.5

- Lua: scripts add their own modules to the menu and HUD elements to the HUD editor, draw on screen and over the
  world, read the player, the world in sight, the inventory and chat, send chat messages and commands, bind keys,
  play sounds, switch and set our modules, change the theme, load configs, keep data, use timers, JSON and HTTPS.
  The reference is API.md next to the scripts; the Lua page is the list of scripts.
- Premium backpacks with exclusive prints and a Labubu on each: Gucci Flora, Louis Vuitton Multicolore, Dior Oblique.
  Their models come from the Arcanic API; only Premium accounts show them to others.
- The sidebar shows the Arcanic account and its plan: Beta, Premium or Free.
- Removed trust mode and the dark/light and wide-menu buttons in the header (themes are in Theme Studio).

## 1.4

- The Arcanic A replaces the cauldron: the mod icon, the intro animation, the menu mark and the nametag badge.
- Theme Studio (Client → Theme Studio): ten ready themes, every menu colour with the colour picker, menu opacity and
  a code to share a theme.
- Lua (Tools → Lua): your own scripts from config/arcanicvisuals/scripts, run in a sandbox.
- World Lyrics: sign in to Yandex Music or Spotify and take the lyrics straight from there, as the first or a second
  source next to lrclib, YouTube Music and NetEase.

## 1.3

- Premium AWP skins: AWP | Asimov, AWP | Dragon Lore and AWP | Information Flow, sharp paint with highlights.
  Their textures come from the Arcanic API to Premium accounts only; without Premium the wardrobe shows them locked.
- Premium keys are gone: the developers give Premium to an Arcanic account.

## 1.2

- FunTime Helper: events of the FunTime servers, item zones and server tools.
- Friends: a friends page, friend cards, their inventory, friend highlight; middle click a player to add or remove.
- Pings: mark a place, an enemy or loot for your friends; an interaction menu.
- World Lyrics: the song playing on your PC sings line by line in the world; when no lyrics are found, Whisper
  recognises the words.
- FastSwap: swap an item for another in the chosen hand on a key.
- No Render is back and covers all particles, screen overlays, camera shake, fog, weather and HUD parts.
- A new crosshair page, chest sorting, slot locking, see-invisible, low fire, interface animations.
- The menu can be resized from its bottom-right corner.
- Translations: the English interface no longer shows Russian text; feature and section names are in English.
- Removed the proxy screen.

## 1.6.7

- Main menu: singleplayer and multiplayer are horizontal cards in the same style as "Visual settings".
- Kill sounds and hit sounds with their own volume; the vanilla hit sound can be muted while a hit sound is on.
- Optimizer module with three profiles (Balance, Max FPS, Ultra); it also switches the bundled optimisation mod.
- Wardrobe: capes, knives, headwear, backpacks, wings and pets, each in its own tab.
- Cosmetics and emotes are shared between players who signed in through the launcher.
- Presets: one row per preset with an active one, "save over it", share by a code and delete; All / Local / Cloud tabs;
  a new preset can be marked as made for the current server.
- The language is picked from a list in the same dialog style.

## 1.6.6

- New singleplayer and multiplayer cards in the main menu: dark surface, accent colours, illustrations that follow
  the cursor.
- Hovering a card no longer moves its neighbours or changes the click area.
- The cards work in Russian and English, in a small window and from the keyboard.

## 1.6.5

- English interface: Settings → English / Русский, applied without a restart.
- Menus, descriptions, settings, modes, HUD and notifications are translated.
- Search finds modules in both languages whatever language is selected.

## 1.6.4

- Dynamic Island rebuilt: compact pill with the clock on the left, FPS and connection on the right.
- Music on the island: cover, title, artist and a player with progress, pause and track switching. Reads the Windows
  media session, so it works with any player that reports there.
- Cloth capes: 16 segments, turning and wind.
- Fixed camera and hand bobbing jumping when walking speed changes.

## 1.6.3

- The off-hand item is held at the end of the visible hand next to a knife; it no longer tilts or drops off screen.
- Fixed the mirrored grip for a left main hand and for a knife in the off hand.

## 1.6.2

- Block highlight follows the block under the crosshair every frame, without delay or sliding between blocks.
- Highlight defaults: 2 px lines, 30% fill, static outline; pulse and wave modes. The vanilla outline is hidden while
  ours is on.

## 1.6.1

- New Dynamic Island: one compact pill, opening animation when you join a world or a server, smooth width changes.

## 1.6.0

- Main menu layout: logo and profile on the left, large game cards on the right, separate visual settings.
- The logo animation plays once at start and stays on its last frame.

## 1.5.0

- New main menu with four shader backgrounds (Ember, Nebula, Silk, Contour) and keyboard focus.
- Local profile manager: add, select and remove offline profiles.

## 1.4.0

- Arcanic logo in the Dynamic Island, the menus and the loading screens.
- Custom resource loading screen with smooth progress.

## 1.3.0

- Animated shader background in the menus and loading screens.
- Target ESP and jump circles draw with their own shader; marker size from 0.1× to 2×.

## 1.2.0

- Module cards stay in their columns; hovering only changes the highlight.
- Compact floating colour picker: hue bar, alpha, pipette, saved colours, copy and paste of hex values.
- Clicking a knife card selects the knife.

## 1.0.0 – 1.1.0

- Animated melee skins with draw, idle, inspect and attack animations, in first and third person.
- Hand and item shaders: 28 modes with colour, speed and density.
- Target ESP styles (Vortex, Brackets, Orbit, Garland, Marker) and jump circle styles (Storm, Pool, Rings, Explosion).
- Modules: BlockOverlay, ProjectileTrails, ElytraTrails, Fireflies, BetterRain, Blizzard, Hitboxes, NameTags,
  TNTTimer, ConsumeESP, Predictions, Weather, Zoom, NoRender.
