import type { AttachQuery, AttachVO } from './model';

import type { ID } from '#/api/common';
import type { KnowledgeSettings } from '#/api/knowledge/settings';

import { documentRequestOptions } from '#/api/knowledge/settings';
import { requestClient } from '#/api/request';

export interface DocumentStats {
  waiting: number;
  processing: number;
  completed: number;
  failed: number;
  total: number;
}
export interface DocumentPage {
  rows: AttachVO[];
  total: number;
  statistics: DocumentStats;
}
export interface KnowledgeOption {
  id: ID;
  name: string;
  status: string;
}
export const documentList = (params: AttachQuery, signal?: AbortSignal) =>
  requestClient.get<DocumentPage>('/system/attach/workbench', {
    ...documentRequestOptions(signal),
    params,
  });
export const knowledgeOptions = (
  keyword = '',
  pageNum = 1,
  signal?: AbortSignal,
) =>
  requestClient.get<{ rows: KnowledgeOption[]; total: number }>(
    '/system/attach/knowledge-options',
    {
      ...documentRequestOptions(signal),
      params: { keyword, pageNum, pageSize: 20 },
    },
  );
export const uploadRules = (signal?: AbortSignal) =>
  requestClient.get<KnowledgeSettings>(
    '/system/attach/upload-rules',
    documentRequestOptions(signal),
  );
export const documentUpload = (
  file: File,
  knowledgeId: ID,
  autoParse: boolean,
  signal: AbortSignal,
  progress: (value: number) => void,
) =>
  requestClient.upload(
    '/system/attach/upload',
    { file, knowledgeId: String(knowledgeId), autoParse: String(autoParse) },
    {
      ...documentRequestOptions(signal),
      timeout: 120_000,
      onUploadProgress: (event) => {
        if (event.total && !signal.aborted)
          progress(Math.round((event.loaded / event.total) * 100));
      },
    },
  );
export const documentState = (
  id: ID,
  businessState: string,
  signal?: AbortSignal,
) =>
  requestClient.put<void>(
    `/system/attach/${encodeURIComponent(id)}/state`,
    { businessState },
    documentRequestOptions(signal),
  );
export async function documentDownload(
  id: ID,
  name: string,
  signal?: AbortSignal,
) {
  const options = documentRequestOptions(signal);
  const blob = await requestClient.get<Blob>(
    `/system/attach/${encodeURIComponent(id)}/download`,
    { ...options, responseType: 'blob', isTransformResponse: false },
  );
  if (!options.isResponseCurrent()) return;
  if (!(blob instanceof Blob)) throw new Error('下载失败，请重试');
  if (blob.type.includes('json'))
    throw new Error(JSON.parse(await blob.text()).msg || '下载失败，请重试');
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  document.body.append(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export const documentFragments = (
  id: ID,
  pageNum: number,
  signal?: AbortSignal,
) =>
  requestClient.get<{
    rows: import('#/api/knowledge/fragment/model').FragmentVO[];
    total: number;
  }>(`/system/attach/${encodeURIComponent(id)}/fragments`, {
    ...documentRequestOptions(signal),
    params: { pageNum, pageSize: 10 },
  });
