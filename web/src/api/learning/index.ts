import router from '@/routers';
import { useUserStore } from '@/stores/modules/user';
import { readApiResult, readPageResult } from '@/utils/api-result';
import { downloadAttachment } from '@/utils/download-attachment';
import { get, post, put } from '@/utils/request';

export interface MaterialSummary {
  id: string;
  name: string;
  size: number;
  suffix: string;
  textLength: number;
}

export interface Course {
  id: string;
  title: string;
  description?: string;
  version: number;
  chapterCount: number;
}
export interface Enrollment {
  id: string;
  courseId: string;
  title: string;
  description?: string;
  courseVersion: number;
  totalChapters: number;
  completedCount: number;
  version: number;
}
export interface Chapter {
  id: string;
  title: string;
  content: string;
}
export interface Learning {
  id: string;
  courseId: string;
  title: string;
  description?: string;
  courseVersion: number;
  chapters: Chapter[];
  completedChapters: string[];
  materials: MaterialSummary[];
  version: number;
}
export function courses(params: { keyword?: string; pageNum: number; pageSize: number }) {
  return readPageResult<Course>(get('/learning/courses', params).json());
}
export function myCourses(params: { pageNum: number; pageSize: number }) {
  return readPageResult<Enrollment>(get('/learning/mine', params).json());
}
export function joinCourse(id: string) {
  return readApiResult<string>(post(`/learning/courses/${id}/join`).json());
}
export function learningDetail(id: string) {
  return readApiResult<Learning>(get(`/learning/mine/${id}`).json());
}
export function setChapterProgress(id: string, chapter: string, completed: boolean, version: number) {
  return readApiResult<void>(
    put(`/learning/mine/${id}/chapters/${encodeURIComponent(chapter)}`, {
      completed,
      version,
    }).json(),
  );
}
export function startChapter(id: string, chapter: string) {
  return readApiResult<{ sessionId: string; initialMessage?: string; created: boolean }>(
    post(`/learning/mine/${id}/chapters/${encodeURIComponent(chapter)}/session`).json(),
  );
}

/** This list owns its auth effects: a superseded request cannot expire another account. */
export async function sessionMaterials(sessionId: string, signal: AbortSignal, isCurrent: () => boolean) {
  const user = useUserStore();
  const token = user.token;
  const assertCurrent = () => {
    if (signal.aborted || !token || token !== user.token || !isCurrent())
      throw new DOMException('资料请求已取消', 'AbortError');
  };
  assertCurrent();
  const response = await fetch(`${import.meta.env.VITE_API_URL.replace(/\/$/, '')}/learning/sessions/${encodeURIComponent(sessionId)}/materials`, {
    headers: { Authorization: `Bearer ${token}`, ClientID: import.meta.env.VITE_CLIENT_ID },
    signal,
  });
  const result = await response.json();
  // Check after parsing and before auth/navigation side effects. The shared
  // hook-fetch interceptor runs too early to know this component's request scope.
  assertCurrent();
  const code = Number(result?.code) || response.status;
  if (code === 401)
    user.handleAuthExpired(router.currentRoute.value.fullPath, '登录状态已失效，请重新登录');
  else if (code === 403)
    void router.replace({ name: '403' });
  if (!response.ok)
    throw new Error(typeof result?.msg === 'string' ? result.msg : '资料加载失败，请重试。');
  return readApiResult<MaterialSummary[]>(Promise.resolve(result));
}
export function downloadLearningMaterial(enrollmentId: string, materialId: string, signal?: AbortSignal) {
  return downloadAttachment(`/learning/mine/${encodeURIComponent(enrollmentId)}/materials/${encodeURIComponent(materialId)}/download`, signal);
}
export function downloadSessionMaterial(sessionId: string, materialId: string, signal?: AbortSignal) {
  return downloadAttachment(`/learning/sessions/${encodeURIComponent(sessionId)}/materials/${encodeURIComponent(materialId)}/download`, signal);
}
