import { Injectable, Injector, PLATFORM_ID } from '@angular/core';
import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { firstValueFrom } from 'rxjs';
import { catchError, of } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class AuthInitializerService {
  constructor(private injector: Injector) { }

  /**
   * Initialize auth on app startup
   * - Restore token and user from localStorage
   * - Restore previous URL if authenticated
   * - Validate token with backend if available
   */
  async initialize(): Promise<void> {
    const authService = this.injector.get(AuthService);
    const router = this.injector.get(Router);

    const token = authService.getTokenFromLocalStorage();
    const previousUrl = sessionStorage.getItem('previousUrl');

    if (!token || !authService.isAuthenticated()) {
      // No valid token, redirect to login
      sessionStorage.removeItem('previousUrl');
      return;
    }

    try {
      // Validate token with backend
      const isValid = await firstValueFrom(
        authService.validateToken(token).pipe(
          catchError(() => of(false))
        )
      );

      if (!isValid) {
        // Token invalid, clear it and redirect to login
        authService.clearTokens();
        router.navigate(['/login']);
        return;
      }

      // Token is valid, restore previous URL if available.
      //
      // Only when the browser did not ask for anything in particular, though.
      // Restoring unconditionally overrode the requested route, so refreshing a
      // deep link — or opening one directly — silently dropped the user on
      // whichever screen they happened to visit last.
      if (previousUrl && previousUrl !== '/login' && this.landedOnEntryPoint()) {
        sessionStorage.removeItem('previousUrl');
        router.navigateByUrl(previousUrl).catch(() => {
          // If URL navigation fails, go to dashboard
          router.navigate(['/dashboard']);
        });
      }
    } catch (error) {
      // On error, just clear tokens safely
      authService.clearTokens();
      router.navigate(['/login']);
    }
  }

  /**
   * True when the app was opened at its root or at the login screen, i.e. the
   * URL carries no destination of its own that restoration would trample.
   */
  private landedOnEntryPoint(): boolean {
    if (!isPlatformBrowser(this.injector.get(PLATFORM_ID))) {
      return false;
    }

    const path = this.injector.get(DOCUMENT).location?.pathname ?? '/';
    return path === '/' || path === '' || path.startsWith('/login');
  }
}

