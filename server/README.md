# Parz registry (optional server)

Parz shows the `[ዞ]` badge in the tab list and above the head of every player who uses the mod. A mod cannot see
that on its own, so every Parz client reports its own account to this tiny registry and reads the list of the others.

## Set it up (once, free)

1. Install Node.js, then in this folder: `npx wrangler login`
2. `npx wrangler kv namespace create USERS` and put the printed `id` into `wrangler.toml`
3. `npx wrangler deploy` - it prints the address, for example `https://parz-registry.yourname.workers.dev`
4. If you host your own copy, put its address into `REGISTRY_URL` in `src/main/java/net/parz/badge/ModUsers.java`
   (for a quick test you can instead write it into `config/parz-registry.txt` of one Minecraft instance).

## Item and armor looks

`POST /looks` stores, per account, the looks a player chose for single items and armor pieces: the SkyBlock ID of each
item (its own unique ID and its kind) with the chosen name, model, glint, head or color. They are kept for 30 days after
they were last sent, so they also show while the player is offline, and are deleted when the player removes all of them.
`GET /looks` lists who shares looks, `GET /looks/<uuid>` returns one player's. Like the rest of the registry it is
public and has no login, so anybody who knows the address can read it and send entries.

## What is stored

The Minecraft name and UUID of accounts that run Parz, with their chosen name format and current armor look, for 7
minutes after their last report, and the item and armor looks described above for 30 days. No IP address, no server
name, no chat. `GET /users` lists the names that are online right now.
