<script setup lang="ts">
import type { MaterialSummary } from '@/api/learning';
import { useRoute } from 'vue-router';
import { downloadSessionMaterial, sessionMaterials } from '@/api/learning';
import CourseMaterials from '@/components/CourseMaterials/index.vue';
import { useUserStore } from '@/stores/modules/user';

const props = defineProps<{ sessionId: string }>();
const user = useUserStore();
const route = useRoute();
const materials = ref<MaterialSummary[]>([]);
const error = ref('');
let sequence = 0;
let controller: AbortController | undefined;
async function load() {
  controller?.abort();
  const pending = new AbortController();
  controller = pending;
  const current = ++sequence;
  const id = props.sessionId;
  const token = user.token;
  materials.value = [];
  error.value = '';
  if (!id || !token)
    return;
  try {
    const result = await sessionMaterials(id, pending.signal, () => current === sequence && token === user.token && id === props.sessionId && id === String(route.params.id || ''));
    if (current === sequence && token === user.token)
      materials.value = result.data || [];
  }
  catch (cause) {
    if (current === sequence && token === user.token)
      error.value = cause instanceof Error ? cause.message : '资料加载失败，请重试。';
  }
}
watch([() => props.sessionId, () => user.token], load, { immediate: true, flush: 'sync' });
onMounted(() => window.addEventListener('focus', load));
onBeforeUnmount(() => {
  window.removeEventListener('focus', load);
  sequence++;
  controller?.abort();
});
</script>

<template>
  <CourseMaterials :materials="materials" :scope-key="sessionId" :download="(id, signal) => downloadSessionMaterial(sessionId, id, signal)" />
  <el-alert v-if="error" :title="`课程资料加载失败：${error}`" type="error" :closable="false" role="alert">
    <el-button link @click="load">
      重试
    </el-button>
  </el-alert>
</template>
