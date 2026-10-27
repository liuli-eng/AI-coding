<script setup lang="ts">
import type { Overview } from '#/api/chat/question';
import { computed, onMounted, ref } from 'vue';
import { useAccess } from '@vben/access';
import { Alert, Button, Empty, Spin, Table } from 'ant-design-vue';
import { questionOverview } from '#/api/chat/question';

const { hasAccessByCodes } = useAccess();
const allowed = computed(() => hasAccessByCodes(['chat:overview:query']));
const data = ref<Overview>();
const loading = ref(false);
const error = ref('');
const figures = computed(() => data.value ? [
  { label: '知识库', value: data.value.knowledgeCount, unit: '个' },
  { label: '文档', value: data.value.documentCount, unit: '份' },
  { label: '今日问答', value: data.value.today.total, unit: '次' },
  { label: '今日成功率', value: completed.value ? `${(Number(data.value.today.success) / completed.value * 100).toFixed(1)}%` : '—', unit: '' },
] : []);
const completed = computed(() => Number(data.value?.today.success || 0) + Number(data.value?.today.failed || 0));
const columns = [
  { title: '模型', dataIndex: 'modelName', key: 'modelName' },
  { title: '调用次数', dataIndex: 'calls', key: 'calls', align: 'right' as const },
  { title: '成功', dataIndex: 'success', key: 'success', align: 'right' as const },
  { title: '失败', dataIndex: 'failed', key: 'failed', align: 'right' as const },
  { title: '平均耗时', key: 'duration', align: 'right' as const },
];
async function load() {
  if (!allowed.value || loading.value) return;
  loading.value = true;
  error.value = '';
  try { data.value = await questionOverview(); }
  catch { error.value = '业务数据加载失败，请重试。'; }
  finally { loading.value = false; }
}
onMounted(load);
</script>

<template>
  <main class="business-overview">
    <header class="overview-heading"><div><h1>业务数据总览</h1><p>AI 编程助手 · 问答与知识库运行情况</p></div><Button v-if="allowed" :loading="loading" @click="load">刷新数据</Button></header>
    <Alert v-if="!allowed" type="info" message="暂无业务数据查看权限，请联系管理员授权。" show-icon />
    <Alert v-else-if="error" type="error" :message="error" show-icon class="mb-4"><template #action><Button size="small" @click="load">重试</Button></template></Alert>
    <Spin v-if="allowed" :spinning="loading">
      <template v-if="data">
        <p class="report-time">统计日期 {{ data.reportDate }}（{{ data.timezone }}） · 更新于 {{ data.updatedAt }}</p>
        <dl class="metrics"><div v-for="figure in figures" :key="figure.label"><dt>{{ figure.label }}</dt><dd>{{ typeof figure.value === 'number' ? figure.value.toLocaleString() : figure.value }} <small>{{ figure.unit }}</small></dd></div></dl>
        <section class="attention"><h2>需要关注</h2><div class="attention-items"><p>待复核问答 <strong>{{ data.pendingReviews }}</strong> 条</p><p>今日回答失败 <strong>{{ data.today.failed }}</strong> 次</p><p>今日未匹配资料 <strong>{{ data.today.unmatched }}</strong> 次</p><p>文档解析失败 <strong>{{ data.failedDocuments }}</strong> 份</p></div></section>
        <section><h2>今日模型调用</h2><p class="section-note">成功率按已结束的回答计算；未匹配资料单独统计，可能与回答成功重叠。回答中 {{ data.today.running }} 次。</p>
          <Table :columns="columns" :data-source="data.models" :pagination="false" row-key="modelName" size="middle" :scroll="{ x: 600 }">
            <template #bodyCell="{ column, record }"><template v-if="column.key === 'duration'">{{ record.averageDurationMs == null ? '—' : `${(Number(record.averageDurationMs) / 1000).toFixed(2)} 秒` }}</template></template>
            <template #emptyText><Empty description="今天还没有模型调用记录" :image="Empty.PRESENTED_IMAGE_SIMPLE" /></template>
          </Table>
        </section>
        <p class="section-note record-scope">问答统计覆盖本功能启用后的普通对话与智能体对话，暂不包含工作流内部调用。{{ data.firstRecordedAt ? `首条记录：${data.firstRecordedAt}` : '尚无问答记录。' }}</p>
      </template>
      <Empty v-else-if="!loading && !error" description="暂无业务数据" />
    </Spin>
  </main>
</template>

<style scoped>
.business-overview { margin: 20px; padding: 24px; background: hsl(var(--card)); border-radius: 8px; }
.overview-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
h1 { margin: 0 0 8px; font-size: 22px; font-weight: 600; }
h2 { margin: 0 0 12px; font-size: 16px; font-weight: 600; }
.overview-heading p, .report-time, .section-note { font-size: 13px; color: hsl(var(--muted-foreground)); }
.metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 24px; padding: 24px 0; }
.metrics dt { margin-bottom: 8px; color: hsl(var(--muted-foreground)); }
.metrics dd { font-size: 30px; font-weight: 600; font-variant-numeric: tabular-nums; }
.metrics small { font-size: 13px; font-weight: 400; }
section { margin-top: 28px; }
.attention { padding: 20px; background: hsl(var(--muted)); border-radius: 6px; }
.attention-items { display: flex; flex-wrap: wrap; gap: 16px 32px; }
.attention strong { font-variant-numeric: tabular-nums; }
.section-note { margin-bottom: 12px; line-height: 1.6; }
.record-scope { margin-top: 24px; }
:deep(.ant-table-cell) { font-variant-numeric: tabular-nums; }
@media (max-width: 700px) { .metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 450px) { .business-overview { margin: 12px; padding: 16px; } .overview-heading { flex-direction: column; } .metrics dd { font-size: 26px; } }
</style>
