import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDetail } from '../api/models';

/** Stable error shape consumed by components, regardless of backend/network format. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly title?: string,
    readonly fieldErrors: Readonly<Record<string, string>> = {},
    readonly originalError?: HttpErrorResponse,
  ) {
    super(message);
    this.name = 'ApiError';
  }

  static fromHttpError(error: HttpErrorResponse): ApiError {
    const problem = isProblemDetail(error.error) ? error.error : null;
    const message = problem?.detail
      ?? (typeof error.error === 'string' && error.error.trim() ? error.error : null)
      ?? defaultMessage(error.status)
      ?? error.message
      ?? 'An unexpected API error occurred.';

    return new ApiError(
      error.status,
      message,
      problem?.title,
      problem?.errors ?? {},
      error,
    );
  }
}

function isProblemDetail(value: unknown): value is ProblemDetail {
  return typeof value === 'object' && value !== null;
}

function defaultMessage(status: number): string | null {
  switch (status) {
    case 0:
      return 'Unable to reach the API gateway.';
    case 401:
      return 'Your session is missing, invalid, or expired.';
    case 403:
      return 'You do not have permission to perform this action.';
    case 404:
      return 'The requested resource was not found.';
    default:
      return null;
  }
}
