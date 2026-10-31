import { ref } from 'vue';

export const systemName = ref('AI 编程助手');
let generation = 0;
let request: AbortController | undefined;

export async function loadSystemName(token?: string) {
  generation++;
  const run = generation;
  request?.abort();
  if (!token) {
    systemName.value = 'AI 编程助手';
    return;
  }
  const controller = new AbortController();
  request = controller;
  try {
    const response = await fetch(`${import.meta.env.VITE_API_URL}/knowledge/preferences`, {
      signal: controller.signal,
      headers: { Authorization: `Bearer ${token}`, ClientID: import.meta.env.VITE_CLIENT_ID },
    });
    const result = await response.json();
    if (run === generation && response.ok && result.code === 200 && typeof result.data?.systemName === 'string')
      systemName.value = result.data.systemName;
  }
  catch {
    /* Keep the current title when the preference service is unavailable. */
  }
}

export function resetSystemName() {
  generation++;
  request?.abort();
  systemName.value = 'AI 编程助手';
}
