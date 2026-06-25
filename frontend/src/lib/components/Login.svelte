<script>
  import { createEventDispatcher } from 'svelte';
  import { login } from '../auth.js';

  // Redesigned login (UI-SPEC). Reproduces the old app.js trust model: requires
  // both fields non-empty, no real password check, stores username in
  // localStorage, then signals the parent to re-evaluate auth (no redirect/router).
  const dispatch = createEventDispatcher();

  let username = '';
  let password = '';
  let message = '';

  function onSubmit() {
    if (!username.trim() || !password.trim()) {
      message = 'Please enter a username and password';
      return;
    }
    message = '';
    login(username.trim());
    dispatch('login');
  }
</script>

<div class="page">
  <form class="card" on:submit|preventDefault={onSubmit}>
    <h1 class="brand">AptDesk</h1>

    <label class="field">
      <span class="label">Username</span>
      <input
        type="text"
        bind:value={username}
        placeholder="your name"
        autocomplete="username"
      />
    </label>

    <label class="field">
      <span class="label">Password</span>
      <input
        type="password"
        bind:value={password}
        autocomplete="current-password"
      />
    </label>

    {#if message}
      <p class="message" role="alert">{message}</p>
    {/if}

    <button class="cta" type="submit">Continue</button>
  </form>
</div>

<style>
  .page {
    min-height: 100vh;
    display: grid;
    place-items: center;
    padding: var(--space-md);
    background: var(--color-bg);
  }

  .card {
    width: 100%;
    max-width: 360px;
    display: flex;
    flex-direction: column;
    gap: var(--space-md);
    padding: var(--space-lg);
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius);
  }

  .brand {
    margin: 0 0 var(--space-sm);
    font-size: var(--font-heading);
    font-weight: var(--weight-heading);
    line-height: var(--line-heading);
    color: var(--text-primary);
  }

  .field {
    display: flex;
    flex-direction: column;
    gap: var(--space-xs);
  }

  .label {
    font-size: var(--font-label);
    font-weight: var(--weight-label);
    color: var(--text-muted);
  }

  input {
    padding: var(--space-sm) var(--space-md);
    background: var(--color-bg);
    border: 1px solid var(--color-border);
    border-radius: var(--radius);
    color: var(--text-primary);
    font-family: var(--font-sans);
    font-size: var(--font-body);
  }

  input:focus-visible {
    border-color: var(--color-accent-deep);
  }

  .message {
    margin: 0;
    font-size: var(--font-label);
    color: var(--color-destructive);
  }

  /* Primary CTA: dark-on-yellow contrast rule — text uses the base bg color. */
  .cta {
    margin-top: var(--space-xs);
    padding: var(--space-sm) var(--space-md);
    background: var(--color-accent);
    color: var(--color-bg);
    border: none;
    border-radius: var(--radius);
    font-family: var(--font-sans);
    font-size: var(--font-body);
    font-weight: var(--weight-label);
    cursor: pointer;
  }

  .cta:hover {
    background: var(--color-accent-deep);
  }

  /* Phone: full-width card with 16px page gutters. */
  @media (max-width: 640px) {
    .page {
      padding: 0 16px;
      align-items: start;
      padding-top: var(--space-3xl);
    }
    .card {
      max-width: none;
    }
  }
</style>
