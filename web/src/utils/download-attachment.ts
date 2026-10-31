import router from '@/routers';
import { useUserStore } from '@/stores/modules/user';

/** Authenticated binary transport: JSON errors must never become downloaded files. */
export async function downloadAttachment(path: string, signal?: AbortSignal): Promise<Blob> {
  const user = useUserStore();
  const token = user.token;
  if (!token)
    throw new Error('请先登录后下载资料。');
  const response = await fetch(`${import.meta.env.VITE_API_URL.replace(/\/$/, '')}${path}`, {
    headers: { Authorization: `Bearer ${token}`, ClientID: import.meta.env.VITE_CLIENT_ID },
    signal,
  });
  const blob = await response.blob();
  if (signal?.aborted || token !== user.token)
    throw new DOMException('下载已取消', 'AbortError');
  // Successful material streams are octet-stream attachments; failures use JSON.
  // Lesson text may itself be JSON, so never classify by document contents.
  const attachment = /^attachment(?:;|$)/i.test(response.headers.get('Content-Disposition') || '');
  const isJson = !attachment && blob.type.includes('json');
  if (!response.ok || isJson) {
    let code = response.status;
    let message = '下载失败，请稍后重试。';
    if (isJson) {
      try {
        const result = JSON.parse(await blob.text());
        code = Number(result.code) || code;
        message = typeof result.msg === 'string' && result.msg ? result.msg : message;
      }
      catch { /* Keep the transport error when the response is invalid JSON. */ }
    }
    if (signal?.aborted || token !== user.token)
      throw new DOMException('下载已取消', 'AbortError');
    if (code === 401)
      user.handleAuthExpired(router.currentRoute.value.fullPath, '登录状态已失效，请重新登录');
    throw new Error(message);
  }
  return blob;
}

export function saveAttachment(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  document.body.append(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
