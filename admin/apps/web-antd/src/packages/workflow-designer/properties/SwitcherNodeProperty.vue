<script setup lang="ts">
import type { SelectValue } from 'ant-design-vue/es/select';

import type { UIWorkflow, WorkflowInfo, WorkflowNode } from '../types/index.d';

import { computed } from 'vue';

import { Button, Collapse, Input, Select, Space } from 'ant-design-vue';
import { v4 as uuidv4 } from 'uuid';

import { conditionOperators, logicOperators } from '../utils/operators';
import {
  createNewEdge,
  deleteEdgesBySourceHandle,
  updateEdgeBySourceHandle,
} from '../utils/workflow-util';

interface Condition {
  node_uuid: string;
  node_param_name: string;
  operator: string;
  value: string;
}

interface Case {
  uuid: string;
  operator: 'and' | 'or';
  target_node_uuid: string;
  conditions: Condition[];
}

interface Props {
  workflow: WorkflowInfo;
  uiWorkflow: UIWorkflow;
  wfNode: WorkflowNode;
}

const props = defineProps<Props>();

function normalizeToString(value: SelectValue): string {
  if (Array.isArray(value)) return normalizeToString(value[0] ?? '');
  if (value === null || value === undefined) return '';
  return String(value);
}

// 确保 nodeConfig 结构正确
const nodeConfig = props.wfNode.nodeConfig as {
  cases: Case[];
  default_target_node_uuid?: string;
};

// 防御性初始化
if (!nodeConfig.cases) {
  nodeConfig.cases = [];
}
if (nodeConfig.default_target_node_uuid === undefined) {
  nodeConfig.default_target_node_uuid = '';
}

// 获取可用的节点列表（排除自身和Start节点）
const availableNodes = computed(() => {
  return props.workflow.nodes
    .filter(
      (n) => n.uuid !== props.wfNode.uuid && n.wfComponent?.name !== 'Start',
    )
    .map((n) => ({
      label: n.title || n.wfComponent?.title || n.uuid,
      value: n.uuid,
    }));
});

// 注意：availableParams 暂未使用，但保留供未来功能使用
// const availableParams = computed(() => {
//   const startNode = props.workflow.nodes.find(n => n.wfComponent?.name === 'Start')
//   if (!startNode?.inputConfig?.user_inputs) return []
//
//   return startNode.inputConfig.user_inputs.map(input => ({
//     label: input.title || input.name,
//     value: input.name,
//     nodeUuid: startNode.uuid,
//   }))
// })

// 获取所有可选的源节点（包括Start和其他节点的输出）
const sourceNodeOptions = computed(() => {
  const options: Array<{ label: string; value: string }> = [];

  // 添加Start节点的用户输入
  const startNode = props.workflow.nodes.find(
    (n) => n.wfComponent?.name === 'Start',
  );
  if (startNode) {
    options.push({
      label: `${startNode.title || '开始'} (用户输入)`,
      value: startNode.uuid,
    });
  }

  // 添加其他前置节点
  props.workflow.nodes
    .filter(
      (n) => n.uuid !== props.wfNode.uuid && n.wfComponent?.name !== 'Start',
    )
    .forEach((n) => {
      options.push({
        label: n.title || n.wfComponent?.title || n.uuid,
        value: n.uuid,
      });
    });

  return options;
});

// 根据选中的节点获取可用参数
function getParamsForNode(nodeUuid: string) {
  const node = props.workflow.nodes.find((n) => n.uuid === nodeUuid);
  if (!node) return [];

  if (node.wfComponent?.name === 'Start') {
    return (
      node.inputConfig?.user_inputs?.map((input) => ({
        label: input.title || input.name,
        value: input.name,
      })) || []
    );
  }

  // 其他节点返回通用输出参数
  return [
    { label: '输出结果', value: 'output' },
    { label: '状态', value: 'status' },
  ];
}

// 新增分支
function onAddCase() {
  const uuid = uuidv4().replaceAll('-', '');
  const startNode = props.workflow.nodes.find(
    (n) => n.wfComponent?.name === 'Start',
  );
  const firstParam = startNode?.inputConfig?.user_inputs?.[0]?.name || '';

  const newCase: Case = {
    uuid,
    operator: 'and',
    target_node_uuid: '',
    conditions: [
      {
        node_uuid: startNode?.uuid || '',
        node_param_name: firstParam,
        operator: 'contains',
        value: '',
      },
    ],
  };

  nodeConfig.cases.push(newCase);
  // 创建对应的边
  createNewEdge({
    workflow: props.workflow,
    uiWorkflow: props.uiWorkflow,
    source: props.wfNode.uuid,
    sourceHandle: uuid,
    target: '',
  });
}

