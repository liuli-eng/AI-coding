<script setup lang="ts">
import type { KnowledgeSettings } from '#/api/knowledge/settings';

import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { updatePreferences } from '@vben/preferences';
import { useAccessStore } from '@vben/stores';

import {
  Alert,
  Button,
  Checkbox,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Spin,
  Switch,
  Textarea,
} from 'ant-design-vue';

import {
  getKnowledgeSettings,
  resetKnowledgeSettings,
  saveKnowledgeSettings,
  supportedTypes,
} from '#/api/knowledge/settings';

const access = useAccessStore();
const { hasAccessByCodes } = useAccess();
const canEdit = computed(() => hasAccessByCodes(['knowledge:settings:edit']));
const data = ref<KnowledgeSettings>();
const saved = ref('');
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const dirty = computed(
  () => !!data.value && JSON.stringify(data.value) !== saved.value,
);
let request: AbortController | undefined;
let generation = 0;
function apply(value: KnowledgeSettings) {
  data.value = value;
  saved.value = JSON.stringify(value);
  updatePreferences({ app: { name: value.systemName } });
  window.dispatchEvent(new Event('knowledge-settings-saved'));
}
async function load() {
  request?.abort();
  request = new AbortController();
  const controller = request;
  loading.value = true;
  error.value = '';
  try {
    const value = await getKnowledgeSettings(controller.signal);
    if (!controller.signal.aborted) apply(value);
  } catch (error_) {
    if (!controller.signal.aborted)
      error.value =
        error_ instanceof Error ? error_.message : '设置加载失败，请重试';
  } finally {
    if (request === controller) loading.value = false;
  }
}
function reload() {
  if (!dirty.value) {
    load();
    return;
  }
  Modal.confirm({
    title: '重新加载会丢弃未保存的修改',
    okText: '重新加载',
    onOk: load,
  });
}
async function save(reset = false) {
  if (!data.value || !canEdit.value || busy.value) return;
  if (
    !reset &&
    (!data.value.systemName.trim() ||
      data.value.allowedTypes.length === 0 ||
      !data.value.failureMessage.trim() ||
      data.value.similarityThreshold === undefined ||
      data.value.similarityThreshold === null)
  ) {
    error.value = '请填写名称、允许格式、失败提示和相似度阈值';
    return;
  }
  busy.value = true;
  error.value = '';
  const run = generation;
  request?.abort();
  request = new AbortController();
  const controller = request;
  try {
    const value = reset
      ? await resetKnowledgeSettings(data.value.version, controller.signal)
      : await saveKnowledgeSettings(data.value, controller.signal);
    if (run !== generation || controller.signal.aborted) return;
    apply(value);
    message.success(reset ? '已恢复默认设置' : '设置已保存');
  } catch (error_) {
    if (run === generation && !controller.signal.aborted)
      error.value =
        error_ instanceof Error
          ? error_.message
          : '保存失败，修改内容已保留，请重试';
  } finally {
    if (run === generation) busy.value = false;
  }
}
function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value) {
    event.preventDefault();
    event.returnValue = '';
  }
}
window.addEventListener('beforeunload', beforeUnload);
onBeforeRouteLeave(
  () =>
    !dirty.value ||
    new Promise<boolean>((resolve) =>
      Modal.confirm({
        title: '还有未保存的修改，确定离开？',
        okText: '离开',
        cancelText: '继续编辑',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      }),
    ),
);
watch(
  () => access.accessToken,
  () => {
    generation++;
    request?.abort();
    data.value = undefined;
    saved.value = '';
    busy.value = false;
    if (access.accessToken) load();
  },
);
onBeforeUnmount(() => {
  generation++;
  request?.abort();
  window.removeEventListener('beforeunload', beforeUnload);
});
load();
</script>
<template>
  <Page>
    <section class="bg-background rounded-lg p-5" aria-label="系统设置">
      <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
        <h2 class="m-0 text-lg font-semibold">系统设置</h2>
        <Button :disabled="busy" @click="reload">重新加载</Button>
      </div>
      <Alert v-if="error" :message="error" type="error" show-icon class="mb-5">
        <template #action>
          <Button size="small" :disabled="busy" @click="reload">
            重新加载
          </Button>
        </template>
      </Alert>
      <Spin :spinning="loading">
        <Form
          v-if="data"
          layout="vertical"
          :model="data"
          class="max-w-3xl"
          :disabled="busy || !canEdit"
        >
          <FormItem label="系统名称" required>
            <Input
              v-model:value="data.systemName"
              aria-label="系统名称"
              :maxlength="100"
              show-count
            />
            <p class="text-muted-foreground mb-0 mt-1 text-sm">
              显示在管理端和客户端。客户端切回窗口时会更新。
            </p>
          </FormItem>
          <FormItem label="知识库允许上传的格式" required>
            <Checkbox.Group
              v-model:value="data.allowedTypes"
              :options="
                supportedTypes.map((value) => ({
                  label: value.toUpperCase(),
                  value,
                }))
              "
              class="formats"
            />
            <p class="text-muted-foreground mb-0 mt-2 text-sm">
              至少保留一种格式。单个文档最大20MB；此设置不影响课程资料上传。
            </p>
          </FormItem>
          <FormItem label="同名文件覆盖">
            <Switch
              v-model:checked="data.overwriteSameName"
              aria-label="同名文件覆盖"
            />
            <p class="text-muted-foreground mb-0 mt-2 text-sm">
              开启后，新文件上传成功才替换旧文件；解析中的文档不能覆盖。相同内容始终不重复保存。
            </p>
          </FormItem>
          <FormItem label="解析失败提示" required>
            <Textarea
              v-model:value="data.failureMessage"
              aria-label="解析失败提示"
              :maxlength="255"
              :rows="3"
              show-count
            />
            <p class="text-muted-foreground mb-0 mt-1 text-sm">
              下次解析失败时显示此提示，已记录的失败提示保留。
            </p>
          </FormItem>
          <FormItem label="默认相似度阈值" required>
            <InputNumber
              v-model:value="data.similarityThreshold"
              aria-label="默认相似度阈值"
              :min="0"
              :max="1"
              :step="0.01"
            />
            <p class="text-muted-foreground mb-0 mt-1 text-sm">
              用于新建知识库，取值0到1；已有知识库保持原设置。
            </p>
          </FormItem>
          <div class="mt-6 flex flex-wrap items-center gap-3">
            <Button
              v-if="canEdit"
              type="primary"
              :loading="busy"
              :disabled="!dirty || loading"
              @click="save()"
            >
              保存设置
            </Button>
            <Popconfirm
              v-if="canEdit"
              title="恢复本租户的默认设置？当前修改会被替换。"
              @confirm="save(true)"
            >
              <Button :disabled="busy || loading">恢复默认</Button>
            </Popconfirm>
            <span class="text-muted-foreground text-sm" aria-live="polite">{{
              dirty ? '有未保存的修改' : '当前设置已生效'
            }}</span>
          </div>
          <p class="text-muted-foreground mb-0 mt-4 text-sm">
            最后修改：{{ data.updatedBy || '尚未修改'
            }}<template v-if="data.updateTime">
              · {{ data.updateTime }}
            </template>
          </p>
        </Form>
      </Spin>
    </section>
  </Page>
</template>
<style scoped>
.formats {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(90px, 1fr));
  gap: 10px 16px;
  width: 100%;
}
:deep(input:focus-visible),
:deep(button:focus-visible) {
  outline: 2px solid hsl(var(--primary));
  outline-offset: 3px;
}
</style>
