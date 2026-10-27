<script setup lang="ts">
import type { Citation, Question } from '#/api/chat/question';
import { computed, ref } from 'vue';
import { useAccess } from '@vben/access';
import { Alert, Button, Descriptions, DescriptionsItem, Drawer, Empty, Form, FormItem, Input, Select, Spin, Tag, message, Grid } from 'ant-design-vue';
import { answerLabels, modeLabels, questionDetail, questionReview, retrievalLabels, reviewLabels } from '#/api/chat/question';

const screens = Grid.useBreakpoint();
const detailColumns = computed(() => screens.value.sm ? 2 : 1);
const emit = defineEmits<{ saved: [] }>();
const { hasAccessByCodes } = useAccess();
const open = ref(false);
const loading = ref(false);
const saving = ref(false);
const saveError = ref('');
const error = ref('');
const record = ref<Question>();
const status = ref('pending');
const note = ref('');
let requestVersion = 0;
const citations = computed<Citation[]>(() => {
  try { const parsed = JSON.parse(record.value?.citations || '[]'); return Array.isArray(parsed) ? parsed : []; }
  catch { return []; }
});
const canReview = computed(() => hasAccessByCodes(['chat:question:review']) && record.value?.answerStatus !== 'running');
const reviewOptions = [{ label: '待复核', value: 'pending' }, { label: '已处理', value: 'resolved' }, { label: '已忽略', value: 'ignored' }];
async function show(id: string) {
  open.value = true;
  loading.value = true;
  error.value = '';
  saveError.value = '';
  record.value = undefined;
  const version = ++requestVersion;
  try {
    const data = await questionDetail(id);
    if (version !== requestVersion) return;
    record.value = data;
    status.value = data.reviewStatus === 'none' ? 'pending' : data.reviewStatus;
    note.value = data.reviewNote || '';
  } catch { if (version === requestVersion) error.value = '问答详情加载失败，请关闭后重新打开。'; }
  finally { if (version === requestVersion) loading.value = false; }
}
async function save() {
  if (!record.value || saving.value) return;
  if (status.value === 'resolved' && !note.value.trim()) { message.warning('请填写处理结果'); return; }
  saving.value = true;
  saveError.value = '';
  try {
    await questionReview(record.value.id, { status: status.value, note: note.value.trim(), version: record.value.version });
    message.success('复核结果已保存');
    emit('saved');
    await show(record.value.id);
  } catch { saveError.value = '保存失败，备注已保留。请重试；若记录已被更新，请重新打开详情后复核。'; }
  finally { saving.value = false; }
}
function score(value?: string) { return value && Number.isFinite(Number(value)) ? `${(Number(value) * 100).toFixed(1)}%` : '—'; }
defineExpose({ show });
</script>

<template>
  <Drawer v-model:open="open" title="问答详情" width="min(860px, 100vw)" :mask-closable="!saving" :closable="!saving" :keyboard="!saving" @close="requestVersion++">
    <Spin :spinning="loading">
      <Alert v-if="error" type="error" :message="error" show-icon />
      <article v-else-if="record" class="question-detail">
        <Descriptions :column="detailColumns" size="small" bordered>
          <DescriptionsItem label="提问用户">{{ record.userName || record.userId }}</DescriptionsItem>
          <DescriptionsItem label="提问时间">{{ record.startedAt }}</DescriptionsItem>
          <DescriptionsItem label="模型">{{ record.modelName }}</DescriptionsItem>
          <DescriptionsItem label="模式">{{ modeLabels[record.chatMode] || '—' }}</DescriptionsItem>
          <DescriptionsItem label="回答状态">{{ answerLabels[record.answerStatus] }}</DescriptionsItem>
          <DescriptionsItem label="检索状态">{{ retrievalLabels[record.retrievalStatus] }}</DescriptionsItem>
          <DescriptionsItem label="耗时">{{ record.durationMs == null ? '—' : `${(record.durationMs / 1000).toFixed(2)} 秒` }}</DescriptionsItem>
          <DescriptionsItem label="Token">{{ record.totalTokens == null ? '模型未返回' : record.totalTokens.toLocaleString() }}</DescriptionsItem>
          <DescriptionsItem label="知识库" :span="detailColumns">{{ record.knowledgeNames || '未使用知识库' }}</DescriptionsItem>
          <DescriptionsItem v-if="record.traceId" label="关联链路" :span="detailColumns">{{ record.traceId }}</DescriptionsItem>
        </Descriptions>
        <section><h2>用户问题</h2><p class="answer-text">{{ record.question }}</p></section>
        <section><h2>AI 回答</h2><p class="answer-text">{{ record.answer || (record.answerStatus === 'running' ? '回答生成中，请稍后重新打开详情。' : '未生成回答') }}</p>
          <Alert v-if="record.errorMessage" type="error" :message="record.errorMessage" show-icon />
        </section>
        <section><h2>引用文档与检索片段</h2>
          <Empty v-if="!citations.length" :description="retrievalLabels[record.retrievalStatus] || '没有引用片段'" :image="Empty.PRESENTED_IMAGE_SIMPLE" />
          <div v-for="(citation, index) in citations" :key="index" class="citation">
            <div class="citation-title"><strong>{{ citation.sourceName || '未命名文档' }}</strong><span>相似度 {{ score(citation.score) }}</span></div>
            <p class="answer-text">{{ citation.content }}</p>
          </div>
        </section>
        <section><Alert v-if="record.issueType === 'dissatisfied'" type="warning" message="用户反馈：回答不满意" show-icon /><h2>复核处理 <Tag>{{ reviewLabels[record.reviewStatus] }}</Tag></h2>
          <p v-if="record.reviewedAt" class="muted">最近处理：{{ record.reviewedAt }} · 操作人 {{ record.reviewedBy }}</p>
          <Form v-if="canReview" :model="{ status, note }" layout="vertical" @finish="save">
            <FormItem label="复核状态"><Select v-model:value="status" :options="reviewOptions" :disabled="saving" /></FormItem>
            <FormItem label="处理备注" :required="status === 'resolved'"><Input.TextArea v-model:value="note" :maxlength="2000" show-count :rows="4" :disabled="saving" placeholder="记录问题原因、修正措施与验证结果" /></FormItem>
            <Alert v-if="saveError" type="error" :message="saveError" show-icon class="mb-4" />
            <Button type="primary" html-type="submit" :loading="saving">保存复核结果</Button>
          </Form>
          <p v-else class="answer-text">{{ record.reviewNote || '暂无处理备注' }}</p>
        </section>
      </article>
    </Spin>
  </Drawer>
</template>

<style scoped>
:deep(.ant-descriptions-item-label) { width: 112px; }
:deep(.ant-descriptions-item-content) { overflow-wrap: anywhere; }
.question-detail { min-width: 0; }
section { margin-top: 28px; }
h2 { margin-bottom: 12px; font-size: 16px; font-weight: 600; }
.answer-text { overflow-wrap: anywhere; white-space: pre-wrap; line-height: 1.75; }
.citation { padding: 16px 0; border-bottom: 1px solid hsl(var(--border)); }
.citation-title { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px; margin-bottom: 8px; }
.citation-title span, .muted { color: hsl(var(--muted-foreground)); font-size: 13px; }
</style>
