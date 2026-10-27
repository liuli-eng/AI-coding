import type { PageQuery, PageResult } from '#/api/common';
import { requestClient } from '#/api/request';

export interface Citation { content: string; sourceName?: string; knowledgeId?: string; docId?: string; score?: string }
export interface Question {
  id: string; sessionId: string; userId: string; userName: string; modelName: string; chatMode: string;
  question: string; answer?: string; answerStatus: string; retrievalStatus: string; knowledgeNames?: string;
  citations?: string; traceId?: string; totalTokens?: number; durationMs?: number; startedAt: string;
  errorMessage?: string; issueType: string; reviewStatus: string; reviewNote?: string;
  reviewedBy?: string; reviewedAt?: string; version: number;
}
export interface Overview {
  today: { total: number; success: number; failed: number; unmatched: number; pending: number; running: number };
  models: { modelName: string; calls: number; success: number; failed: number; averageDurationMs?: number }[];
  knowledgeCount: number; documentCount: number; failedDocuments: number; pendingReviews: number;
  firstRecordedAt?: string; reportDate: string; timezone: string; updatedAt: string;
}
export const questionList = (params: PageQuery) => requestClient.get<PageResult<Question>>('/system/question/list', { params });
export const questionDetail = (id: string) => requestClient.get<Question>(`/system/question/${id}`);
export const questionReview = (id: string, data: { status: string; note: string; version: number }) => requestClient.put(`/system/question/${id}/review`, data);
export const questionOverview = () => requestClient.get<Overview>('/system/question/overview');
export const answerLabels: Record<string, string> = { success: '回答成功', failed: '回答失败', running: '回答中' };
export const retrievalLabels: Record<string, string> = { matched: '已命中', no_match: '未匹配资料', not_used: '未使用知识库', failed: '检索异常' };
export const reviewLabels: Record<string, string> = { none: '无需复核', pending: '待复核', resolved: '已处理', ignored: '已忽略' };
export const modeLabels: Record<string, string> = { learning: '教学模式', programming: '编程助手', debugging: '分析代码错误' };
