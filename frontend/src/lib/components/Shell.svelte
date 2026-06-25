<script>
  import { onMount, onDestroy } from 'svelte';
  import { statusStore } from '../stores/status.js';
  import TopBar from './TopBar.svelte';
  import Nav, { NAV_ITEMS } from './Nav.svelte';
  import ViewPlaceholder from './ViewPlaceholder.svelte';
  import Loading from './Loading.svelte';
  import ErrorState from './ErrorState.svelte';

  let activeView = 'desktop';

  // Shell owns the polling-store lifecycle.
  onMount(() => statusStore.start());
  onDestroy(() => statusStore.stop());

  $: activeTitle = NAV_ITEMS.find((i) => i.id === activeView)?.label ?? '';
</script>

<div class="shell">
  <TopBar />

  <!-- DASH-05: shared state styling wired visibly at the shell level. -->
  {#if $statusStore.loading && !$statusStore.data}
    <div class="banner">
      <Loading label="Connecting..." />
    </div>
  {:else if $statusStore.error}
    <div class="banner">
      <ErrorState onRetry={() => statusStore.start()} />
    </div>
  {/if}

  <div class="body">
    <main>
      <ViewPlaceholder title={activeTitle} />
    </main>
    <Nav activeId={activeView} on:select={(e) => (activeView = e.detail)} />
  </div>
</div>

<style>
  .shell {
    display: flex;
    flex-direction: column;
    min-height: 100vh;
    background: var(--color-bg);
  }

  .banner {
    border-bottom: 1px solid var(--color-border);
  }

  /* Desktop/tablet: Nav beside main. Phone: column, Nav after main (bottom). */
  .body {
    display: flex;
    flex-direction: row;
    flex: 1;
  }

  main {
    flex: 1;
    width: 100%;
    max-width: 1100px;
    margin: 0 auto;
    padding: var(--space-lg);
  }

  @media (max-width: 1023px) {
    main {
      padding: var(--space-md);
    }
  }

  @media (max-width: 639px) {
    .body {
      flex-direction: column;
    }
    main {
      padding: var(--space-md);
    }
  }
</style>
