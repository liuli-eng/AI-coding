<script setup lang="ts">
import type { OperatorPreview, OperatorStatus } from '#/api/system/operator';
import { onMounted, ref } from 'vue';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  DescriptionsItem,
  Input,
  Space,
  Spin,
  Tag,
  message,
} from 'ant-design-vue';
import {
  applyOperator,
  followOperatorConfiguration,
  getOperatorPreview,
  getOperatorStatus,
  revertOperator,
  updateOperatorInject,
} from '#/api/system/operator';

const status = ref<OperatorStatus>();
const preview = ref<OperatorPreview>();
const loading = ref(false);
const acting = ref(false);
const injectDraft = ref('');
const savingInject = ref(false);

async function load() {
  loading.value = true;
  try {
    status.value = await getOperatorStatus();
    preview.value = await getOperatorPreview();
    injectDraft.value = preview.value?.injectText ?? '';
  } catch {
    message.error('读取破甲状态失败');
  } finally {
    loading.value = false;
  }
}

async function saveInject() {
  savingInject.value = true;
  try {
    const result = await updateOperatorInject(injectDraft.value);
    status.value = result;
    preview.value = await getOperatorPreview();
    message.success(
      `注入已更新并生效（${result.injectText?.length ?? 0} 字），下一轮对话立即使用`,
    );
  } catch {
    message.error('保存注入失败');
  } finally {
    savingInject.value = false;
  }
}

async function act(kind: 'apply' | 'revert' | 'follow') {
  acting.value = true;
  try {
    const note = `admin console ${new Date().toISOString()}`;
    status.value =
      kind === 'apply'
        ? await applyOperator(note)
        : kind === 'revert'
          ? await revertOperator(note)
          : await followOperatorConfiguration(note);
    preview.value = await getOperatorPreview();
    message.success(
      kind === 'apply'
        ? '已生效，下一轮对话立即使用破甲提示词'
        : kind === 'revert'
          ? '已回滚，下一轮对话恢复原生措辞'
          : '已回到跟随配置文件',
    );
  } catch {
    message.error('操作失败');
  } finally {
    acting.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="p-4">
    <Alert
      type="info"
      show-icon
      message="破甲（Operator Override）运行态"
      description="这里的「应用 / 回滚」只切换服务端运行时开关，不改任何文件、不影响历史会话；回滚后下一轮对话立即恢复原生措辞。提示词模式、身份、审批策略与权限上限来自 application.yml 的 ai.operator.* 配置。"
    />

    <Card class="mt-4" title="当前状态">
      <Spin :spinning="loading">
        <Descriptions :column="2" bordered size="small">
          <DescriptionsItem label="总开关">
            <Tag :color="status?.enabled ? 'green' : 'default'">
              {{ status?.enabled ? '已开启' : '已关闭' }}
            </Tag>
          </DescriptionsItem>
          <DescriptionsItem label="破甲态">
            <Tag :color="status?.operatorMode ? 'red' : 'default'">
              {{ status?.operatorMode ? 'operator' : 'native' }}
            </Tag>
          </DescriptionsItem>
          <DescriptionsItem label="运行时覆盖">
            {{ status?.runtimeOverride == null ? '跟随配置文件' : status?.runtimeOverride ? '已生效(apply)' : '已回滚(revert)' }}
          </DescriptionsItem>
          <DescriptionsItem label="配置文件开关">
            {{ status?.configuredEnabled ? 'enabled=true' : 'enabled=false' }}
          </DescriptionsItem>
          <DescriptionsItem label="提示词模式">
            {{ status?.promptMode }}
          </DescriptionsItem>
          <DescriptionsItem label="身份名">
            {{ status?.identityName || '（未设置）' }}
          </DescriptionsItem>
          <DescriptionsItem label="Harness 提示词版本">
            {{ status?.harnessPromptVersion }}
          </DescriptionsItem>
          <DescriptionsItem label="注入指纹">
            <code>{{ status?.injectHash?.slice(0, 16) }}…</code>
          </DescriptionsItem>
          <DescriptionsItem label="审批策略">
            {{ status?.approvalPolicy }}
          </DescriptionsItem>
          <DescriptionsItem label="权限上限">
            {{ status?.permissionMode }}
          </DescriptionsItem>
          <DescriptionsItem label="服务端强制权限上限">
            {{ status?.forcePermissionMode ? '是' : '否' }}
          </DescriptionsItem>
          <DescriptionsItem label="注入模式">
            <Tag :color="status?.replaceBasePrompt ? 'orange' : 'default'">
              {{ status?.replaceBasePrompt ? '替换人设' : '追加' }}
            </Tag>
          </DescriptionsItem>
          <DescriptionsItem label="拒绝兜底重试">
            {{ status?.refusalRetryMax }} 次
          </DescriptionsItem>
        </Descriptions>

        <Space class="mt-4">
          <Button type="primary" :loading="acting" @click="act('apply')">
            应用
          </Button>
          <Button danger :loading="acting" @click="act('revert')">
            回滚
          </Button>
          <Button :loading="acting" @click="act('follow')">
            跟随配置文件
          </Button>
          <Button :loading="loading" @click="load">刷新</Button>
        </Space>
      </Spin>
    </Card>

    <Card class="mt-4" title="注入正文（改完立即生效，不用重启）">
      <Alert
        type="warning"
        show-icon
        class="mb-3"
        message="调提示词是这个功能唯一需要反复迭代的地方"
        description="改完点「保存并生效」，下一轮对话立刻使用新正文。建议一次只改一处，然后用 docs/operator/refusal-bench.mjs 对比拒绝率，别凭单条对话下结论。"
      />
      <Input.TextArea
        v-model:value="injectDraft"
        :auto-size="{ minRows: 8, maxRows: 24 }"
        placeholder="留空则回落到 ai.operator.inject-file，再回落到内置默认。"
      />
      <Space class="mt-3">
        <Button type="primary" :loading="savingInject" @click="saveInject">
          保存并生效
        </Button>
        <Button :disabled="savingInject" @click="injectDraft = preview?.injectText ?? ''">
          还原为当前生效内容
        </Button>
      </Space>
    </Card>

    <Card class="mt-4" title="装配预览（会拼进系统提示词的三段文本）">
      <Descriptions :column="2" size="small">
        <DescriptionsItem label="前置段字数">
          {{ preview?.headChars ?? 0 }}
        </DescriptionsItem>
        <DescriptionsItem label="后置段字数">
          {{ preview?.tailChars ?? 0 }}
        </DescriptionsItem>
      </Descriptions>
      <h4 class="mt-3 font-medium">前置（身份 + 强句）</h4>
      <pre class="operator-text">{{ preview?.head || '（空）' }}</pre>
      <h4 class="mt-3 font-medium">注入正文</h4>
      <pre class="operator-text">{{ preview?.injectText || '（空）' }}</pre>
      <h4 class="mt-3 font-medium">后置（输出纪律）</h4>
      <pre class="operator-text">{{ preview?.tail || '（空）' }}</pre>
    </Card>
  </div>
</template>

<style scoped>
.operator-text {
  white-space: pre-wrap;
  word-break: break-word;
  padding: 8px 12px;
  border-radius: 6px;
  background: rgb(0 0 0 / 3%);
  font-size: 12px;
  line-height: 1.6;
}
</style>
