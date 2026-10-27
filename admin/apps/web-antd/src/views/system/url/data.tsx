import type { FormSchemaGetter } from '#/adapter/form';
import type { VxeGridProps } from '#/adapter/vxe-table';

import { DictEnum } from '@vben/constants';
import { getPopupContainer } from '@vben/utils';

import { getDictOptions } from '#/utils/dict';
import { renderDict } from '#/utils/render';

export const querySchema: FormSchemaGetter = () => [
  {
    component: 'Input',
    fieldName: 'name',
    label: '链接名称',
  },
  {
    component: 'Select',
    componentProps: {
      getPopupContainer,
      options: getDictOptions(DictEnum.SYS_NORMAL_DISABLE),
    },
    fieldName: 'status',
    label: '状态',
  },
];

export const columns: VxeGridProps['columns'] = [
  { type: 'checkbox', width: 60 },
  {
    title: '链接名称',
    field: 'name',
    minWidth: 140,
  },
  {
    title: '链接地址',
    field: 'url',
    minWidth: 220,
    slots: {
      default: ({ row }) => {
        return <a href={row.url} target="_blank" rel="noopener noreferrer" class="text-primary">{row.url}</a>;
      },
    },
  },
  {
    title: '说明',
    field: 'description',
    minWidth: 180,
  },
  {
    title: '排序',
    field: 'sortOrder',
    width: 80,
  },
  {
    title: '状态',
    field: 'status',
    width: 100,
    slots: {
      default: ({ row }) => {
        return renderDict(row.status, DictEnum.SYS_NORMAL_DISABLE);
      },
    },
  },
  {
    title: '创建时间',
    field: 'createTime',
    width: 170,
  },
  {
    field: 'action',
    fixed: 'right',
    slots: { default: 'action' },
    title: '操作',
    resizable: false,
    width: 'auto',
  },
];
