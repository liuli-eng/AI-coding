export interface ApiResult<T> {
  code: number;
  data: T;
  msg?: string;
}

/** hook-fetch 2.0.4-beta.1 may resolve json() with a normalized Error instead of rejecting. */
export async function readApiResult<T>(pending: Promise<unknown>): Promise<ApiResult<T>> {
  const result = await pending;
  if (result instanceof Error) {
    let message = result.message;
    if ('response' in result && result.response instanceof Response) {
      try {
        const body: unknown = await result.response.clone().json();
        if (body && typeof body === 'object' && 'msg' in body && typeof body.msg === 'string')
          message = body.msg;
      }
      catch {
        /* Keep the transport error when no JSON response is available. */
      }
    }
    throw new Error(message || '请求未完成，请稍后再试。');
  }
  if (!result || typeof result !== 'object' || !('code' in result) || result.code !== 200) {
    const message
      = result && typeof result === 'object' && 'msg' in result && typeof result.msg === 'string'
        ? result.msg
        : '接口返回了无效响应。';
    throw new Error(message);
  }
  return result as ApiResult<T>;
}

export interface PageResult<T> {
  code: number;
  rows: T[];
  total: number;
  msg?: string;
}

export async function readPageResult<T>(pending: Promise<unknown>): Promise<PageResult<T>> {
  const result = await readApiResult<never>(pending);
  if (
    !('rows' in result)
    || !Array.isArray(result.rows)
    || !('total' in result)
    || typeof result.total !== 'number'
  ) {
    throw new Error('接口返回了无效分页数据。');
  }
  return result as unknown as PageResult<T>;
}
