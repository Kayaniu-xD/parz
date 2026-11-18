# Parz

Parz is a client-side [Fabric](https://fabricmc.net/) mod with utilities for **Hypixel SkyBlock**. It adds a book-style
settings menu, a dashboard for your skills and collections, a customised `/pets` menu, a dungeon map and a few
dungeon helpers. It only changes what you see on your own screen: it never sends anything to the server that the
normal game would not send, and it only reads information that the game has already loaded.

## Requirements

| | |
|---|---|
| Minecraft | 26.2 or 26.3 (one jar per version) |
| Fabric Loader | 0.19.0 or newer |
| Fabric API | required |
| Java | 25 or newer |

## Installation

1. Install Fabric Loader and [Fabric API](https://modrinth.com/mod/fabric-api) for your Minecraft version.
2. Download the jar that matches your Minecraft version from the
   [releases page](https://github.com/Kayaniu-xD/parz/releases): `parz-<version>-mc26.2.jar` or
   `parz-<version>-mc26.3.jar`.
3. Put it into your `mods` folder and start the game.

## Updating

Parz updates itself from the GitHub releases.

- When the game starts it checks for a newer version and, if there is one, shows a chat message you can click.
- `/pz update` downloads the newest jar for your Minecraft version and swaps it with the old one in the `mods`
  folder. Restart the game afterwards.
- Both the start-up check and the chat message can be turned off in the **Update** category of the menu.

## Commands

| Command | What it does |
|---|---|
| `/pz` | Opens the settings menu |
| `/pz dash` | Opens the dashboard (skills, collections, playtime) |
| `/pz hud` | Opens the HUD editor to move and resize the HUD elements |
| `/pz dungeon` | Prints what the dungeon scanner currently sees (useful for bug reports) |
| `/pz update` | Installs the newest version from GitHub |

## How it works

### Only on SkyBlock
By default every Parz feature is only active while you are on Hypixel SkyBlock (setting **Only on SkyBlock**).

### Settings menu (`/pz`)
The settings are shown as an open book. The categories are on the left, the options of the selected category on the
right. All settings are saved in `config/parz.json`.

The glass pane in the top left corner of the `/skyblockmenu` can be replaced by a button that opens this menu
(setting **SkyBlock Menu Button**).

### Dashboard and HUD trackers (`/pz dash`, `/pz hud`)
The dashboard lists your SkyBlock skills and collections as cards. The data comes from the menus of the game:
open `/skills` and `/collections` once and Parz reads them. Afterwards:

- **Live collections**: collections go up while you play. Parz counts items you pick up and items that go into
  your sacks (read from the `[Sacks]` chat message). Enchanted items count as the items they are made of.
- **Subtract lost items**: a collection goes down again when items disappear from your inventory or sacks.
  Items sold to the Bazaar or an NPC, and items used in menus, are not subtracted.
- **Boss collections** also go up when a dungeon run ends, based on the completion message in the chat
  (for example `Master Mode - M7` followed by `Defeated ... in ...`). A boss collection only counts up once you have
  opened it in `/collections` at least once.
- **Skill XP per hour** is calculated from the XP you gain while you play.
- **Playtime** per world is tracked as well.

Click a card to pin it. Pinned skills and collections are drawn on your screen as HUD trackers. `/pz hud` opens the
editor: drag an element to move it, use the mouse wheel to resize it. A grid helps you to line things up; elements
snap to the grid, to the screen edges and to the screen center, and a cyan line shows what they are aligned to.

Dashboard data is saved in `config/parz-dashboard.json`. If you start the game with another Minecraft account, the
data of the previous account is removed.

### Custom pets menu (`/pets`)
The vanilla `/pets` chest menu is shown as a book (it can be switched off in the settings):

- Pet list on the left with the level, name, favourite star and a green gem for the active pet.
- Details of the selected pet on the right (its normal tooltip) and the buttons of the original menu, sorted into
  groups: pet actions (Despawn, Convert to item, Exp Sharing), navigation (Previous / Next Page, Go Back, Close,
  Search, Sort) and the rest (Pet Slots, Autopet, Pet Score Rewards, Pet Visibility). Every button stays at its own spot.
- One click selects a pet, a double click summons or despawns it.
- Favourite pets are listed first. The equipped pet is shown on the first page, at the top, under the heading
  **Equipped Pet**, even if it is on another page. A pet from another page can only be looked at there; to despawn
  it, open its page.
- **Pet Collection**: a second view that lists every pet of the game with the highest rarity it can have. Pets you
  own show the best rarity you have; missing pets are shown as grey dye. It can be filtered (all / missing / not at
  max rarity) and sorted by rarity. Parz learns your pets from every page of the menu you open, so open each page
  once. Pets you sell disappear from the list again when the page is read next time.

### Dungeons
- **Dungeon Map**: shows every room of the dungeon on your screen from the very start, also rooms you have not
  entered. The map is not read from the Magic Map item in your hotbar: Parz scans the blocks that are already loaded
  in your game and recognises each room by its blocks. The entrance is always at the bottom. Rooms show their name
  and the number of secrets, and their state (not entered / entered / cleared / completed / failed). A footer shows
  score, secrets, crypts and deaths. Move and resize the map with `/pz hud`.
- **M7 Dragon Circles**: in the dragon phase of the Master Mode 7 boss fight, a circle is drawn on the neck of each
  of the five dragons. The circles are fixed in the world and stay for the rest of the fight. If they disappear
  anyway, the chat says why.

### Other
- **Remove Blindness** hides the black fog of the blindness effect. The effect itself stays, so you still can not
  sprint.

## Credits and licenses

- The list of pets comes from the [Hypixel SkyBlock wiki](https://hypixel-skyblock.fandom.com/).
- Parz is not affiliated with Hypixel or Mojang.

Parz itself is not released under an open source license (all rights reserved).
