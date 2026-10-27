import type { VxeGridProps } from '#/adapter/vxe-table';
import type { FormSchemaGetter } from '#/adapter/form';

import { Tag } from 'ant-design-vue';

export const graphTypeOptions = [
  { label: '全部', value: '' },
  { label: '文件库图谱', value: 'KNOWLEDGE' },
  { label: '企业图谱', value: 'ENTERPRISE' },
  { label: '供应链图谱', value: 'SUPPLY_CHAIN' },
  { label: '展会图谱', value: 'EXHIBITION' },
  { label: '统一图谱', value: 'UNIFIED' },
];

export const statusOptions = [
  { label: '全部', value: '' },
  { label: '构建中', value: 'BUILDING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '失败', value: 'FAILED' },
  { label: '未构建', value: 'NOT_BUILT' },
];

const typeTag = (v?: string) => {
  switch (v) {
    case 'ENTERPRISE': {
      return { color: 'blue', text: '企业' };
    }
    case 'SUPPLY_CHAIN': {
      return { color: 'cyan', text: '供应链' };
    }
    case 'EXHIBITION': {
      return { color: 'purple', text: '展会' };
    }
    case 'UNIFIED': {
      return { color: 'geekblue', text: '统一' };
    }
    default: {
      return { color: 'default', text: '文件库' };
    }
  }
};

// graphStatus 数字码：10 构建中 / 20 已完成 / 30 失败
export const statusTag = (code?: number) => {
  switch (code) {
    case 10: {
      return { color: 'processing', text: '构建中' };
    }
    case 20: {
      return { color: 'success', text: '已完成' };
    }
    case 30: {
      return { color: 'error', text: '失败' };
    }
    default: {
      return { color: 'default', text: '未构建' };
    }
  }
};

export const querySchema: FormSchemaGetter = () => [
  {
    component: 'Input',
    fieldName: 'instanceName',
    label: '图谱名称',
  },
  {
    component: 'Select',
    componentProps: { options: graphTypeOptions, allowClear: true },
    fieldName: 'graphType',
    label: '图谱类型',
  },
  {
    component: 'Select',
    componentProps: { options: statusOptions, allowClear: true },
    fieldName: 'status',
    label: '状态',
  },
];

export const columns: VxeGridProps['columns'] = [
  { type: 'seq', width: 50, title: '#' },
  { title: '图谱名称', field: 'instanceName', minWidth: 200 },
  {
    title: '类型',
    field: 'graphType',
    width: 100,
    align: 'center',
    slots: {
      default: ({ row }) => {
        const t = typeTag(row.graphType);
        return <Tag color={t.color}>{t.text}</Tag>;
      },
    },
  },
  {
    title: '状态',
    field: 'graphStatus',
    width: 100,
    align: 'center',
    slots: {
      default: ({ row }) => {
        const t = statusTag(row.graphStatus);
        return <Tag color={t.color}>{t.text}</Tag>;
      },
    },
  },
  { title: '节点数', field: 'nodeCount', width: 90, align: 'right' },
  {
    title: '关系数',
    field: 'relationshipCount',
    width: 90,
    align: 'right',
  },
  { title: '创建时间', field: 'createTime', minWidth: 160 },
  {
    field: 'action',
    fixed: 'right',
    title: '操作',
    width: 260,
    slots: { default: 'action' },
  },
];

export { typeTag };
