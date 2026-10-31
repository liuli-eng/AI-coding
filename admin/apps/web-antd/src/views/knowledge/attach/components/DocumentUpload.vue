<script setup lang="ts">
import type { KnowledgeSettings } from '#/api/knowledge/settings';

import { computed, onBeforeUnmount, ref, watch } from 'vue';

import { useAccessStore } from '@vben/stores';

import { InboxOutlined } from '@ant-design/icons-vue';
import {
  Alert,
  Button,
  Checkbox,
  message,
  Progress,
  Upload,
} from 'ant-design-vue';

import { documentUpload, uploadRules } from '#/api/knowledge/attach/workbench';

import KnowledgePicker from './KnowledgePicker.vue';

const props = defineProps<{ knowledgeId?: number | string }>();
const emit = defineEmits<{ uploaded: [] }>();
interface Item {
  id: number;
  file: File;
  state: 'done' | 'error' | 'ready' | 'uploading';
  progress: number;
  error: string;
}
const access = useAccessStore();
const selected = ref(props.knowledgeId);
const policy = ref<KnowledgeSettings>();
const queue = ref<Item[]>([]);
const busy = ref(false);
const autoParse = ref(true);
const rulesError = ref('');
const accept = computed(() =>
  policy.value?.allowedTypes.map((type) => `.${type}`).join(','),
);
let sequence = 0;
let generation = 0;
const controllers = new Set<AbortController>();
function controller() {
  const value = new AbortController();
  controllers.add(value);
  return value;
}
async function loadRules() {
  const run = generation;
  const request = controller();
  rulesError.value = '';
  try {
    const data = await uploadRules(request.signal);
    if (run === generation) policy.value = data;
  } catch {
    if (run === generation) rulesError.value = '上传规则加载失败，请重试';
  } finally {
    controllers.delete(request);
  }
}
function add(file: File) {
  if (busy.value) return false;
  const suffix = file.name.split('.').at(-1)?.toLowerCase() || '';
  let error = '';
  if (file.size === 0) error = '文件为空';
  else if (file.size > 20 * 1024 * 1024) error = '单个文档不能超过20MB';
  else if (!policy.value?.allowedTypes.includes(suffix))
    error = '此文件格式未启用';
  queue.value.push({
    id: ++sequence,
    file,
    state: error ? 'error' : 'ready',
    progress: 0,
    error,
  });
  return false;
}
async function upload() {
  const knowledgeId = props.knowledgeId ?? selected.value;
  if (!knowledgeId || !policy.value || busy.value) return;
  busy.value = true;
  const run = generation;
  let successes = 0;
  // A retry only submits failed/pending files; successful files stay completed.
  for (const item of queue.value.filter((item) => item.state !== 'done')) {
    if (run !== generation) break;
    if (
      item.file.size === 0 ||
      item.file.size > 20 * 1024 * 1024 ||
      !policy.value.allowedTypes.includes(
        item.file.name.split('.').at(-1)?.toLowerCase() || '',
      )
    )
      continue;
    const request = controller();
    item.state = 'uploading';
    item.error = '';
    try {
      await documentUpload(
        item.file,
        knowledgeId,
        autoParse.value,
        request.signal,
        (value) => {
          if (run === generation) item.progress = value;
        },
      );
      if (run !== generation) break;
      item.state = 'done';
      item.progress = 100;
      successes++;
    } catch (error) {
      if (run !== generation) break;
      item.state = 'error';
      item.error = error instanceof Error ? error.message : '上传失败，请重试';
    } finally {
      controllers.delete(request);
    }
  }
  if (run === generation) {
    busy.value = false;
    if (successes) {
      message.success(`已上传 ${successes} 个文档`);
      emit('uploaded');
    }
  }
}
function clear() {
  queue.value = queue.value.filter((item) => item.state !== 'done');
}
function cancel() {
  generation++;
  controllers.forEach((item) => item.abort());
  controllers.clear();
  queue.value = [];
  busy.value = false;
  policy.value = undefined;
}
watch(
  () => props.knowledgeId,
  (value) => {
    cancel();
    selected.value = value;
    loadRules();
  },
);
watch(
  () => access.accessToken,
  () => {
    cancel();
    selected.value = props.knowledgeId;
    if (access.accessToken) loadRules();
  },
);
onBeforeUnmount(cancel);
loadRules();
</script>
<template>
  <section aria-label="上传文档" class="document-upload">
    <h3 class="mb-3 text-base font-semibold">上传文档</h3>
    <Alert v-if="rulesError" type="error" :message="rulesError" class="mb-3">
      <template #action>
        <Button size="small" @click="loadRules">重试</Button>
      </template>
    </Alert>
    <template v-if="policy">
      <KnowledgePicker
        v-if="!knowledgeId"
        v-model="selected"
        :disabled="busy || queue.some((item) => item.state === 'done')"
        class="mb-3 max-w-md"
      />
      <p class="text-muted-foreground mb-3 text-sm">
        单个文件最大20MB。{{
          policy.overwriteSameName
            ? '同名文件会被覆盖，解析中的文件除外。'
            : '同名文件不会覆盖，请更名后上传。'
        }}相同内容不重复保存。
      </p>
      <Upload.Dragger
        :before-upload="add"
        :accept="accept"
        :disabled="busy"
        :show-upload-list="false"
        multiple
      >
        <p class="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p class="ant-upload-text">点击或拖入文件</p>
        <p class="ant-upload-hint break-words">
          允许格式：{{ policy.allowedTypes.join('、') }}
        </p>
      </Upload.Dragger>
      <ul
        v-if="queue.length > 0"
        class="mt-3 divide-y"
        aria-label="上传队列"
        aria-live="polite"
      >
        <li
          v-for="item in queue"
          :key="item.id"
          class="flex flex-wrap items-center gap-3 py-3"
        >
          <span class="min-w-0 flex-1 break-all">{{ item.file.name }}</span>
          <span
            v-if="item.state === 'done'"
            class="text-green-700 dark:text-green-400"
          >
            上传成功
          </span>
          <span
            v-else-if="item.state === 'error'"
            role="alert"
            class="text-red-700 dark:text-red-400"
          >
            {{ item.error }}
          </span>
          <Progress
            v-else-if="item.state === 'uploading'"
            :percent="item.progress"
            class="!w-28"
          />
          <span v-else>待上传</span>
          <Button
            v-if="item.state !== 'done'"
            :disabled="busy"
            size="small"
            @click="queue = queue.filter((row) => row.id !== item.id)"
          >
            移除
          </Button>
        </li>
      </ul>
      <div class="mt-4 flex flex-wrap items-center gap-3">
        <Checkbox v-model:checked="autoParse" :disabled="busy">
          上传后自动解析
        </Checkbox>
        <Button
          type="primary"
          :loading="busy"
          :disabled="
            !(knowledgeId || selected) ||
            !queue.some((item) => item.state !== 'done')
          "
          @click="upload"
        >
          {{
            queue.some((item) => item.state === 'error')
              ? '重试未成功文件'
              : '开始上传'
          }}
        </Button>
        <Button
          v-if="queue.some((item) => item.state === 'done')"
          :disabled="busy"
          @click="clear"
        >
          清除已成功项
        </Button>
      </div>
    </template>
  </section>
</template>
<style scoped>
.document-upload {
  padding: 20px;
  border: 1px solid hsl(var(--border));
  border-radius: 8px;
}
</style>
