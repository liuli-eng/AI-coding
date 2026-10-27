import type { PageQuery, PageResult } from '#/api/common';
import { requestClient } from '#/api/request';
export interface Chapter {
  id?: string;
  title: string;
  content: string;
}
export interface Course {
  id: string;
  title: string;
  description?: string;
  status: string;
  version: number;
  chapterCount: number;
}
export interface CourseDetail extends Omit<Course, 'chapterCount'> {
  chapters: Chapter[];
}
export type CourseSave = Omit<CourseDetail, 'id' | 'version'> & {
  version?: number;
};
export const courseList = (params: PageQuery) =>
  requestClient.get<PageResult<Course>>('/system/course/list', { params });
export const courseDetail = (id: string) =>
  requestClient.get<CourseDetail>(`/system/course/${id}`);
export const courseSave = (id: string | undefined, data: CourseSave) =>
  id
    ? requestClient.put(`/system/course/${id}`, data)
    : requestClient.post('/system/course', data);
