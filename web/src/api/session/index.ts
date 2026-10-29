import type {
  ChatSessionResponse,
  ChatSessionVo,
  CreateSessionDTO,
  // CreateSessionVO,
  GetSessionListParams,
} from './types';
import { readApiResult, readPageResult } from '@/utils/api-result';
import { del, get, post, put } from '@/utils/request';

function normalizeSession(session: ChatSessionResponse): ChatSessionVo {
  return { ...session, id: session.id == null ? undefined : String(session.id) };
}

export async function get_session_list(params: GetSessionListParams) {
  const result = await readPageResult<ChatSessionResponse>(get<ChatSessionResponse[]>('/system/session/list', params).json());
  return { ...result, rows: result.rows.map(normalizeSession) };
}

export function create_session(data: CreateSessionDTO) {
  return post('/system/session', data).json();
}

export function update_session(data: ChatSessionVo) {
  return put('/system/session', data).json();
}

export async function get_session(id: string) {
  const result = await readApiResult<ChatSessionResponse>(get<ChatSessionResponse>(`/system/session/${id}`).json());
  return { ...result, data: normalizeSession(result.data) };
}

export function delete_session(ids: string[]) {
  return del(`/system/session/${ids}`).json();
}
