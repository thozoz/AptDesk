<script>
  import { isLoggedIn } from './lib/auth.js';
  import Login from './lib/components/Login.svelte';
  import Shell from './lib/components/Shell.svelte';

  // The full extent of "routing": a single boolean conditional (D-11, no router).
  let loggedIn = isLoggedIn();
</script>

{#if loggedIn}
  <Shell />
{:else}
  <Login on:login={() => (loggedIn = true)} />
{/if}
