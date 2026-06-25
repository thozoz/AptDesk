<script>
  // Reusable loading state: centered muted spinner + label (UI-SPEC State Components).
  export let label = 'Loading...';
</script>

<div class="loading" role="status" aria-live="polite">
  <span class="spinner" aria-hidden="true"></span>
  <span class="label">{label}</span>
</div>

<style>
  .loading {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: var(--space-md);
    padding: var(--space-2xl);
    color: var(--text-muted);
  }

  .spinner {
    width: 20px;
    height: 20px;
    border: 2px solid var(--color-border);
    border-top-color: var(--text-muted);
    border-radius: var(--radius-pill);
    animation: spin 0.8s linear infinite;
  }

  .label {
    font-family: var(--font-mono);
    font-size: var(--font-label);
    font-weight: var(--weight-label);
    line-height: var(--line-label);
  }

  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .spinner {
      animation: none;
    }
  }
</style>
