<script setup lang="ts">
import type { NodeProps } from '@vue-flow/core';

import { computed } from 'vue';

import { Handle, Position } from '@vue-flow/core';

import CommonNodeHeader from '../../components/CommonNodeHeader.vue';

const props = defineProps<NodeProps>();

const engineLabel = computed(() => {
  const labels: Record<string, string> = {
    search_pro: '高阶搜索',
    search_pro_quark: '高阶搜索 · 夸克',
    search_pro_sogou: '高阶搜索 · 搜狗',
    search_std: '基础搜索',
  };
  return labels[props.data.nodeConfig?.search_engine] || '基础搜索';
});

const recencyLabel = computed(() => {
  const labels: Record<string, string> = {
    noLimit: '不限时间',
    oneDay: '一天内',
    oneMonth: '一个月内',
    oneWeek: '一周内',
    oneYear: '一年内',
  };
  return labels[props.data.nodeConfig?.search_recency_filter] || '不限时间';
});
</script>

<template>
  <div class="flex w-full flex-col">
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
    <CommonNodeHeader :wf-node="data" />
    <div class="flex-1 flex-col">
      <div class="content_line px-3 text-left">引擎：{{ engineLabel }}</div>
      <div class="content_line px-3 text-left">
        结果：{{ data.nodeConfig?.result_count ?? 10 }} 条
      </div>
      <div class="content_line px-3 text-left">时间：{{ recencyLabel }}</div>
    </div>
  </div>
</template>
