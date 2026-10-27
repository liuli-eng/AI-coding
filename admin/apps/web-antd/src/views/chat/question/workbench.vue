<script setup lang="ts">
import type { Question } from '#/api/chat/question';
import { computed, onMounted, reactive, ref } from 'vue';
import { Alert, Button, DatePicker, Form, FormItem, Input, Select, Table, Tag, Empty, Spin, Pagination } from 'ant-design-vue';
import { answerLabels, questionList, retrievalLabels, reviewLabels } from '#/api/chat/question';
import QuestionDetail from './detail.vue';

const props = withDefaults(defineProps<{ exceptionsOnly?: boolean }>(), { exceptionsOnly: false });
const rows = ref<Question[]>([]);
const loading = ref(false);
const error = ref('');
const total = ref(0);
const page = ref(1);
const size = ref(20);
const filters = reactive({ userName: '', keyword: '', modelName: '', answerStatus: undefined as string | undefined, reviewStatus: undefined as string | undefined });
const dates = ref<[string, string]>();
const detailRef = ref<InstanceType<typeof QuestionDetail>>();
let loadVersion = 0;
const title = computed(() => props.exceptionsOnly ? '异常问题排查' : '问答记录');
const columns = [
  { title: '提问时间', dataIndex: 'startedAt', key: 'startedAt', width: 170 },
  { title: '用户 / 模型', key: 'user', width: 170 },
  { title: '用户问题', dataIndex: 'question', key: 'question', ellipsis: true, width: 340 },
  { title: '回答 / 检索', key: 'status', width: 150 },
  { title: '复核状态', key: 'review', width: 110 },
  { title: '耗时', key: 'duration', width: 100, align: 'right' as const },
  { title: '操作', key: 'action', width: 100, fixed: 'right' as const },
];
const answerOptions = [{ value: 'success', label: '回答成功' }, { value: 'failed', label: '回答失败' }, { value: 'running', label: '回答中' }, { value: 'no_match', label: '未匹配资料' }];
const reviewOptions = Object.entries(reviewLabels).map(([value, label]) => ({ value, label }));
const pagination = computed(() => ({ current: page.value, pageSize: size.value, total: total.value, showSizeChanger: true, pageSizeOptions: ['10', '20', '50', '100'], showTotal: (n: number) => `共 ${n} 条问答` }));
async function load() {
  const version = ++loadVersion;
  loading.value = true;
  error.value = '';
  try {
    const result = await questionList({ ...filters, exceptionsOnly: props.exceptionsOnly, pageNum: page.value, pageSize: size.value,
      beginTime: dates.value?.[0] ? `${dates.value[0]} 00:00:00` : undefined,
      endTime: dates.value?.[1] ? `${dates.value[1]} 23:59:59` : undefined });
    if (version !== loadVersion) return;
    rows.value = result.rows || [];
    total.value = result.total;
  } catch { if (version === loadVersion) { rows.value = []; total.value = 0; error.value = '问答记录加载失败，请重试。'; } }
  finally { if (version === loadVersion) loading.value = false; }
}
function search() { page.value = 1; load(); }
function reset() { Object.assign(filters, { userName: '', keyword: '', modelName: '', answerStatus: undefined, reviewStatus: undefined }); dates.value = undefined; search(); }
function changePage(pagination: { current?: number; pageSize?: number }) { page.value = pagination.current || 1; size.value = pagination.pageSize || 20; load(); }
onMounted(load);
</script>

