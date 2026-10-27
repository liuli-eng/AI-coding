import { requestClient } from '#/api/request';

/** 图谱内企业清单（Company 节点） */
export function graphCompanies(
  id: string | number,
  limit?: number,
) {
  return requestClient.get<Array<Record<string, any>>>(
    `/graph/instance/${id}/companies`,
    { params: { limit } },
  );
}
