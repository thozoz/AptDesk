<script>
  import Empty from './Empty.svelte';

  // Honest placeholder for out-of-scope views — real content lands in Phase 6.
  export let title = '';
</script>

<Empty heading="Nothing here yet" body={`${title} view — coming in Phase 6.`} />
