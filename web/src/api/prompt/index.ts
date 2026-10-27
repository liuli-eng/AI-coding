import { readApiResult } from '@/utils/api-result';
import { get } from '@/utils/request';

export interface PromptOption {
  id: string;
  name: string;
  description?: string;
  chatMode: string;
  version: number;
}
export function getPrompts(mode: string) {
  return readApiResult<PromptOption[]>(get('/chat/prompts', { mode }).json());
}
