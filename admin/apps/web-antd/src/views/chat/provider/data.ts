import type { FormSchemaGetter } from '#/adapter/form';
import type { VxeGridProps } from '#/adapter/vxe-table';

import { providerStatusOptions } from './options';

export const querySchema: FormSchemaGetter = () => [
  {
    component: 'Input',
    fieldName: 'providerName',
    label: '厂商名称',
  },
  {
    component: 'Input',
    fieldName: 'providerCode',
    label: '厂商编码',
  },
  {
    component: 'Select',
    componentProps: { options: providerStatusOptions },
    fieldName: 'status',
    label: '状态',
  },
];

/**
 * 表格列配置
 * 如果需要使用 i18n 国际化，请使用 getter 函数形式，否则切换语言时列配置不会刷新
 * 使用方式: export const columns: () => VxeGridProps['columns'] = () => [...]
 */
export const columns: VxeGridProps['columns'] = [
  { type: 'checkbox', width: 60 },
  {
    title: '厂商图标',
    field: 'providerIcon',
    slots: { default: 'providerIcon' },
  },
  {
    title: '厂商名称',
    field: 'providerName',
  },
  {
    title: '厂商编码',
    field: 'providerCode',
  },

  {
    title: '厂商描述',
    field: 'providerDesc',
  },
  {
    title: 'API地址',
    field: 'apiHost',
  },
  {
    title: '排序',
    field: 'sortOrder',
    width: 80,
  },
  {
    title: '状态',
    field: 'status',
    width: 80,
    formatter: ({ cellValue }) =>
      providerStatusOptions.find((option) => option.value === cellValue)?.label ?? '未设置',
  },
  {
    title: '备注',
    field: 'remark',
  },

  {
    field: 'action',
    fixed: 'right',
    slots: { default: 'action' },
    title: '操作',
    width: 180,
  },
];
