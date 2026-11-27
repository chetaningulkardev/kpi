import { HttpErrorResponse } from '@angular/common/http';
import { extractHttpError } from './http-error.util';

function httpError(status: number, body: unknown): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: body, statusText: 'Error' });
}

describe('extractHttpError', () => {

  it('should prefer the error field, which is where ApiResponse.error() writes', () => {
    const err = httpError(400, { success: false, error: 'Review cycle is already closed' });
    expect(extractHttpError(err, 'fallback')).toBe('Review cycle is already closed');
  });

  it('should fall back to the message field', () => {
    const err = httpError(400, { message: 'Weights must total 100%' });
    expect(extractHttpError(err, 'fallback')).toBe('Weights must total 100%');
  });

  it('should trim whitespace from the server message', () => {
    const err = httpError(400, { error: '  padded  ' });
    expect(extractHttpError(err, 'fallback')).toBe('padded');
  });

  describe('status-specific defaults when the body carries no message', () => {
    // Regression: the old per-component helper returned the caller's generic
    // fallback here, so a permission failure read "Failed to save."
    it('should explain a 403 rather than using the caller fallback', () => {
      expect(extractHttpError(httpError(403, null), 'Failed to save.'))
        .toContain('permission');
    });

    it('should explain a 429 from the login rate limiter', () => {
      expect(extractHttpError(httpError(429, null), 'Failed.')).toContain('Too many');
    });

    it('should explain a 409 conflict', () => {
      expect(extractHttpError(httpError(409, null), 'Failed.')).toContain('Reload');
    });

    it('should explain a 413 oversized upload', () => {
      expect(extractHttpError(httpError(413, null), 'Failed.')).toContain('too large');
    });

    it('should explain an unreachable server (status 0)', () => {
      expect(extractHttpError(httpError(0, null), 'Failed.')).toContain('Cannot reach');
    });

    it('should explain a 500 without leaking anything', () => {
      expect(extractHttpError(httpError(500, null), 'Failed.')).toContain('server');
    });
  });

  it('should use a server message in preference to the status default', () => {
    const err = httpError(403, { error: 'You are not allowed to review this KPI' });
    expect(extractHttpError(err, 'Failed.')).toBe('You are not allowed to review this KPI');
  });

  it('should use a short plain-text body', () => {
    expect(extractHttpError(httpError(400, 'Bad cycle code'), 'Failed.')).toBe('Bad cycle code');
  });

  it('should ignore a long plain-text body, which is an HTML page not a message', () => {
    const html = '<!DOCTYPE html>' + 'x'.repeat(400);
    expect(extractHttpError(httpError(502, html), 'Failed.')).not.toContain('DOCTYPE');
  });

  it('should fall back to the caller string for an unmapped status with no body', () => {
    expect(extractHttpError(httpError(418, null), 'Failed to save.')).toBe('Failed to save.');
  });

  it('should not throw on null or undefined', () => {
    expect(extractHttpError(null, 'Failed.')).toBe('Failed.');
    expect(extractHttpError(undefined, 'Failed.')).toBe('Failed.');
  });
});

