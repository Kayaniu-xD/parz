# Parz registry (optional server)

Parz shows the `[ዞ]` badge in the tab list and above the head of every player who uses the mod. A mod cannot see
that on its own, so every Parz client reports its own account to this tiny registry and reads the list of the others.

## Set it up (once, free)

1. Install Node.js, then in this folder: `npx wrangler login`
2. `npx wrangler kv namespace create USERS` and put the printed `id` into `wrangler.toml`
3. `npx wrangler deploy` - it prints the address, for example `https://parz-registry.yourname.workers.dev`
4. If you host your own copy, put its address into `REGISTRY_URL` in `src/main/java/net/parz/badge/ModUsers.java`
   (for a quick test you can instead write it into `config/parz-registry.txt` of one Minecraft instance).

## What is stored

Only the Minecraft name and UUID of accounts that run Parz, for 7 minutes after their last report. No IP address,
no server name, no chat. `GET /users` lists the names that are online right now.
