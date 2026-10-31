<script setup lang="ts">
import type { KnowledgeBaseSummary } from '@/api/knowledge';
import { getKnowledgeBase, getKnowledgeBases } from '@/api/knowledge';
import { useChatStore } from '@/stores/modules/chat';
import { useUserStore } from '@/stores/modules/user';

const chatStore = useChatStore();
const userStore = useUserStore();
const options = ref<KnowledgeBaseSummary[]>([]);
const loading = ref(false);
const error = ref('');
const selectionError = ref('');
let selectionSequence = 0;
const keyword = ref('');
let sequence = 0;
let searchTimer: ReturnType<typeof setTimeout> | undefined;
const selectedId = computed({
  get: () => chatStore.draftKnowledge?.id || '',
  set: (id: string) => chatStore.setDraftKnowledge(options.value.find(item => item.id === id) || null),
});
const visibleOptions = computed(() => {
  const selected = chatStore.draftKnowledge;
  return selected && !options.value.some(item => item.id === selected.id) ? [selected, ...options.value] : options.value;
});
async function load() {
  const current = ++sequence;
  error.value = '';
  options.value = [];
  if (!userStore.token) {
    options.value = [];
    loading.value = false;
    return;
  }
  loading.value = true;
  try {
    const result = await getKnowledgeBases({ keyword: keyword.value, pageNum: 1, pageSize: 100 });
    if (current === sequence)
      options.value = result.rows;
  }
  catch {
    if (current === sequence)
      error.value = '知识库加载失败，请重试。';
  }
  finally {
    if (current === sequence)
      loading.value = false;
  }
}
async function validateSelected() {
  const current = ++selectionSequence;
  const id = chatStore.draftKnowledge?.id;
  selectionError.value = '';
  if (!id || !userStore.token)
    return;
  try {
    await getKnowledgeBase(id);
  }
  catch (error) {
    if (current === selectionSequence)
      selectionError.value = error instanceof Error && error.message === '知识库已停用' ? '知识库已停用，请更换知识库或稍后重试。' : '所选知识库暂不可用，请重试或更换知识库。';
  }
}
function refresh() {
  load();
  validateSelected();
}
function onVisibleChange(visible: boolean) {
  if (visible)
    refresh();
}
function search(value: string) {
  keyword.value = value.trim().slice(0, 100);
  clearTimeout(searchTimer);
  searchTimer = setTimeout(load, 250);
}
watch(() => userStore.token, load, { immediate: true, flush: 'sync' });
watch([() => chatStore.draftKnowledge?.id, () => userStore.token], validateSelected, { immediate: true, flush: 'sync' });
onMounted(() => window.addEventListener('focus', refresh));
onBeforeUnmount(() => {
  sequence++;
  selectionSequence++;
  window.removeEventListener('focus', refresh);
  clearTimeout(searchTimer);
});
</script>

<template>
  <div class="knowledge-picker">
    <label for="chat-knowledge">知识库（可选）</label>
    <el-select
      id="chat-knowledge"
      v-model="selectedId"
      clearable
      filterable
      remote
      :remote-method="search"
      :loading="loading"
      placeholder="不使用知识库"
      no-data-text="暂无可用知识库，可更换搜索词"
      aria-label="知识库（可选）"
      @visible-change="onVisibleChange"
    >
      <el-option v-for="base in visibleOptions" :key="base.id" :value="base.id" :label="base.name" :disabled="!!selectionError && base.id === selectedId" />
    </el-select>
    <p v-if="selectionError || error" role="alert">
      {{ selectionError || error }} <el-button link @click="refresh">
        重试
      </el-button>
    </p>
    <p v-else class="knowledge-hint">
      选定后会随本次会话保存。<router-link to="/knowledge">
        浏览知识库
      </router-link>
    </p>
  </div>
</template>

<style scoped>
.knowledge-picker { width: 100%; margin-bottom: 16px; }
label { display: block; margin-bottom: 8px; font-size: 13px; }
p { margin-top: 8px; font-size: 13px; line-height: 1.6; color: var(--el-text-color-regular); }
a { color: var(--el-color-primary); }
</style>
