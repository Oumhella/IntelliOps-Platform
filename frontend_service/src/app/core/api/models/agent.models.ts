export interface AgentStatusResponse {
  readonly enabled: boolean;
  readonly nvidiaApiKeyConfigured: boolean;
  readonly model: string;
  readonly state: string;
  readonly readOnlyCapabilities: readonly string[];
  readonly mutationSafety: string;
}

export interface AgentChatRequest {
  readonly message: string;
}

export interface AgentReplyResponse {
  readonly answer: string;
  readonly safety: string;
}
