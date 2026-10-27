<script setup lang="ts">
import type { NodeProps } from '@vue-flow/core';

import { computed } from 'vue';

import { Handle, Position } from '@vue-flow/core';

import { getOperatorLabel } from '../../utils/operators';
import CommonNodeHeader from '../CommonNodeHeader.vue';

interface SwitchCondition {
  node_param_name?: string;
  operator?: string;
  value?: string;
}

interface SwitchCase {
  uuid: string;
  operator?: 'and' | 'or';
  conditions?: SwitchCondition[];
}

interface SwitcherConfig {
  cases?: SwitchCase[];
}

const props = defineProps<NodeProps>();

const cases = computed<SwitchCase[]>(() => {
  const config = (props.data?.nodeConfig ?? {}) as SwitcherConfig;
  return Array.isArray(config.cases) ? config.cases : [];
});

function truncate(value: string, length = 18) {
  if (!value) return '';
  return value.length > length ? `${value.slice(0, length)}…` : value;
}

function getConditionSummary(caseItem: SwitchCase) {
  const conditions = caseItem.conditions ?? [];
  if (conditions.length === 0) return '尚未配置条件';

  const first = conditions[0];
  if (!first) return '尚未配置条件';

  const operator = getOperatorLabel(first.operator ?? '');
  const value = truncate(String(first.value ?? ''));
  const summary = [first.node_param_name || '参数', operator, value]
    .filter(Boolean)
    .join(' ');

  return conditions.length > 1
    ? `${summary} · 另有 ${conditions.length - 1} 项`
    : summary;
}
</script>

<template>
  <div class="switcher-node">
    <Handle
      type="target"
      :position="Position.Left"
      class="switcher-input-handle"
    />

    <CommonNodeHeader :wf-node="data" />

    <div class="switcher-summary">
      <span class="summary-label">按顺序匹配分支</span>
      <span class="summary-count">{{ cases.length }} 条规则</span>
    </div>

    <div class="branch-list">
      <div
        v-for="(caseItem, index) in cases"
        :key="caseItem.uuid"
        class="branch-row"
      >
        <span class="branch-index">{{ index + 1 }}</span>
        <div class="branch-content">
          <span class="branch-title">
            分支 {{ index + 1 }}
            <span class="logic-label">
              {{ caseItem.operator === 'or' ? '任一满足' : '全部满足' }}
            </span>
          </span>
          <span
            class="branch-condition"
            :class="{ 'is-empty': !caseItem.conditions?.length }"
          >
            {{ getConditionSummary(caseItem) }}
          </span>
        </div>
        <Handle
          :id="caseItem.uuid"
          type="source"
          :position="Position.Right"
          class="branch-handle"
        />
      </div>

      <div class="branch-row default-row">
        <span class="branch-index default-index">兜底</span>
        <div class="branch-content">
          <span class="branch-title">默认分支</span>
          <span class="branch-condition">以上条件均未命中</span>
        </div>
        <Handle
          id="default"
          type="source"
          :position="Position.Right"
          class="branch-handle default-handle"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.switcher-node {
  width: 100%;
  min-width: 0;
  color: #1f2937;
}

.switcher-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 30px;
  margin-bottom: 8px;
  padding: 0 2px;
  font-size: 12px;
}

.summary-label {
  color: #64748b;
}

.summary-count {
  padding: 2px 7px;
  color: #334155;
  font-weight: 600;
  line-height: 18px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
}

.branch-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.branch-row {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 48px;
  padding: 7px 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  transition:
    border-color 160ms ease,
    background-color 160ms ease;
}

.branch-row:hover {
  background: #f5f8fc;
  border-color: #cbd5e1;
}

.branch-index {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  margin-right: 8px;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 700;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 5px;
}

.branch-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  text-align: left;
}

.branch-title {
  display: flex;
  align-items: center;
  min-width: 0;
  color: #334155;
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
}

.logic-label {
  margin-left: 6px;
  color: #94a3b8;
  font-size: 10px;
  font-weight: 500;
}

.branch-condition {
  overflow: hidden;
  color: #64748b;
  font-size: 11px;
  line-height: 17px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.branch-condition.is-empty {
  color: #94a3b8;
}

.default-row {
  background: #fff;
  border-style: dashed;
}

.default-row:hover {
  background: #fafafa;
}

.default-index {
  width: 32px;
  color: #64748b;
  background: #f8fafc;
  border-color: #cbd5e1;
}

.switcher-input-handle,
.branch-handle {
  width: 8px;
  height: 16px;
  background: #475569;
  border: 0;
  border-radius: 4px;
  transition:
    background-color 160ms ease,
    box-shadow 160ms ease;
}

.branch-handle {
  right: -15px;
  top: 50%;
  transform: translateY(-50%);
}

.default-handle {
  background: #94a3b8;
}

.branch-handle:hover {
  background: #2563eb;
  box-shadow: 0 0 0 3px rgb(37 99 235 / 14%);
}
</style>