// 删除分支
function onDeleteCase(caseItem: Case) {
  const idx = nodeConfig.cases.findIndex((item) => item.uuid === caseItem.uuid);
  if (idx !== -1) {
    deleteEdgesBySourceHandle(
      props.workflow,
      props.uiWorkflow,
      props.wfNode.uuid,
      caseItem.uuid,
    );
    nodeConfig.cases.splice(idx, 1);
  }
}

// 更新分支目标节点
function onCaseTargetSelected(caseItem: Case, nodeUuid: SelectValue) {
  const normalized = normalizeToString(nodeUuid);
  caseItem.target_node_uuid = normalized;
  updateEdgeBySourceHandle({
    workflow: props.workflow,
    uiWorkflow: props.uiWorkflow,
    source: props.wfNode.uuid,
    sourceHandle: caseItem.uuid,
    target: normalized,
  });
}

// 更新默认分支目标
function onDefaultTargetSelected(nodeUuid: SelectValue) {
  const normalized = normalizeToString(nodeUuid);
  nodeConfig.default_target_node_uuid = normalized;
  // 默认分支使用特殊的handle id
  updateEdgeBySourceHandle({
    workflow: props.workflow,
    uiWorkflow: props.uiWorkflow,
    source: props.wfNode.uuid,
    sourceHandle: 'default',
    target: normalized,
  });
}

// 新增条件
function onAddCondition(caseItem: Case) {
  const startNode = props.workflow.nodes.find(
    (n) => n.wfComponent?.name === 'Start',
  );
  const firstParam = startNode?.inputConfig?.user_inputs?.[0]?.name || '';

  caseItem.conditions.push({
    node_uuid: startNode?.uuid || '',
    node_param_name: firstParam,
    operator: 'contains',
    value: '',
  });
}

// 删除条件
function onDeleteCondition(caseItem: Case, conditionIndex: number) {
  if (caseItem.conditions.length > 1) {
    caseItem.conditions.splice(conditionIndex, 1);
  }
}

// 当源节点变化时，重置参数选择
function onSourceNodeChange(condition: Condition, nodeUuid: SelectValue) {
  const normalized = normalizeToString(nodeUuid);
  condition.node_uuid = normalized;
  const params = getParamsForNode(normalized);
  condition.node_param_name = params[0]?.value || '';
}

// 检查是否有无效配置
function hasInvalidConfig(caseItem: Case): boolean {
  return (
    !caseItem.target_node_uuid ||
    caseItem.conditions.some(
      (c) =>
        !c.node_uuid ||
        !c.node_param_name ||
        (!['empty', 'not empty'].includes(c.operator) && !c.value),
    )
  );
}
</script>

