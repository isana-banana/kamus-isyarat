const CATALOG_KEY = "catalog";
const SESSION_HOURS = 8;

function json(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      ...headers
    }
  });
}

function getCookie(request, name) {
  const cookies = request.headers.get("Cookie") || "";
  const item = cookies.split(";").map(v => v.trim())
    .find(v => v.startsWith(name + "="));
  return item ? item.substring(name.length + 1) : null;
}

function base64url(bytes) {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function fromBase64url(value) {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
  return Uint8Array.from(atob(padded), c => c.charCodeAt(0));
}

async function hmac(message, secret) {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign", "verify"]
  );
  return new Uint8Array(await crypto.subtle.sign(
    "HMAC", key, new TextEncoder().encode(message)
  ));
}

async function createSession(env) {
  const payload = base64url(new TextEncoder().encode(JSON.stringify({
    exp: Date.now() + SESSION_HOURS * 60 * 60 * 1000
  })));
  const signature = base64url(await hmac(payload, env.SESSION_SECRET));
  return payload + "." + signature;
}

async function validSession(request, env) {
  const token = getCookie(request, "kamus_session");
  if (!token || !env.SESSION_SECRET) return false;

  const parts = token.split(".");
  if (parts.length !== 2) return false;

  const expected = await hmac(parts[0], env.SESSION_SECRET);
  const actual = fromBase64url(parts[1]);

  if (expected.length !== actual.length) return false;

  let diff = 0;
  for (let i = 0; i < expected.length; i++) diff |= expected[i] ^ actual[i];
  if (diff !== 0) return false;

  try {
    const payload = JSON.parse(
      new TextDecoder().decode(fromBase64url(parts[0]))
    );
    return payload.exp > Date.now();
  } catch {
    return false;
  }
}

async function requireAdmin(request, env) {
  return await validSession(request, env);
}

async function readCatalog(env) {
  const stored = await env.KAMUS_KV.get(CATALOG_KEY);
  if (!stored) return { items: [] };

  try {
    const data = JSON.parse(stored);
    return { items: Array.isArray(data.items) ? data.items : [] };
  } catch {
    return { items: [] };
  }
}

async function saveCatalog(env, catalog) {
  await env.KAMUS_KV.put(CATALOG_KEY, JSON.stringify(catalog));
}

function extractFolderId(value) {
  const input = String(value || "").trim();

  if (/^[a-zA-Z0-9_-]{10,}$/.test(input)) return input;

  try {
    const url = new URL(input);
    const match = url.pathname.match(/\/folders\/([a-zA-Z0-9_-]+)/);
    if (match) return match[1];
  } catch {}

  return null;
}

async function driveFetch(env, path, requestHeaders = {}) {
  if (!env.GOOGLE_API_KEY) {
    throw new Error("GOOGLE_API_KEY belum dipasang di Cloudflare.");
  }

  const separator = path.includes("?") ? "&" : "?";
  const url = "https://www.googleapis.com/drive/v3/" +
    path + separator + "key=" + encodeURIComponent(env.GOOGLE_API_KEY);

  return await fetch(url, { headers: requestHeaders });
}

async function driveRequest(env, path) {
  const response = await driveFetch(env, path);
  if (!response.ok) {
    const detail = await response.text();
    throw new Error("Google Drive API gagal (" + response.status + "): " + detail.slice(0, 250));
  }
  return response;
}

// Mengalirkan file media dari Drive melalui Worker agar video dapat diputar
// memakai elemen HTML5 <video>, termasuk permintaan byte-range untuk seek.
async function handleMedia(request, env, fileId) {
  if (!/^[a-zA-Z0-9_-]{10,}$/.test(fileId)) {
    return json({ error: "ID file Google Drive tidak valid." }, 400);
  }

  const requestHeaders = {};
  const range = request.headers.get("Range");
  if (range) requestHeaders.Range = range;

  const upstream = await driveFetch(
    env,
    "files/" + encodeURIComponent(fileId) + "?alt=media",
    requestHeaders
  );

  if (!upstream.ok && upstream.status !== 416) {
    const detail = await upstream.text();
    return json({
      error: "Google Drive tidak dapat mengirim media. Periksa izin berbagi dan ID file.",
      detail: detail.slice(0, 300)
    }, upstream.status);
  }

  const headers = new Headers();
  for (const name of [
    "Content-Type", "Content-Length", "Content-Range",
    "Accept-Ranges", "Last-Modified", "ETag"
  ]) {
    const value = upstream.headers.get(name);
    if (value) headers.set(name, value);
  }
  if (!headers.has("Content-Type")) headers.set("Content-Type", "video/mp4");
  if (!headers.has("Accept-Ranges")) headers.set("Accept-Ranges", "bytes");
  headers.set("Cache-Control", "public, max-age=3600");
  headers.set("X-Content-Type-Options", "nosniff");

  return new Response(request.method === "HEAD" ? null : upstream.body, {
    status: upstream.status,
    headers
  });
}

