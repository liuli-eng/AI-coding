import type { SysUrl } from './model';

import type { ID, IDS, PageQuery } from '#/api/common';

import { requestClient } from '#/api/request';

enum Api {
  root = '/system/url',
  urlList = '/system/url/list',
}

/**
 * URL 管理分页
 * @param params 分页及查询参数
 * @returns 分页结果
 */
export function urlList(params?: PageQuery) {
  return requestClient.get<SysUrl[]>(Api.urlList, { params });
}

/**
 * URL 详情
 * @param urlId 链接ID
 * @returns 详情
 */
export function urlInfo(urlId: ID) {
  return requestClient.get<SysUrl>(`${Api.root}/${urlId}`);
}

/**
 * 新增 URL
 * @param data 参数
 */
export function urlAdd(data: Partial<SysUrl>) {
  return requestClient.postWithMsg<void>(Api.root, data);
}

/**
 * 修改 URL
 * @param data 参数
 */
export function urlUpdate(data: Partial<SysUrl>) {
  return requestClient.putWithMsg<void>(Api.root, data);
}

/**
 * 删除 URL（单个/批量）
 * @param urlIds 链接ID
 */
export function urlRemove(urlIds: IDS) {
  return requestClient.deleteWithMsg<void>(`${Api.root}/${urlIds}`);
}
