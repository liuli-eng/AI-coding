<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';

import {
  Button,
  Card,
  Col,
  message,
  Progress,
  Row,
  Space,
  Statistic,
} from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  collectList,
  collectSummary,
  preheatProgress,
  preheatTrigger,
} from '#/api/graph/collect';

import { columns, querySchema } from './data';
import collectDrawer from './collect-drawer.vue';

const summary = ref<Record<string, any>>({});
const progress = ref<Record<string, any>>({ running: false, percent: 0 });
let pollTimer: null | ReturnType<typeof setInterval> = null;

async function loadSummary() {
  summary.value = (await collectSummary()) || {};
}

async function loadProgress() {
  progress.value = (await preheatProgress()) || { running: false };
  if (progress.value.running && !pollTimer) {
    pollTimer = setInterval(loadProgress, 3000);
  } else if (!progress.value.running && pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

async function handleTrigger() {
  try {
    await preheatTrigger();
    message.success('已触发批量采集，后台异步执行中');
    await loadProgress();
  } catch {
    // 接口层已统一提示
  }
}

onMounted(async () => {
  await Promise.all([loadSummary(), loadProgress()]);
});

onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
});

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
        return await collectList({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          ...formValues,
        });
      },
    },
  },
  rowConfig: { keyField: 'companyId' },
  id: 'graph-collect-index',
};

const [BasicTable, tableApi] = useVbenVxeGrid({ formOptions, gridOptions });
const [CollectDrawer, drawerApi] = useVbenDrawer({
  connectedComponent: collectDrawer,
});

function handleDetail(row: Record<string, any>) {
  drawerApi.setData({ companyId: row.companyId, companyName: row.companyName });
  drawerApi.open();
}
</script>

<template>
  <Page :auto-content-height="true">
    <Row :gutter="12" class="mb-3">
      <Col :span="5">
        <Card size="small">
          <Statistic
            title="企业总数"
            :value="summary.total ?? 0"
            :value-style="{ color: '#1677ff' }"
          />
        </Card>
      </Col>
      <Col :span="5">
        <Card size="small">
          <Statistic
            title="已完成推理"
            :value="summary.completed ?? 0"
            :value-style="{ color: '#52c41a' }"
          />
        </Card>
      </Col>
      <Col :span="5">
        <Card size="small">
          <Statistic
            title="待采集"
            :value="summary.pending ?? 0"
            :value-style="{ color: '#faad14' }"
          />
        </Card>
      </Col>
      <Col :span="5">
        <Card size="small">
          <Statistic
            title="采集失败"
            :value="summary.failed ?? 0"
            :value-style="{ color: '#ff4d4f' }"
          />
        </Card>
      </Col>
      <Col :span="4">
        <Card size="small">
          <Statistic
            title="已打标企业"
            :value="summary.taggedCompanies ?? 0"
          />
        </Card>
      </Col>
    </Row>

    <Card size="small" class="mb-3" title="批量预热进度">
      <Space align="center" :size="16">
        <Progress
          type="circle"
          :percent="progress.percent ?? 0"
          :width="64"
        />
        <Space :size="24" wrap>
          <Statistic
            title="本批进度"
            :value="progress.processed ?? 0"
            :suffix="` / ${progress.total ?? 0}`"
          />
          <Statistic title="已采集" :value="progress.enriched ?? 0" />
          <Statistic title="已打标" :value="progress.tagged ?? 0" />
          <Statistic
            title="失败"
            :value="progress.failed ?? 0"
            :value-style="{ color: '#ff4d4f' }"
          />
          <Button
            type="primary"
            :loading="progress.running"
            @click="handleTrigger"
          >
            {{ progress.running ? '执行中…' : '触发批量采集' }}
          </Button>
        </Space>
      </Space>
      <div v-if="progress.lastError" class="mt-2 text-red-500">
        最近错误：{{ progress.lastError }}
      </div>
    </Card>

    <BasicTable table-title="企业采集状态">
      <template #action="{ row }">
        <Button type="link" size="small" @click="handleDetail(row)">
          查看明细
        </Button>
      </template>
    </BasicTable>

    <CollectDrawer @reload="tableApi.query()" />
  </Page>
</template>
