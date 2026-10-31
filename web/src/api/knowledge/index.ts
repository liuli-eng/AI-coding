import { readApiResult, readPageResult } from '@/utils/api-result';
import { get } from '@/utils/request';

/** Safe ordinary-user summary. Internal retrieval/model settings are never exposed. */
export interface KnowledgeBaseSummary {
  id: string;
  name: string;
  description: string | null;
  documentCount: number;
  updateTime: string | null;
}

export interface KnowledgeBaseQuery {
  keyword?: string;
  pageNum?: number;
  pageSize?: number;
}

export function getKnowledgeBases(params: KnowledgeBaseQuery = {}) {
  return readPageResult<KnowledgeBaseSummary>(get('/knowledge/bases', params).json());
}

export function getKnowledgeBase(id: string) {
  return readApiResult<KnowledgeBaseSummary>(get(`/knowledge/bases/${encodeURIComponent(id)}`).json());
}
