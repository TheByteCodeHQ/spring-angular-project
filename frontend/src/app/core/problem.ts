import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDetail } from './models';

/** Turns any failed request into one readable sentence, keeping the request id so support can find it. */
export function describeError(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return 'Something went wrong. Please try again.';
  }
  if (error.status === 0) {
    return 'Cannot reach the server. Check your connection and try again.';
  }
  const problem = (error.error ?? {}) as ProblemDetail;
  const fields = problem.errors?.map((e) => `${e.field}: ${e.message}`).join('; ');
  const message = [problem.detail ?? problem.title ?? `Request failed (${error.status})`, fields]
    .filter(Boolean)
    .join(' - ');
  return problem.requestId ? `${message} (request ${problem.requestId})` : message;
}
