<script setup lang="ts">
import type { Prompt, PromptSave } from '#/api/chat/prompt';
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useAccess } from '@vben/access';
import {
  Alert,
  Button,
  Drawer,
  Empty,
  Form,
  FormItem,
  Input,
  Modal,
  Pagination,
  Popconfirm,
  Select,
  Spin,
  Table,
  Tag,
  message,
} from 'ant-design-vue';
import {
  promptDetail,
  promptList,
  promptRemove,
  promptSave,
} from '#/api/chat/prompt';
const { hasAccessByCodes } = useAccess();
const rows = ref<Prompt[]>([]);
const loading = ref(false);
const error = ref('');
const page = ref(1);
const total = ref(0);
const keyword = ref('');
const mode = ref<string>();
const status = ref<string>();
const modes = [
  { value: 'learning', label: '教学模式' },
  { value: 'programming', label: '编程助手' },
  { value: 'debugging', label: '分析代码错误' },
];
const states = [
  { value: 'enabled', label: '已启用' },
  { value: 'disabled', label: '已停用' },
];
const labels = Object.fromEntries(modes.map((m) => [m.value, m.label]));
const columns = [
  { title: '模板名称', dataIndex: 'name', key: 'name', width: 220 },
  { title: '适用模式', key: 'mode', width: 150 },
  {
    title: '说明',
    dataIndex: 'description',
    key: 'description',
    ellipsis: true,
  },
  { title: '状态', key: 'status', width: 100 },
  { title: '版本', dataIndex: 'version', key: 'version', width: 80 },
  { title: '操作', key: 'actions', width: 150, fixed: 'right' as const },
];
let sequence = 0;
async function load() {
  const current = ++sequence;
  loading.value = true;
  error.value = '';
  try {
    const data = await promptList({
      keyword: keyword.value,
      mode: mode.value,
      status: status.value,
      pageNum: page.value,
      pageSize: 20,
    });
    if (current !== sequence) return;
    rows.value = data.rows || [];
    total.value = data.total;
  } catch {
    if (current === sequence) {
      error.value = '模板加载失败，请重试。';
      rows.value = [];
      total.value = 0;
    }
  } finally {
    if (current === sequence) loading.value = false;
  }
}
function search() {
  page.value = 1;
  load();
}
const open = ref(false);
const editing = ref<string>();
const fetching = ref(false);
const saving = ref(false);
const formError = ref('');
const detailFailed = ref(false);
const errorRegion = ref<HTMLElement>();
const savedDraft = ref('');
let confirmingClose = false;
watch(formError, async (value) => {
  if (!value) return;
  await nextTick();
  errorRegion.value?.scrollIntoView({ block: 'nearest' });
  errorRegion.value?.focus({ preventScroll: true });
});
function requestClose() {
  if (saving.value || confirmingClose) return;
  if (fetching.value || JSON.stringify(draft) === savedDraft.value) {
    open.value = false;
    return;
  }
  confirmingClose = true;
  Modal.confirm({
    title: '放弃未保存的修改？',
    content: '关闭后将丢弃本次修改。',
    okText: '放弃修改',
    cancelText: '继续编辑',
    onOk: () => {
      confirmingClose = false;
      open.value = false;
    },
    onCancel: () => {
      confirmingClose = false;
    },
  });
}
const draft = reactive<PromptSave>({
  name: '',
  description: '',
  chatMode: 'programming',
  content: '',
  status: 'disabled',
});
const editable = computed(
  () =>
    !detailFailed.value &&
    hasAccessByCodes([editing.value ? 'chat:prompt:edit' : 'chat:prompt:add']),
);
let detailSequence = 0;
watch(open, (value) => {
  if (!value) detailSequence++;
});
async function show(id?: string) {
  const current = ++detailSequence;
  fetching.value = false;
  editing.value = id;
  open.value = true;
  formError.value = '';
  detailFailed.value = false;
  Object.assign(draft, {
    name: '',
    description: '',
    chatMode: 'programming',
    content: '',
    status: 'disabled',
    version: undefined,
  });
  savedDraft.value = JSON.stringify(draft);
  if (!id) return;
  fetching.value = true;
  try {
    const result = await promptDetail(id);
    if (current === detailSequence) {
      Object.assign(draft, result);
      savedDraft.value = JSON.stringify(draft);
    }
  } catch {
    if (current === detailSequence) {
      detailFailed.value = true;
      formError.value = '详情加载失败，请关闭后重试。';
    }
  } finally {
    if (current === detailSequence) fetching.value = false;
  }
}
async function save() {
  if (saving.value || fetching.value || !editable.value) return;
  if (!draft.name.trim() || !draft.content?.trim()) {
    formError.value = '请填写模板名称和提示词内容。';
    return;
  }
  saving.value = true;
  formError.value = '';
  try {
    await promptSave(editing.value, {
      name: draft.name.trim(),
      description: draft.description,
      chatMode: draft.chatMode,
      content: draft.content,
      status: draft.status,
      version: draft.version,
    });
    message.success('模板已保存');
    open.value = false;
    await load();
  } catch {
    formError.value = '保存失败，输入已保留。若版本已更新，请重新打开模板。';
  } finally {
    saving.value = false;
  }
}
async function remove(id: string, version: number) {
  try {
    await promptRemove(id, version);
    message.success('模板已删除');
    await load();
  } catch {
    message.error('删除失败，请刷新后重试。');
  }
}
onMounted(load);
</script>
<template>
  <main class="prompt-workbench">
    <header>
      <div>
        <h1>Prompt 管理</h1>
        <p>
          维护各对话模式的提示词模板。新会话保存模板快照，已有会话保持创建时的版本。
        </p>
      </div>
      <Button v-access:code="['chat:prompt:add']" type="primary" @click="show()"
        >新增模板</Button
      >
    </header>
    <Form
      class="filters"
      :model="{ keyword, mode, status }"
      layout="vertical"
      @finish="search"
    >
      <FormItem name="keyword" label="模板名称"
        ><Input v-model:value="keyword" placeholder="搜索模板名称" allow-clear
      /></FormItem>
      <FormItem name="mode" label="适用模式"
        ><Select
          v-model:value="mode"
          :options="modes"
          placeholder="全部模式"
          allow-clear
      /></FormItem>
      <FormItem name="status" label="状态"
        ><Select
          v-model:value="status"
          :options="states"
          placeholder="全部状态"
          allow-clear
      /></FormItem>
      <div class="filter-actions">
        <Button type="primary" html-type="submit" :loading="loading"
          >查询</Button
        ><Button
          @click="
            keyword = '';
            mode = undefined;
            status = undefined;
            search();
          "
          >重置</Button
        >
      </div>
    </Form>
    <Alert v-if="error" :message="error" type="error" show-icon
      ><template #action><Button @click="load">重试</Button></template></Alert
    >
    <Table
      class="desktop-list"
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="false"
      :scroll="{ x: 900 }"
      row-key="id"
      :locale="{ emptyText: '暂无符合条件的模板' }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'mode'">{{
          labels[record.chatMode]
        }}</template>
        <template v-else-if="column.key === 'status'"
          ><Tag :color="record.status === 'enabled' ? 'success' : 'default'">{{
            record.status === 'enabled' ? '已启用' : '已停用'
          }}</Tag></template
        >
        <template v-else-if="column.key === 'actions'"
          ><Button
            v-access:code="['chat:prompt:query']"
            type="link"
            @click="show(record.id)"
            >查看 / 编辑</Button
          ><Popconfirm
            v-if="hasAccessByCodes(['chat:prompt:remove'])"
            title="删除模板？已有会话仍保留原快照。"
            @confirm="remove(record.id, record.version)"
            ><Button type="link" danger>删除</Button></Popconfirm
          ></template
        >
      </template>
    </Table>
    <div class="mobile-list">
      <Spin :spinning="loading"
        ><Empty
          v-if="!rows.length && !loading"
          description="暂无符合条件的模板"
          :image="Empty.PRESENTED_IMAGE_SIMPLE"
        />
        <article v-for="row in rows" :key="row.id">
          <h2>{{ row.name }}</h2>
          <p>{{ row.description || '暂无说明' }}</p>
          <p>
            {{ labels[row.chatMode] }} · 版本 {{ row.version }} ·
            {{ row.status === 'enabled' ? '已启用' : '已停用' }}
          </p>
          <Button v-access:code="['chat:prompt:query']" @click="show(row.id)"
            >查看 / 编辑</Button
          ><Popconfirm
            v-if="hasAccessByCodes(['chat:prompt:remove'])"
            title="删除模板？已有会话仍保留原快照。"
            @confirm="remove(row.id, row.version)"
            ><Button danger>删除</Button></Popconfirm
          >
        </article></Spin
      >
    </div>
    <Pagination
      v-if="total"
      v-model:current="page"
      :total="total"
      :page-size="20"
      :show-size-changer="false"
      class="pagination"
      @change="load"
    />
    <Drawer
      :open="open"
      @close="requestClose"
      :title="editing ? 'Prompt 详情' : '新增 Prompt 模板'"
      width="min(760px,100vw)"
      :closable="!saving"
      :mask-closable="!saving"
      :keyboard="!saving"
    >
      <Spin :spinning="fetching"
        ><div
          v-if="formError && detailFailed"
          ref="errorRegion"
          tabindex="-1"
          role="alert"
          class="save-error"
        >
          <Alert :message="formError" type="error" show-icon />
        </div>
        <Form :model="draft" layout="vertical" @finish="save">
          <FormItem name="name" label="模板名称" required
            ><Input
              v-model:value="draft.name"
              :maxlength="100"
              :disabled="!editable || saving"
          /></FormItem>
          <FormItem name="chatMode" label="适用模式" required
            ><Select
              v-model:value="draft.chatMode"
              :options="modes"
              :disabled="!editable || saving"
          /></FormItem>
          <FormItem name="description" label="模板说明"
            ><Input.TextArea
              v-model:value="draft.description"
              :maxlength="500"
              :rows="2"
              :disabled="!editable || saving"
              placeholder="说明该模板适合解决什么问题"
          /></FormItem>
          <FormItem name="content" label="提示词内容" required
            ><Input.TextArea
              v-model:value="draft.content"
              :maxlength="16000"
              show-count
              :rows="12"
              :disabled="!editable || saving"
              placeholder="填写该模式下的补充指导、回答结构与约束"
          /></FormItem>
          <FormItem name="status" label="状态"
            ><Select
              v-model:value="draft.status"
              :options="states"
              :disabled="!editable || saving"
          /></FormItem>
          <p class="note">
            仅启用的模板可供新会话选择。停用、修改或删除模板不改变已有会话。
          </p>
          <div
            v-if="formError && !detailFailed"
            ref="errorRegion"
            tabindex="-1"
            role="alert"
            class="save-error"
          >
            <Alert :message="formError" type="error" show-icon />
          </div>
          <Button
            v-if="editable"
            type="primary"
            html-type="submit"
            :disabled="fetching"
            :loading="saving"
            >保存模板</Button
          >
        </Form>
      </Spin>
    </Drawer>
  </main>
