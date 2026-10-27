<script setup lang="ts">
import { computed } from 'vue';

import { Handle, Position } from '@vue-flow/core';

import CommonNodeHeader from '../../components/CommonNodeHeader.vue';

interface Props {
  data: any;
  hasTarget?: boolean;
  hasSource?: boolean;
  extra?: string;
}
const props = withDefaults(defineProps<Props>(), {
  hasTarget: true,
  hasSource: true,
  extra: '',
});

function cut(val: string, n = 30) {
  if (!val) return '';
  return val.length > n ? `${val.slice(0, n)}...` : val;
}

const lines = computed(() => {
  const n = (props.data?.wfComponent?.name || '').toLowerCase();
  const c = props.data?.nodeConfig || {};
  switch (n) {
    case 'answer': {
      return [`${c.model_name || ''}`];
    }
    case 'dalle3': {
      return [`尺寸： ${c.size || ''}`, `质量： ${c.quality || ''}`];
    }
    case 'faqextractor': {
      return [`抽取数量： ${c.top_n ?? 5}`, `模型： ${c.model_name || ''}`];
    }
    case 'google': {
      return [
        `引擎： ${
          {
            search_pro: '高阶搜索',
            search_pro_quark: '高阶搜索 · 夸克',
            search_pro_sogou: '高阶搜索 · 搜狗',
            search_std: '基础搜索',
          }[c.search_engine as string] || '基础搜索'
        }`,
        `结果： ${c.result_count ?? 10} 条`,
        `时间： ${
          {
            noLimit: '不限时间',
            oneDay: '一天内',
            oneMonth: '一个月内',
            oneWeek: '一周内',
            oneYear: '一年内',
          }[c.search_recency_filter as string] || '不限时间'
        }`,
      ];
    }
    case 'httprequest': {
      return [`${c.method || 'GET'} ${cut(c.url || '')}`];
    }
    case 'knowledgeretrieval': {
      return [
        `知识库： ${c.knowledge_base_name || c.knowledge_base_uuid || ''}`,
        `数量： ${c.top_n ?? 3}  分数： ${c.score ?? 0.6}`,
        `严格模式： ${c.is_strict ? '是' : '否'}`,
      ];
    }
    case 'mailsend': {
      return [
        `收件人： ${cut(c.to_mails || '')}`,
        `主题： ${cut(c.subject || '')}`,
      ];
    }
    case 'switcher': {
      return [`分支数： ${Array.isArray(c.cases) ? c.cases.length : 0}`];
    }
    case 'tongyiwanx': {
      return [`模型： ${c.model_name || ''}`, `尺寸： ${c.size || ''}`];
    }
    default: {
      return props.extra ? [props.extra] : [];
    }
  }
});
</script>

<template>
  <div class="flex w-full flex-col">
    <Handle v-if="hasTarget" type="target" :position="Position.Left" />
    <Handle v-if="hasSource" type="source" :position="Position.Right" />
    <CommonNodeHeader :wf-node="data" />
    <div
      v-for="(line, idx) in lines"
      :key="idx"
      class="content_line px-3 text-left"
    >
      {{ line }}
    </div>
    <div v-if="lines.length === 0 && extra" class="content_line px-3 text-left">
      {{ extra }}
    </div>
  </div>
</template>
