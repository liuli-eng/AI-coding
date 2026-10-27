export interface McpMarket {
  id: number;
  name: string;
  url: string;
  description: string;
  /** Write-only authentication configuration; responses intentionally omit it. */
  authConfig?: string;
  status: string;
  createTime: string;
  updateTime: string;
}

export interface McpMarketTool {
  id: number;
  marketId: number;
  toolName: string;
  toolDescription: string;
  toolVersion: string;
  isLoaded: boolean;
  localToolId: number;
}

export interface McpMarketRefreshResult {
  success: boolean;
  message: string;
  addedCount: number;
  updatedCount: number;
}

export interface McpMarketListResult {
  success: boolean;
  data: McpMarket[];
  total: number;
}

export interface McpMarketToolListResult {
  success: boolean;
  data: McpMarketTool[];
  total: number;
  page: number;
  size: number;
  pages: number;
}

export interface McpMarketBatchLoadResult {
  successCount: number;
}
