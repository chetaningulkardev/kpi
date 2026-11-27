import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';

export type ReviewScope = 'SELF' | 'SQUAD' | 'PORTFOLIO' | 'TRIBE' | 'ORGANIZATION';

export interface MyPermissions {
  userId: number;
  email: string;
  fullName: string;
  roles: string[];
  reviewScope: ReviewScope;
  admin: boolean;
  canReviewKpis: boolean;
  canViewTeamFlow: boolean;
  canManageScorecards: boolean;
  canConfigureKpis: boolean;
  accessibleSquadIds: number[];
  accessibleUserCount: number;
}

/**
 * Server-resolved capabilities for the signed-in user.
 *
 * The UI previously decided what to show from a hardcoded list of role names,
 * which silently drifted from the seeded roles. The server derives scope from
 * actual squad/portfolio/tribe assignments, so it is the only reliable source.
 */
@Injectable({ providedIn: 'root' })
export class PermissionsService {

  private readonly http = inject(HttpClient);

  /** Null until loaded, or when the request failed and callers must fall back. */
  readonly permissions = signal<MyPermissions | null>(null);

  load(): Observable<MyPermissions | null> {
    return this.http.get<MyPermissions>('/api/me/permissions').pipe(
      tap(p => this.permissions.set(p)),
      // Never block the shell on this: callers degrade to role-name checks.
      catchError(() => {
        this.permissions.set(null);
        return of(null);
      })
    );
  }

  clear(): void {
    this.permissions.set(null);
  }

  get scope(): ReviewScope | null {
    return this.permissions()?.reviewScope ?? null;
  }
}

