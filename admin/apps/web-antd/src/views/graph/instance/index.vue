<script setup lang="ts">
import { Page, useVbenDrawer } from '@vben/common-ui';

import { Button, message, Popconfirm, Space } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  graphInstanceBuild,
  graphInstanceList,
  graphInstanceRemove,
  graphInstanceRebuild,
} from '#/api/graph';

import { columns, querySchema } from './data';
import instanceCompaniesDrawer from './instance-companies-drawer.vue';

const formOptions = {
  commonConfig: { labelWidth: 80, componentProps: { allowClear: true } },
  schema: querySchema(),
  wrapperClass: 'grid-cols-1 md:grid-cols-2 lg:grid-cols-3',
};

const gridOptions = {
  columns,
  height: 'auto',
  keepSource: true,
  pagerConfig: {},
  proxyConfig: {
    ajax: {
      query: async ({ page }: any, formValues = {}) => {
        return await graphInstanceList({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          ...formValues,
        });
      },
    },
  },
  rowConfig: { keyField: 'id' },
  id: 'graph-instance-index',
};

const [BasicTable, tableApi] = useVbenVxeGrid({ formOptions, gridOptions });
const [CompaniesDrawer, drawerApi] = useVbenDrawer({
  connectedComponent: instanceCompaniesDrawer,
});

async function refresh() {
  await tableApi.query();
}

async function handleBuild(row: Record<string, any>) {
  await graphInstanceBuild(row.id ?? row.graphUuid);
  message.success('已触发构建，后台异步执行');
  await refresh();
}

async function handleRebuild(row: Record<string, any>) {
  await graphInstanceRebuild(row.id ?? row.graphUuid);
  message.success('已触发重建，后台异步执行');
  await refresh();
}

async function handleDelete(row: Record<string, any>) {
  await graphInstanceRemove(row.id ?? row.graphUuid);
  message.success('删除成功');
  await refresh();
}

function handleCompanies(row: Record<string, any>) {
  drawerApi.setData({ id: row.id ?? row.graphUuid, name: row.instanceName });
  drawerApi.open();
}
</script>

<template>
  <Page :auto-content-height="true">
    <BasicTable table-title="图谱构建情况">
      <template #action="{ row }">
        <Space :size="4">
          <Button
            type="link"
            size="small"
            @click="handleCompanies(row)"
          >
            企业清单
          </Button>
          <Button type="link" size="small" @click="handleBuild(row)">
            构建
          </Button>
          <Button type="link" size="small" @click="handleRebuild(row)">
            重建
          </Button>
          <Popconfirm
            title="确认删除该图谱实例？"
            @confirm="handleDelete(row)"
          >
            <Button type="link" size="small" danger>删除</Button>
          </Popconfirm>
        </Space>
      </template>
    </BasicTable>
    <CompaniesDrawer />
  </Page>
</template>
