import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { PermissionsService } from './permissions.service';
import { canManageScorecards, isManagerUser } from './role-access.util';
import { map } from 'rxjs';

export const managerGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  if (!authService.isAuthenticated()) {
    router.navigate(['/login']);
    return false;
  }
  const permissions = inject(PermissionsService);
  return permissions.load().pipe(map(resolved =>
    (resolved?.canReviewKpis ?? isManagerUser(authService.currentUserValue?.roles))
      ? true : router.createUrlTree(['/kpi/my-kpis'])
  ));
};

export const scorecardGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const permissions = inject(PermissionsService);
  return permissions.load().pipe(map(resolved =>
    canManageScorecards(auth.currentUserValue?.roles, resolved?.roles)
      ? true : router.createUrlTree(['/kpi/my-kpis'])
  ));
};
