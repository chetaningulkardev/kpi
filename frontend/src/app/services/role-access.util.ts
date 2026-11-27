import { Role } from '../models/auth.model';

function normalizeRoleName(raw: string | null | undefined): string {
  return String(raw ?? '')
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');
}

export function getNormalizedRoleNames(roles: Role[] | null | undefined): string[] {
  return (roles ?? [])
    .map((r) => normalizeRoleName(r.role_name))
    .filter((r) => !!r);
}

export function hasAnyRole(roles: Role[] | null | undefined, expected: string[]): boolean {
  const userRoles = new Set(getNormalizedRoleNames(roles));
  return expected.some((r) => userRoles.has(normalizeRoleName(r)));
}

export function isAdminUser(roles: Role[] | null | undefined): boolean {
  return hasAnyRole(roles, ['ADMIN', 'ROLE_ADMIN', 'SYSTEM_ADMINISTRATOR']);
}

/**
 * Roles that carry a review scope beyond the user themselves.
 *
 * These MUST stay aligned with the seeded role codes. `PORTFOLIO_MANAGER` was
 * previously listed here while the database seeds `PORTFOLIO_LEAD`, which locked
 * Portfolio Leads out of the review screen entirely; `TECH_FOCAL` was missing
 * altogether. Prefer the server-provided permissions (`/api/me/permissions`);
 * this list is only the offline fallback.
 */
export const REVIEWER_ROLES = [
  'ADMIN',
  'ROLE_ADMIN',
  'SYSTEM_ADMINISTRATOR',
  'MANAGER',
  'SQUAD_LEAD',
  'TECH_FOCAL',
  'TRIBE_LEAD',
  'PORTFOLIO_LEAD',
  'PORTFOLIO_MANAGER',
  'SCRUM_MASTER'
];

export function isManagerUser(roles: Role[] | null | undefined): boolean {
  return hasAnyRole(roles, REVIEWER_ROLES);
}

export function canManageScorecards(roles: Role[] | null | undefined, permissionRoles?: string[]): boolean {
  // Scorecard endpoints are ADMIN/MANAGER-only, unlike hierarchy-based review.
  return (permissionRoles ?? getNormalizedRoleNames(roles))
    .some(role => ['ADMIN', 'MANAGER'].includes(normalizeRoleName(role).replace(/^ROLE_/, '')));
}