</template>
<style scoped>
.save-error {
  margin-bottom: 16px;
}
.save-error:focus-visible {
  outline: 2px solid hsl(var(--primary));
  outline-offset: 3px;
}
.prompt-workbench {
  margin: 20px;
  padding: 24px;
  background: hsl(var(--card));
  border-radius: 8px;
  min-width: 0;
}
header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}
h1 {
  font-size: 22px;
  font-weight: 600;
  margin: 0 0 8px;
}
header p,
.note {
  color: hsl(var(--muted-foreground));
  font-size: 13px;
  line-height: 1.6;
}
.filters {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) auto;
  gap: 16px;
}
.filter-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.pagination {
  margin-top: 20px;
  text-align: right;
}
.mobile-list {
  display: none;
}
.mobile-list article {
  padding: 16px 0;
  border-bottom: 1px solid hsl(var(--border));
  overflow-wrap: anywhere;
}
.mobile-list h2 {
  font-size: 16px;
  font-weight: 600;
}
.mobile-list p {
  margin: 8px 0;
  color: hsl(var(--muted-foreground));
}
.note {
  margin-bottom: 16px;
}
@media (max-width: 1000px) {
  .filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 600px) {
  .prompt-workbench {
    margin: 12px;
    padding: 16px;
  }
  header {
    flex-direction: column;
  }
  .filters {
    grid-template-columns: minmax(0, 1fr);
    gap: 0;
  }
  .filter-actions {
    padding-bottom: 20px;
  }
  .mobile-list {
    display: block;
  }
  .desktop-list {
    display: none;
  }
}
</style>
