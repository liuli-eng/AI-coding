import { readApiResult, readPageResult } from '@/utils/api-result';
import { get, post, put } from '@/utils/request';

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
