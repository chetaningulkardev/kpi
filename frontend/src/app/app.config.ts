import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
  APP_INITIALIZER
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptorsFromDi, HTTP_INTERCEPTORS } from '@angular/common/http';

import { routes } from './app.routes';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';
import { HttpTokenInterceptor } from './services/http-token.interceptor';
import { UiActivityInterceptor } from './services/ui-activity.interceptor';
import { AuthInitializerService } from './services/auth-initializer.service';
import { UrlRestorationService } from './services/url-restoration.service';

export const appConfig: ApplicationConfig = {
  providers: [
    // Angular 21 no longer turns on Zone.js change detection by default: an app
    // is zoneless unless it asks for this provider. These components are all
    // written the zone way — plain `subscribe`, assign a field, let the
    // framework notice — so without it the data arrives and the view never
    // repaints, leaving screens stuck on "Loading..." until an unrelated click
    // happens to schedule a render.
    //
    // Requires zone.js in the build's polyfills (see angular.json).
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideClientHydration(withEventReplay()),
    provideHttpClient(withInterceptorsFromDi()),
    {
      provide: HTTP_INTERCEPTORS,
      useClass: HttpTokenInterceptor,
      multi: true
    },
    {
      provide: HTTP_INTERCEPTORS,
      useClass: UiActivityInterceptor,
      multi: true
    },
    // Initialize auth and restore URL on app startup
    {
      provide: APP_INITIALIZER,
      useFactory: (authInitializer: AuthInitializerService) => () => authInitializer.initialize(),
      deps: [AuthInitializerService],
      multi: true
    },
    // Track URL changes for restoration on refresh
    {
      provide: APP_INITIALIZER,
      useFactory: () => () => { /* UrlRestorationService initializes itself */ },
      deps: [UrlRestorationService],
      multi: true
    }
  ]
};
