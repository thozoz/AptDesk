<script context="module">
  import { Monitor, TerminalSquare, Folder } from 'lucide-svelte';

  // Single source of truth for nav items, exported so Shell derives view titles
  // from the same list (no duplication, no router).
  export const NAV_ITEMS = [
    { id: 'desktop', label: 'VNC', icon: Monitor },
    { id: 'terminal', label: 'Term', icon: TerminalSquare },
    { id: 'files', label: 'Files', icon: Folder },
  ];
</script>

<script>
  import { createEventDispatcher } from 'svelte';

  export let activeId = 'desktop';
  const dispatch = createEventDispatcher();

  function select(id) {
    dispatch('select', id);
  }
</script>

<nav class="nav">
  {#each NAV_ITEMS as item}
    <button
      type="button"
      class="item"
      class:active={item.id === activeId}
      aria-current={item.id === activeId ? 'page' : undefined}
      on:click={() => select(item.id)}
    >
      <svelte:component this={item.icon} size={18} strokeWidth={1.75} />
      <span class="label">{item.label}</span>
    </button>
  {/each}
</nav>

<style>
  /* Desktop default (>=1024px): vertical left sidebar, 220px, icon + label. */
  .nav {
    display: flex;
    flex-direction: column;
    gap: var(--space-xs);
    width: 220px;
    flex: 0 0 220px;
    padding: var(--space-md) var(--space-sm);
    background: var(--color-surface);
    border-right: 1px solid var(--color-border);
  }

  .item {
    display: flex;
    align-items: center;
    gap: var(--space-sm);
    padding: var(--space-sm) var(--space-md);
    border: none;
    border-left: 2px solid transparent;
    border-radius: var(--radius);
    background: transparent;
    color: var(--text-body);
    font-family: var(--font-sans);
    font-size: var(--font-body);
    cursor: pointer;
    text-align: left;
  }

  .item:hover {
    background: var(--color-surface-hover);
  }

  /* Active nav: accent-deep LEFT border + faint tint (not a full yellow fill). */
  .item.active {
    border-left-color: var(--color-accent-deep);
    background: var(--color-surface-raised);
    color: var(--text-primary);
  }

  .label {
    font-size: var(--font-body);
  }

  /* Tablet (640-1023px): collapse to a 64px icon-only rail. */
  @media (max-width: 1023px) and (min-width: 640px) {
    .nav {
      width: 64px;
      flex: 0 0 64px;
      align-items: center;
    }
    .item {
      justify-content: center;
      padding: var(--space-sm);
    }
    .label {
      display: none;
    }
  }

  /* Phone (<640px): horizontal bottom bar, icon + short label, evenly spread. */
  @media (max-width: 639px) {
    .nav {
      flex-direction: row;
      width: 100%;
      flex: 0 0 auto;
      position: sticky;
      bottom: 0;
      justify-content: space-around;
      padding: var(--space-xs) var(--space-sm);
      border-right: none;
      border-top: 1px solid var(--color-border);
    }
    .item {
      flex-direction: column;
      gap: var(--space-xs);
      border-left: none;
      border-top: 2px solid transparent;
      font-size: var(--font-label);
    }
    .item.active {
      border-left-color: transparent;
      border-top-color: var(--color-accent-deep);
    }
    .label {
      display: inline;
      font-size: var(--font-label);
    }
  }
</style>
