import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PermissionsService, MyPermissions } from './permissions.service';
import { isManagerUser, REVIEWER_ROLES } from './role-access.util';
import { Role } from '../models/auth.model';

const samplePermissions: MyPermissions = {
  userId: 1, email: 'lead@example.invalid', fullName: 'Squad Lead',
  roles: ['SQUAD_LEAD'], reviewScope: 'SQUAD', admin: false,
  canReviewKpis: true, canViewTeamFlow: true, canManageScorecards: true,
  canConfigureKpis: false, accessibleSquadIds: [100], accessibleUserCount: 6
};

function roles(...names: string[]): Role[] {
  return names.map(n => ({ role_name: n } as Role));
}

describe('PermissionsService', () => {
  let service: PermissionsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [PermissionsService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(PermissionsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => TestBed.resetTestingModule());

  it('should start with no permissions loaded', () => {
    expect(service.permissions()).toBeNull();
  });

  it('should store permissions returned by the server', () => {
    service.load().subscribe();
    httpMock.expectOne('/api/me/permissions').flush(samplePermissions);

    expect(service.permissions()?.reviewScope).toBe('SQUAD');
    expect(service.permissions()?.canReviewKpis).toBe(true);
    expect(service.scope).toBe('SQUAD');
  });

  it('should degrade to null when the request fails, never throwing', () => {
    let errored = false;
    service.load().subscribe({ error: () => { errored = true; } });
    httpMock.expectOne('/api/me/permissions').flush('nope', { status: 500, statusText: 'Server Error' });

    // The shell must still render when permissions cannot be resolved.
    expect(errored).toBe(false);
    expect(service.permissions()).toBeNull();
  });

  it('clear() should drop cached permissions on logout', () => {
    service.load().subscribe();
    httpMock.expectOne('/api/me/permissions').flush(samplePermissions);
    expect(service.permissions()).not.toBeNull();

    service.clear();
    expect(service.permissions()).toBeNull();
  });
});

describe('role-access.util reviewer roles', () => {

  // Regression: the list previously said PORTFOLIO_MANAGER while the backend
  // seeds PORTFOLIO_LEAD, and omitted TECH_FOCAL entirely — so those users
  // never saw the review menu entry.
  it('should treat PORTFOLIO_LEAD as a reviewer', () => {
    expect(isManagerUser(roles('PORTFOLIO_LEAD'))).toBe(true);
  });

  it('should treat TECH_FOCAL as a reviewer', () => {
    expect(isManagerUser(roles('TECH_FOCAL'))).toBe(true);
  });

  it('should treat SQUAD_LEAD and TRIBE_LEAD as reviewers', () => {
    expect(isManagerUser(roles('SQUAD_LEAD'))).toBe(true);
    expect(isManagerUser(roles('TRIBE_LEAD'))).toBe(true);
  });

  it('should not treat a plain developer as a reviewer', () => {
    expect(isManagerUser(roles('DEVELOPER'))).toBe(false);
  });

  it('should cover every seeded reviewer role code', () => {
    const seeded = ['ADMIN', 'MANAGER', 'PORTFOLIO_LEAD', 'SCRUM_MASTER',
                    'SQUAD_LEAD', 'TECH_FOCAL', 'TRIBE_LEAD'];
    seeded.forEach(code => {
      expect(REVIEWER_ROLES).toContain(code);
    });
  });
});

