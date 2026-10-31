<script setup lang="ts">
import type {
  Course,
  CourseSave,
  MaterialSummary,
} from '#/api/learning/course';

import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from 'vue';

import { useAccess } from '@vben/access';
import { isCancel } from '@vben/request';

import {
  Alert,
  Button,
  Drawer,
  Empty,
  Form,
  FormItem,
  Input,
  message,
  Modal,
  Pagination,
  Progress,
  Select,
  Spin,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  courseDetail,
  courseList,
  courseSave,
  deleteCourseMaterial,
  downloadCourseMaterial,
  uploadCourseMaterial,
} from '#/api/learning/course';

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
let disposed = false;
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
  if (saving.value || uploading.value || confirmingClose) return;
  if (
    fetching.value ||
    (JSON.stringify(draft) === savedDraft.value &&
      !materialRows.value.some((row) => row.state === 'error'))
  ) {
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
  materialIds: [],
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
  if (!value) {
    detailSequence++;
    cleanupDraft();
  }
});
async function show(courseId?: string) {
  if (saving.value || uploading.value) return;
  cleanupDraft();
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
    materialIds: [],
  });
  savedDraft.value = JSON.stringify(draft);
  if (!courseId) return;
  fetching.value = true;
  try {
    const result = await courseDetail(courseId);
    if (current === detailSequence) {
      Object.assign(draft, {
        title: result.title,
        description: result.description,
        status: result.status,
        chapters: result.chapters,
        version: result.version,
        materialIds: (result.materials || []).map((material) => material.id),
      });
      materialRows.value = (result.materials || []).map((material) => ({
        key: material.id,
        name: material.name,
        size: material.size,
        material,
        state: 'ready',
        percent: 100,
      }));
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
  if (uploading.value || saving.value) return;
  const target = index + direction;
  if (target < 0 || target >= draft.chapters.length) return;
  const items = [...draft.chapters];
  [items[index], items[target]] = [items[target]!, items[index]!];
  draft.chapters = items;
}
async function save() {
  if (saving.value || uploading.value || fetching.value || !editable.value)
    return;
  formError.value = '';
  if (materialRows.value.some((row) => row.state === 'error')) {
    formError.value = '请重试或移除上传失败的资料后保存。';
    return;
  }
  if (!draft.title.trim()) {
    formError.value = '请填写课程名称。';
    return;
  }
  if (draft.status === 'published' && draft.chapters.length === 0) {
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
      materialIds: materialRows.value.flatMap((row) =>
        row.material ? [row.material.id] : [],
      ),
    });
    newMaterialIds.clear();
    message.success('课程已保存');
    open.value = false;
    await load();
  } catch {
    formError.value = '保存失败，输入已保留。若版本已更新，请重新打开课程。';
  } finally {
    saving.value = false;
    if (disposed) cleanupDraft();
  }
}
interface MaterialRow {
  key: string;
  name: string;
  size: number;
  material?: MaterialSummary;
  file?: File;
  state: 'error' | 'processing' | 'ready' | 'uploading';
  percent: number;
  error?: string;
}
const materialRows = ref<MaterialRow[]>([]);
const fileInput = ref<HTMLInputElement>();
const materialError = ref('');
const downloads = ref<string[]>([]);
const uploading = computed(() =>
  materialRows.value.some((row) =>
    ['processing', 'uploading'].includes(row.state),
  ),
);
const textLength = computed(() =>
  materialRows.value.reduce(
    (sum, row) => sum + (row.material?.textLength || 0),
    0,
  ),
);
const newMaterialIds = new Set<string>();
const uploads = new Set<AbortController>();
const downloadControllers = new Set<AbortController>();
let draftGeneration = 0;
let nextMaterialKey = 0;
function syncMaterials() {
  draft.materialIds = materialRows.value.flatMap((row) =>
    row.material ? [row.material.id] : [],
  );
}
async function cleanupMaterial(materialId: string) {
  try {
    await deleteCourseMaterial(materialId);
  } catch (error) {
    if (!isCancel(error)) message.warning('未保存的资料清理失败，请稍后重试。');
  }
}
function cleanupDraft() {
  draftGeneration++;
  uploads.forEach((controller) => controller.abort());
  uploads.clear();
  downloadControllers.forEach((controller) => controller.abort());
  downloadControllers.clear();
  // A navigation can unmount this view while save is in flight. Wait for its
  // result before deleting anything that the server may already have bound.
  if (!saving.value) {
    newMaterialIds.forEach((materialId) => {
      void cleanupMaterial(materialId);
    });
    newMaterialIds.clear();
  }
  materialRows.value = [];
  materialError.value = '';
  downloads.value = [];
}
function formatSize(size: number) {
  return size >= 1024 * 1024
    ? `${(size / 1024 / 1024).toFixed(1)} MB`
    : `${Math.max(1, Math.ceil(size / 1024))} KB`;
}
async function uploadMaterial(row: MaterialRow) {
  if (!row.file || saving.value) return;
  const generation = draftGeneration;
  const controller = new AbortController();
  uploads.add(controller);
  row.error = '';
  row.state = 'uploading';
  row.percent = 0;
  try {
    const material = await uploadCourseMaterial(
      row.file,
      controller.signal,
      (percent) => {
        if (generation !== draftGeneration) return;
        row.percent = percent;
        row.state = percent >= 100 ? 'processing' : 'uploading';
      },
    );
    if (generation !== draftGeneration || !materialRows.value.includes(row)) {
      void cleanupMaterial(material.id);
      return;
    }
    if (textLength.value + material.textLength > 60_000) {
      void cleanupMaterial(material.id);
      throw new Error(
        '课程资料文字总量不能超过 60000 字，请移除其他资料后重试。',
      );
    }
    newMaterialIds.add(material.id);
    row.material = material;
    row.name = material.name;
    row.size = material.size;
    row.state = 'ready';
    row.percent = 100;
    syncMaterials();
  } catch (error) {
    if (generation === draftGeneration && isCancel(error)) {
      materialRows.value = materialRows.value.filter(
        (item) => item.key !== row.key,
      );
      syncMaterials();
    } else if (generation === draftGeneration) {
      row.state = 'error';
      row.error =
        error instanceof Error && error.message
          ? error.message
          : '上传失败，请重试。';
    }
  } finally {
    uploads.delete(controller);
  }
}
function selectMaterials(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = [...(input.files || [])];
  input.value = '';
  if (!editable.value || saving.value || fetching.value) return;
  materialError.value = '';
  for (const file of files) {
    const suffix = file.name.split('.').pop()?.toLowerCase();
    if (!suffix || !['docx', 'md', 'pdf', 'txt'].includes(suffix)) {
      materialError.value = '仅支持 PDF、DOCX、TXT、MD 文件。';
      continue;
    }
    if (file.size === 0 || file.size > 10 * 1024 * 1024) {
      materialError.value = '请选择非空且不超过 10 MB 的文件。';
      continue;
    }
    if (materialRows.value.length >= 10) {
      materialError.value = '每门课程最多上传 10 份资料。';
      break;
    }
    materialRows.value.push({
      key: `upload-${++nextMaterialKey}`,
      name: file.name,
      size: file.size,
      file,
      state: 'uploading',
      percent: 0,
    });
    void uploadMaterial(materialRows.value[materialRows.value.length - 1]!);
  }
}
function removeMaterial(row: MaterialRow) {
  if (saving.value || uploading.value || !editable.value) return;
  materialRows.value = materialRows.value.filter(
    (item) => item.key !== row.key,
  );
  if (row.material && newMaterialIds.delete(row.material.id))
    void cleanupMaterial(row.material.id);
  syncMaterials();
}
async function downloadMaterial(material: MaterialSummary) {
  const generation = draftGeneration;
  const controller = new AbortController();
  downloadControllers.add(controller);
  downloads.value.push(material.id);
  materialError.value = '';
  try {
    await downloadCourseMaterial(material.id, material.name, controller.signal);
  } catch (error) {
    if (generation === draftGeneration && !isCancel(error))
      materialError.value =
        error instanceof Error ? error.message : '下载失败，请重试。';
  } finally {
    downloadControllers.delete(controller);
    if (generation === draftGeneration)
      downloads.value = downloads.value.filter(
        (value) => value !== material.id,
      );
  }
}
onBeforeUnmount(() => {
  disposed = true;
  sequence++;
  detailSequence++;
  cleanupDraft();
});
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
      >
        新增课程
      </Button>
    </header>
    <Form
      :model="{ keyword, status }"
      layout="vertical"
      class="filters"
      @finish="search"
    >
      <FormItem name="keyword" label="课程名称">
        <Input v-model:value="keyword" allow-clear placeholder="搜索课程名称" />
      </FormItem>
      <FormItem name="status" label="发布状态">
        <Select
          v-model:value="status"
          :options="states"
          placeholder="全部状态"
          allow-clear
        />
      </FormItem>
      <div class="filter-actions">
        <Button type="primary" html-type="submit" :loading="loading">
          查询
        </Button>
        <Button
          @click="
            keyword = '';
            status = undefined;
            search();
          "
        >
          重置
        </Button>
      </div>
    </Form>
    <Alert v-if="error" :message="error" type="error" show-icon>
      <template #action>
        <Button @click="load">重试 </Button>
      </template>
    </Alert>
    <Table
      class="desktop-list"
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="false"
      :scroll="{ x: 900 }"
      row-key="id"
      :locale="{ emptyText: '暂无符合条件的课程' }"
    >
      <template #bodyCell="{ column, record }">
        <Tag
          v-if="column.key === 'status'"
          :color="record.status === 'published' ? 'success' : 'default'"
        >
          {{ labels[record.status] }}
        </Tag>
        <Button
          v-else-if="column.key === 'actions'"
          v-access:code="['learning:course:query']"
          type="link"
          @click="show(record.id)"
        >
          查看 / 编辑
        </Button>
      </template>
    </Table>
    <div class="mobile-list">
      <Spin :spinning="loading">
        <Empty
          v-if="rows.length === 0 && !loading"
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
          >
            查看 / 编辑
          </Button>
        </article>
      </Spin>
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
      :closable="!saving && !uploading"
      :mask-closable="!saving && !uploading"
      :keyboard="!saving && !uploading"
    >
      <Spin :spinning="fetching">
        <div
          v-if="formError && detailFailed"
          ref="errorRegion"
          tabindex="-1"
          role="alert"
          class="save-error"
        >
          <Alert :message="formError" type="error" show-icon />
        </div>
        <Form :model="draft" layout="vertical" @finish="save">
          <FormItem name="title" label="课程名称" required>
            <Input
              v-model:value="draft.title"
              :maxlength="150"
              :disabled="!editable || saving"
            />
          </FormItem>
          <FormItem name="description" label="课程说明">
            <Input.TextArea
              v-model:value="draft.description"
              :maxlength="2000"
              :rows="3"
              :disabled="!editable || saving"
            />
          </FormItem>
          <section
            class="course-materials"
            aria-labelledby="course-materials-heading"
            :aria-busy="uploading"
          >
            <div class="chapter-heading">
              <h2 id="course-materials-heading">课程资料</h2>
              <Button
                v-if="editable"
                :disabled="saving || fetching || materialRows.length >= 10"
                @click="fileInput?.click()"
              >
                上传资料
              </Button>
              <input
                ref="fileInput"
                v-show="false"
                class="material-input"
                type="file"
                multiple
                accept=".pdf,.docx,.txt,.md"
                aria-label="上传课程资料"
                :disabled="
                  !editable || saving || fetching || materialRows.length >= 10
                "
                @change="selectMaterials"
              />
            </div>
            <p class="note">
              支持 PDF、DOCX、TXT、MD；每份最多 10 MB，每门课程最多 10
              份，提取文字合计最多 60000 字。
            </p>
            <p class="note" aria-live="polite">
              {{ materialRows.length }} / 10 份 · {{ textLength }} / 60000 字
              <span v-if="uploading">
                · 上传处理期间请稍候，完成后可保存或关闭。
              </span>
            </p>
            <Alert
              v-if="materialError"
              :message="materialError"
              type="error"
              show-icon
              role="alert"
            />
            <p v-if="materialRows.length === 0" class="note">
              尚未添加课程资料。
            </p>
            <ul v-else class="material-list">
              <li
                v-for="row in materialRows"
                :key="row.key"
                class="material-row"
              >
                <div class="material-copy">
                  <span class="material-name">{{ row.name }} </span>
                  <span class="note">{{ formatSize(row.size) }} </span>
                  <p role="status">
                    {{
                      row.state === 'uploading'
                        ? `正在上传 ${row.percent}%`
                        : row.state === 'processing'
                          ? '正在提取文字…'
                          : row.state === 'ready'
                            ? '已就绪'
                            : '上传失败'
                    }}
                  </p>
                  <Progress
                    v-if="
                      row.state === 'uploading' || row.state === 'processing'
                    "
                    :percent="row.percent"
                    :show-info="false"
                    size="small"
                  />
                  <p v-if="row.error" class="material-failure" role="alert">
                    {{ row.error }}
                  </p>
                </div>
                <div class="material-actions">
                  <Button
                    v-if="row.state === 'error' && editable"
                    :disabled="saving"
                    @click="uploadMaterial(row)"
                  >
                    重试
                  </Button>
                  <Button
                    v-if="row.material"
                    :loading="downloads.includes(row.material.id)"
                    @click="downloadMaterial(row.material)"
                  >
                    下载
                  </Button>
                  <Button
                    v-if="editable"
                    :disabled="saving || uploading"
                    danger
                    :aria-label="`移除资料 ${row.name}`"
                    @click="removeMaterial(row)"
                  >
                    移除
                  </Button>
                </div>
              </li>
            </ul>
          </section>
          <div class="chapter-heading">
            <h2>章节编排</h2>
            <Button
              v-if="editable"
              :disabled="saving || draft.chapters.length >= 100"
              @click="draft.chapters.push({ title: '', content: '' })"
            >
              添加章节
            </Button>
          </div>
          <Empty
            v-if="draft.chapters.length === 0"
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
                  :disabled="saving || uploading || index === 0"
                  @click="move(index, -1)"
                >
                  上移
                </Button>
                <Button
                  size="small"
                  :disabled="
                    saving || uploading || index === draft.chapters.length - 1
                  "
                  @click="move(index, 1)"
                >
                  下移
                </Button>
                <Button
                  size="small"
                  danger
                  :disabled="saving"
                  @click="draft.chapters.splice(index, 1)"
                >
                  移除
                </Button>
              </div>
            </div>
            <FormItem
              :name="['chapters', index, 'title']"
              :label="`第 ${index + 1} 章名称`"
              required
            >
              <Input
                v-model:value="chapter.title"
                :maxlength="150"
                :disabled="!editable || saving"
              />
            </FormItem>
            <FormItem
              :name="['chapters', index, 'content']"
              label="学习材料与目标"
              required
            >
              <Input.TextArea
                v-model:value="chapter.content"
                :maxlength="12000"
                show-count
                :rows="7"
                :disabled="!editable || saving"
                placeholder="填写学习目标、概念、示例和练习。AI 教学会话将参考这些材料。"
              />
            </FormItem>
          </section>
          <FormItem name="status" label="发布状态">
            <Select
              v-model:value="draft.status"
              :options="states"
              :disabled="!editable || saving"
            />
          </FormItem>
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
            :disabled="fetching || uploading"
            :loading="saving"
          >
            保存课程
          </Button>
        </Form>
      </Spin>
    </Drawer>
  </main>
</template>
<style scoped>
.course-materials {
  margin: 24px 0;
}
.material-input {
  display: none;
}
.material-list {
  padding: 0;
  margin: 16px 0;
  list-style: none;
}
.material-row {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid hsl(var(--border));
}
.material-copy {
  min-width: 0;
  flex: 1;
}
.material-name {
  display: block;
  overflow-wrap: anywhere;
}
.material-copy p {
  margin: 4px 0;
  font-size: 13px;
}
.material-failure {
  color: hsl(var(--destructive));
}
.material-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
@media (max-width: 600px) {
  .material-row {
    flex-direction: column;
    gap: 8px;
  }
  .material-copy {
    width: 100%;
  }
}
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
