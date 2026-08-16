import assetlinks from "./assetlinks.json";

// Serves https://masto.nyc/.well-known/assetlinks.json for Android App Links verification.
//
// The route in wrangler.toml is scoped to that exact path, deliberately: Mastodon serves several
// other /.well-known/ endpoints (webfinger, nodeinfo, host-meta) that must keep reaching the
// origin untouched. Never widen the route to /.well-known/*.
//
// Android is strict about this response - it must be 200, application/json, over HTTPS, with no
// redirect. Serving it from a Worker means it does not depend on the origin being up, and needs
// no filesystem access to the Mastodon host.
export default {
	fetch(request) {
		if (request.method !== "GET" && request.method !== "HEAD") {
			return new Response("Method Not Allowed", {
				status: 405,
				headers: { allow: "GET, HEAD" },
			});
		}
		return new Response(JSON.stringify(assetlinks, null, 2) + "\n", {
			headers: {
				"content-type": "application/json",
				// Short TTL: a bad deploy should age out quickly, and Android re-checks rarely
				// enough that caching buys nothing.
				"cache-control": "public, max-age=300",
			},
		});
	},
};
