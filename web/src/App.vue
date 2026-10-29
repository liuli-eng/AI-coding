<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useUserStore } from '@/stores';
import { loadSystemName, resetSystemName, systemName } from '@/stores/modules/application';

const user = useUserStore();
const route = useRoute();

function refresh() {
  loadSystemName(user.token);
}

watch(() => user.token, () => {
  resetSystemName();
  refresh();
}, { immediate: true });

watch([systemName, () => route.meta.title], () => {
  document.title = route.meta.title ? `${route.meta.title} · ${systemName.value}` : systemName.value;
}, { immediate: true });

window.addEventListener('focus', refresh);
onBeforeUnmount(() => {
  window.removeEventListener('focus', refresh);
  resetSystemName();
});
</script>

<template>
  <router-view />
</template>
