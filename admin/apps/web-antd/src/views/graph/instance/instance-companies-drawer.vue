<script setup lang="ts">
import { ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Spin, Table, Tag } from 'ant-design-vue';

import { graphCompanies } from '#/api/graph/instance';

interface CompanyRow {
  nodeId?: string;
  name?: string;
  description?: string;
  properties?: string;
  legalPerson?: string;
  registeredCapital?: string;
  industry?: string;
  website?: string;
}

const list = ref<CompanyRow[]>([]);
const loading = ref(false);

function parseProps(row: CompanyRow): CompanyRow {
  if (!row.properties) {
    return row;
  }
  try {
    const obj = JSON.parse(row.properties);
    return {
      ...row,
      legalPerson: obj.legalPerson,
      registeredCapital: obj.registeredCapital,
      industry: obj.industry,
      website: obj.website,
    };
  } catch {
    return row;
  }
}

const columns = [
  { title: '企业名称', dataIndex: 'name', ellipsis: true, width: 200 },
  { title: '法人', dataIndex: 'legalPerson', width: 100 },
  { title: '注册资本', dataIndex: 'registeredCapital', width: 120 },
  { title: '行业', dataIndex: 'industry', ellipsis: true, width: 140 },
  { title: '官网', dataIndex: 'website', ellipsis: true, width: 160 },
  {
    title: '描述',
    dataIndex: 'description',
    ellipsis: true,
  },
];

const [BasicDrawer, drawerApi] = useVbenDrawer({
  destroyOnClose: true,
  async onOpenChange(isOpen) {
    if (!isOpen) {
      list.value = [];
      return null;
    }
    loading.value = true;
    const { id, name } = drawerApi.getData() as {
      id?: number | string;
      name?: string;
    };
    try {
      const rows = (await graphCompanies(id!)) || [];
      list.value = rows.map((r: any) => parseProps({ ...r }));
    } finally {
      loading.value = false;
      void name;
    }
  },
});
</script>

<template>
  <BasicDrawer
    :title="`图谱企业清单（${list.length} 家）`"
    class="w-[960px]"
    :footer="false"
  >
    <Spin :spinning="loading">
      <Table
        :columns="columns"
        :data-source="list"
        row-key="nodeId"
        size="small"
        :pagination="{ pageSize: 20, showSizeChanger: true }"
        :scroll="{ y: 480 }"
      >
        <template #emptyText>
          <Tag>暂无企业节点</Tag>
        </template>
      </Table>
    </Spin>
  </BasicDrawer>
</template>
