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
  folder. Restart the game afterwards. If you update again before the restart, only the newest download is
  kept.
- Both the start-up check and the chat message can be turned off in the **Update** category of the menu.

## Commands

| Command | What it does |
|---|---|
| `/pz` | Opens the settings menu |
| `/pz dash` | Opens the dashboard (skills, collections, playtime) |
| `/pz hud` | Opens the HUD editor to move and resize the HUD elements |
| `/pz dungeon` | Prints what the dungeon scanner currently sees (useful for bug reports) |
| `/pz nucleus` | Shows the Nucleus slot machine as a test (`/pz nucleus alloy` for the jackpot) |
| `/pz fishing` | Shows the fishing loot memory as a test (`jawbus`, `thunder` or `ragnarok` to pick one) |
| `/pz customname` | Menu to choose how your name looks to other Parz users (also `/pz customname json <text component>` and `/pz customname reset`) |
| `/pz custom` | Menu with two tabs: **Armor** (head and colors per armor piece, seen by other Parz users; also `/pz custom #RRGGBB` for the pieces you wear and `/pz custom reset` for all pieces) and **Item** (name, model and glint of your items) |
| `/pz update` | Installs the newest version from GitHub |
| `/pz help` | Lists all commands in the chat with a short description; click a command to run it |

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
- **Right click** on a pet makes it a favourite (or removes the star). Parz reads the click the server wants for
  that from the tooltip of the pet.
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
  score, secrets, crypts and deaths. Move and resize the map with `/pz hud`. In the boss room the map is hidden.
