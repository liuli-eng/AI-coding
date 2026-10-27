import { requestClient } from '#/api/request';

/** 采集汇总统计 */
export function collectSummary() {
  return requestClient.get<Record<string, any>>('/graph/enrich/summary');
}

/** 企业采集状态分页 */
export function collectList(params?: any) {
  return requestClient.get('/graph/enrich/companies', { params });
}

/** 单企业采集明细（详情抽屉） */
export function collectDetail(companyId: string | number) {
  return requestClient.get<Record<string, any>>(
    `/graph/enrich/companies/${companyId}`,
  );
}

/** 触发企业批量初始化（采集/融合/打标，异步） */
export function preheatTrigger() {
  return requestClient.post<Record<string, any>>('/graph/preheat');
}

/** 查询预热进度 */
export function preheatProgress() {
  return requestClient.get<Record<string, any>>('/graph/preheat/progress');
}
