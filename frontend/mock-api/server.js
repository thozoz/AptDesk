// Standalone dev-only mock backend. Node built-ins only (no Express) so it adds
// zero supply-chain surface (BUILD-04). Emulates the /api endpoints the Vite dev
// proxy targets by default, so the frontend is developable with no device attached.
//
//   npm run mock-api      # this server, port 8090
//   npm run dev           # Vite, proxies /api -> http://127.0.0.1:8090
//
// Never built into the shipped APK; binds to localhost only.

import { createServer } from 'node:http';
import {
  mockStatusRunning,
  mockSessions,
} from './responses.js';

const PORT = process.env.APTDESK_MOCK_PORT || 8090;
const HOST = '127.0.0.1';

function sendJson(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json',
    // Permissive CORS — dev-only, localhost-bound (threat T-05-03: accepted).
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Accept',
  });
  res.end(payload);
}

const server = createServer((req, res) => {
  const { method, url } = req;
  console.log(`[mock-api] ${method} ${url}`);

  if (method === 'OPTIONS') {
    sendJson(res, 204, {});
    return;
  }

  if (method === 'GET' && url === '/api/status') {
    sendJson(res, 200, mockStatusRunning());
    return;
  }

  if (method === 'GET' && url === '/api/sessions') {
    sendJson(res, 200, mockSessions());
    return;
  }

  if (method === 'POST' && url === '/api/restart') {
    sendJson(res, 200, { status: 'restarted' });
    return;
  }

  sendJson(res, 404, { error: `no mock route for ${method} ${url}` });
});

server.listen(PORT, HOST, () => {
  console.log(`[mock-api] listening on http://${HOST}:${PORT}`);
  console.log('[mock-api] routes: GET /api/status, GET /api/sessions, POST /api/restart');
});
