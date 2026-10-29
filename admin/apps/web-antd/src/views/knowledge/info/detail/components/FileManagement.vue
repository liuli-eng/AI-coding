<script setup lang="ts">
import DocumentWorkbench from '#/views/knowledge/attach/components/DocumentWorkbench.vue';

defineProps<{ knowledgeId?: number | string }>();
</script>
<template>
  <div class="p-2"><DocumentWorkbench :knowledge-id="knowledgeId" /></div>
</template>
