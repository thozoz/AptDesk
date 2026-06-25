<script>
  import { statusStore } from '../stores/status.js';
  import StatusBadge from './StatusBadge.svelte';

  // Persistent top bar (DASH-04). The one component permitted a subtle shadow
  // (UI-SPEC "borders over shadows... one subtle shadow allowed on the top bar").
  // Stays full-width and visible at every breakpoint.
</script>

<header class="topbar">
  <span class="brand">AptDesk</span>
  <StatusBadge state={$statusStore.data?.backend_state ?? 'idle'} />
</header>

<style>
  .topbar {
    position: sticky;
    top: 0;
    z-index: 10;
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 56px;
    padding: 0 var(--space-lg);
    background: var(--color-surface);
    border-bottom: 1px solid var(--color-border);
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
  }

  .brand {
    font-family: var(--font-mono);
    font-size: var(--font-body);
    font-weight: var(--weight-heading);
    color: var(--text-primary);
    letter-spacing: 0.02em;
  }
</style>
