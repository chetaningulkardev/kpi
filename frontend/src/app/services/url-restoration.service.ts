import { Injectable } from '@angular/core';
import { Router, NavigationEnd, NavigationStart } from '@angular/router';
import { filter } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class UrlRestorationService {
  constructor(private router: Router) {
    this.initializeUrlTracking();
  }

  /**
   * Track navigation and save URL to sessionStorage
   * This allows restoration on page refresh
   */
  private initializeUrlTracking(): void {
    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe((event: any) => {
        // Save the current URL to sessionStorage on successful navigation
        if (event.url && event.url !== '/login') {
          sessionStorage.setItem('previousUrl', event.url);
        }
      });

    // Also listen to NavigationStart to preserve URL before any redirect happens
    this.router.events
      .pipe(filter(event => event instanceof NavigationStart))
      .subscribe((event: any) => {
        if (event.url && event.url !== '/login' && !event.url.includes('/login')) {
          // Gently save it before navigation, but let RouteGuard override if needed
          if (!sessionStorage.getItem('previousUrl')) {
            sessionStorage.setItem('previousUrl', event.url);
          }
        }
      });
  }

  /**
   * Get previously saved URL
   */
  getPreviousUrl(): string | null {
    return sessionStorage.getItem('previousUrl');
  }

  /**
   * Clear saved URL
   */
  clearPreviousUrl(): void {
    sessionStorage.removeItem('previousUrl');
  }
}

