// Parz registry: a tiny Cloudflare Worker that remembers which Minecraft accounts run Parz right now, how
// they want their name to look and how they chose to show their armor (a head and colors).
//
//   POST /register   {"name": "Pay2Loze", "uuid": "<uuid>", "format": "<name format, optional>",
//                      "armor": "<head texture>|<chest>|<legs>|<feet>, optional"}      -> remembered 7 minutes
//   GET  /users       -> {"users": [...], "formats": {...}, "armors": {"Pay2Loze": "<head>|ff5555||"}}
//   POST /looks       {"name", "uuid", "looks": {"items": {...}, "armor": {...}}}   looks of single items and armor
//                      pieces (by the ID of the item), kept 30 days; without any entry the player's looks are deleted
//   GET  /looks       -> {"players": [{"uuid", "name", "v", "t"}]}   whose looks there are, v changes with them
//   GET  /looks/<uuid> -> {"items": {...}, "armor": {...}}
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

const LOOKS_TTL_SECONDS = 30 * 24 * 60 * 60;
const MAX_LOOKS_BYTES = 64 * 1024;
const MAX_ENTRIES = 200;
const ITEM_UUID = /^[0-9a-fA-F-]{32,36}$/;
const SKYBLOCK_ID = /^[A-Za-z0-9_:;\-]{1,64}$/;
const MODEL = /^[a-z0-9_.\-]{1,64}:[a-z0-9_.\/\-]{1,128}$/;
const HEAD = /^[0-9a-f]{20,64}$/;
const COLOR = /^(?:[0-9a-f]{6}|~[a-z_]{1,24})$/;
let cachedLooks = null; // { body, at }

const isObject = (value) => value !== null && typeof value === "object" && !Array.isArray(value);

// A name as [[text, color, formats], ...], or null.
function cleanName(name) {
	if (!Array.isArray(name) || name.length === 0 || name.length > 120) {
		return null;
	}
	let length = 0;
	const runs = [];
	for (const run of name) {
		if (!Array.isArray(run) || typeof run[0] !== "string" || !Number.isInteger(run[1]) || !Number.isInteger(run[2])) {
			return null;
		}
		length += run[0].length;
		runs.push([run[0], Math.max(-1, Math.min(0xffffff, run[1])), run[2] & 31]);
	}
	return length > 120 ? null : runs;
}

// Only the known fields with valid values are kept.
function cleanLooks(looks) {
	const items = {};
	const armor = {};
	if (isObject(looks) && isObject(looks.items)) {
		for (const [uuid, item] of Object.entries(looks.items).slice(0, MAX_ENTRIES)) {
			if (!ITEM_UUID.test(uuid) || !isObject(item)) {
				continue;
			}
			const entry = {};
			if (typeof item.model === "string" && MODEL.test(item.model)) entry.model = item.model;
			if (typeof item.head === "string" && HEAD.test(item.head)) entry.head = item.head;
			if (typeof item.glint === "boolean") entry.glint = item.glint;
			const name = cleanName(item.name);
			if (name) entry.name = name;
			if (Object.keys(entry).length === 0) {
				continue;
			}
			if (typeof item.id === "string" && SKYBLOCK_ID.test(item.id)) entry.id = item.id;
			items[uuid] = entry;
		}
	}
	if (isObject(looks) && isObject(looks.armor)) {
		for (const [uuid, piece] of Object.entries(looks.armor).slice(0, MAX_ENTRIES)) {
			if (!ITEM_UUID.test(uuid) || !isObject(piece)) {
				continue;
			}
			const entry = {};
			if (typeof piece.head === "string" && HEAD.test(piece.head)) entry.head = piece.head;
			if (typeof piece.color === "string" && COLOR.test(piece.color)) entry.color = piece.color;
			if (Object.keys(entry).length > 0) armor[uuid] = entry;
		}
	}
	return { items, armor };
}

async function saveLooks(request, env) {
	const text = await request.text();
	if (text.length > MAX_LOOKS_BYTES) {
		return new Response("too large", { status: 413 });
	}
	let body;
	try {
		body = JSON.parse(text);
	} catch (e) {
		return new Response("bad json", { status: 400 });
	}
	const name = String((body && body.name) || "");
	const uuid = String((body && body.uuid) || "").replace(/-/g, "").toLowerCase();
	if (!/^[A-Za-z0-9_]{1,16}$/.test(name) || !/^[0-9a-f]{32}$/.test(uuid)) {
		return new Response("bad data", { status: 400 });
	}
	const looks = cleanLooks(body.looks);
	if (Object.keys(looks.items).length === 0 && Object.keys(looks.armor).length === 0) {
		await env.USERS.delete("l:" + uuid);
		return new Response("ok");
	}
	const value = JSON.stringify(looks);
	const digest = await crypto.subtle.digest("SHA-1", new TextEncoder().encode(value));
	const version = [...new Uint8Array(digest)].slice(0, 8).map((b) => b.toString(16).padStart(2, "0")).join("");
	await env.USERS.put("l:" + uuid, value, { expirationTtl: LOOKS_TTL_SECONDS, metadata: { name, v: version, t: Date.now() } });
	return new Response("ok");
}

// The list of the players who share looks.
async function readLookList(env) {
	const players = [];
	let cursor;
	for (let page = 0; page < 10; page++) {
		const result = await env.USERS.list({ prefix: "l:", cursor });
		for (const key of result.keys) {
			if (key.metadata && key.metadata.name) {
				players.push({ uuid: key.name.slice(2), name: key.metadata.name, v: key.metadata.v, t: key.metadata.t });
			}
		}
		if (result.list_complete) {
			break;
		}
		cursor = result.cursor;
	}
	return JSON.stringify({ players });
}

const json = (body) => new Response(body, {
	headers: { "content-type": "application/json", "cache-control": "public, max-age=30" }
});

export default {
	async fetch(request, env) {
		const url = new URL(request.url);

		if (url.pathname === "/looks" && request.method === "POST") {
			return saveLooks(request, env);
		}

		if (url.pathname === "/looks" && request.method === "GET") {
			let answer = cachedLooks;
			if (!answer || Date.now() - answer.at >= LIST_CACHE_MS) {
				const at = Date.now();
				answer = cachedLooks = { body: await readLookList(env), at };
			}
			return json(answer.body);
		}

		const one = url.pathname.match(/^\/looks\/([0-9a-f]{32})$/);
		if (one && request.method === "GET") {
			const value = await env.USERS.get("l:" + one[1]);
			return value === null ? new Response("none", { status: 404 }) : json(value);
		}

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
