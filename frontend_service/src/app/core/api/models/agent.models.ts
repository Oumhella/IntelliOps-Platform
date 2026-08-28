export interface AgentStatusResponse {
  readonly enabled: boolean;
  readonly nvidiaApiKeyConfigured: boolean;
  readonly model: string;
  readonly state: string;
  readonly readOnlyCapabilities: readonly string[];
  readonly actionCapabilities: readonly string[];
  readonly mutationSafety: string;
}

export interface AgentActionPreview {
  readonly approvalToken: string;
  readonly operation: string;
  readonly summary: string;
  readonly expiresAt: string;
  readonly requiresExplicitConfirmation: boolean;
  readonly riskLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  readonly requiresReason: boolean;
  readonly nextStep: string;
}

export interface AgentChatRequest {
  readonly message: string;
  readonly locale?: 'en' | 'fr' | 'ar';
}

export interface AgentReplyResponse {
  readonly answer: string;
  readonly safety: string;
  readonly action?: AgentActionPreview | null;
}

export interface AgentActionExecutionResponse {
  readonly operation: string;
  readonly message: string;
  readonly result?: string | null;
}
