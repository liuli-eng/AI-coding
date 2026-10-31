<script setup lang="ts">
import type { AttachVO } from '#/api/knowledge/attach/model';
import type { DocumentStats } from '#/api/knowledge/attach/workbench';
import type { FragmentVO } from '#/api/knowledge/fragment/model';

import { computed, onBeforeUnmount, ref, watch } from 'vue';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import {
  Alert,
  Badge,
  Button,
  Descriptions,
  DescriptionsItem,
  Input,
  message,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
} from 'ant-design-vue';

import {
  attachExport,
  attachParse,
  attachRemove,
  attachReparseKnowledge,
} from '#/api/knowledge/attach';
import {
  documentDownload,
  documentFragments,
  documentList,
  documentState,
} from '#/api/knowledge/attach/workbench';
import { commonDownloadExcel } from '#/utils/file/download';

import DocumentUpload from './DocumentUpload.vue';
import KnowledgePicker from './KnowledgePicker.vue';

const props = defineProps<{
  knowledgeId?: number | string;
  startUpload?: boolean;
}>();
const access = useAccessStore();
const { hasAccessByCodes } = useAccess();
const can = (code: string) => hasAccessByCodes([`system:attach:${code}`]);
const uploadVisible = ref(!!props.startUpload);
const rows = ref<AttachVO[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const pageNum = ref(1);
const pageSize = ref(20);
const name = ref('');
const selectedKnowledge = ref<number | string>();
const status = ref<number>();
const businessState = ref<string>();
const selected = ref<(number | string)[]>([]);
const pending = ref(new Set<number | string>());
const statistics = ref<DocumentStats>({
  waiting: 0,
  processing: 0,
  completed: 0,
  failed: 0,
  total: 0,
});
const statusLabels = ['待解析', '解析中', '已解析', '解析失败'];
const badgeStatuses = ['default', 'processing', 'success', 'error'] as const;
let request: AbortController | undefined;
const lifetime = new AbortController();
let generation = 0;
let fragmentRequest: AbortController | undefined;
let deletionConfirmation: ReturnType<typeof Modal.confirm> | undefined;
const query = () => ({
  pageNum: pageNum.value,
  pageSize: pageSize.value,
  name: name.value.trim() || undefined,
  knowledgeId: props.knowledgeId ?? selectedKnowledge.value,
  status: status.value,
  businessState: businessState.value,
});
const columns = computed(() => [
  { title: '文档名称', key: 'name', dataIndex: 'name', width: 230 },
  ...(props.knowledgeId
    ? []
    : [{ title: '所属知识库', dataIndex: 'knowledgeName', width: 180 }]),
  { title: '大小', key: 'size', width: 100 },
  { title: '解析状态', key: 'status', width: 210 },
  { title: '启用', key: 'state', width: 90 },
  { title: '上传人', dataIndex: 'uploadedBy', width: 110 },
  { title: '上传时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 290 },
]);
function formatSize(size?: number) {
  if (size === undefined || size === null) return '未知';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}
async function load(reset = false) {
  if (!can('list')) return;
  if (reset) pageNum.value = 1;
  request?.abort();
  request = new AbortController();
  const controller = request;
  loading.value = true;
  error.value = '';
  try {
    const data = await documentList(query(), controller.signal);
    if (controller.signal.aborted) return;
    rows.value = data.rows;
    total.value = data.total;
    statistics.value = data.statistics;
    selected.value = [];
    if (rows.value.length === 0 && pageNum.value > 1 && data.total) {
      pageNum.value = Math.max(1, Math.ceil(data.total / pageSize.value));
      return load();
    }
  } catch (error_) {
    if (!controller.signal.aborted) {
      rows.value = [];
      total.value = 0;
      error.value =
        error_ instanceof Error ? error_.message : '文档加载失败，请重试';
    }
  } finally {
    if (request === controller) loading.value = false;
  }
}
function paginationChange(page: { current?: number; pageSize?: number }) {
  pageNum.value = page.current || 1;
  pageSize.value = page.pageSize || 20;
  load();
}
async function action(
  row: AttachVO,
  operation: () => Promise<unknown>,
  success?: string,
) {
  if (pending.value.has(row.id)) return;
  pending.value.add(row.id);
  const run = generation;
  try {
    await operation();
    if (run !== generation) return;
    if (success) message.success(success);
    await load();
  } catch (error_) {
    if (run === generation)
      message.error(
        error_ instanceof Error ? error_.message : '操作失败，请重试',
      );
  } finally {
    if (run === generation) pending.value.delete(row.id);
  }
}
async function batchDelete() {
  const ids = [...selected.value];
  if (ids.length === 0) return;
  const run = generation;
  deletionConfirmation = Modal.confirm({
    title: `删除选中的 ${ids.length} 个文档？`,
    content: '文档和检索内容会一并删除。',
    okText: '删除',
    okType: 'danger',
    onOk: async () => {
      if (run !== generation) return;
      try {
        await attachRemove(ids, lifetime.signal);
        if (run === generation) await load();
      } catch (error_) {
        if (run === generation)
          message.error(
            error_ instanceof Error ? error_.message : '删除失败，请重试',
          );
      }
    },
  });
}
const detail = ref<AttachVO>();
const fragmentsVisible = ref(false);
const fragments = ref<FragmentVO[]>([]);
const fragmentsLoading = ref(false);
const fragmentError = ref('');
const fragmentPage = ref(1);
const fragmentTotal = ref(0);
const fragmentDoc = ref<AttachVO>();
const fragmentText = ref<string>();
async function loadFragments(row = fragmentDoc.value, page = 1) {
  if (!row?.docId) return;
  fragmentRequest?.abort();
  const controller = new AbortController();
  fragmentRequest = controller;
  fragmentDoc.value = row;
  fragmentPage.value = page;
  fragmentsVisible.value = true;
  fragmentsLoading.value = true;
  fragmentError.value = '';
  const run = generation;
  try {
    const data = await documentFragments(row.id, page, controller.signal);
    if (run !== generation || controller.signal.aborted) return;
    fragments.value = data.rows;
    fragmentTotal.value = data.total;
  } catch {
    if (run === generation && !controller.signal.aborted) {
      fragments.value = [];
      fragmentError.value = '片段加载失败，请重试';
    }
  } finally {
    if (run === generation) fragmentsLoading.value = false;
  }
}
async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text);
    message.success('已复制');
  } catch {
    message.error('复制失败，请手动选择文本');
  }
}
async function reparseAll() {
  if (!props.knowledgeId) return;
  const run = generation;
  try {
    const data = await attachReparseKnowledge(
      props.knowledgeId,
      lifetime.signal,
    );
    if (run !== generation) return;
    message.success(
      `已提交 ${data.submitted} 个，跳过 ${data.skipped} 个解析中文档`,
    );
    await load();
  } catch {
    if (run === generation) message.error('重新解析失败，请重试');
  }
}
function reset() {
  request?.abort();
  fragmentRequest?.abort();
  generation++;
  deletionConfirmation?.destroy();
  deletionConfirmation = undefined;
  rows.value = [];
  detail.value = undefined;
  fragmentsVisible.value = false;
  fragmentText.value = undefined;
  pending.value = new Set();
  selected.value = [];
}
watch(
  () => props.knowledgeId,
  () => {
    reset();
    load(true);
  },
);
watch(
  () => access.accessToken,
  () => {
    reset();
    if (access.accessToken) load(true);
  },
);
onBeforeUnmount(() => {
  reset();
  lifetime.abort();
});
load();
</script>
<template>
  <div class="document-workbench">
    <div class="mb-5 flex flex-wrap items-center justify-between gap-3">
      <h2 class="m-0 text-lg font-semibold">
        {{ startUpload ? '上传文档' : '文档管理' }}
      </h2>
      <Space wrap>
        <Button
          v-if="can('export') && can('list')"
          @click="commonDownloadExcel(attachExport, '知识库文档', query())"
        >
          导出
        </Button>
        <Button
          v-if="can('remove')"
          :disabled="selected.length === 0"
          danger
          @click="batchDelete"
        >
          删除所选
        </Button>
        <Popconfirm
          v-if="knowledgeId && can('edit')"
          title="按当前配置重新解析全部文档？"
          @confirm="reparseAll"
        >
          <Button>全部重新解析</Button>
        </Popconfirm>
        <Button v-if="can('list')" :loading="loading" @click="load()">
          刷新
        </Button>
        <Button
          v-if="can('add')"
          type="primary"
          @click="uploadVisible = !uploadVisible"
        >
          {{ uploadVisible ? '收起上传' : '上传文档' }}
        </Button>
      </Space>
    </div>
    <DocumentUpload
      v-if="can('add') && uploadVisible"
      :knowledge-id="knowledgeId"
      class="mb-6"
      @uploaded="load(true)"
    />
    <template v-if="can('list')">
      <div
        class="mb-5 flex flex-wrap gap-x-6 gap-y-2 border-b pb-4 text-sm"
        aria-label="文档统计"
        aria-live="polite"
      >
        <div>
          总计 <strong>{{ statistics.total }}</strong>
        </div>
        <div>
          待解析 <strong>{{ statistics.waiting }}</strong>
        </div>
        <div>
          解析中 <strong>{{ statistics.processing }}</strong>
        </div>
        <div>
          已解析 <strong>{{ statistics.completed }}</strong>
        </div>
        <div>
          解析失败 <strong>{{ statistics.failed }}</strong>
        </div>
      </div>
      <form
        class="mb-4 flex flex-wrap items-end gap-3"
        @submit.prevent="load(true)"
      >
        <label v-if="!knowledgeId" class="min-w-0 flex-1 basis-44">
          <span class="mb-1 block text-sm">所属知识库</span>
          <KnowledgePicker v-model="selectedKnowledge" label="筛选知识库" />
        </label>
        <label class="min-w-0 flex-1 basis-44">
          <span class="mb-1 block text-sm">文档名称</span>
          <Input
            v-model:value="name"
            :maxlength="255"
            placeholder="搜索文档名称"
            allow-clear
          />
        </label>
        <label class="min-w-0 basis-36">
          <span class="mb-1 block text-sm">解析状态</span>
          <Select
            v-model:value="status"
            aria-label="解析状态"
            placeholder="全部状态"
            :options="statusLabels.map((label, value) => ({ label, value }))"
            allow-clear
            class="w-full"
          />
        </label>
        <label class="min-w-0 basis-32">
          <span class="mb-1 block text-sm">业务状态</span>
          <Select
            v-model:value="businessState"
            aria-label="业务状态"
            placeholder="全部"
            :options="[
              { label: '启用', value: '0' },
              { label: '停用', value: '1' },
            ]"
            allow-clear
            class="w-full"
          />
        </label>
        <Button type="primary" html-type="submit" :loading="loading">
          查询
        </Button>
        <Button
          @click="
            name = '';
            selectedKnowledge = undefined;
            status = undefined;
            businessState = undefined;
            load(true);
          "
        >
          重置
        </Button>
      </form>
      <Alert v-if="error" type="error" :message="error" show-icon class="mb-4">
        <template #action>
          <Button size="small" @click="load()">重试</Button>
        </template>
      </Alert>
      <Table
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :scroll="{ x: 1280 }"
        :pagination="{
          current: pageNum,
          pageSize,
          total,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50', '100'],
          showTotal: (count: number) => `共 ${count} 个文档`,
        }"
        :row-selection="
          can('remove')
            ? {
                selectedRowKeys: selected,
                onChange: (keys: (string | number)[]) => (selected = keys),
                getCheckboxProps: (record: AttachVO) => ({
                  disabled: record.status === 1,
                }),
              }
            : undefined
        "
        row-key="id"
        @change="paginationChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <span class="break-all">{{ record.name }}</span>
            <span class="text-muted-foreground ml-2 text-xs">{{
              record.type
            }}</span>
          </template>
          <template v-else-if="column.key === 'size'">
            {{ formatSize(record.fileSize) }}
          </template>
          <template v-else-if="column.key === 'status'">
            <Badge
              :status="badgeStatuses[record.status]"
              :text="statusLabels[record.status] || '未知'"
            />
            <p
              v-if="record.status === 3"
              class="mb-0 mt-1 text-sm text-red-700 dark:text-red-400"
            >
              {{ record.remark || '解析失败，请检查文件后重试' }}
            </p>
          </template>
          <template v-else-if="column.key === 'state'">
            <Switch
              :checked="record.businessState !== '1'"
              :aria-label="`${record.name}启用状态`"
              :disabled="!can('edit') || pending.has(record.id)"
              :loading="pending.has(record.id)"
              @change="
                (value) =>
                  action(record as AttachVO, () =>
                    documentState(
                      record.id,
                      value ? '0' : '1',
                      lifetime.signal,
                    ),
                  )
              "
            />
          </template>
          <template v-else-if="column.key === 'action'">
            <Space wrap :size="0">
              <Button
                v-if="can('edit')"
                type="link"
                size="small"
                :disabled="record.status === 1 || pending.has(record.id)"
                @click="
                  action(
                    record as AttachVO,
                    () => attachParse(record.id),
                    '已提交解析，稍后点击刷新查看结果',
                  )
                "
              >
                {{
                  record.status === 3
                    ? '重试解析'
                    : record.status === 2
                      ? '重新解析'
                      : '解析'
                }}
              </Button>
              <Button
                v-if="
                  hasAccessByCodes(['system:fragment:list']) &&
                  record.status === 2
                "
                type="link"
                size="small"
                @click="loadFragments(record as AttachVO)"
              >
                知识片段
              </Button>
              <Button
                v-if="can('query')"
                type="link"
                size="small"
                @click="detail = record as AttachVO"
              >
                源文件
              </Button>
              <Popconfirm
                v-if="can('remove')"
                title="删除文档及其检索内容？"
                @confirm="
                  action(record as AttachVO, () => attachRemove(record.id))
                "
              >
                <Button
                  type="link"
                  size="small"
                  danger
                  :disabled="record.status === 1 || pending.has(record.id)"
                >
                  删除
                </Button>
              </Popconfirm>
            </Space>
          </template>
        </template>
      </Table>
    </template>
    <Modal
      :open="!!detail"
      title="源文件"
      :footer="null"
      @cancel="detail = undefined"
    >
      <template v-if="detail">
        <Descriptions :column="1" bordered size="small">
          <DescriptionsItem label="文件名">{{ detail.name }}</DescriptionsItem>
          <DescriptionsItem label="格式">{{ detail.type }}</DescriptionsItem>
          <DescriptionsItem label="大小">
            {{ formatSize(detail.fileSize) }}
          </DescriptionsItem>
          <DescriptionsItem label="所属知识库">
            {{ detail.knowledgeName || knowledgeId }}
          </DescriptionsItem>
          <DescriptionsItem label="上传人">
            {{ detail.uploadedBy || '未知' }}
          </DescriptionsItem>
          <DescriptionsItem label="上传时间">
            {{ detail.createTime || '未知' }}
          </DescriptionsItem>
        </Descriptions>
        <Button
          class="mt-4"
          type="primary"
          :loading="pending.has(detail.id)"
          @click="
            action(detail, () =>
              documentDownload(detail!.id, detail!.name, lifetime.signal),
            )
          "
        >
          下载源文件
        </Button>
      </template>
    </Modal>
    <Modal
      v-model:open="fragmentsVisible"
      title="知识片段"
      :width="900"
      :footer="null"
    >
      <Alert v-if="fragmentError" :message="fragmentError" type="error">
        <template #action>
          <Button @click="loadFragments()">重试</Button>
        </template>
      </Alert>
      <Table
        :columns="[
          { title: '序号', dataIndex: 'idx', width: 80 },
          { title: '内容', key: 'content' },
        ]"
        :data-source="fragments"
        :loading="fragmentsLoading"
        row-key="id"
        :pagination="{
          current: fragmentPage,
          pageSize: 10,
          total: fragmentTotal,
        }"
        @change="(value) => loadFragments(fragmentDoc, value.current)"
      >
        <template #bodyCell="{ column, record }">
          <span v-if="column.dataIndex === 'idx'">{{
            Number(record.idx) + 1
          }}</span>
          <Button
            v-else
            type="link"
            class="!h-auto !whitespace-normal !text-left"
            @click="fragmentText = record.content"
          >
            <span class="line-clamp-3 whitespace-pre-wrap">{{
              record.content
            }}</span>
          </Button>
        </template>
      </Table>
    </Modal>
    <Modal
      :open="fragmentText !== undefined"
      title="片段详情"
      :footer="null"
      @cancel="fragmentText = undefined"
    >
      <p class="max-h-96 overflow-auto whitespace-pre-wrap break-words">
        {{ fragmentText }}
      </p>
      <Button @click="copy(fragmentText || '')">复制内容</Button>
    </Modal>
  </div>
</template>
<style scoped>
.document-workbench {
  min-width: 0;
}
strong {
  font-variant-numeric: tabular-nums;
}
:deep(button:focus-visible),
:deep(input:focus-visible) {
  outline: 2px solid hsl(var(--primary));
  outline-offset: 3px;
}
</style>
