import type { VxeGridProps } from '#/adapter/vxe-table';
import type { FormSchemaGetter } from '#/adapter/form';

import { Tag } from 'ant-design-vue';

export const statusOptions = [
  { label: '全部', value: 'all' },
  { label: '已完成', value: 'completed' },
  { label: '待采集', value: 'pending' },
  { label: '采集失败', value: 'failed' },
];

const llmReasonTag = (v?: number) => {
  if (v === 2) return { color: 'green', text: '已完成' };
  if (v === 1) return { color: 'orange', text: '待推理' };
  return { color: 'default', text: '未开始' };
};

const enrichTag = (v?: number) => {
  if (v === 1) return { color: 'green', text: '已补全' };
  if (v === 2) return { color: 'red', text: '失败' };
  return { color: 'default', text: '未补全' };
};

const fusionTag = (v?: number) => {
  if (v === 1) return { color: 'green', text: '已融合' };
  if (v === 2) return { color: 'red', text: '失败' };
  return { color: 'default', text: '未融合' };
};

export const querySchema: FormSchemaGetter = () => [
  {
    component: 'Input',
    fieldName: 'name',
    label: '企业名称',
  },
  {
    component: 'Select',
    componentProps: { options: statusOptions },
    fieldName: 'status',
    label: '采集状态',
  },
];

export const columns: VxeGridProps['columns'] = [
  { type: 'seq', width: 50, title: '#' },
  { title: '企业名称', field: 'companyName', minWidth: 180 },
  { title: '别名', field: 'alias', minWidth: 120 },
  { title: '地区', field: 'region', minWidth: 120 },
  {
    title: 'LLM状态',
    field: 'llmReason',
    width: 100,
    align: 'center',
    slots: {
      default: ({ row }) => {
        const t = llmReasonTag(row.llmReason);
        return <Tag color={t.color}>{t.text}</Tag>;
      },
    },
  },
  {
    title: '采集',
    field: 'enrichStatus',
    width: 100,
    align: 'center',
    slots: {
      default: ({ row }) => {
        const t = enrichTag(row.enrichStatus);
        return <Tag color={t.color}>{t.text}</Tag>;
      },
    },
  },
  {
    title: '融合',
    field: 'fusionStatus',
    width: 100,
    align: 'center',
    slots: {
      default: ({ row }) => {
        const t = fusionTag(row.fusionStatus);
        return <Tag color={t.color}>{t.text}</Tag>;
      },
    },
  },
  {
    title: '百科',
    field: 'hasBaike',
    width: 70,
    align: 'center',
    slots: {
      default: ({ row }) =>
        row.hasBaike ? (
          <Tag color="blue">有</Tag>
        ) : (
          <Tag>无</Tag>
        ),
    },
  },
  {
    title: '官网',
    field: 'hasWebsite',
    width: 70,
    align: 'center',
    slots: {
      default: ({ row }) =>
        row.hasWebsite ? (
          <Tag color="blue">有</Tag>
        ) : (
          <Tag>无</Tag>
        ),
    },
  },
  {
    title: '画像',
    field: 'hasFusedProfile',
    width: 70,
    align: 'center',
    slots: {
      default: ({ row }) =>
        row.hasFusedProfile ? (
          <Tag color="green">有</Tag>
        ) : (
          <Tag>无</Tag>
        ),
    },
  },
  { title: '更新时间', field: 'updateTime', minWidth: 160 },
  {
    field: 'action',
    fixed: 'right',
    title: '操作',
    width: 100,
    slots: { default: 'action' },
  },
];

export { enrichTag, fusionTag, llmReasonTag };
