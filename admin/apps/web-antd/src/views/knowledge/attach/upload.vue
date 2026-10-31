<script setup lang="ts">
import { Page } from '@vben/common-ui';

import DocumentWorkbench from './components/DocumentWorkbench.vue';
</script>
<template>
  <Page>
    <div class="bg-background rounded-lg p-5">
      <DocumentWorkbench start-upload />
    </div>
  </Page>
</template>