async function getManifest(env, folderId) {
  const query = "'" + folderId + "' in parents and name = 'kamus.json' and trashed = false";

  const listUrl = "files?q=" + encodeURIComponent(query) +
    "&fields=files(id,name,mimeType)&pageSize=10";

  const listResponse = await driveRequest(env, listUrl);
  const listData = await listResponse.json();

  const file = (listData.files || []).find(f => f.name === "kamus.json");

  if (!file) {
    throw new Error("File kamus.json tidak ditemukan di folder Google Drive.");
  }

  const fileResponse = await driveRequest(
    env,
    "files/" + encodeURIComponent(file.id) + "?alt=media"
  );

  const manifest = await fileResponse.json();

  if (!manifest || typeof manifest !== "object" ||
      !manifest.kata || typeof manifest.kata !== "object" ||
      Array.isArray(manifest.kata)) {
    throw new Error("Format kamus.json tidak sesuai. Periksa bagian kata.");
  }

  return manifest;
}

function countWords(manifest) {
  return Object.keys(manifest.kata || {}).length;
}

async function handleApi(request, env) {
  const url = new URL(request.url);
  const path = url.pathname;
  const method = request.method;

  const mediaMatch = path.match(/^\/api\/media\/([^/]+)$/);
  if (mediaMatch && (method === "GET" || method === "HEAD")) {
    return await handleMedia(request, env, decodeURIComponent(mediaMatch[1]));
  }

  if (path === "/api/catalog" && method === "GET") {
    const catalog = await readCatalog(env);
    return json({
      items: catalog.items
        .filter(item => item.active !== false)
        .map(({ id, name, wordCount }) => ({ id, name, wordCount }))
    });
  }

  if (path.startsWith("/api/dictionary/") && method === "GET") {
    const id = decodeURIComponent(path.split("/").pop());
    const catalog = await readCatalog(env);
    const item = catalog.items.find(x => x.id === id && x.active !== false);

    if (!item) return json({ error: "Kamus tidak ditemukan atau tidak aktif." }, 404);

    const manifest = await getManifest(env, item.folderId);

    return json({
      id: item.id,
      name: item.name,
      manifest
    });
  }

  if (path === "/api/admin/login" && method === "POST") {
    const body = await request.json().catch(() => ({}));

    if (!env.ADMIN_PASSWORD || !env.SESSION_SECRET) {
      return json({ error: "ADMIN_PASSWORD atau SESSION_SECRET belum dipasang." }, 500);
    }

    if (String(body.password || "") !== env.ADMIN_PASSWORD) {
      return json({ error: "Kata sandi salah." }, 401);
    }

    const token = await createSession(env);

    return json({ success: true }, 200, {
      "Set-Cookie": "kamus_session=" + token +
        "; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=" +
        (SESSION_HOURS * 60 * 60)
    });
  }

  if (path === "/api/admin/logout" && method === "POST") {
    return json({ success: true }, 200, {
      "Set-Cookie": "kamus_session=; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=0"
    });
  }

  if (path.startsWith("/api/admin/")) {
    if (!(await requireAdmin(request, env))) {
      return json({ error: "Silakan login sebagai admin." }, 401);
    }

    if (path === "/api/admin/me" && method === "GET") {
      return json({ loggedIn: true });
    }

    if (path === "/api/admin/dictionaries" && method === "GET") {
      return json(await readCatalog(env));
    }

    if (path === "/api/admin/dictionaries" && method === "POST") {
      const body = await request.json().catch(() => ({}));
      const name = String(body.name || "").trim();
      const folderId = extractFolderId(body.folderUrl);

      if (!name || !folderId) {
        return json({ error: "Nama kamus dan URL folder Google Drive wajib diisi." }, 400);
      }

      const catalog = await readCatalog(env);

      if (catalog.items.some(x => x.folderId === folderId)) {
        return json({ error: "Folder ini sudah terdaftar." }, 409);
      }

      const manifest = await getManifest(env, folderId);

      const item = {
        id: crypto.randomUUID(),
        name,
        folderId,
        active: true,
        addedAt: new Date().toISOString(),
        wordCount: countWords(manifest)
      };

      catalog.items.push(item);
      await saveCatalog(env, catalog);

      return json({ success: true, item }, 201);
    }

    const match = path.match(/^\/api\/admin\/dictionaries\/([^/]+)$/);

    if (match) {
      const id = decodeURIComponent(match[1]);
      const catalog = await readCatalog(env);
      const index = catalog.items.findIndex(x => x.id === id);

      if (index < 0) return json({ error: "Kamus tidak ditemukan." }, 404);

      if (method === "PATCH") {
        const body = await request.json().catch(() => ({}));

        if (typeof body.name === "string" && body.name.trim()) {
          catalog.items[index].name = body.name.trim();
        }

        if (typeof body.active === "boolean") {
          catalog.items[index].active = body.active;
        }

        await saveCatalog(env, catalog);
        return json({ success: true, item: catalog.items[index] });
      }

      if (method === "DELETE") {
        catalog.items.splice(index, 1);
        await saveCatalog(env, catalog);
        return json({ success: true });
      }
    }

    return json({ error: "Rute admin tidak ditemukan." }, 404);
  }

  return json({ error: "API tidak ditemukan." }, 404);
}

export default {
  async fetch(request, env) {
    try {
      const url = new URL(request.url);

      if (url.pathname.startsWith("/api/")) {
        return await handleApi(request, env);
      }

      if (env.ASSETS) {
        return await env.ASSETS.fetch(request);
      }

      return new Response("Kamus Isyarat Online", { status: 200 });
    } catch (error) {
      return json({ error: error.message || "Terjadi kesalahan server." }, 500);
    }
  }
};