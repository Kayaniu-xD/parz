// Parz registry: a tiny Cloudflare Worker that remembers which Minecraft accounts run Parz right now, how
// they want their name to look and how they chose to show their armor (a head and colors).
//
//   POST /register   {"name": "Pay2Loze", "uuid": "<uuid>", "format": "<name format, optional>",
//                      "armor": "<head texture>|<chest>|<legs>|<feet>, optional"}      -> remembered 7 minutes
//   GET  /users       -> {"users": [...], "formats": {...}, "armors": {"Pay2Loze": "<head>|ff5555||"}}
//
// The mod registers itself every 3 minutes while you are in a world (and at once when you change your name
// format or armor) and reads the list every minute. The Worker keeps the list in memory for 30 seconds, so it does
// not list the storage for every request. Stored: the name, the UUID, the name format (a short
// list of colored text pieces) and the armor (the texture hash of a head and three 6-digit colors), nothing else (no IP address, no server, no chat). Entries
// disappear by themselves after 7 minutes without a new registration.

const TTL_SECONDS = 420;
const MAX_FORMAT = 700; // KV metadata is limited to 1024 bytes in total
// "<head texture hash, up to 64 hex>|<chest>|<legs>|<feet>", every part may be empty; a color is 6 hex digits or
// ~name for an animated dye (~aurora)
const ARMOR_PATTERN = /^[0-9a-f]{0,64}\|(?:[0-9a-f]{0,6}|~[a-z_]{1,24})\|(?:[0-9a-f]{0,6}|~[a-z_]{1,24})\|(?:[0-9a-f]{0,6}|~[a-z_]{1,24})$/;

// Cloudflare counts every listing of the storage, so the finished answer of /users is remembered for a short time.
// Every instance of the Worker has its own copy; a change shows up at most this much later.
const LIST_CACHE_MS = 30_000;
let cached = null; // { body, at }

// The answer of /users, read from the storage.
async function readUsers(env) {
	const users = [];
	const formats = {};
	const armors = {};
	let cursor;
	for (let page = 0; page < 10; page++) {
		const result = await env.USERS.list({ prefix: "u:", cursor });
		for (const key of result.keys) {
			if (key.metadata && key.metadata.name) {
				users.push(key.metadata.name);
				if (key.metadata.format) {
					formats[key.metadata.name] = key.metadata.format;
				}
				if (key.metadata.armor) {
					armors[key.metadata.name] = key.metadata.armor;
				}
			}
		}
		if (result.list_complete) {
			break;
		}
		cursor = result.cursor;
	}
	return JSON.stringify({ users, formats, armors });
}

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
			let armor = body.armor == null ? "" : String(body.armor).toLowerCase();
			if (!/^[A-Za-z0-9_]{1,16}$/.test(name) || !/^[0-9a-f]{32}$/.test(uuid)) {
				return new Response("bad data", { status: 400 });
			}
			if (format.length > MAX_FORMAT) {
				format = "";
			}
			if (!ARMOR_PATTERN.test(armor) || armor === "|||") {
				armor = "";
			}
			await env.USERS.put("u:" + uuid, name, { expirationTtl: TTL_SECONDS, metadata: { name, format, armor } });
			return new Response("ok");
		}

		if (url.pathname === "/users" && request.method === "GET") {
			let answer = cached;
			if (!answer || Date.now() - answer.at >= LIST_CACHE_MS) {
				const at = Date.now();
				answer = cached = { body: await readUsers(env), at };
			}
			return new Response(answer.body, {
				headers: { "content-type": "application/json", "cache-control": "public, max-age=30" }
			});
		}

		return new Response("Parz registry", { status: 200 });
	}
};