<template>
  <div class="switcher-property-panel">
    <div class="info-card">
      <span class="info-title">匹配规则</span>
      <span class="info-description">
        按顺序执行第一个满足条件的分支；均未命中时执行默认分支。分支内支持 AND /
        OR。
      </span>
    </div>

    <!-- 分支列表 -->
    <div class="branches-section">
      <div class="section-header">
        <h3 class="section-title">条件分支</h3>
        <Button size="small" type="dashed" @click="onAddCase">
          <template #icon>
            <span>+</span>
          </template>
          新增分支
        </Button>
      </div>

      <Collapse
        v-if="nodeConfig.cases.length > 0"
        :bordered="false"
        class="branches-collapse"
      >
        <Collapse.Panel
          v-for="(caseItem, caseIdx) in nodeConfig.cases"
          :key="caseItem.uuid"
          class="branch-collapse-item"
        >
          <template #header>
            <div class="branch-header">
              <div class="branch-header-left">
                <span
                  class="branch-number-badge"
                  :class="{ invalid: hasInvalidConfig(caseItem) }"
                >
                  {{ caseIdx + 1 }}
                  <span v-if="hasInvalidConfig(caseItem)" class="warning-icon">
                    ⚠
                  </span>
                </span>
                <span class="branch-title">分支 {{ caseIdx + 1 }}</span>
              </div>
              <span class="branch-meta">
                {{ caseItem.conditions.length }} 个条件 ·
                {{ caseItem.operator.toUpperCase() }}
              </span>
            </div>
          </template>

          <template #extra>
            <div class="delete-btn" @click.stop="onDeleteCase(caseItem)">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path
                  d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M10 11v6M14 11v6"
                />
              </svg>
            </div>
          </template>

          <div class="branch-content">
            <!-- 逻辑运算符 -->
            <div class="form-group">
              <label class="form-label">条件组合方式</label>
              <Select
                v-model:value="caseItem.operator"
                :options="logicOperators as any"
                size="small"
              />
            </div>

            <!-- 条件列表 -->
            <div class="conditions-section">
              <label class="form-label">条件列表</label>
              <div
                v-for="(condition, condIdx) in caseItem.conditions"
                :key="condIdx"
                class="condition-item"
              >
                <Space direction="vertical" :size="12">
                  <!-- 源节点选择 -->
                  <div class="field-group">
                    <label class="field-label">源节点</label>
                    <Select
                      :value="condition.node_uuid"
                      :options="sourceNodeOptions"
                      size="small"
                      placeholder="选择节点"
                      @update:value="
                        (val) => onSourceNodeChange(condition, val)
                      "
                    />
                  </div>

                  <!-- 参数选择 -->
                  <div class="field-group">
                    <label class="field-label">参数</label>
                    <Select
                      v-model:value="condition.node_param_name"
                      :options="getParamsForNode(condition.node_uuid)"
                      size="small"
                      placeholder="选择参数"
                    />
                  </div>

                  <!-- 运算符 -->
                  <div class="field-group">
                    <label class="field-label">运算符</label>
                    <Select
                      v-model:value="condition.operator"
                      :options="conditionOperators as any"
                      size="small"
                    />
                  </div>

                  <!-- 比较值 -->
                  <div
                    v-if="!['empty', 'not empty'].includes(condition.operator)"
                    class="field-group"
                  >
                    <label class="field-label">
                      比较值
                      <span v-if="!condition.value" class="required-mark">
                        *
                      </span>
                    </label>
                    <Input
                      v-model:value="condition.value"
                      :status="!condition.value ? 'warning' : undefined"
                      size="small"
                      placeholder="输入比较值"
                    />
                    <div v-if="!condition.value" class="warning-hint">
                      💡 建议填写比较值，空值会匹配任何内容
                    </div>
                  </div>

                  <!-- 删除条件按钮 -->
                  <div
                    v-if="caseItem.conditions.length > 1"
                    class="condition-actions"
                  >
                    <Button
                      size="small"
                      type="text"
                      danger
                      @click="onDeleteCondition(caseItem, condIdx)"
                    >
                      <template #icon>
                        <span>×</span>
                      </template>
                      删除此条件
                    </Button>
                  </div>
                </Space>
              </div>

              <!-- 添加条件按钮 -->
              <Button
                class="add-condition-btn"
                size="small"
                type="dashed"
                block
                @click="onAddCondition(caseItem)"
              >
                <template #icon>
                  <span>+</span>
                </template>
                添加条件
              </Button>
            </div>

            <div class="divider"></div>

            <!-- 目标节点 -->
            <div class="form-group">
              <label class="form-label">
                跳转到节点
                <span v-if="!caseItem.target_node_uuid" class="required-mark">
                  *
                </span>
              </label>
              <Select
                :value="caseItem.target_node_uuid"
                :options="availableNodes"
                :status="!caseItem.target_node_uuid ? 'error' : undefined"
                size="small"
                placeholder="选择目标节点"
                allow-clear
                @update:value="
                  (val) => onCaseTargetSelected(caseItem, val || '')
                "
              />
              <div v-if="!caseItem.target_node_uuid" class="error-hint">
                ⚠️ 目标节点不能为空，否则该分支将被跳过
              </div>
            </div>
          </div>
        </Collapse.Panel>
      </Collapse>

      <div v-else class="empty-state">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          width="48"
          height="48"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
        >
          <path d="M9 11l3 3L22 4" />
          <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
        </svg>
        <p>暂无分支</p>
        <span>点击上方按钮添加条件分支</span>
      </div>
    </div>

    <!-- 默认分支 -->
    <div class="default-branch-section">
      <div class="default-branch-card">
        <div class="default-branch-header">
          <div class="default-branch-title">
            <span class="default-icon">🔄</span>
            <span>默认分支</span>
          </div>
          <span class="default-branch-desc">所有条件都不满足时执行</span>
        </div>
        <div class="form-group">
          <label class="form-label">
            跳转到节点
            <span
              v-if="!nodeConfig.default_target_node_uuid"
              class="required-mark"
            >
              *
            </span>
          </label>
          <Select
            :value="nodeConfig.default_target_node_uuid"
            :options="availableNodes"
            :status="!nodeConfig.default_target_node_uuid ? 'error' : undefined"
            placeholder="选择默认目标节点"
            allow-clear
            @update:value="(val) => onDefaultTargetSelected(val || '')"
          />
          <div v-if="!nodeConfig.default_target_node_uuid" class="error-hint">
            ⚠️ 默认分支的目标节点不能为空
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.switcher-property-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 0 4px 20px;
}

