<script setup lang="ts">
import type { Course, Enrollment, Learning } from '@/api/learning';
import type { ChatSessionVo } from '@/api/session/types';
import { onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import {
  courses,
  joinCourse,
  learningDetail,
  myCourses,
  setChapterProgress,
  startChapter,
} from '@/api/learning';
import { get_session } from '@/api/session';
import { useAgentStore } from '@/stores/modules/agent';
import { useChatStore } from '@/stores/modules/chat';
import { useSessionStore } from '@/stores/modules/session';
import { useUserStore } from '@/stores/modules/user';
import { readApiResult } from '@/utils/api-result';

const user = useUserStore();
const sessionStore = useSessionStore();
const agentStore = useAgentStore();
const chatStore = useChatStore();
const router = useRouter();
const tab = ref<'mine' | 'catalog'>('mine');
const keyword = ref('');
const page = ref(1);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const catalog = ref<Course[]>([]);
const enrolled = ref<Enrollment[]>([]);
const joining = ref<string>();
let sequence = 0;
async function load() {
  const current = ++sequence;
  error.value = '';
  catalog.value = [];
  enrolled.value = [];
  if (!user.token) {
    loading.value = false;
    total.value = 0;
    return;
  }
  loading.value = true;
  try {
    if (tab.value === 'catalog') {
      const result = await courses({ keyword: keyword.value, pageNum: page.value, pageSize: 12 });
      if (current !== sequence)
        return;
      catalog.value = result.rows || [];
      total.value = result.total;
    }
    else {
      const result = await myCourses({ pageNum: page.value, pageSize: 12 });
      if (current !== sequence)
        return;
      enrolled.value = result.rows || [];
      total.value = result.total;
    }
  }
  catch {
    if (current === sequence)
      error.value = '课程加载失败，请重试。';
  }
  finally {
    if (current === sequence)
      loading.value = false;
  }
}
function search() {
  page.value = 1;
  load();
}
watch([tab, () => user.token], search);
onMounted(load);
const open = ref(false);
const detail = ref<Learning>();
const detailLoading = ref(false);
const detailError = ref('');
const action = ref('');
const selectedChapter = ref('');
let detailSequence = 0;
watch(() => user.token, () => {
  detailSequence++;
  open.value = false;
  detail.value = undefined;
});
async function show(id: string) {
  const current = ++detailSequence;
  open.value = true;
  detail.value = undefined;
  detailError.value = '';
  detailLoading.value = true;
  try {
    const result = await learningDetail(id);
    if (current !== detailSequence)
      return;
    const record = result.data;
    detail.value = record;
    selectedChapter.value
      = record.chapters.find(c => !record.completedChapters.includes(c.id))?.id
        || record.chapters[0]?.id
        || '';
  }
  catch {
    if (current === detailSequence)
      detailError.value = '学习记录加载失败，请关闭后重试。';
  }
  finally {
    if (current === detailSequence)
      detailLoading.value = false;
  }
}
async function join(id: string) {
  if (joining.value)
    return;
  joining.value = id;
  try {
    const result = await joinCourse(id);
    await show(result.data);
    tab.value = 'mine';
  }
  catch {
    ElMessage.error('加入失败，课程可能已下架，请刷新后重试。');
  }
  finally {
    joining.value = undefined;
  }
}
async function progress(chapterId: string) {
  if (!detail.value || action.value)
    return;
  action.value = chapterId;
  detailError.value = '';
  try {
    const done = !detail.value.completedChapters.includes(chapterId);
    await setChapterProgress(detail.value.id, chapterId, done, detail.value.version);
    const result = await learningDetail(detail.value.id);
    detail.value = result.data;
    await load();
  }
  catch {
    detailError.value = '进度保存失败，请重试；若记录已更新，请重新打开课程。';
  }
  finally {
    action.value = '';
  }
}
async function study(chapterId: string) {
  if (!detail.value || action.value)
    return;
  action.value = chapterId;
  detailError.value = '';
  try {
    const result = await startChapter(detail.value.id, chapterId);
    const session = await readApiResult<ChatSessionVo>(get_session(result.data.sessionId));
    sessionStore.setCurrentSession(session.data);
    agentStore.clearCurrentAgentInfo();
    chatStore.setCurrentWorkflow(null);
    if (result.data.created && result.data.initialMessage)
      localStorage.setItem('chatContent', result.data.initialMessage);
    await sessionStore.requestSessionList(1, true);
    await router.push({ name: 'chatWithId', params: { id: result.data.sessionId } });
  }
  catch {
    detailError.value = '教学会话打开失败，请重试。';
  }
  finally {
    action.value = '';
  }
}
</script>

<template>
  <main class="learning-workbench">
    <header>
      <div>
        <h1>我的课程</h1>
        <p>按章节学习，保存进度，随时回到上次的教学会话。</p>
      </div>
      <el-button :loading="loading" :disabled="!user.token" @click="load">
        刷新课程
      </el-button>
    </header>
    <div v-if="!user.token" class="login-state">
      <el-empty description="登录后查看课程和学习进度">
        <el-button type="primary" @click="user.ensureLogin('/learning', '登录后即可继续学习')">
          登录后学习
        </el-button>
      </el-empty>
    </div>
    <template v-else>
      <nav class="learning-tabs" aria-label="课程分类">
        <button
          type="button"
          :class="{ active: tab === 'mine' }"
          :aria-pressed="tab === 'mine'"
          @click="tab = 'mine'"
        >
          已加入的课程
        </button><button
          type="button"
          :class="{ active: tab === 'catalog' }"
          :aria-pressed="tab === 'catalog'"
          @click="tab = 'catalog'"
        >
          课程目录
        </button>
      </nav>
      <form v-if="tab === 'catalog'" class="course-search" @submit.prevent="search">
        <el-input
          v-model="keyword"
          placeholder="搜索课程名称"
          clearable
          aria-label="搜索课程名称"
          @clear="search"
        /><el-button native-type="submit" :loading="loading">
          搜索
        </el-button>
      </form>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon>
        <el-button link @click="load">
          重试
        </el-button>
      </el-alert>
      <div v-loading="loading" class="course-list" :aria-busy="loading">
        <template v-if="tab === 'mine'">
          <article v-for="item in enrolled" :key="item.id">
            <div class="course-copy">
              <h2>{{ item.title }}</h2>
              <p>{{ item.description || "暂无课程说明" }}</p>
              <p class="metadata">
                已完成 {{ item.completedCount }} / {{ item.totalChapters }} 章 · 课程版本
                {{ item.courseVersion }}
              </p>
              <el-progress
                :percentage="
                  item.totalChapters
                    ? Math.round((item.completedCount / item.totalChapters) * 100)
                    : 0
                "
                :stroke-width="6"
              />
            </div>
            <el-button type="primary" @click="show(item.id)">
              继续学习
            </el-button>
          </article>
          <el-empty v-if="!enrolled.length && !loading && !error" description="还没有加入课程">
            <el-button @click="tab = 'catalog'">
              浏览课程目录
            </el-button>
          </el-empty>
        </template>
        <template v-else>
          <article v-for="item in catalog" :key="item.id">
            <div class="course-copy">
              <h2>{{ item.title }}</h2>
              <p>{{ item.description || "暂无课程说明" }}</p>
              <p class="metadata">
                {{ item.chapterCount }} 章 · 版本 {{ item.version }}
              </p>
            </div>
            <el-button
              type="primary"
              :loading="joining === item.id"
              :disabled="!!joining"
              @click="join(item.id)"
            >
              加入课程
            </el-button>
          </article>
          <el-empty
            v-if="!catalog.length && !loading && !error"
            description="暂无符合条件的已发布课程"
          />
        </template>
      </div>
      <el-pagination
        v-if="total > 12"
        v-model:current-page="page"
        :total="total"
        :page-size="12"
        layout="prev,pager,next"
        @current-change="load"
      />
    </template>
    <el-drawer
      v-model="open"
      title="课程学习"
      size="min(980px,100vw)"
      :close-on-click-modal="!action"
      :close-on-press-escape="!action"
      :show-close="!action"
      @closed="detailSequence++"
    >
      <div v-loading="detailLoading">
        <el-alert
          v-if="detailError"
          :title="detailError"
          type="error"
          :closable="false"
          show-icon
        />
        <template v-if="detail">
          <h2>{{ detail.title }}</h2>
          <p class="course-description">
            {{ detail.description }}
          </p>
          <p class="metadata">
            加入时的课程版本 {{ detail.courseVersion }} · 已完成
            {{ detail.completedChapters.length }} / {{ detail.chapters.length }} 章
          </p>
          <div class="chapter-layout">
            <nav class="chapter-nav" aria-label="课程章节">
              <button
                v-for="(chapter, index) in detail.chapters"
                :key="chapter.id"
                type="button"
                :class="{ selected: selectedChapter === chapter.id }"
                :aria-pressed="selectedChapter === chapter.id"
                @click="selectedChapter = chapter.id"
              >
                <span>第 {{ index + 1 }} 章 · {{ chapter.title }}</span><small>{{
                  detail.completedChapters.includes(chapter.id) ? "已完成" : "未完成"
                }}</small>
              </button>
            </nav>
            <section
              v-for="chapter in detail.chapters.filter((c) => c.id === selectedChapter)"
              :key="chapter.id"
              class="chapter-content"
            >
              <h3>{{ chapter.title }}</h3>
              <p class="learning-material">
                {{ chapter.content }}
              </p>
              <div class="chapter-actions">
                <el-button
                  type="primary"
                  :loading="action === chapter.id"
                  :disabled="!!action"
                  @click="study(chapter.id)"
                >
                  进入 AI 教学
                </el-button><el-button :disabled="!!action" @click="progress(chapter.id)">
                  {{
                    detail.completedChapters.includes(chapter.id) ? "标记为未完成" : "标记为已完成"
                  }}
                </el-button>
              </div>
            </section>
          </div>
        </template>
      </div>
    </el-drawer>
  </main>
</template>

<style scoped>
.learning-workbench {
  box-sizing: border-box;
  min-height: 100%;
  padding: var(--workspace-top-gap, 24px) var(--workspace-padding, 24px);
  color: var(--el-text-color-primary);
}
header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}
h1 {
  margin: 0;
  font-size: 26px;
  font-weight: 600;
}
header p,
.course-description,
.metadata {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.7;
}
.learning-tabs {
  display: flex;
  gap: 24px;
  border-bottom: 1px solid var(--el-border-color-light);
  margin-bottom: 24px;
}
.learning-tabs button {
  font: inherit;
  font-size: 14px;
  padding: 12px 0;
  color: var(--el-text-color-regular);
  background: none;
  border: 0;
  border-bottom: 2px solid transparent;
  cursor: pointer;
}
.learning-tabs button.active {
  color: var(--el-color-primary);
  border-bottom-color: var(--el-color-primary);
}
.course-search {
  display: flex;
  gap: 12px;
  max-width: 560px;
  margin-bottom: 20px;
}
.course-list {
  min-height: 180px;
}
.course-list article {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 0;
  border-bottom: 1px solid var(--el-border-color-light);
}
h2 {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 8px;
}
.course-copy {
  min-width: 0;
  max-width: 70ch;
}
.course-copy p {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.7;
}
.course-copy .el-progress {
  max-width: 320px;
  margin-top: 12px;
}
.chapter-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 28px;
  margin-top: 24px;
}
.chapter-nav button {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  font: inherit;
  font-size: 14px;
  line-height: 1.6;
  padding: 12px;
  text-align: left;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  overflow-wrap: anywhere;
}
.chapter-nav button.selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.chapter-nav small {
  color: var(--el-text-color-secondary);
}
h3 {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 16px;
}
.learning-material {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.8;
  max-width: 75ch;
}
.chapter-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 28px;
}
.chapter-actions .el-button + .el-button {
  margin-left: 0;
}
button:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 3px;
}
@media (max-width: 700px) {
  .chapter-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  header {
    flex-direction: column;
  }
  .course-list article {
    align-items: flex-start;
    flex-direction: column;
    gap: 16px;
  }
  .course-copy {
    max-width: 100%;
  }
  .learning-workbench {
    padding: 20px 16px;
  }
}
</style>
