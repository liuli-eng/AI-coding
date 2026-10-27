import type { PageQuery, PageResult } from '#/api/common';
import { requestClient } from '#/api/request';
export interface Prompt {
  id: string;
  name: string;
  description?: string;
  chatMode: string;
  content?: string;
  status: string;
  version: number;
  updateTime?: string;
}
export type PromptSave = Omit<Prompt, 'id' | 'updateTime' | 'version'> & {
  version?: number;
};
export const promptList = (params: PageQuery) =>
  requestClient.get<PageResult<Prompt>>('/system/prompt/list', { params });
export const promptDetail = (id: string) =>
  requestClient.get<Prompt>(`/system/prompt/${id}`);
export const promptSave = (id: string | undefined, data: PromptSave) =>
  id
    ? requestClient.put(`/system/prompt/${id}`, data)
    : requestClient.post('/system/prompt', data);
export const promptRemove = (id: string, version: number) =>
  requestClient.delete(`/system/prompt/${id}`, { params: { version } });