.info-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  color: #475569;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
}

.info-title {
  flex: 0 0 auto;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
}

.info-description {
  color: #64748b;
  font-size: 12px;
  line-height: 20px;
}

.branches-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title {
  margin: 0;
  color: #1f2937;
  font-size: 15px;
  font-weight: 600;
}

.branches-collapse {
  overflow: hidden;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.branch-collapse-item {
  margin: 0;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
}

.branch-collapse-item:last-child {
  border-bottom: 0;
}

:deep(.branches-collapse > .ant-collapse-item) {
  border-bottom: 1px solid #e2e8f0;
}

:deep(.branches-collapse > .ant-collapse-item:last-child) {
  border-bottom: 0;
}

:deep(.branches-collapse > .ant-collapse-item > .ant-collapse-header) {
  align-items: center;
  padding: 12px 16px;
  background: #fff;
}

:deep(.branches-collapse .ant-collapse-expand-icon) {
  color: #64748b;
}

:deep(.branches-collapse .ant-collapse-content) {
  background: #f8fafc;
  border-top: 1px solid #e2e8f0;
}

:deep(.branches-collapse .ant-collapse-content-box) {
  padding: 0;
}

.branch-header {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 8px;
}

.branch-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.branch-number-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 24px;
  padding: 0 6px;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 600;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 5px;
}

.branch-title {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
}

.branch-meta {
  font-size: 12px;
  color: #6b7280;
  font-weight: 500;
}

.delete-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: #ef4444;
  cursor: pointer;
  border-radius: 5px;
  transition:
    color 160ms ease,
    background-color 160ms ease;
}

.delete-btn:hover {
  background: #fee2e2;
  color: #dc2626;
}

.branch-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 14px 16px 16px;
  background: #f8fafc;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}

.conditions-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.condition-item {
  position: relative;
  padding: 12px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  transition: border-color 160ms ease;
}

.condition-item:hover {
  border-color: #cbd5e1;
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 12px;
  font-weight: 500;
  color: #6b7280;
}

.condition-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 4px;
}

.add-condition-btn {
  margin-top: 4px;
}

.divider {
  height: 1px;
  margin: 8px 0;
  background: #e2e8f0;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  border: 2px dashed #e5e7eb;
  border-radius: 8px;
  background: #fafafa;
  color: #9ca3af;
}

.empty-state svg {
  margin-bottom: 12px;
  opacity: 0.5;
}

.empty-state p {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 4px 0;
  color: #6b7280;
}

.empty-state span {
  font-size: 12px;
}

.default-branch-section {
  margin-top: 0;
}

.default-branch-card {
  padding: 14px 16px;
  background: #fff;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  transition: border-color 160ms ease;
}

.default-branch-card:hover {
  border-color: #94a3b8;
}

.default-branch-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f3f4f6;
}

.default-branch-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #374151;
}

.default-icon {
  color: #64748b;
  font-size: 16px;
  filter: grayscale(1);
}

.default-branch-desc {
  font-size: 12px;
  color: #6b7280;
  font-weight: 500;
}

.required-mark {
  color: #ef4444;
  font-weight: bold;
  margin-left: 4px;
}

.error-hint {
  padding: 6px 10px;
  margin-top: 6px;
  color: #b91c1c;
  font-size: 12px;
  background: #fef2f2;
  border-left: 3px solid #ef4444;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.warning-hint {
  padding: 6px 10px;
  margin-top: 6px;
  color: #a16207;
  font-size: 12px;
  background: #fffbeb;
  border-left: 3px solid #f59e0b;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.branch-number-badge.invalid {
  color: #b91c1c;
  background: #fef2f2;
  border-color: #fecaca;
}

.warning-icon {
  margin-left: 2px;
  font-size: 10px;
}
</style>
