/** Spring's RFC 9457 error response, including field-validation errors. */
export interface ProblemDetail {
  readonly type?: string;
  readonly title?: string;
  readonly status?: number;
  readonly detail?: string;
  readonly instance?: string;
  readonly errors?: Readonly<Record<string, string>>;
  readonly [key: string]: unknown;
}

export interface MessageResponse {
  readonly message: string;
}

export interface PageResponse<T> {
  readonly content: readonly T[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly first: boolean;
  readonly last: boolean;
}

/** Dates stay as strings at the transport boundary to avoid timezone surprises. */
export type IsoDate = string;
export type IsoDateTime = string;
