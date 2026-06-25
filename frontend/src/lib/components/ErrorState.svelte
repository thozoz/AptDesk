<script>
  import { AlertTriangle } from 'lucide-svelte';

  // Reusable error state: destructive-tinted icon + problem heading + solution
  // body + bordered Retry button. No native browser dialogs (UI-SPEC hard rule).
  export let heading = "Can't reach the backend";
  export let body =
    "The control API didn't respond. Check the service is running, then retry.";
  export let onRetry = null; // () => void
</script>

<div class="error" role="alert">
  <AlertTriangle class="error-icon" size={28} strokeWidth={1.5} aria-hidden="true" />
  <h2 class="heading">{heading}</h2>
  <p class="body">{body}</p>
  {#if onRetry}
    <button class="retry" type="button" on:click={onRetry}>Retry</button>
  {/if}
</div>

<style>
  .error {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: var(--space-sm);
    padding: var(--space-2xl);
    text-align: center;
    color: var(--text-body);
  }

  .error :global(.error-icon) {
    color: var(--color-destructive);
    margin-bottom: var(--space-xs);
  }

  .heading {
    margin: 0;
    font-size: var(--font-heading);
    font-weight: var(--weight-heading);
    line-height: var(--line-heading);
    color: var(--text-primary);
  }

  .body {
    margin: 0;
    max-width: 40ch;
    font-size: var(--font-body);
    color: var(--text-muted);
  }

  /* Bordered, NOT accent-filled — per UI-SPEC (Retry button is bordered). */
  .retry {
    margin-top: var(--space-md);
    padding: var(--space-sm) var(--space-md);
    border: 1px solid var(--color-border-strong);
    border-radius: var(--radius);
    background: transparent;
    color: var(--text-primary);
    font-family: var(--font-sans);
    font-size: var(--font-label);
    cursor: pointer;
  }

  .retry:hover {
    background: var(--color-surface-hover);
  }
</style>
