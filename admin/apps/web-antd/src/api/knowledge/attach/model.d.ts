import type { BaseEntity, PageQuery } from '#/api/common';

export interface AttachVO {
  fileSize?: number;
  businessState: '0' | '1';
  knowledgeName?: string;
  uploadedBy?: string;
  createTime?: string;
  docId?: string;
  fragmentCount?: number;
  /**
   *
   */
  id: number | string;

  /**
   * 知识库ID
   */
  knowledgeId: number | string;

  /**
   * 附件名称
   */
  name: string;

  /**
   * 附件类型
   */
  type: string;

  /**
   * 对象存储ID
   */
  ossId: number | string;

  /**
   * 文档内容
   */
  content: string;

  /**
   * 备注
   */
  remark: string;

  /**
   * 解析状态
   */
  status: number;
}

export interface AttachForm extends BaseEntity {
  /**
   *
   */
  id?: number | string;

  /**
   * 知识库ID
   */
  knowledgeId?: number | string;

  /**
   * 附件名称
   */
  name?: string;

  /**
   * 附件类型
   */
  type?: string;

  /**
   * 对象存储ID
   */
  ossId?: number | string;

  /**
   * 文档内容
   */
  content?: string;

  /**
   * 备注
   */
  remark?: string;
}

export interface AttachQuery extends PageQuery {
  status?: number;
  businessState?: string;
  /**
   * 知识库ID
   */
  knowledgeId?: number | string;

  /**
   * 附件名称
   */
  name?: string;

  /**
   * 附件类型
   */
  type?: string;

  /**
   * 对象存储ID
   */
  ossId?: number | string;

  /**
   * 文档内容
   */
  content?: string;

  /**
   * 日期范围参数
   */
  params?: any;
}
