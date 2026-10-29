<!-- 默认消息列表页 -->
<script setup lang="ts">
import type { PromptOption } from '@/api/prompt';
import { EditPen, Reading, Warning } from '@element-plus/icons-vue';
import { ref } from 'vue';
import { getPrompts } from '@/api/prompt';
import ChatSender from '@/components/ChatSender/index.vue';
import KnowledgePicker from '@/components/KnowledgePicker/index.vue';
import WelecomeText from '@/components/WelecomeText/index.vue';
import { useUserStore } from '@/stores';
import { useAgentStore } from '@/stores/modules/agent';
import { useChatStore } from '@/stores/modules/chat';
import { useSessionStore } from '@/stores/modules/session';

const userStore = useUserStore();
const sessionStore = useSessionStore();
const chatStore = useChatStore();
const agentStore = useAgentStore();
const applicationMode = computed(() => !!chatStore.currentWorkflow || !!agentStore.currentAgentInfo.id);
const creating = ref(false);
const createError = ref('');

const senderValue = ref('');
watch(() => userStore.token, () => {
  senderValue.value = '';
  createError.value = '';
});
const senderRef = ref<InstanceType<typeof ChatSender> | null>(null);
const mode = ref<'learning' | 'programming' | 'debugging'>('programming');

const promptId = ref<string>();
const prompts = ref<PromptOption[]>([]);
const promptsLoading = ref(false);
const promptsError = ref('');
let promptSequence = 0;
async function loadPrompts() {
  const current = ++promptSequence;
  promptId.value = undefined;
  prompts.value = [];
  promptsError.value = '';
  if (!userStore.token) {
    promptsLoading.value = false;
    return;
  }
  promptsLoading.value = true;
  try {
    const result = await getPrompts(mode.value);
    if (current === promptSequence)
      prompts.value = result.data || [];
  }
  catch {
    if (current === promptSequence)
      promptsError.value = '模板加载失败，可重试或使用默认提示词。';
  }
  finally {
    if (current === promptSequence)
      promptsLoading.value = false;
  }
}
watch([mode, () => userStore.token], loadPrompts, { immediate: true });
const modes = [
  { key: 'learning', icon: Reading, title: '教学模式', description: '循序渐进地学习编程知识' },
  { key: 'programming', icon: EditPen, title: '编程助手', description: '编写、解释和优化代码' },
  { key: 'debugging', icon: Warning, title: '分析代码错误', description: '定位问题并给出修复建议' },
];

function selectMode(key: string) {
  if (!['learning', 'programming', 'debugging'].includes(key))
    return;
  mode.value = key as typeof mode.value;
  const prompts: Record<string, string> = {
    learning: '我想学习一项编程知识，请从基础开始讲解：',
    programming: '请帮我编写或优化下面的代码：',
    debugging: '请分析下面的代码错误并给出修复建议：',
  };
  senderValue.value = prompts[key] || '';
}

async function handleSubmit(content: string) {
  if (creating.value || !content.trim())
    return;
  creating.value = true;
  createError.value = '';
  try {
    await sessionStore.createSessionList({
      userId: userStore.userInfo?.userId as number,
      sessionContent: content,
      chatMode: mode.value,
      promptId: applicationMode.value ? undefined : promptId.value,
      knowledgeId: applicationMode.value ? undefined : chatStore.draftKnowledge?.id,
      sessionTitle: content.slice(0, 10),
      remark: content.slice(0, 10),
    });
    senderValue.value = '';
  }
  catch (error) {
    createError.value = error instanceof Error && error.message === '知识库已停用' ? '知识库已停用，请更换知识库。问题已保留。' : '对话创建失败，问题和知识库选择已保留，请重试。';
  }
  finally { creating.value = false; }
}
</script>

<template>
  <div class="chat-defaul-wrap">
    <WelecomeText />
    <p class="welcome-subtitle">
      今天想解决什么编程问题？
    </p>
    <div class="mode-cards">
      <button
        v-for="item in modes"
        :key="item.key"
        class="mode-card"
        :class="{ active: mode === item.key }"
        :aria-pressed="mode === item.key"
        type="button"
        @click="selectMode(item.key)"
      >
        <span class="mode-icon"><component :is="item.icon" /></span>
        <span class="mode-copy"><strong>{{ item.title }}</strong><small>{{ item.description }}</small></span>
      </button>
    </div>
    <div v-if="userStore.token && !applicationMode" class="prompt-picker">
      <label for="chat-prompt">提示词模板</label>
      <el-select id="chat-prompt" v-model="promptId" clearable :loading="promptsLoading" placeholder="默认提示词" no-data-text="该模式暂无可用模板" aria-label="提示词模板">
        <el-option v-for="prompt in prompts" :key="prompt.id" :value="prompt.id" :label="prompt.name" />
      </el-select>
      <p v-if="promptId" class="prompt-description">
        {{ prompts.find(p => p.id === promptId)?.description }}
      </p>
      <p v-if="promptsError" role="alert">
        {{ promptsError }} <el-button link @click="loadPrompts">
          重试
        </el-button>
      </p>
    </div>
    <KnowledgePicker v-if="userStore.token && !applicationMode" />
    <p v-if="createError" class="create-error" role="alert">
      {{ createError }}
    </p>
    <ChatSender
      ref="senderRef"
      v-model="senderValue"
      :loading="creating"
      @submit="handleSubmit"
    />
  </div>
</template>

<style scoped lang="scss">
.chat-defaul-wrap {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  max-width: 800px;
  min-height: 450px;
}

.create-error { width: 100%; margin-bottom: 12px; color: var(--el-color-danger); font-size: 13px; line-height: 1.6; }
.prompt-picker { width: 100%; margin-bottom: 16px; }
.prompt-picker label { display: block; margin-bottom: 8px; font-size: 13px; }
.prompt-picker p { margin-top: 8px; color: var(--el-text-color-secondary); font-size: 13px; }
.welcome-subtitle { margin: -18px 0 24px; color: var(--el-text-color-secondary); font-size: 14px; }
.mode-cards { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; width: 100%; margin-bottom: 20px; }
.mode-card { display: flex; align-items: center; gap: 10px; padding: 14px; text-align: left; background: var(--el-bg-color); border: 1px solid var(--el-border-color-light); border-radius: 10px; cursor: pointer; transition: .2s; }
.mode-card:hover, .mode-card.active { border-color: var(--el-color-primary); box-shadow: 0 2px 10px rgb(64 158 255 / 12%); }
.mode-icon { display: grid; place-items: center; width: 30px; height: 30px; color: var(--el-color-primary); font-size: 20px; }
.mode-icon svg { width: 22px; height: 22px; }
.mode-copy { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.mode-copy strong { color: var(--el-text-color-primary); font-size: 14px; }
.mode-copy small { overflow: hidden; color: var(--el-text-color-secondary); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 700px) { .mode-cards { grid-template-columns: 1fr; } }
</style>
