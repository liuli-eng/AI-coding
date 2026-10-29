<script lang="ts" setup>
import { computed, onBeforeUnmount, watch } from 'vue';

import { useAntdDesignTokens } from '@vben/hooks';
import {
  preferences,
  updatePreferences,
  usePreferences,
} from '@vben/preferences';
import { useAccessStore } from '@vben/stores';

import { App, ConfigProvider, theme } from 'ant-design-vue';

import { getSystemName } from '#/api/knowledge/settings';
import { antdLocale } from '#/locales';

defineOptions({ name: 'App' });

const access = useAccessStore();
let nameRequest: AbortController | undefined;
async function refreshSystemName() {
  nameRequest?.abort();
  if (!access.accessToken) return;
  const controller = new AbortController();
  nameRequest = controller;
  try {
    const value = await getSystemName(controller.signal);
    if (!controller.signal.aborted && value.systemName)
      updatePreferences({ app: { name: value.systemName } });
  } catch {
    /* Keep title on temporary failures. */
  }
}
watch(
  () => access.accessToken,
  () => {
    nameRequest?.abort();
    updatePreferences({ app: { name: import.meta.env.VITE_APP_TITLE } });
    refreshSystemName();
  },
  { immediate: true },
);
window.addEventListener('focus', refreshSystemName);
window.addEventListener('knowledge-settings-saved', refreshSystemName);
onBeforeUnmount(() => {
  nameRequest?.abort();
  window.removeEventListener('focus', refreshSystemName);
  window.removeEventListener('knowledge-settings-saved', refreshSystemName);
});
const { isDark } = usePreferences();
const { tokens } = useAntdDesignTokens();

const tokenTheme = computed(() => {
  const algorithm = isDark.value
    ? [theme.darkAlgorithm]
    : [theme.defaultAlgorithm];

  // antd 紧凑模式算法
  if (preferences.app.compact) {
    algorithm.push(theme.compactAlgorithm);
  }

  return {
    algorithm,
    token: tokens,
  };
});
</script>

<template>
  <ConfigProvider :locale="antdLocale" :theme="tokenTheme">
    <App>
      <RouterView />
    </App>
  </ConfigProvider>
</template>

<style lang="scss">
body {
  /**
   * 全局启用 抗锯齿字体
   * @see https://developer.mozilla.org/zh-CN/docs/Web/CSS/font-smooth
   */
  @apply antialiased;
}
</style>
