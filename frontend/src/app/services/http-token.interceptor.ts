import { Injectable } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptor,
  HttpErrorResponse
} from '@angular/common/http';
import { Observable, throwError, BehaviorSubject, defer, TimeoutError } from 'rxjs';
import { catchError, filter, take, switchMap, timeout, finalize } from 'rxjs/operators';
import { AuthService } from './auth.service';

@Injectable()
export class HttpTokenInterceptor implements HttpInterceptor {
  private readonly requestTimeoutMs = 45000;
  private isRefreshing = false;
  private refreshTokenSubject: BehaviorSubject<any> = new BehaviorSubject<any>(null);
  private pendingRequests = 0;

  constructor(private authService: AuthService) { }

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    // Never attach/refresh tokens for the auth endpoints themselves
    const isAuthCall = request.url.includes('/api/auth/');

    // Add token to request if available and valid
    const token = this.authService.getTokenFromLocalStorage();
    if (!isAuthCall && token && !this.isTokenExpired(token)) {
      request = this.addToken(request, token);
    }

    this.pendingRequests++;

    return next.handle(request).pipe(
      timeout(this.requestTimeoutMs),
      catchError(error => {
        // Only attempt 401 recovery for non-auth endpoints
        if (!isAuthCall && error instanceof HttpErrorResponse && error.status === 401) {
          return this.handle401Error(request, next);
        }

        if (error instanceof TimeoutError) {
          return throwError(() => new Error('Request timed out. Please try again.'));
        }

        return throwError(() => error);
      }),
      finalize(() => this.pendingRequests--)
    );
  }

  private addToken(request: HttpRequest<any>, token: string): HttpRequest<any> {
    return request.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  private handle401Error(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const refreshToken = this.authService.getRefreshTokenFromLocalStorage();

    // No refresh token available -> cannot recover. Log out and fail fast (do NOT hang).
    if (!refreshToken) {
      this.forceLogout('No refresh token available');
      return throwError(() => new Error('Session expired. Please log in again.'));
    }

    if (!this.isRefreshing) {
      this.isRefreshing = true;
      this.refreshTokenSubject.next(null);

      // defer() ensures any synchronous throw from refreshToken() becomes an error notification
      return defer(() => this.authService.refreshToken()).pipe(
        timeout(10000), // Token refresh must complete within 10 seconds
        switchMap((response: any) => {
          this.isRefreshing = false;
          this.refreshTokenSubject.next(response.access_token);
          return next.handle(this.addToken(request, response.access_token)).pipe(
            timeout(this.requestTimeoutMs)
          );
        }),
        catchError((err) => {
          this.isRefreshing = false;
          this.refreshTokenSubject.next(null);

          const errorMsg = err?.message || 'Token refresh failed';
          console.error('🔴 Token refresh failed:', errorMsg);

          this.forceLogout('Token refresh failed');
          return throwError(() => new Error(`Token refresh failed: ${errorMsg}`));
        })
      );
    }

    // A refresh is already in progress: queue this request until the new token arrives.
    return this.refreshTokenSubject.pipe(
      filter(token => token != null),
      take(1),
      timeout(15000), // Wait max 15s for token refresh to complete
      switchMap(token => next.handle(this.addToken(request, token)).pipe(
        timeout(this.requestTimeoutMs)
      )),
      catchError((err) => {
        console.error('🔴 Queued request failed after token refresh:', err?.message);
        this.forceLogout('Token refresh timeout');
        return throwError(() => err);
      })
    );
  }

  private forceLogout(reason: string): void {
    console.warn('🔐 Force logout triggered:', reason);
    try {
      this.authService.clearTokens();
      sessionStorage.removeItem('previousUrl');
    } catch { /* ignore */ }

    if (typeof window !== 'undefined' && window.location) {
      // Redirect to login so the user can re-authenticate
      if (!window.location.pathname.includes('/login')) {
        // Use setTimeout to allow current request to finish
        setTimeout(() => {
          window.location.href = '/login';
        }, 100);
      }
    }
  }

  private isTokenExpired(token: string): boolean {
    try {
      const expirationTime = this.authService.getTokenExpirationTime();
      if (!expirationTime) {
        return true;
      }
      return expirationTime < Date.now();
    } catch (error) {
      return true;
    }
  }

  /**
   * Check if there are pending requests
   */
  hasPendingRequests(): boolean {
    return this.pendingRequests > 0;
  }
}

