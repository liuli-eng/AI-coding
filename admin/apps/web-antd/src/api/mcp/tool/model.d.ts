export interface McpTool {
  id: number;
  name: string;
  description: string;
  type: string;
  status: string;
  /** Write-only connection configuration; responses intentionally omit it. */
  configJson?: string;
  createTime: string;
  updateTime: string;
}

export interface McpToolTestResult {
  success: boolean;
  message: string;
  data?: any;
}

export interface McpToolListResult {
  success: boolean;
  data: McpTool[];
  total: number;
}
