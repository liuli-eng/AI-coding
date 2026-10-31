import type { PageQuery, PageResult } from '#/api/common';

import { useAccessStore } from '@vben/stores';

import { requestClient } from '#/api/request';

export interface Chapter {
  id?: string;
  title: string;
  content: string;
}
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
  status: string;
  version: number;
  chapterCount: number;
}
export interface CourseDetail extends Omit<Course, 'chapterCount'> {
  chapters: Chapter[];
  materials: MaterialSummary[];
}
export type CourseSave = Omit<CourseDetail, 'id' | 'materials' | 'version'> & {
  materialIds?: string[];
  version?: number;
};
export const courseList = (params: PageQuery) =>
  requestClient.get<PageResult<Course>>('/system/course/list', { params });
export const courseDetail = (id: string) =>
  requestClient.get<CourseDetail>(`/system/course/${id}`);
export const courseSave = (id: string | undefined, data: CourseSave) =>
  id
    ? requestClient.put<string>(`/system/course/${id}`, data)
    : requestClient.post<string>('/system/course', data);

function materialRequestOptions(signal?: AbortSignal) {
  const accessStore = useAccessStore();
  const token = accessStore.accessToken;
  return {
    signal,
    isResponseCurrent: () =>
      !signal?.aborted && accessStore.accessToken === token,
  };
}

export const uploadCourseMaterial = (
  file: File,
  signal: AbortSignal,
  onProgress: (percent: number) => void,
) => {
  const options = materialRequestOptions(signal);
  return requestClient.upload<MaterialSummary>(
    '/system/course/materials',
    { file },
    {
      ...options,
      timeout: 120_000,
      onUploadProgress: (event) => {
        if (options.isResponseCurrent() && event.total)
          onProgress(Math.round((event.loaded / event.total) * 100));
      },
    },
  );
};
export const deleteCourseMaterial = (id: string) =>
  requestClient.delete<void>(
    `/system/course/materials/${encodeURIComponent(id)}`,
    materialRequestOptions(),
  );
export async function downloadCourseMaterial(
  id: string,
  name: string,
  signal?: AbortSignal,
) {
  const options = materialRequestOptions(signal);
  const blob = await requestClient.get<Blob>(
    `/system/course/materials/${encodeURIComponent(id)}/download`,
    { responseType: 'blob', isTransformResponse: false, ...options },
  );
  if (!options.isResponseCurrent()) return;
  if (!(blob instanceof Blob)) throw new Error('下载失败，请重试。');
  // The backend streams successful files as octet-stream attachments and
  // reports errors as JSON. JSON examples inside TXT/MD are document content.
  if (blob.type.includes('json')) {
    let reason = '下载失败，请重试。';
    try {
      reason = JSON.parse(await blob.text()).msg || reason;
    } catch {
      /* Keep fallback. */
    }
    if (!options.isResponseCurrent()) return;
    throw new Error(reason);
  }
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  document.body.append(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
