// Parz registry: a tiny Cloudflare Worker that remembers which Minecraft accounts run Parz right now.
//
//   POST /register   {"name": "Pay2Loze", "uuid": "<uuid>"}   -> remembered for 7 minutes
//   GET  /users                                              -> {"users": ["Pay2Loze", ...]}
//
// The mod registers itself every 3 minutes while you are in a world and reads the list every minute.
// Only the name and the UUID are stored, nothing else (no IP address, no server, no chat). Entries disappear
// by themselves after 7 minutes without a new registration.

const TTL_SECONDS = 420;

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
			if (!/^[A-Za-z0-9_]{1,16}$/.test(name) || !/^[0-9a-f]{32}$/.test(uuid)) {
				return new Response("bad data", { status: 400 });
			}
			await env.USERS.put("u:" + uuid, name, { expirationTtl: TTL_SECONDS, metadata: { name } });
			return new Response("ok");
		}

		if (url.pathname === "/users" && request.method === "GET") {
			const users = [];
			let cursor;
			for (let page = 0; page < 10; page++) {
				const result = await env.USERS.list({ prefix: "u:", cursor });
				for (const key of result.keys) {
					if (key.metadata && key.metadata.name) {
						users.push(key.metadata.name);
					}
				}
				if (result.list_complete) {
					break;
				}
				cursor = result.cursor;
			}
			return new Response(JSON.stringify({ users }), {
				headers: { "content-type": "application/json", "cache-control": "public, max-age=30" }
			});
		}

		return new Response("Parz registry", { status: 200 });
	}
};
