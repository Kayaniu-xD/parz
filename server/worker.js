// Parz registry: a tiny Cloudflare Worker that remembers which Minecraft accounts run Parz right now, how
// they want their name to look and what color they chose for their (leather) armor.
//
//   POST /register   {"name": "Pay2Loze", "uuid": "<uuid>", "format": "<name format, optional>",
//                      "armorColor": "<6 hex digits, optional>"}                        -> remembered 7 minutes
//   GET  /users       -> {"users": [...], "formats": {...}, "armorColors": {"Pay2Loze": "ff5555"}}
//
// The mod registers itself every 3 minutes while you are in a world (and at once when you change your name
// format or armor color) and reads the list every minute. Stored: the name, the UUID, the name format (a short
// list of colored text pieces) and the armor color, nothing else (no IP address, no server, no chat). Entries
// disappear by themselves after 7 minutes without a new registration.

const TTL_SECONDS = 420;
const MAX_FORMAT = 700; // KV metadata is limited to 1024 bytes in total

export default {
	async fetch(request, env) {
		const url = new URL(request.url);

		if (url.pathname === "/register" && request.method === "POST") {
			let body;
			try {
				body = await request.json();
			} catch (e) {
				return new Response("bad json", { status: 400 });
			}
			const name = String(body.name || "");
			const uuid = String(body.uuid || "").replace(/-/g, "").toLowerCase();
			let format = body.format == null ? "" : String(body.format);
			let armorColor = body.armorColor == null ? "" : String(body.armorColor).toLowerCase();
			if (!/^[A-Za-z0-9_]{1,16}$/.test(name) || !/^[0-9a-f]{32}$/.test(uuid)) {
				return new Response("bad data", { status: 400 });
			}
			if (format.length > MAX_FORMAT) {
				format = "";
			}
			if (!/^[0-9a-f]{6}$/.test(armorColor)) {
				armorColor = "";
			}
			await env.USERS.put("u:" + uuid, name, { expirationTtl: TTL_SECONDS, metadata: { name, format, armorColor } });
			return new Response("ok");
		}

		if (url.pathname === "/users" && request.method === "GET") {
			const users = [];
			const formats = {};
			const armorColors = {};
			let cursor;
			for (let page = 0; page < 10; page++) {
				const result = await env.USERS.list({ prefix: "u:", cursor });
				for (const key of result.keys) {
					if (key.metadata && key.metadata.name) {
						users.push(key.metadata.name);
						if (key.metadata.format) {
							formats[key.metadata.name] = key.metadata.format;
						}
						if (key.metadata.armorColor) {
							armorColors[key.metadata.name] = key.metadata.armorColor;
						}
					}
				}
				if (result.list_complete) {
					break;
				}
				cursor = result.cursor;
			}
			return new Response(JSON.stringify({ users, formats, armorColors }), {
				headers: { "content-type": "application/json", "cache-control": "public, max-age=30" }
			});
		}

		return new Response("Parz registry", { status: 200 });
	}
};
