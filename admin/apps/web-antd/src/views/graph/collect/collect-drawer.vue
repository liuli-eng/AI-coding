<script setup lang="ts">
import { ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Descriptions, DescriptionsItem, Tag } from 'ant-design-vue';

import { collectDetail } from '#/api/graph/collect';
import { enrichTag, fusionTag } from './data';

const record = ref<Record<string, any>>({});

const emit = defineEmits<{ reload: [] }>();

const [BasicDrawer, drawerApi] = useVbenDrawer({
  destroyOnClose: true,
  onClosed() {
    emit('reload');
  },
  async onOpenChange(isOpen) {
    if (!isOpen) {
      record.value = {};
      return null;
    }
    drawerApi.drawerLoading(true);
    const { companyId, companyName } = drawerApi.getData() as {
      companyId?: number | string;
      companyName?: string;
    };
    const detail = (await collectDetail(companyId!)) || {};
    detail.companyName = detail.companyName || companyName;
    record.value = detail;
    drawerApi.drawerLoading(false);
  },
});
</script>

<template>
  <BasicDrawer
    :title="`${record.companyName || '企业'} 采集明细`"
    class="w-[720px]"
    :footer="false"
  >
    <Descriptions
      :column="2"
      bordered
      size="small"
      :label-style="{ width: '110px' }"
    >
      <DescriptionsItem label="企业名称" :span="2">
        {{ record.companyName }}
      </DescriptionsItem>
      <DescriptionsItem label="法人">
        {{ record.legalPerson }}
      </DescriptionsItem>
      <DescriptionsItem label="注册资本">
        {{ record.registeredCapital }}
      </DescriptionsItem>
      <DescriptionsItem label="成立日期">
        {{ record.establishDate }}
      </DescriptionsItem>
      <DescriptionsItem label="经营状态">
        {{ record.businessStatus }}
      </DescriptionsItem>
      <DescriptionsItem label="行业" :span="2">
        {{ record.industry }}
      </DescriptionsItem>
      <DescriptionsItem label="地区">
        {{ record.region }}
      </DescriptionsItem>
      <DescriptionsItem label="企业类型">
        {{ record.companyType }}
      </DescriptionsItem>
      <DescriptionsItem label="人员规模">
        {{ record.staffSize }}
      </DescriptionsItem>
      <DescriptionsItem label="官网">
        {{ record.website }}
      </DescriptionsItem>
      <DescriptionsItem label="电话">
        {{ record.phone }}
      </DescriptionsItem>
      <DescriptionsItem label="邮箱">
        {{ record.email }}
      </DescriptionsItem>
      <DescriptionsItem label="采集状态">
        <Tag :color="enrichTag(record.enrichStatus).color">
          {{ enrichTag(record.enrichStatus).text }}
        </Tag>
      </DescriptionsItem>
      <DescriptionsItem label="融合状态">
        <Tag :color="fusionTag(record.fusionStatus).color">
          {{ fusionTag(record.fusionStatus).text }}
        </Tag>
      </DescriptionsItem>
      <DescriptionsItem label="错误信息" :span="2">
        {{ record.errorMessage || '-' }}
      </DescriptionsItem>
      <DescriptionsItem label="工商简介" :span="2">
        <div class="max-h-[120px] overflow-auto whitespace-pre-wrap">
          {{ record.profile || '-' }}
        </div>
      </DescriptionsItem>
      <DescriptionsItem label="融合画像" :span="2">
        <div class="max-h-[200px] overflow-auto whitespace-pre-wrap">
          {{ record.fusedProfile || '-' }}
        </div>
      </DescriptionsItem>
    </Descriptions>
  </BasicDrawer>
</template>
