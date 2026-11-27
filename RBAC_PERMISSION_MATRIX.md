# Authorization Model

This document describes the authorization model currently implemented by the
backend and frontend. Backend checks are authoritative; frontend route guards
improve navigation but do not grant or enforce API access.

## Access principles

- Unauthenticated requests are limited to explicitly public auth endpoints,
  public demo metadata, health/info probes, and frontend assets.
- All other API requests require authentication.
- User, role, team, and KPI-configuration endpoints are restricted to
  administrators by Spring Security and/or controller method checks.
- KPI actions use record-level checks in the workflow service. Employees can
  submit their own KPIs; reviewers must be an administrator, the employee's
  reporting manager, or have an active organizational review scope.
- Review scope follows active squad, portfolio, and tribe assignments and
  leadership. Higher-level scopes expand to descendant squads. Inactive
  assignments do not grant access.
- Evidence reads and writes also check access to the associated KPI; knowing an
  evidence URL or record ID is not authorization.
- Self-review is denied as a management action.

## Frontend route groups

| Route group | Frontend gate | Notes |
| --- | --- | --- |
| Dashboard, profile, KPI dashboard, own KPIs, own performance, notifications | `authGuard` | Requires a valid signed-in session. |
| User, role, team, tribe, portfolio, squad, assignment, organization administration | `authGuard` + `adminGuard` | Navigation gate only; backend remains authoritative. |
| KPI cycles, categories, frequencies, data sources, masters, templates, generation | `authGuard` + `adminGuard` | Configuration and generation screens. |
| KPI review and team flow | `authGuard` + `managerGuard` | Uses `/api/me/permissions` to resolve review access. |
| Scorecards | `authGuard` + `scorecardGuard` | Limited to roles permitted by the scorecard API. |

See `frontend/src/app/app.routes.ts` for the current route definitions and
`frontend/src/app/services/manager.guard.ts` for route-guard behavior.

## API authorization examples

| Operation | Backend rule |
| --- | --- |
| Login, refresh, logout, token validation | Explicitly public auth endpoints; logout revokes the presented token when possible. |
| `/api/users/**`, `/api/roles/**`, `/api/teams/**` | `ADMIN` |
| KPI templates and organizational configuration | Administrator checks at controller/method level. |
| Own KPI list and self-assessment | Authenticated; self-assessment additionally verifies KPI ownership. |
| Employee KPI lookup, manager decisions, bulk review | Authenticated plus reviewer-scope/ownership checks in the workflow service. |
| Pending review queue | Authenticated; result set is scoped to the reviewer, with administrators receiving organization-wide results. |
| KPI evidence | Authenticated plus access check against the associated KPI. |
| Scorecard management | `ADMIN` or `MANAGER` where the controller requires a manager role. |
| Actuator health and info | Public for runtime probes. |
| Other actuator endpoints | `ADMIN` |

Endpoint-level rules can differ by operation. Check `SecurityConfig` and the
relevant controller's `@PreAuthorize` annotations together with the service
checks before changing this matrix.

## Verification

Authorization changes should be covered at both layers:

1. Service tests for ownership, reporting relationships, active assignments,
   administrative access, and denial cases.
2. Controller/security tests for authentication and role-protected endpoints.
3. Frontend guard tests for navigation behavior.

The frontend is not a security boundary. Do not rely on hiding a link or
blocking a route to protect KPI or employee data.
