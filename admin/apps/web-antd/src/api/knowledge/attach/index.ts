import type { AttachForm, AttachQuery, AttachVO } from './model';

import type { ID, IDS, PageResult } from '#/api/common';

import { commonExport } from '#/api/helper';
import { documentRequestOptions } from '#/api/knowledge/settings';
import { requestClient } from '#/api/request';

/**
 * 查询知识库附件列表
 * @param params
 * @returns 知识库附件列表
 */
export function attachList(params?: AttachQuery) {
  return requestClient.get<PageResult<AttachVO>>('/system/attach/list', {
    params,
  });
}

/**
 * 导出知识库附件列表
 * @param params
 * @returns 知识库附件列表
 */
export function attachExport(params?: AttachQuery) {
  return commonExport('/system/attach/export', params ?? {});
}

/**
 * 查询知识库附件详情
 * @param id id
 * @returns 知识库附件详情
 */
export function attachInfo(id: ID) {
  return requestClient.get<AttachVO>(`/system/attach/${id}`);
}

/**
 * 新增知识库附件
 * @param data
 * @returns void
 */
export function attachAdd(data: AttachForm) {
  return requestClient.postWithMsg<void>('/system/attach', data);
}

/**
 * 更新知识库附件
 * @param data
 * @returns void
 */
export function attachUpdate(data: AttachForm) {
  return requestClient.putWithMsg<void>('/system/attach', data);
}

/**
 * 删除知识库附件
 * @param id id
 * @returns void
 */
export function attachRemove(id: ID | IDS, signal?: AbortSignal) {
  return requestClient.deleteWithMsg<void>(
    `/system/attach/${id}`,
    documentRequestOptions(signal),
  );
}

/**
 * 手动解析附件内容
 * @param id id
 */
export function attachParse(id: ID, signal?: AbortSignal) {
  return requestClient.postWithMsg<void>(
    `/system/attach/parse/${id}`,
    undefined,
    documentRequestOptions(signal),
  );
}

export interface KnowledgeReparseResult {
  submitted: number;
  skipped: number;
  total: number;
}

export function attachReparseKnowledge(knowledgeId: ID, signal?: AbortSignal) {
  return requestClient.post<KnowledgeReparseResult>(
    `/system/attach/reparse/knowledge/${knowledgeId}`,
    undefined,
    documentRequestOptions(signal),
  );
}
