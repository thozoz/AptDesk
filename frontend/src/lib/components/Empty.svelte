<script>
  import { Inbox } from 'lucide-svelte';

  // Reusable empty state: faint icon + heading + body + optional CTA.
  // Calm, NOT error-colored (UI-SPEC State Components / Copywriting Contract).
  export let heading = 'Nothing here yet';
  export let body =
    'This view fills in once the backend is running. Start it from the dashboard.';
  export let cta = null; // { label, onClick } — optional
</script>

<div class="empty">
  <Inbox class="empty-icon" size={28} strokeWidth={1.5} aria-hidden="true" />
  <h2 class="heading">{heading}</h2>
  <p class="body">{body}</p>
  {#if cta}
    <button class="cta" type="button" on:click={cta.onClick}>{cta.label}</button>
  {/if}
</div>

<style>
  .empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: var(--space-sm);
    padding: var(--space-2xl);
    text-align: center;
    color: var(--text-body);
  }

  .empty :global(.empty-icon) {
    color: var(--text-faint);
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
    max-width: 36ch;
    font-size: var(--font-body);
    color: var(--text-muted);
  }

  .cta {
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

  .cta:hover {
    background: var(--color-surface-hover);
  }
</style>
