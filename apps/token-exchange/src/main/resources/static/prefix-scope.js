const appPrefixes = ["/sharkord", "/clearflask", "/alexandrie", "/crm", "/mentingo"];
const prefixByClient = new Map();
const navPrefixByClient = new Map();
const ownedPrefixes = [
  "/auth",
  "/logout",
  "/session",
  "/exchange",
  "/sharkord",
  "/clearflask",
  "/alexandrie",
  "/alexandrie-api",
  "/alexandrie-files",
  "/crm",
  "/crm-api",
  "/mentingo",
  "/mentingo-api",
  "/api/auth"
];

self.addEventListener("install", function () {
  self.skipWaiting();
});

self.addEventListener("activate", function (event) {
  event.waitUntil(self.clients.claim());
});

self.addEventListener("fetch", function (event) {
  const url = new URL(event.request.url);
  if (url.origin !== self.location.origin) {
    return;
  }
  const prefix = appPrefix(url.pathname);
  if (prefix) {
    remember(event, prefix);
    if ((prefix === "/clearflask" || prefix === "/alexandrie") && event.request.mode === "navigate" && event.request.method === "GET") {
      event.respondWith(appDocument(event, prefix));
    }
    return;
  }
  if (owned(url.pathname)) {
    return;
  }
  event.respondWith(withAppPrefix(event, url));
});

function remember(event, prefix) {
  if (event.request.mode === "navigate") {
    if (event.resultingClientId) {
      prefixByClient.set(event.resultingClientId, prefix);
      navPrefixByClient.set(event.resultingClientId, prefix);
    }
    return;
  }
  if (event.clientId) {
    prefixByClient.set(event.clientId, prefix);
  }
}

function owned(pathname) {
  return ownedPrefixes.some(function (prefix) {
    return pathname === prefix || pathname.startsWith(prefix + "/");
  });
}

function portalPath(pathname) {
  return pathname === "/" || pathname === "/login" || pathname.startsWith("/login/");
}

function appPrefix(pathname) {
  let match = "";
  appPrefixes.forEach(function (prefix) {
    if ((pathname === prefix || pathname.startsWith(prefix + "/")) && prefix.length > match.length) {
      match = prefix;
    }
  });
  return match;
}

async function withAppPrefix(event, url) {
  const client = event.clientId ? await self.clients.get(event.clientId) : null;
  let prefix = "";
  if (event.request.mode === "navigate" && event.clientId) {
    prefix = navPrefixByClient.get(event.clientId) || "";
  }
  if (!prefix && event.request.mode === "navigate" && event.request.referrer) {
    prefix = appPrefix(new URL(event.request.referrer).pathname);
  }
  if (!prefix && client) {
    const path = new URL(client.url).pathname;
    prefix = appPrefix(path);
    if (!prefix && event.clientId && (client.frameType === "nested" || !portalPath(path))) {
      prefix = prefixByClient.get(event.clientId) || navPrefixByClient.get(event.clientId) || "";
    }
  } else if (!prefix && event.resultingClientId) {
    prefix = prefixByClient.get(event.resultingClientId) || "";
  } else if (!prefix && event.clientId) {
    prefix = prefixByClient.get(event.clientId) || "";
  }
  if (!prefix && event.request.referrer) {
    prefix = appPrefix(new URL(event.request.referrer).pathname);
  }
  if (!prefix) {
    return fetch(event.request);
  }
  url.pathname = prefix + url.pathname;
  if (event.request.mode === "navigate") {
    return Response.redirect(url.toString(), 302);
  }
  return fetchPrefixed(event, url, prefix);
}

async function fetchPrefixed(event, url, prefix) {
  const headers = event ? new Headers(event.request.headers) : new Headers();
  const method = event ? event.request.method : "GET";
  let body;
  if (event && method !== "GET" && method !== "HEAD") {
    body = await event.request.clone().arrayBuffer();
  }
  const response = await fetch(url.toString(), {
    method,
    headers,
    body,
    credentials: "include",
    cache: "no-store",
    redirect: "manual"
  });
  if (response.type === "opaqueredirect" || (response.status >= 300 && response.status < 400 && !response.headers.get("Location"))) {
    return fetch(url.toString(), {
      method,
      headers,
      body,
      credentials: "include",
      cache: "no-store",
      redirect: "follow"
    });
  }
  const location = response.headers.get("Location");
  if (location && response.status >= 300 && response.status < 400) {
    return Response.redirect(placed(location, url, prefix), response.status);
  }
  return response;
}

async function appDocument(event, prefix) {
  const response = await fetch(event.request.url, {
    method: "GET",
    headers: event.request.headers,
    credentials: "include",
    cache: "no-store",
    redirect: "follow"
  });
  const headers = new Headers(response.headers);
  headers.delete("content-encoding");
  headers.delete("content-length");
  const type = headers.get("content-type") || "";
  if (!response.ok || type.indexOf("text/html") === -1) {
    return new Response(await response.arrayBuffer(), {
      status: response.status,
      statusText: response.statusText,
      headers: headers
    });
  }
  const guard = "<script>(function(){if(window.parent===window)return;var prefix=" + JSON.stringify(prefix) + ";var path=location.pathname;if(path===prefix||path.indexOf(prefix+\"/\")===0){var next=path.substring(prefix.length)||\"/\";history.replaceState(history.state,\"\",next+location.search+location.hash);}function fixLink(node){if(!node||node.tagName!==\"A\")return;var href=node.getAttribute(\"href\")||\"\";var fixed=href.replace(/^https:\\/\\/http:\\/\\//,\"http://\").replace(/^https:\\/\\/common\\.localhost/,\"http://common.localhost\");if(fixed!==href)node.setAttribute(\"href\",fixed);}function scan(root){if(!root||!root.querySelectorAll)return;var list=root.querySelectorAll(\"a[href]\");for(var i=0;i<list.length;i++)fixLink(list[i]);}function arm(){scan(document);new MutationObserver(function(records){for(var i=0;i<records.length;i++){var rec=records[i];if(rec.type===\"attributes\")fixLink(rec.target);var nodes=rec.addedNodes;for(var j=0;j<nodes.length;j++)scan(nodes[j]);}}).observe(document.documentElement,{subtree:true,childList:true,attributes:true,attributeFilter:[\"href\"]});}if(document.documentElement)arm();else document.addEventListener(\"DOMContentLoaded\",arm);})();</script>";
  const html = await response.text();
  const next = /<head[^>]*>/i.test(html)
    ? html.replace(/<head[^>]*>/i, function (tag) { return tag + guard; })
    : guard + html;
  return new Response(next, {
    status: response.status,
    statusText: response.statusText,
    headers: headers
  });
}

function placed(location, base, prefix) {
  const next = new URL(location, base);
  if (next.origin === base.origin && prefix && !owned(next.pathname) && !appPrefix(next.pathname)) {
    next.pathname = prefix + next.pathname;
  }
  return next.toString();
}
