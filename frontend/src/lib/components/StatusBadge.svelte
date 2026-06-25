<script>
  // Persistent status pill (DASH-04). Maps each backend_state to the exact
  // UI-SPEC "Status Indicator" label + color. Only the dot + label carry status
  // color; the pill background/border stay neutral. Colors come from tokens —
  // never hardcoded hex.
  export let state = 'idle';

  // Four visual buckets, keyed off the 7 backend_state enum values.
  const STARTING = new Set([
    'downloading_rootfs',
    'extracting_rootfs',
    'copying_assets',
    'starting_backend',
  ]);

  $: meta = mapState(state);

  function mapState(s) {
    if (s === 'running') {
      return { label: 'running', color: 'var(--color-status-running)', pulse: false };
    }
    if (STARTING.has(s)) {
      return { label: 'starting…', color: 'var(--color-status-starting)', pulse: true };
    }
    if (s === 'error') {
      return { label: 'error', color: 'var(--color-status-error)', pulse: false };
    }
    // idle / unknown
    return { label: 'stopped', color: 'var(--color-status-down)', pulse: false };
  }
</script>

<span class="pill" title={`backend: ${state}`}>
  <span class="dot" class:pulse={meta.pulse} style="background: {meta.color};"></span>
  <span class="label" style="color: {meta.color};">{meta.label}</span>
</span>

<style>
  .pill {
    display: inline-flex;
    align-items: center;
    gap: var(--space-xs);
    padding: var(--space-xs) var(--space-sm);
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-pill);
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: var(--radius-pill);
    flex: 0 0 auto;
  }

  .dot.pulse {
    animation: pulse 1.2s ease-in-out infinite;
  }

  .label {
    font-family: var(--font-mono);
    font-size: var(--font-label);
    font-weight: 500;
    line-height: var(--line-label);
  }

  @keyframes pulse {
    0%,
    100% {
      opacity: 1;
      transform: scale(1);
    }
    50% {
      opacity: 0.4;
      transform: scale(0.85);
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .dot.pulse {
      animation: none;
    }
  }
</style>
