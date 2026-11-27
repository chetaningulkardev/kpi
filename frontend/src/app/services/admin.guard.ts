import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { isAdminUser } from './role-access.util';
import { PermissionsService } from './permissions.service';
import { map } from 'rxjs';

export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const permissions = inject(PermissionsService);
  return permissions.load().pipe(map(resolved =>
    (resolved?.admin ?? isAdminUser(authService.currentUserValue?.roles))
      ? true : router.createUrlTree(['/kpi/my-kpis'])
  ));
};