<template>
  <main class="question-workbench">
    <header class="page-heading"><div><h1>{{ title }}</h1><p>{{ exceptionsOnly ? '集中处理回答失败、检索异常、未命中资料和待复核的问答。' : '按一次提问查看回答、引用依据和处理结果。记录从本功能启用后开始积累。' }}</p></div><Button :loading="loading" @click="load">刷新记录</Button></header>
    <Form :model="filters" class="question-filters" layout="vertical" @finish="search">
      <FormItem label="时间范围"><DatePicker.RangePicker v-model:value="dates" value-format="YYYY-MM-DD" class="w-full" /></FormItem>
      <FormItem label="用户"><Input v-model:value="filters.userName" allow-clear placeholder="搜索用户名" /></FormItem>
      <FormItem label="关键词"><Input v-model:value="filters.keyword" allow-clear placeholder="搜索问题或回答" /></FormItem>
      <FormItem label="模型"><Input v-model:value="filters.modelName" allow-clear placeholder="输入完整模型名称" /></FormItem>
      <FormItem label="回答状态"><Select v-model:value="filters.answerStatus" :options="answerOptions" allow-clear placeholder="全部状态" /></FormItem>
      <FormItem label="复核状态"><Select v-model:value="filters.reviewStatus" :options="reviewOptions" allow-clear placeholder="全部状态" /></FormItem>
      <div class="filter-actions"><Button type="primary" html-type="submit" :loading="loading">查询</Button><Button :disabled="loading" @click="reset">重置</Button></div>
    </Form>
    <Alert v-if="error" type="error" :message="error" show-icon class="mb-4"><template #action><Button size="small" @click="load">重试</Button></template></Alert>
    <Table class="desktop-records" :columns="columns" :data-source="rows" :loading="loading" :pagination="pagination" :scroll="{ x: 1240 }" row-key="id" size="middle" @change="changePage" :locale="{ emptyText: error ? '暂时无法获取记录' : '当前条件下没有问答记录' }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'user'"><div>{{ record.userName || record.userId }}</div><small class="secondary">{{ record.modelName }}</small></template>
        <template v-else-if="column.key === 'status'"><Tag :color="record.answerStatus === 'failed' ? 'error' : record.answerStatus === 'success' ? 'success' : 'processing'">{{ answerLabels[record.answerStatus] }}</Tag><div class="secondary">{{ retrievalLabels[record.retrievalStatus] }}</div></template>
        <template v-else-if="column.key === 'review'"><Tag :color="record.reviewStatus === 'pending' ? 'warning' : 'default'">{{ reviewLabels[record.reviewStatus] }}</Tag></template>
        <template v-else-if="column.key === 'duration'"><span class="tabular">{{ record.durationMs == null ? '—' : `${(record.durationMs / 1000).toFixed(2)} 秒` }}</span></template>
        <template v-else-if="column.key === 'action'"><Button v-access:code="['chat:question:query']" type="link" size="small" @click="detailRef?.show(record.id)">查看详情</Button></template>
      </template>
    </Table>
    <div class="mobile-records"><Spin :spinning="loading">
      <Empty v-if="!rows.length && !loading" :description="error ? '暂时无法获取记录' : '当前条件下没有问答记录'" :image="Empty.PRESENTED_IMAGE_SIMPLE" />
      <ul v-else class="record-list">
        <li v-for="record in rows" :key="record.id">
          <p class="record-question">{{ record.question }}</p>
          <p class="secondary">{{ record.userName || record.userId }} · {{ record.modelName }}</p>
          <p class="secondary">{{ record.startedAt }}</p>
          <div class="record-status"><Tag :color="record.answerStatus === 'failed' ? 'error' : record.answerStatus === 'success' ? 'success' : 'processing'">{{ answerLabels[record.answerStatus] }}</Tag><Tag :color="record.reviewStatus === 'pending' ? 'warning' : 'default'">{{ reviewLabels[record.reviewStatus] }}</Tag></div>
          <div class="record-actions"><span class="secondary">{{ retrievalLabels[record.retrievalStatus] }}</span><Button v-access:code="['chat:question:query']" type="link" size="small" @click="detailRef?.show(record.id)">查看详情</Button></div>
        </li>
      </ul>
      <Pagination v-if="total > 0" v-bind="pagination" :show-size-changer="false" size="small" @change="(current, pageSize) => changePage({ current, pageSize })" />
    </Spin></div>
    <QuestionDetail ref="detailRef" @saved="load" />
  </main>
</template>

<style scoped>
.question-workbench { margin: 20px; padding: 24px; background: hsl(var(--card)); border-radius: 8px; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 24px; }
h1 { margin: 0 0 8px; font-size: 22px; font-weight: 600; }
.page-heading p, .secondary { color: hsl(var(--muted-foreground)); font-size: 13px; }
.question-filters { display: grid; grid-template-columns: minmax(270px, 2fr) repeat(3, minmax(140px, 1fr)); gap: 0 16px; margin-bottom: 16px; }
.filter-actions { display: flex; align-items: center; gap: 8px; padding-top: 10px; }
.mobile-records { display: none; }
.record-list { margin: 0 0 16px; padding: 0; list-style: none; }
.record-list li { padding: 16px 0; border-bottom: 1px solid hsl(var(--border)); }
.record-list p { margin-bottom: 8px; overflow-wrap: anywhere; }
.record-question { font-size: 14px; line-height: 1.6; }
.record-status { margin: 12px 0 8px; }
.record-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.tabular { font-variant-numeric: tabular-nums; }
@media (max-width: 1000px) { .question-filters { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 600px) { .mobile-records { display: block; } .desktop-records { display: none; } .question-workbench { margin: 12px; padding: 16px; } .question-filters { grid-template-columns: minmax(0, 1fr); } .page-heading { flex-direction: column; } .filter-actions { padding: 0 0 12px; } }
</style>
