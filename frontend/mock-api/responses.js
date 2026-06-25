// Mock /api payloads matching the exact JSON shapes from WebServer.kt
// (documented in 05-02-PLAN.md <interfaces>). Dev-only — never shipped.

export function mockStatusRunning() {
  return {
    status: 'running',
    backend_state: 'running',
    resolution: '1280x720',
    progress: null,
    ip: '192.168.1.50',
    ram: { total: '4.00', used: '1.80' },
    disk: { total: '32.00', used: '12.40' },
    uptime: '0h 14m',
    cpu: 23,
    battery: { percent: 78, charging: false, temp: 31.2 },
  };
}

// step: one of downloading_rootfs | extracting_rootfs | copying_assets | starting_backend
export function mockStatusStarting(step = 'starting_backend') {
  return {
    status: 'running',
    backend_state: step,
    resolution: '1280x720',
    progress: 42,
    ip: '192.168.1.50',
    ram: { total: '4.00', used: '0.90' },
    disk: { total: '32.00', used: '12.40' },
    uptime: '0h 1m',
    cpu: 64,
    battery: { percent: 78, charging: true, temp: 33.5 },
  };
}

export function mockStatusError() {
  return {
    status: 'error',
    backend_state: 'error',
    resolution: '1280x720',
    progress: null,
    ip: '192.168.1.50',
    ram: { total: '4.00', used: '0.40' },
    disk: { total: '32.00', used: '12.40' },
    uptime: '0h 0m',
    cpu: null,
    battery: { percent: 78, charging: false, temp: 31.2 },
    error: 'PRoot exited unexpectedly (code 1)',
  };
}

export function mockSessions() {
  return [
    { name: 'desktop', user: 'aptdesk', uptime: '0h 14m', status: 'active', badge: 'success' },
    { name: 'terminal', user: 'aptdesk', uptime: '0h 12m', status: 'active', badge: 'neutral' },
    { name: 'files', user: 'aptdesk', uptime: '0h 14m', status: 'idle', badge: 'warning' },
  ];
}
