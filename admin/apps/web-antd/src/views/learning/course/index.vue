<script setup lang="ts">
import type { Course, CourseSave } from '#/api/learning/course';
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
  Select,
  Spin,
  Table,
  Tag,
  message,
} from 'ant-design-vue';
import { courseDetail, courseList, courseSave } from '#/api/learning/course';
const { hasAccessByCodes } = useAccess();
const rows = ref<Course[]>([]);
const loading = ref(false);
const error = ref('');
const keyword = ref('');
const status = ref<string>();
const page = ref(1);
const total = ref(0);
const states = [
  { value: 'draft', label: '草稿' },
  { value: 'published', label: '已发布' },
  { value: 'archived', label: '已归档' },
];
const labels = Object.fromEntries(states.map((s) => [s.value, s.label]));
const columns = [
  { title: '课程名称', dataIndex: 'title', key: 'title', width: 240 },
  {
    title: '说明',
    dataIndex: 'description',
    key: 'description',
    ellipsis: true,
  },
  { title: '章节', dataIndex: 'chapterCount', key: 'chapters', width: 80 },
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
    const res = await courseList({
      keyword: keyword.value,
      status: status.value,
      pageNum: page.value,
      pageSize: 20,
    });
    if (current !== sequence) return;
    rows.value = res.rows || [];
    total.value = res.total;
  } catch {
    if (current === sequence) {
      error.value = '课程加载失败，请重试。';
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
const id = ref<string>();
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
const draft = reactive<CourseSave>({
  title: '',
  description: '',
  status: 'draft',
  chapters: [],
});
const editable = computed(
  () =>
    !detailFailed.value &&
    hasAccessByCodes([
      id.value ? 'learning:course:edit' : 'learning:course:add',
    ]),
);
let detailSequence = 0;
watch(open, (value) => {
  if (!value) detailSequence++;
});
async function show(courseId?: string) {
  const current = ++detailSequence;
  fetching.value = false;
  id.value = courseId;
  open.value = true;
  formError.value = '';
  detailFailed.value = false;
  Object.assign(draft, {
    title: '',
    description: '',
    status: 'draft',
    chapters: [],
    version: undefined,
  });
  savedDraft.value = JSON.stringify(draft);
  if (!courseId) return;
  fetching.value = true;
  try {
    const result = await courseDetail(courseId);
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
function move(index: number, direction: number) {
  const target = index + direction;
  if (target < 0 || target >= draft.chapters.length) return;
  const items = [...draft.chapters];
  [items[index], items[target]] = [items[target]!, items[index]!];
  draft.chapters = items;
}
async function save() {
  if (saving.value || fetching.value || !editable.value) return;
  formError.value = '';
  if (!draft.title.trim()) {
    formError.value = '请填写课程名称。';
    return;
  }
  if (draft.status === 'published' && !draft.chapters.length) {
    formError.value = '发布前请至少添加一章。';
    return;
  }
  if (draft.chapters.some((c) => !c.title.trim() || !c.content.trim())) {
    formError.value = '请填写每章的名称和学习材料。';
    return;
  }
  saving.value = true;
  try {
    await courseSave(id.value, {
      title: draft.title.trim(),
      description: draft.description,
      status: draft.status,
      chapters: draft.chapters,
      version: draft.version,
    });
    message.success('课程已保存');
    open.value = false;
    await load();
  } catch {
    formError.value = '保存失败，输入已保留。若版本已更新，请重新打开课程。';
  } finally {
    saving.value = false;
  }
}
onMounted(load);
</script>
<template>
  <main class="course-workbench">
    <header>
      <div>
        <h1>课程管理</h1>
        <p>
          编排学习章节，发布后供用户加入。已有学习记录保留加入时的课程版本。
        </p>
      </div>
      <Button
        v-access:code="['learning:course:add']"
        type="primary"
        @click="show()"
        >新增课程</Button
      >
    </header>
    <Form
      :model="{ keyword, status }"
      layout="vertical"
      class="filters"
      @finish="search"
      ><FormItem name="keyword" label="课程名称"
        ><Input
          v-model:value="keyword"
          allow-clear
          placeholder="搜索课程名称" /></FormItem
      ><FormItem name="status" label="发布状态"
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
            status = undefined;
            search();
          "
          >重置</Button
        >
      </div></Form
    >
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
      :locale="{ emptyText: '暂无符合条件的课程' }"
      ><template #bodyCell="{ column, record }"
        ><Tag
          v-if="column.key === 'status'"
          :color="record.status === 'published' ? 'success' : 'default'"
          >{{ labels[record.status] }}</Tag
        ><Button
          v-else-if="column.key === 'actions'"
          v-access:code="['learning:course:query']"
          type="link"
          @click="show(record.id)"
          >查看 / 编辑</Button
        ></template
      ></Table
    >
    <div class="mobile-list">
      <Spin :spinning="loading"
        ><Empty
          v-if="!rows.length && !loading"
          description="暂无符合条件的课程"
          :image="Empty.PRESENTED_IMAGE_SIMPLE"
        />
        <article v-for="row in rows" :key="row.id">
          <h2>{{ row.title }}</h2>
          <p>{{ row.description || '暂无说明' }}</p>
          <p>
            {{ row.chapterCount }} 章 · {{ labels[row.status] }} · 版本
            {{ row.version }}
          </p>
          <Button
            v-access:code="['learning:course:query']"
            @click="show(row.id)"
            >查看 / 编辑</Button
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
      :title="id ? '课程详情' : '新增课程'"
      width="min(880px,100vw)"
      :closable="!saving"
      :mask-closable="!saving"
      :keyboard="!saving"
      ><Spin :spinning="fetching"
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
          <FormItem name="title" label="课程名称" required
            ><Input
              v-model:value="draft.title"
              :maxlength="150"
              :disabled="!editable || saving"
          /></FormItem>
          <FormItem name="description" label="课程说明"
            ><Input.TextArea
              v-model:value="draft.description"
              :maxlength="2000"
              :rows="3"
              :disabled="!editable || saving"
          /></FormItem>
          <div class="chapter-heading">
            <h2>章节编排</h2>
            <Button
              v-if="editable"
              :disabled="saving || draft.chapters.length >= 100"
              @click="draft.chapters.push({ title: '', content: '' })"
              >添加章节</Button
            >
          </div>
          <Empty
            v-if="!draft.chapters.length"
            description="还没有章节"
            :image="Empty.PRESENTED_IMAGE_SIMPLE"
          />
          <section
            v-for="(chapter, index) in draft.chapters"
            :key="chapter.id || index"
            class="chapter-editor"
          >
            <div class="chapter-tools">
              <h3>第 {{ index + 1 }} 章</h3>
              <div v-if="editable">
                <Button
                  size="small"
                  :disabled="saving || index === 0"
                  @click="move(index, -1)"
                  >上移</Button
                ><Button
                  size="small"
                  :disabled="saving || index === draft.chapters.length - 1"
                  @click="move(index, 1)"
                  >下移</Button
                ><Button
                  size="small"
                  danger
                  :disabled="saving"
                  @click="draft.chapters.splice(index, 1)"
                  >移除</Button
                >
              </div>
            </div>
            <FormItem
              :name="['chapters', index, 'title']"
              :label="`第 ${index + 1} 章名称`"
              required
              ><Input
                v-model:value="chapter.title"
                :maxlength="150"
                :disabled="!editable || saving"
            /></FormItem>
            <FormItem
              :name="['chapters', index, 'content']"
              label="学习材料与目标"
              required
              ><Input.TextArea
                v-model:value="chapter.content"
                :maxlength="12000"
                show-count
                :rows="7"
                :disabled="!editable || saving"
                placeholder="填写学习目标、概念、示例和练习。AI 教学会话将参考这些材料。"
            /></FormItem>
          </section>
          <FormItem name="status" label="发布状态"
            ><Select
              v-model:value="draft.status"
              :options="states"
              :disabled="!editable || saving"
          /></FormItem>
          <p class="note">
            草稿和归档课程不出现在课程目录中。课程更新或归档后，已加入的用户仍可继续学习原版本。
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
            >保存课程</Button
          >
        </Form>
      </Spin></Drawer
    >
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
.course-workbench {
  margin: 20px;
  padding: 24px;
  background: hsl(var(--card));
  border-radius: 8px;
  min-width: 0;
}
header,
.chapter-heading,
.chapter-tools {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
header {
  margin-bottom: 24px;
}
h1 {
  font-size: 22px;
  font-weight: 600;
  margin: 0 0 8px;
}
h2 {
  font-size: 16px;
  font-weight: 600;
}
h3 {
  font-size: 14px;
  font-weight: 600;
}
header p,
.note {
  color: hsl(var(--muted-foreground));
  font-size: 13px;
  line-height: 1.6;
}
.filters {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr)) auto;
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
.chapter-editor {
  padding: 20px 0;
  border-bottom: 1px solid hsl(var(--border));
  margin-bottom: 20px;
}
.chapter-tools {
  margin-bottom: 16px;
}
.chapter-tools > div {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.note {
  margin: 16px 0;
}
.mobile-list {
  display: none;
}
.mobile-list article {
  padding: 16px 0;
  border-bottom: 1px solid hsl(var(--border));
  overflow-wrap: anywhere;
}
.mobile-list p {
  margin: 8px 0;
  color: hsl(var(--muted-foreground));
}
@media (max-width: 600px) {
  .course-workbench {
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
  .desktop-list {
    display: none;
  }
  .mobile-list {
    display: block;
  }
  .chapter-tools {
    flex-wrap: wrap;
  }
}
</style>
