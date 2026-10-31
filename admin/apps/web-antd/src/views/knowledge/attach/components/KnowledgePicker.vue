<script setup lang="ts">
import type { KnowledgeOption } from '#/api/knowledge/attach/workbench';

import { onBeforeUnmount, ref, watch } from 'vue';

import { useAccessStore } from '@vben/stores';

import { Button, Select } from 'ant-design-vue';

import { knowledgeOptions } from '#/api/knowledge/attach/workbench';

const props = withDefaults(
  defineProps<{ disabled?: boolean; label?: string }>(),
  { label: '选择知识库' },
);
const access = useAccessStore();
const value = defineModel<number | string>();
const options = ref<KnowledgeOption[]>([]);
const loading = ref(false);
const error = ref('');
const total = ref(0);
let keyword = '';
let page = 1;
let request: AbortController | undefined;
let timer: ReturnType<typeof setTimeout> | undefined;
async function load(append = false) {
  request?.abort();
  const controller = new AbortController();
  request = controller;
  loading.value = true;
  error.value = '';
  try {
    const data = await knowledgeOptions(
      keyword,
      append ? page + 1 : 1,
      controller.signal,
    );
    if (controller.signal.aborted) return;
    page = append ? page + 1 : 1;
    options.value = append ? [...options.value, ...data.rows] : data.rows;
    total.value = data.total;
  } catch {
    if (!controller.signal.aborted) error.value = '知识库加载失败，点击重试';
  } finally {
    if (request === controller) loading.value = false;
  }
}
function scroll(event: Event) {
  const target = event.target;
  if (
    target instanceof HTMLElement &&
    target.scrollTop + target.clientHeight >= target.scrollHeight - 20 &&
    !loading.value &&
    options.value.length < total.value
  )
    load(true);
}
function search(text: string) {
  keyword = text;
  clearTimeout(timer);
  timer = setTimeout(() => load(), 250);
}
watch(
  () => access.accessToken,
  () => {
    request?.abort();
    clearTimeout(timer);
    options.value = [];
    error.value = '';
    total.value = 0;
    keyword = '';
    page = 1;
    value.value = undefined;
  },
);
load();
onBeforeUnmount(() => {
  request?.abort();
  clearTimeout(timer);
});
</script>
<template>
  <div class="min-w-0">
    <Select
      v-model:value="value"
      :aria-label="props.label"
      :placeholder="props.label"
      :disabled="disabled"
      :loading="loading"
      :filter-option="false"
      :options="
        options.map((item) => ({
          value: item.id,
          label: item.name + (item.status === '1' ? '（已停用）' : ''),
        }))
      "
      show-search
      allow-clear
      class="w-full"
      @search="search"
      @dropdown-visible-change="
        (open) => {
          if (open && options.length === 0) load();
        }
      "
      @popup-scroll="scroll"
    />
    <Button v-if="error" type="link" danger size="small" @click="load()">
      {{ error }}
    </Button>
  </div>
</template>
