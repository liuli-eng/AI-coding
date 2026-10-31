<script setup lang="ts">
import { getKnowledgeBase } from '@/api/knowledge';
import { useUserStore } from '@/stores/modules/user';

const props = defineProps<{ knowledgeId?: string | null }>();
const userStore = useUserStore();
const name = ref('');
const loading = ref(false);
const unavailable = ref('');
let sequence = 0;
async function load() {
  const current = ++sequence;
  name.value = '';
  unavailable.value = '';
  loading.value = !!props.knowledgeId;
  if (!props.knowledgeId || !userStore.token) {
    loading.value = false;
    return;
  }
  try {
    const result = await getKnowledgeBase(props.knowledgeId);
    if (current === sequence)
      name.value = result.data.name;
  }
  catch (error) {
    if (current === sequence)
      unavailable.value = error instanceof Error && error.message === '知识库已停用' ? '知识库已停用' : '该知识库暂不可用';
  }
  finally {
    if (current === sequence)
      loading.value = false;
  }
}
watch([() => props.knowledgeId, () => userStore.token], load, { immediate: true, flush: 'sync' });
onMounted(() => window.addEventListener('focus', load));
onBeforeUnmount(() => {
  sequence++;
  window.removeEventListener('focus', load);
});
</script>

<template>
  <p class="session-knowledge" aria-live="polite">
    知识库：<span v-if="loading">正在加载…</span><template v-else-if="unavailable">
      {{ unavailable }} <el-button link @click="load">
        重试
      </el-button>
    </template><span v-else>{{ name || '未使用知识库' }}</span>
  </p>
</template>

<style scoped>
.session-knowledge { margin: 0 0 8px; color: var(--el-text-color-regular); font-size: 13px; line-height: 1.6; overflow-wrap: anywhere; }
</style>
