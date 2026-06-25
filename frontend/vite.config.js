import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));

// The Android dev device IP — used by the dev proxy for live backend services.
// Override at the shell: `APTDESK_DEVICE=192.168.1.42 npm run dev`.
const DEVICE = process.env.APTDESK_DEVICE || '127.0.0.1';
const DEVICE_PORT = process.env.APTDESK_PORT || '8080';
const deviceOrigin = `http://${DEVICE}:${DEVICE_PORT}`;

// During `npm run dev`, /api/* is proxied to the standalone mock backend by
// default (developable without an Android device). To develop against a LIVE
// device instead, set APTDESK_DEVICE and flip USE_LIVE_DEVICE to true below —
// that also routes /term, /vnc, /filesapp (only available on a real backend).
const USE_LIVE_DEVICE = process.env.APTDESK_LIVE === '1';
const MOCK_PORT = process.env.APTDESK_MOCK_PORT || '8090';

const proxy = USE_LIVE_DEVICE
  ? {
      // Live-device mode: everything goes to the real Caddy on the device.
      '/api': { target: deviceOrigin, changeOrigin: true },
      '/term': { target: deviceOrigin, changeOrigin: true, ws: true },
      '/vnc': { target: deviceOrigin, changeOrigin: true, ws: true },
      '/filesapp': { target: deviceOrigin, changeOrigin: true },
    }
  : {
      // Default mock mode: only /api is emulated; device-only services are absent.
      '/api': { target: `http://127.0.0.1:${MOCK_PORT}`, changeOrigin: true },
    };

export default defineConfig({
  plugins: [svelte()],
  // BUILD-01/BUILD-02: emit the production bundle directly into the Android
  // assets dir. emptyOutDir:false is REQUIRED so the build never wipes the
  // runtime-only payloads (bin/filebrowser, libs/novnc) that are not produced
  // by Vite but must remain in the packaged APK.
  build: {
    outDir: resolve(__dirname, '../app/src/main/assets/www'),
    emptyOutDir: false,
    assetsDir: 'assets',
  },
  server: {
    port: 5173,
    // Dev proxy. DEFAULT (mock mode): /api -> the standalone mock server on 8090,
    // so `npm run dev` works with no device. LIVE mode (APTDESK_LIVE=1): /api,
    // /term, /vnc, /filesapp all route to a real device's Caddy on :8080. Flip
    // via env vars (see README) — this `proxy` object is the single toggle point.
    proxy,
  },
});
