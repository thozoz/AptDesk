import { writable } from 'svelte/store';

/**
 * Shared backend-status store. Polls GET /api/status on an interval and exposes
 * a single source of truth every view can subscribe to. Polling is visibility-
 * gated (pauses while document.hidden) and guards against overlapping requests
 * so a slow backend never stacks up in-flight fetches.
 *
 * Store value shape:
 *   { loading, error, data, lastUpdated }
 * where `data` (when present) is the parsed /api/status body:
 *   { status, backend_state, resolution, progress, ip, ram, disk,
 *     uptime, cpu, battery, error }
 */

const POLL_INTERVAL_MS = 4000;
const ENDPOINT = '/api/status';

function createStatusStore() {
  const { subscribe, set, update } = writable({
    loading: true,
    error: null,
    data: null,
    lastUpdated: null,
  });

  let timer = null;
  let inFlight = false;
  let started = false;
  let visibilityBound = false;

  async function poll() {
    // Overlap guard: skip if a previous request has not resolved yet.
    if (inFlight) return;
    inFlight = true;
    try {
      const res = await fetch(ENDPOINT, { headers: { Accept: 'application/json' } });
      if (!res.ok) throw new Error(`status ${res.status}`);
      const data = await res.json();
      set({ loading: false, error: null, data, lastUpdated: Date.now() });
    } catch (err) {
      update((s) => ({
        loading: false,
        error: err instanceof Error ? err.message : String(err),
        data: s.data, // keep last-known data so the UI doesn't flash empty
        lastUpdated: s.lastUpdated,
      }));
    } finally {
      inFlight = false;
    }
  }

  function schedule() {
    stopTimer();
    // Only poll while the tab is visible.
    if (typeof document !== 'undefined' && document.hidden) return;
    poll();
    timer = setInterval(() => {
      if (typeof document !== 'undefined' && document.hidden) return;
      poll();
    }, POLL_INTERVAL_MS);
  }

  function stopTimer() {
    if (timer !== null) {
      clearInterval(timer);
      timer = null;
    }
  }

  function onVisibilityChange() {
    if (document.hidden) {
      stopTimer();
    } else {
      // Resume immediately on return, then keep the interval going.
      schedule();
    }
  }

  function start() {
    if (started) {
      // Already running — treat start() as a manual refresh (e.g. Retry button).
      schedule();
      return;
    }
    started = true;
    if (typeof document !== 'undefined' && !visibilityBound) {
      document.addEventListener('visibilitychange', onVisibilityChange);
      visibilityBound = true;
    }
    schedule();
  }

  function stop() {
    started = false;
    stopTimer();
    if (typeof document !== 'undefined' && visibilityBound) {
      document.removeEventListener('visibilitychange', onVisibilityChange);
      visibilityBound = false;
    }
  }

  return { subscribe, start, stop };
}

export const statusStore = createStatusStore();