- **M7 Dragon Arrow Stack**: in the dragon phase of the Master Mode 7 boss fight, a cyan ring appears while a dragon
  is spawning. It shows where to aim your bow so that the arrow lands on the stack position of that dragon, with the
  drop of the arrow included. The ring always faces you, is visible through walls and gets bigger with the distance.
  Once started it stays on for the whole dragon phase, until you leave the dungeon (the sidebar stops showing the
  dungeon during the Wither King fight; that does not switch it off).
  The positions and the calculation are the ones of the Wither Dragons feature of
  [NoammAddons](https://github.com/Noamm9/NoammAddons).

### Crystal Hollows
- **Nucleus Slot Machine**: when you place all crystals and the loot bundle of the Crystal Nucleus appears in the
  chat, a slot machine with three reels spins on your screen and stops on the rarest item you got (rated with the loot
  table of the SkyBlock wiki). If you got a Divan's Alloy, all three reels stop on the alloy, like three sevens.
  Try it with `/pz nucleus` or `/pz nucleus alloy`.

### Fishing
- **Loot Memory**: when Lord Jawbus, Thunder or Ragnarok is dead, a memory board with all the loot it can drop
  appears on your screen, all cards face up. Then every card of an item you did **not** get is turned around; the
  cards of your drops stay open and glow. Parz knows the creature from its spawn message, sees it die when its name
  tag disappears, and reads your drops from the "DROP!" chat lines and from the items that arrive in your
  inventory. Try it with `/pz fishing`.

- **Trophy Froggs**: on the Lotus Atoll, while you hold a water fishing rod, a list of all twelve trophy frogs sorted by
  rarity, with how many you caught and a check mark or a red cross for bronze, silver, gold and diamond. Open the menu
  of **Researcher Ribery** once and Parz reads what you already have (until then unknown tiers show a ?). Every catch
  after that is counted from the "TROPHY FROG!" chat message. The list has no background. With the setting **Hide
  Completed Frogs** a frog disappears from it once you have all four of its trophies. Move and resize the list with `/pz hud`. The numbers are
  saved per Minecraft account in `config/parz-frogs.json`; after switching your SkyBlock profile, open Ribery's menu
  again.

### Other
- **Badge / name**: every player who uses Parz is shown as `[ዞ] Name` (red brackets and name, gold symbol) in the tab
  list, in the scoreboard (for example the party list in a dungeon), in the chat, above the head and in the "Spawned by: Name" line above a slayer boss, so you can see who else has
  the mod. The setting **Custom Names** (`/pz`, General; on by default) switches this off completely on your screen: no name
  is changed, neither yours nor those of others (chat lines that are already there keep their look; others still see your custom name).
  In the tab list and the scoreboard only the drawing is changed: other mods (dungeon maps, for example) still read the
  real names. This covers the tab list and sidebar of the game and the compact tab list of SkyHanni; a mod that draws
  its own scoreboard or tab list from those names shows the real ones.
  With `/pz customname` everybody chooses their own look:
  one text (`{name}` = the real name) with one color for everything or a color for every single letter, or a Minecraft
  text component in JSON (the menu can write the JSON from your letters). The "Presets" button opens a list where you save a look under a name and load it again (saved in `config/parz-badge-presets.json`) (`{name}` stands for the
  real name; at most 48 characters and 16 colored pieces; clicks and hover texts are removed). It is saved in
  `config/parz-badge.json` and stays until you change or reset it. To know who uses Parz and how they want to look, the mod reports your Minecraft **name and
  UUID** and the chosen name format to a small registry (see [server/](server/)) while you are in a world and reads
  the list of the others; nothing else is sent (no IP address is stored, no server, no chat). Entries disappear 7
  minutes after the last report. If the registry is not reachable, only your own account (and `Pay2Loze`) get the badge.
- **Custom armor** (`/pz custom`, tab **Armor**): every choice belongs to **one armor piece**, the one you wear in that
  slot (recognised by the own ID of the SkyBlock item). It shows while you wear that piece; other armor looks normal,
  so every set can have its own colors. The menu has a preview of your player. Pick a slot under the preview and click
  what you want. **Helmet**: any SkyBlock item that is a head (armor heads, minions of every tier, and so on) is shown
  instead of your helmet; the list comes from the public Hypixel items API, is saved in `config/parz-heads.json` and
  can be searched by name. **Chestplate, leggings and boots**: a color, from the palette or typed as `#RRGGBB` in
  the search box (the piece is drawn as leather armor in that color, whatever armor you really wear, SkyBlock armor included).
  The menu lists the dyes of SkyBlock under their real names (Carmine Dye, Necron Dye, Pure White Dye, ...; 42 normal dyes, list of
  the SkyBlock wiki), drawn as the dye items of the game (heads with the texture of the dye). The first entries are the **animated dyes** (Aurora, Rose, Portal, Lucky, ... 27 of them): the piece changes its
  color the way the dye does, for everybody with Parz (the colors are the frames of the public SkyBlock item repository, 20 per second). "Same color on worn pieces" copies the
  color of the selected slot to the other two. Your choice is saved at once in `config/parz-armor.json` and sent to
  the registry, so every Parz user sees it within a minute or two, and it is still there the next time you start the
  game. The registry only stores the texture of the head and the colors. The custom head only replaces a helmet slot
  that is not empty, and your chosen colors win over the dye the armor already has. It also works when the Catharsis mod draws custom SkyBlock armor models: for the slots you chose
  something for, Parz switches those custom models off so your head and colors show instead. A color chosen in Parz
  also wins over a dye you set for the piece in Skyblocker.
- **Custom items** (`/pz custom`, tab **Item**; like the item customization of Skyblocker): **Select Item** picks an
  item of your inventory (only SkyBlock items with an own ID: weapons, tools, armor, accessories). **Glint** switches the
  enchantment glint (Default / On / Off). **Select Model** shows every item model of the game and your resource packs
  and every SkyBlock head; a model id can also be typed into **Item Model Override**. The name editor changes the
  name: bold, italic, underline, strikethrough and obfuscated, the 16 Minecraft colors, any color (**Custom**) and
  gradients; it works on the marked letters, or on the whole name if nothing is marked. An empty name field gives the
  item its normal name back, **Reset item** removes everything. The changes belong to exactly that item, are saved in
  `config/parz-items.json` and are shown **only on your screen** (tooltips, the name above the hotbar, the item in
  menus and in your hand). The real item is not changed and nothing is sent anywhere.
- **Window title** (General settings **Vanilla Window Title** / **Fabric Window Title**): some clients rename the game
  window (for example to "Nebula Client 2.5.10"). Parz forces the title Minecraft itself would use. **Fabric Window
  Title** is on by default and gives the title of a modded game (`Minecraft* 26.2`, the star is added when mods like
  Fabric are installed); turn on **Vanilla Window Title** instead for the plain one (`Minecraft 26.2`). Only one can be
  on; with both off the title is not touched. The part after the version (Singleplayer, Multiplayer, ...) is kept. Parz
  builds the title itself like Minecraft does and puts it on the window several times per second, so it also wins
  against a client that renames the window behind Minecraft's back or replaces Minecraft's own title code.
- **Remove Damage Shake** removes the screen shaking (the camera tilt) when you take damage. The red flash stays.
- **Remove Blindness** hides the black fog of the blindness effect. The effect itself stays, so you still can not
  sprint.

## Credits and licenses

- The list of pets comes from the [Hypixel SkyBlock wiki](https://hypixel-skyblock.fandom.com/).
- Parz is not affiliated with Hypixel or Mojang.

Parz itself is not released under an open source license (all rights reserved).
