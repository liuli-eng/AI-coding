export interface SysUrl {
  urlId: number;
  tenantId?: string;
  name: string;
  url: string;
  description?: string;
  sortOrder?: number;
  status: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}
