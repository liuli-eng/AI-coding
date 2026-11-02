import { requestClient } from '#/api/request';

/** 破甲（Operator Override）运行态。对应后端 /system/operator/*。 */
export interface OperatorStatus {
  enabled: boolean;
  operatorMode: boolean;
  runtimeOverride: boolean | null;
  configuredEnabled: boolean;
  promptMode: string;
  identityName: string;
  injectHash: string;
  injectChars: number;
  harnessPromptVersion: string;
  approvalPolicy: string;
  permissionMode: string;
  forcePermissionMode: boolean;
  bypassPermissionChecks: boolean;
  replaceBasePrompt: boolean;
  refusalRetryMax: number;
}

export interface OperatorPreview {
  head: string;
  tail: string;
  injectText: string;
  headChars: number;
  tailChars: number;
}

export const getOperatorStatus = () =>
  requestClient.get<OperatorStatus>('/system/operator/status');

export const getOperatorPreview = () =>
  requestClient.get<OperatorPreview>('/system/operator/preview');

/** 生效：打开运行时开关，下一轮对话立即使用破甲提示词。 */
export const applyOperator = (note?: string) =>
  requestClient.post<OperatorStatus>('/system/operator/apply', null, {
    params: { note },
  });

/** 回滚：关闭运行时开关，不需要重启，也不改动任何源文件。 */
export const revertOperator = (note?: string) =>
  requestClient.post<OperatorStatus>('/system/operator/revert', null, {
    params: { note },
  });

/** 回到跟随配置文件（清除运行时覆盖）。 */
export const followOperatorConfiguration = (note?: string) =>
  requestClient.post<OperatorStatus>('/system/operator/follow-configuration', null, {
    params: { note },
  });

/** 在线改写注入正文并立即生效（不重启、不改库）。 */
export const updateOperatorInject = (text: string) =>
  requestClient.post<OperatorStatus & { injectText: string }>('/system/operator/inject', {
    text,
  });
