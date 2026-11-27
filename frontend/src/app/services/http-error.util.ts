import { HttpErrorResponse } from '@angular/common/http';

/**
 * Extracts a message worth showing a user from a failed HTTP call.
 *
 * This logic was previously copy-pasted into eleven components. Beyond the
 * duplication, each copy fell back to a generic string whenever the server sent
 * no body — which is exactly what happens on a 403, 429 or 502, so the user was
 * told "Failed to save." when the real reason was "you aren't allowed" or
 * "you're rate limited".
 *
 * Resolution order:
 *   1. `error.error`   — where ApiResponse.error() puts the text
 *   2. `error.message` — where ApiResponse.success()-shaped bodies put it
 *   3. a status-specific default
 *   4. the caller's fallback
 */
export function extractHttpError(err: unknown, fallback: string): string {
  const body = (err as HttpErrorResponse | undefined)?.error;

  // A string body shows up when the response wasn't JSON (a proxy error page,
  // for instance). Only use it when it is short enough to be a message rather
  // than an HTML document.
  if (typeof body === 'string' && body.trim() && body.length < 300) {
    return body.trim();
  }

  const fromBody = body?.error ?? body?.message;
  if (typeof fromBody === 'string' && fromBody.trim()) {
    return fromBody.trim();
  }

  const status = (err as HttpErrorResponse | undefined)?.status;
  const byStatus = defaultForStatus(status);
  if (byStatus) {
    return byStatus;
  }

  // Deliberately NOT using HttpErrorResponse.message: Angular generates it as
  // "Http failure response for <url>: <status> <text>", which is diagnostic
  // noise, not something to show a user. Only a plain thrown Error carries a
  // message worth surfacing.
  if (!(err instanceof HttpErrorResponse)) {
    const thrown = (err as { message?: string } | undefined)?.message;
    if (typeof thrown === 'string' && thrown.trim()) {
      return thrown.trim();
    }
  }

  return fallback;
}

function defaultForStatus(status: number | undefined): string | null {
  switch (status) {
    // 0 means the request never completed — offline, DNS, CORS, or the server
    // is down. err.message here is an unhelpful "Http failure response for ...".
    case 0:
      return 'Cannot reach the server. Check your connection and try again.';
    case 401:
      return 'Your session has expired. Please sign in again.';
    case 403:
      return 'You do not have permission to perform this action.';
    case 404:
      return 'That item no longer exists. It may have been removed.';
    case 409:
      return 'This conflicts with a change someone else made. Reload and try again.';
    case 413:
      return 'That file is too large to upload.';
    case 429:
      return 'Too many attempts. Please wait a moment and try again.';
    case 500:
    case 502:
    case 503:
    case 504:
      return 'Something went wrong on the server. Please try again.';
    default:
      return null;
  }
}

