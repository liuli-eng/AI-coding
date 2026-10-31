import { useAccessStore } from '@vben/stores';

import { requestClient } from '#/api/request';

export interface KnowledgeSettings {
  systemName: string;
  allowedTypes: string[];
  overwriteSameName: boolean;
  failureMessage: string;
  similarityThreshold: number;
  version: number;
  updateTime: null | string;
  updatedBy: null | string;
}
export const supportedTypes =
  'txt md pdf doc docx xlsx xls csv json java html htm css js ts py cpp c h hpp sql php ruby swift rs perl shell bat cmd xml yaml yml properties ini log'.split(
    ' ',
  );
export function documentRequestOptions(signal?: AbortSignal) {
  const store = useAccessStore();
  const token = store.accessToken;
  return {
    signal,
    errorMessageMode: 'none' as const,
    isResponseCurrent: () => !signal?.aborted && store.accessToken === token,
  };
}
export const getKnowledgeSettings = (signal?: AbortSignal) =>
  requestClient.get<KnowledgeSettings>(
    '/system/knowledge-settings',
    documentRequestOptions(signal),
  );
export const saveKnowledgeSettings = (
  data: KnowledgeSettings,
  signal?: AbortSignal,
) =>
  requestClient.put<KnowledgeSettings>(
    '/system/knowledge-settings',
    data,
    documentRequestOptions(signal),
  );
export const resetKnowledgeSettings = (version: number, signal?: AbortSignal) =>
  requestClient.post<KnowledgeSettings>(
    '/system/knowledge-settings/defaults',
    { version },
    documentRequestOptions(signal),
  );
export const getSystemName = (signal?: AbortSignal) =>
  requestClient.get<{ systemName: string }>(
    '/knowledge/preferences',
    documentRequestOptions(signal),
  );
