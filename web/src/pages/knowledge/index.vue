<script setup lang="ts">
import type { KnowledgeBaseSummary } from '@/api/knowledge';
import { getKnowledgeBases } from '@/api/knowledge';
import { useChatStore } from '@/stores/modules/chat';
import { useSessionStore } from '@/stores/modules/session';
import { useUserStore } from '@/stores/modules/user';

const userStore = useUserStore();
const sessionStore = useSessionStore();
const chatStore = useChatStore();
const keyword = ref('');
const page = ref(1);
const total = ref(0);
const rows = ref<KnowledgeBaseSummary[]>([]);
const loading = ref(false);
const error = ref('');
const opening = ref(false);
let sequence = 0;
async function load() {
  const current = ++sequence;
  error.value = '';
  rows.value = [];
  total.value = 0;
  if (!userStore.token) {
    loading.value = false;
    return;
  }
  loading.value = true;
  try {
    const result = await getKnowledgeBases({ keyword: keyword.value.trim(), pageNum: page.value, pageSize: 12 });
    if (current !== sequence)
      return;
    rows.value = result.rows;
    total.value = result.total;
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
function search() {
  page.value = 1;
  load();
}
async function startChat(base: KnowledgeBaseSummary) {
  if (opening.value)
    return;
  opening.value = true;
  try {
    await sessionStore.createSessionBtn();
    chatStore.setDraftKnowledge(base);
  }
  finally { opening.value = false; }
}
watch(() => userStore.token, search, { immediate: true, flush: 'sync' });
onMounted(() => window.addEventListener('focus', load));
onBeforeUnmount(() => {
  sequence++;
  window.removeEventListener('focus', load);
});
</script>

<template>
  <main class="knowledge-workbench">
    <header>
      <div>
        <h1>知识库</h1>
        <p>选择知识库，结合资料开始编程对话。</p>
      </div>
      <el-button :loading="loading" :disabled="!userStore.token" @click="load">
        刷新
      </el-button>
    </header>
    <el-empty v-if="!userStore.token" description="登录后查看可用知识库">
      <el-button type="primary" @click="userStore.ensureLogin('/knowledge', '登录后即可使用知识库')">
        登录
      </el-button>
    </el-empty>
    <template v-else>
      <form class="knowledge-search" @submit.prevent="search">
        <el-input v-model="keyword" maxlength="100" clearable placeholder="搜索知识库名称" aria-label="搜索知识库名称" @clear="search" />
        <el-button native-type="submit" :loading="loading">
          搜索
        </el-button>
      </form>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon role="alert">
        <el-button link @click="load">
          重试
        </el-button>
      </el-alert>
      <div v-if="loading" class="knowledge-state" role="status" aria-busy="true">
        <el-skeleton :rows="5" animated />
        <p>正在加载知识库…</p>
      </div>
      <template v-else-if="!error">
        <p class="result-count" aria-live="polite">
          共 {{ total }} 个知识库
        </p>
        <section aria-label="可用知识库" class="knowledge-list">
          <article v-for="base in rows" :key="base.id">
            <div class="knowledge-copy">
              <h2>{{ base.name }}</h2>
              <p>{{ base.description || '暂无说明' }}</p>
              <p class="metadata">
                {{ base.documentCount }} 份文档 · 更新于 {{ base.updateTime || '—' }}
              </p>
            </div>
            <el-button :disabled="opening" :aria-label="`使用${base.name}开始对话`" @click="startChat(base)">
              开始对话
            </el-button>
          </article>
        </section>
        <el-empty v-if="!rows.length" :description="keyword.trim() ? '没有找到匹配的知识库，请更换搜索词' : '暂无可用知识库'" />
        <el-pagination v-if="total > 12" v-model:current-page="page" :total="total" :page-size="12" :pager-count="5" layout="prev,pager,next" aria-label="知识库分页" @current-change="load" />
      </template>
    </template>
  </main>
</template>

<style scoped>
.knowledge-workbench { box-sizing: border-box; width: 100%; max-width: 1120px; margin: 0 auto; padding: var(--workspace-top-gap, 24px) var(--workspace-padding, 24px); color: var(--el-text-color-primary); }
header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 24px; }
h1 { margin: 0; font-size: 26px; font-weight: 600; }
header p, .metadata, .result-count { margin-top: 8px; color: var(--el-text-color-regular); font-size: 13px; line-height: 1.7; }
.knowledge-search { display: flex; gap: 12px; max-width: 560px; margin-bottom: 20px; }
.knowledge-state { padding: 24px 0; }
.knowledge-state p { margin-top: 20px; font-size: 14px; color: var(--el-text-color-regular); }
.knowledge-list article { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 24px 0; border-bottom: 1px solid var(--el-border-color-light); }
.knowledge-copy { min-width: 0; max-width: 70ch; }
h2 { margin: 0 0 8px; font-size: 18px; font-weight: 600; overflow-wrap: anywhere; }
.knowledge-copy p { line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }
.el-pagination { margin-top: 24px; }
@media (max-width: 700px) {
  .knowledge-workbench { padding: 20px 16px; }
  .knowledge-list article { align-items: flex-start; flex-direction: column; gap: 12px; }
}
</style>
