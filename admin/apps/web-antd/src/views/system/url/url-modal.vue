<script setup lang="ts">
import type { RuleObject } from 'ant-design-vue/es/form';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { DictEnum } from '@vben/constants';
import { $t } from '@vben/locales';
import { cloneDeep } from '@vben/utils';

import { Form, FormItem, Input, InputNumber, RadioGroup, Textarea } from 'ant-design-vue';
import { pick } from 'lodash-es';

import { urlAdd, urlInfo, urlUpdate } from '#/api/system/url';
import { getDictOptions } from '#/utils/dict';
import { useBeforeCloseDiff } from '#/utils/popup';

const emit = defineEmits<{ reload: [] }>();

const isUpdate = ref(false);
const title = computed(() => {
  return isUpdate.value ? $t('pages.common.edit') : $t('pages.common.add');
});

interface FormData {
  urlId?: number;
  name?: string;
  url?: string;
  description?: string;
  sortOrder?: number;
  status?: string;
}

const defaultValues: FormData = {
  urlId: undefined,
  name: '',
  url: '',
  description: '',
  sortOrder: 0,
  status: '0',
};

const formData = ref<FormData>({ ...defaultValues });

type AntdFormRules<T> = Partial<Record<keyof T, RuleObject[]>> & {
  [key: string]: RuleObject[];
};

const validateUrl = async (_rule: RuleObject, value: string) => {
  if (!value) {
    return Promise.reject('请输入链接地址');
  }
  if (!/^https?:\/\/.+/.test(value.trim())) {
    return Promise.reject('链接地址必须以 http:// 或 https:// 开头');
  }
  return Promise.resolve();
};

const formRules = ref<AntdFormRules<FormData>>({
  name: [{ required: true, message: '请输入链接名称' }],
  url: [{ required: true, validator: validateUrl, trigger: 'blur' }],
  sortOrder: [{ required: true, message: '请输入排序' }],
  status: [{ required: true, message: $t('ui.formRules.selectRequired') }],
});

const { validate, validateInfos, resetFields } = Form.useForm(
  formData,
  formRules,
);

function customFormValueGetter() {
  return JSON.stringify(formData.value);
}

const { onBeforeClose, markInitialized, resetInitialized } = useBeforeCloseDiff(
  {
    initializedGetter: customFormValueGetter,
    currentGetter: customFormValueGetter,
  },
);

const [BasicModal, modalApi] = useVbenModal({
  class: 'w-[600px]',
  fullscreenButton: true,
  onBeforeClose,
  onClosed: handleClosed,
  onConfirm: handleConfirm,
  onOpenChange: async (isOpen) => {
    if (!isOpen) {
      return null;
    }
    modalApi.modalLoading(true);
    const { id } = modalApi.getData() as { id?: number | string };
    isUpdate.value = !!id;
    if (isUpdate.value && id) {
      const record = await urlInfo(id);
      formData.value = pick(record, Object.keys(defaultValues)) as FormData;
    }
    await markInitialized();
    modalApi.modalLoading(false);
  },
});

async function handleConfirm() {
  try {
    modalApi.lock(true);
    await validate();
    const data = cloneDeep(formData.value);
    await (isUpdate.value ? urlUpdate(data) : urlAdd(data));
    resetInitialized();
    emit('reload');
    modalApi.close();
  } catch (error) {
    console.error(error);
  } finally {
    modalApi.lock(false);
  }
}

async function handleClosed() {
  formData.value = { ...defaultValues };
  resetFields();
  resetInitialized();
}
</script>

<template>
  <BasicModal :title="title">
    <Form layout="vertical">
      <FormItem label="链接名称" v-bind="validateInfos.name">
        <Input placeholder="请输入链接名称" v-model:value="formData.name" />
      </FormItem>
      <FormItem label="链接地址" v-bind="validateInfos.url">
        <Input placeholder="https://example.com" v-model:value="formData.url" />
      </FormItem>
      <FormItem label="链接说明" v-bind="validateInfos.description">
        <Textarea
          :rows="3"
          placeholder="请输入链接说明"
          v-model:value="formData.description"
        />
      </FormItem>
      <div class="grid sm:grid-cols-1 lg:grid-cols-2">
        <FormItem label="排序" v-bind="validateInfos.sortOrder">
          <InputNumber
            :min="0"
            class="w-full"
            v-model:value="formData.sortOrder"
          />
        </FormItem>
        <FormItem label="状态" v-bind="validateInfos.status">
          <RadioGroup
            button-style="solid"
            option-type="button"
            v-model:value="formData.status"
            :options="getDictOptions(DictEnum.SYS_NORMAL_DISABLE)"
          />
        </FormItem>
      </div>
    </Form>
  </BasicModal>
</template>
