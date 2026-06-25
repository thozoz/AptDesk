# AptDesk Frontend

The AptDesk dashboard UI — a Svelte 4 + Vite app compiled into the Android app's
assets and served on-device behind Caddy. There is **no runtime framework, router,
or component library** shipped to the device: Svelte compiles to vanilla JS, and
the only runtime dependency is a handful of tree-shaken `lucide-svelte` icon
components.

## Prerequisites

- **Node 20+** (any current LTS). Verify with `node --version`.

## Install

```bash
cd frontend
npm install
```

## Develop (no Android device needed)

The dev workflow uses two terminals:

```bash
# Terminal 1 — mock backend (serves realistic /api responses)
npm run mock-api      # http://127.0.0.1:8090

# Terminal 2 — Vite dev server (proxies /api -> the mock backend)
npm run dev           # http://localhost:5173
```

`npm run dev` starts Vite and proxies `/api/*` to the mock server by default, so
the frontend is fully developable with **zero device attached**. Without the mock
server running, `npm run dev` still serves the UI, but `/api/*` calls fail
(connection refused / 404) until either the mock server **or** a live device proxy
target is up.

### Mock API

`mock-api/server.js` is a zero-dependency Node `http` server (built-ins only — no
Express) on port **8090**. It returns the exact `/api/status`, `/api/sessions`,
and `/api/restart` JSON shapes the real `WebServer.kt` produces. Edit
`mock-api/responses.js` to exercise other states (running / starting / error).

### Develop against a live device instead

To point the dev proxy at a real Android device on your LAN (this also enables the
device-only `/term`, `/vnc`, `/filesapp` services that the mock cannot emulate):

```bash
APTDESK_LIVE=1 APTDESK_DEVICE=192.168.1.42 npm run dev
```

The proxy block in `vite.config.js` is the single toggle — `APTDESK_LIVE=1` routes
everything to `http://<device-ip>:8080`; the default routes only `/api` to the
mock server. The relevant lines are commented in `vite.config.js`.

## Build

```bash
npm run build
```

Vite writes the compiled bundle to `../app/src/main/assets/www/` (with
`emptyOutDir: false`, so the runtime-only `www/bin/` and `www/libs/novnc/` payloads
are preserved). This is the same command the Gradle `buildFrontend` task runs
before the APK is packaged, so the shipped dashboard is never stale.

```bash
npm run preview       # serve the production build locally to sanity-check
```

## Dependency Review

For the Google Play audit (BUILD-04). Exactly four dependencies; all MIT-licensed;
nothing fetched from a component registry at build time.

| Package | Role | Publisher | License | Justification |
|---------|------|-----------|---------|---------------|
| `vite` | Build tool / dev server | Vite core team | MIT | Build-time only; not shipped to device. |
| `svelte` | UI compiler | Svelte core team | MIT | Compiles to vanilla JS — **no runtime framework ships** in the bundle. |
| `@sveltejs/vite-plugin-svelte` | Vite ↔ Svelte glue | Svelte core team | MIT | Build-time only. |
| `lucide-svelte` | Icon set | lucide-icons org | MIT | Tree-shakeable SVG icons; **only the icons actually imported** end up in the bundle. The one approved component-library exception (see 05-UI-SPEC.md). |

The compiled `assets/www/` bundle therefore contains only Svelte's compiled-away
output plus the few `lucide-svelte` icon components actually used — no runtime
framework, no router, no component library beyond the named icon exception. The
dev-only mock server uses Node built-ins exclusively and is never bundled.

> Note: `lucide-svelte@0.417.0` carries an upstream deprecation notice (renamed to
> `@lucide/svelte`, which targets Svelte 5). We pin the Svelte-4-compatible
> `lucide-svelte`; it is a build/dev dependency for icon components and adds no
> device runtime surface.
