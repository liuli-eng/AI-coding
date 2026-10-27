<script setup lang="ts">
import type { WorkflowInfo } from '#/packages/workflow-designer/types/index.d';

import {
  computed,
  onActivated,
  onBeforeUnmount,
  onDeactivated,
  onMounted,
  shallowRef,
} from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { ArrowLeftOutlined, ExperimentOutlined } from '@ant-design/icons-vue';
import { useScrollLock } from '@vueuse/core';
import { Button, Empty, message, Spin } from 'ant-design-vue';

import { workflowApi } from '#/api/aiflow';
import RunDetail from '#/packages/workflow-designer/components/RunDetail.vue';

const router = useRouter();
const route = useRoute();
const isPageScrollLocked = useScrollLock(document.body);

const workflow = shallowRef<WorkflowInfo>({
  uuid: '',
  title: '工作流',
  nodes: [],
  edges: [],
});
const loading = shallowRef(true);

const workflowTitle = computed(() => workflow.value.title || '未命名工作流');

function getErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : '加载工作流失败';
}

function handleBack() {
  const uuid = workflow.value.uuid || (route.params.uuid as string);
  if (uuid) {
    router.push({
      name: 'WorkflowEdit',
      params: { uuid },
    });
    return;
  }
  router.push('/chat/aiflow');
}

async function loadWorkflow() {
  const uuid = route.params.uuid as string;
  if (!uuid) {
    message.error('工作流 ID 不存在');
    handleBack();
    return;
  }

  try {
    loading.value = true;
    workflow.value = await workflowApi.workflowGet(uuid);
  } catch (error: unknown) {
    message.error(getErrorMessage(error));
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  isPageScrollLocked.value = true;
  loadWorkflow();
});

onActivated(() => {
  isPageScrollLocked.value = true;
});

onDeactivated(() => {
  isPageScrollLocked.value = false;
});

onBeforeUnmount(() => {
  isPageScrollLocked.value = false;
});
</script>

<template>
  <Page
    :auto-content-height="true"
    class="workflow-run-page"
    content-class="workflow-run-content"
    header-class="workflow-run-header"
  >
    <template #title>
      <div class="page-heading">
        <Button class="back-button" type="text" @click="handleBack">
          <template #icon>
            <ArrowLeftOutlined />
          </template>
          返回设计
        </Button>
        <div class="heading-divider"></div>
        <div class="heading-copy">
          <div class="heading-eyebrow">
            <ExperimentOutlined />
            <span>测试工作流</span>
          </div>
          <h1 class="heading-title">{{ workflowTitle }}</h1>
        </div>
      </div>
    </template>

    <div v-if="loading" class="state-panel">
      <Spin size="large" tip="正在加载工作流..." />
    </div>

    <main v-else-if="workflow.uuid" class="run-workspace">
      <section class="workspace-intro">
        <div>
          <h2 class="workspace-title">运行与调试</h2>
          <p class="workspace-description">
            填写起始节点所需参数并运行，执行过程和节点输出将在下方实时展示。
          </p>
        </div>
      </section>

      <RunDetail :workflow="workflow" />
    </main>

    <div v-else class="state-panel">
      <Empty description="工作流加载失败">
        <template #description>
          <div class="empty-description">
            <span>工作流加载失败，请返回设计页面后重试</span>
            <Button type="primary" @click="handleBack">返回设计</Button>
          </div>
        </template>
      </Empty>
    </div>
  </Page>
</template>

<style scoped>
.workflow-run-page {
  height: 100vh;
  max-height: 100vh;
  overflow: hidden;
  min-height: 0;
  background:
    radial-gradient(circle at 8% 0%, rgb(37 99 235 / 8%), transparent 28rem),
    #f5f7fb;
}

:deep(.workflow-run-content) {
  min-height: 0;
  flex: 1;
  overflow: hidden;
  padding: 0;
  background: transparent;
}

:deep(.workflow-run-header) {
  align-items: center;
  min-height: 76px;
  padding: 14px 24px;
  background: rgb(255 255 255 / 92%);
  backdrop-filter: blur(12px);
}

.page-heading,
.heading-eyebrow {
  display: flex;
  align-items: center;
}

.page-heading {
  min-width: 0;
  gap: 18px;
}

.back-button {
  height: 36px;
  padding-inline: 8px 12px;
  color: #334155;
  font-weight: 500;
}

.back-button:hover {
  color: #1677ff;
  background: #eff6ff;
}

.heading-divider {
  width: 1px;
  height: 32px;
  background: #e2e8f0;
}

.heading-copy {
  min-width: 0;
}

.heading-eyebrow {
  gap: 6px;
  margin-bottom: 3px;
  color: #64748b;
  font-size: 12px;
  line-height: 1;
}

.heading-title {
  overflow: hidden;
  max-width: min(48vw, 680px);
  margin: 0;
  color: #172033;
  font-size: 19px;
  font-weight: 650;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.run-workspace {
  display: flex;
  box-sizing: border-box;
  min-height: 0;
  width: min(1180px, calc(100% - 48px));
  height: 100%;
  flex-direction: column;
  margin: 0 auto;
  padding: 24px 0;
}

.workspace-intro {
  display: flex;
  flex: none;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 16px;
}

.workspace-title {
  margin: 0 0 5px;
  color: #172033;
  font-size: 20px;
  font-weight: 650;
}

.workspace-description {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.state-panel {
  display: flex;
  min-height: 420px;
  align-items: center;
  justify-content: center;
}

.empty-description {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

@media (max-width: 768px) {
  :deep(.workflow-run-header) {
    padding-inline: 14px;
  }

  .page-heading {
    gap: 10px;
  }

  .heading-divider {
    display: none;
  }

  .back-button {
    padding-inline: 6px;
  }

  .run-workspace {
    width: calc(100% - 24px);
    padding-top: 20px;
  }

  .workspace-intro {
    align-items: flex-start;
  }
}
</style>
