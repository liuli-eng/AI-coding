<script setup lang="ts">
import type { WorkflowInfo, WorkflowNode } from '../types/index.d';

import { computed } from 'vue';
import { Input } from 'ant-design-vue';
import WfVariableSelector from '../components/WfVariableSelector.vue';

const props = defineProps<{
  workflow: WorkflowInfo;
  wfNode: WorkflowNode;
  uiWorkflow?: unknown;
}>();

// The designer's property editors share the selected mutable workflow model.
const result = computed({
  get: () => String((props.wfNode.nodeConfig as Record<string, unknown>)?.result ?? ''),
  set: (value: string) => {
    props.wfNode.nodeConfig = { ...props.wfNode.nodeConfig, result: value };
  },
});
</script>

<template>
  <div class="space-y-4">
    <WfVariableSelector :workflow="workflow" :wf-node="wfNode" :exclude-nodes="[wfNode.uuid]" />
    <div>
      <label for="workflow-end-result" class="mb-2 block text-sm font-medium">最终结果模板</label>
      <Input.TextArea id="workflow-end-result" v-model:value="result" :auto-size="{ minRows: 8, maxRows: 20 }" placeholder="先选择上游变量，再用 {变量名} 组织最终返回内容。" />
      <p class="mt-2 text-xs leading-5 text-gray-500">支持 Markdown。模板中的变量名应与上方引用变量名称一致；此内容会作为流程最终输出返回用户端。</p>
    </div>
  </div>
</template>
