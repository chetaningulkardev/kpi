import { Injectable } from '@angular/core';
import { Router, CanActivateFn, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { AuthService } from './auth.service';
import { inject } from '@angular/core';
import { hasAnyRole } from './role-access.util';

/**
 * Auth Guard Service for protecting routes that require authentication
 */
@Injectable({
  providedIn: 'root'
})
export class AuthGuardService {
  constructor(private authService: AuthService, private router: Router) { }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    if (this.authService.isAuthenticated()) {
      return true;
    }

    // Save the intended URL so we can redirect back after login
    sessionStorage.setItem('redirectUrl', state.url);

    this.router.navigate(['/login']);
    return false;
  }

  canActivateByRole(requiredRole: string): boolean {
    const user = this.authService.currentUserValue;
    if (user && hasAnyRole(user.roles, [requiredRole])) {
      return true;
    }

    this.router.navigate(['/dashboard']);
    return false;
  }
}

/**
 * Functional auth guard (for newer Angular routing)
 */
export const authGuard: CanActivateFn = (route: ActivatedRouteSnapshot, state: RouterStateSnapshot) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  // Save the intended URL for redirect after login
  sessionStorage.setItem('redirectUrl', state.url);

  router.navigate(['/login']);
  return false;
};
