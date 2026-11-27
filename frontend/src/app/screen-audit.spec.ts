import { TestBed } from '@angular/core/testing';
import { Type } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { of, Subject, throwError, firstValueFrom, Observable } from 'rxjs';
import { vi } from 'vitest';
import { NotificationsComponent } from './components/notifications/notifications.component';
import { UserManagementComponent } from './components/user-management/user-management.component';
import { KpiScorecardsComponent } from './components/kpi-management/kpi-scorecards/kpi-scorecards.component';
import { KpiCategoriesComponent } from './components/kpi-management/kpi-categories/kpi-categories.component';
import { KpiFrequenciesComponent } from './components/kpi-management/kpi-frequencies/kpi-frequencies.component';
import { KpiDataSourcesComponent } from './components/kpi-management/kpi-data-sources/kpi-data-sources.component';
import { KpiMastersComponent } from './components/kpi-management/kpi-masters/kpi-masters.component';
import { KpiReviewCyclesComponent } from './components/kpi-management/kpi-review-cycles/kpi-review-cycles.component';
import { NotificationService, AppNotification } from './services/notification.service';
import { AdminApiService } from './services/admin-api.service';
import { ConfigurationService, KpiScorecard, ReviewCycle } from './services/configuration.service';
import { canManageScorecards } from './services/role-access.util';
import { scorecardGuard, managerGuard } from './services/manager.guard';
import { PermissionsService } from './services/permissions.service';
import { AuthService } from './services/auth.service';
import { adminGuard } from './services/admin.guard';

describe('screen audit regressions', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  const notification: AppNotification = {
    id: 1, title: 'KPI update', message: 'Updated', type: 'KPI_APPROVED',
    entityType: 'KPI', entityId: 10, read: false, createdAt: '2026-01-01T12:00:00Z'
  };

  function notifications() {
    const service = {
      getNotifications: vi.fn(() => of({ items: [notification], page: 0, size: 20, totalItems: 41, totalPages: 3 })),
      getUnreadCount: vi.fn(() => of(25)),
      getNotificationTypes: vi.fn(() => of(['KPI_APPROVED'])),
      markRead: vi.fn(() => of(null)),
      markAllRead: vi.fn(() => of(null))
    };
    const router = { navigate: vi.fn() };
    const component = new NotificationsComponent(service as unknown as NotificationService, router as unknown as Router);
    return { component, service, router };
  }

  it('uses the global unread count, not the current notification page', () => {
    const { component } = notifications();
    component.load();
    expect(component.unreadCount).toBe(25);
    expect(component.notifications.length).toBe(1);
  });

  it('rejects an inverted notification date range before requesting data', () => {
    const { component, service } = notifications();
    component.fromDate = '2026-02-01';
    component.toDate = '2026-01-01';
    component.load();
    expect(component.error).toContain('From date');
    expect(service.getNotifications).not.toHaveBeenCalled();
  });

  it('cancels older notification filter requests', () => {
    const { component, service } = notifications();
    const oldRequest = new Subject<any>();
    service.getNotifications.mockReturnValueOnce(oldRequest as any);
    component.load();
    component.readFilter = 'read';
    component.onFilterChange();
    oldRequest.next({ items: [], page: 2, totalItems: 0, totalPages: 0 });
    expect(component.notifications.length).toBe(1);
    expect(component.page).toBe(0);
    component.ngOnDestroy();
  });

  it('does not present notification failures as an empty success state', () => {
    const { component, service } = notifications();
    service.getNotifications.mockReturnValue(throwError(() => new Error('offline')));
    component.load();
    expect(component.error).toContain('Failed to load');
    expect(component.loading).toBe(false);
  });

  it('opens employee workflow updates in My KPIs', () => {
    const { component, router } = notifications();
    component.openReviewFromNotification({ ...notification, read: true });
    expect(router.navigate).toHaveBeenCalledWith(['/kpi/my-kpis'], { queryParams: { openKpiId: 10 } });
  });

  it('opens submitted KPI notifications in the review queue', () => {
    const { component, router } = notifications();
    component.openReviewFromNotification({ ...notification, type: 'KPI_SUBMITTED', read: true });
    expect(router.navigate).toHaveBeenCalledWith(['/kpi/review'], { queryParams: { openKpiId: 10 } });
  });

  it('reloads the filtered notification page after marking read', () => {
    const { component, service } = notifications();
    component.readFilter = 'unread';
    component.markRead({ ...notification });
    expect(service.getNotifications).toHaveBeenCalledWith(expect.objectContaining({ read: 'unread' }));
  });

  function users() {
    const api = {
      getUserAssignments: vi.fn(() => of({ data: [] })),
      updateUser: vi.fn(() => of({ data: { id: 1 } })),
      setUserAssignments: vi.fn(() => of({ data: [] })),
      getUsers: () => of({ data: [] }),
      getRoles: () => of({ data: [] }),
      getTeams: () => of({ data: [] }),
      getTribes: () => of({ data: [] }),
      getPortfolios: () => of({ data: [] }),
      getSquads: () => of({ data: [] })
    };
    TestBed.configureTestingModule({
      imports: [UserManagementComponent],
      providers: [{ provide: AdminApiService, useValue: api }]
    });
    const component = TestBed.createComponent(UserManagementComponent).componentInstance;
    const user = { id: 1, first_name: 'Test', last_name: 'User', roles: [{ role_name: 'Employee' }] };
    return { component, api, user };
  }

  it('blocks saving while existing organization assignments are loading', () => {
    const { component, api, user } = users();
    api.getUserAssignments.mockReturnValue(new Subject<any>() as any);
    component.openEdit(user);
    component.save();
    expect(component.assignmentsLoading).toBe(true);
    expect(api.updateUser).not.toHaveBeenCalled();
  });

  it('blocks destructive assignment saves when assignment loading fails', () => {
    const { component, api, user } = users();
    api.getUserAssignments.mockReturnValue(throwError(() => new Error('offline')));
    component.openEdit(user);
    component.save();
    expect(component.formError).toContain('avoid losing assignments');
    expect(api.updateUser).not.toHaveBeenCalled();
  });

  it('ignores late assignment responses belonging to another edit dialog', () => {
    const { component, api, user } = users();
    const pending = new Subject<any>();
    api.getUserAssignments.mockReturnValueOnce(pending as any);
    component.openEdit(user);
    component.openEdit({ ...user, id: 2 });
    pending.next({ data: [{ unit_type: 'TRIBE', unit_id: 99 }] });
    expect(component.form.tribeIds).toEqual([]);
  });

  it('reports partial user-save failures without closing the assignment dialog', () => {
    const { component, api, user } = users();
    api.setUserAssignments.mockReturnValue(throwError(() => new Error('offline')));
    component.openEdit(user);
    component.save();
    expect(component.showModal).toBe(true);
    expect(component.editingId).toBe(1);
    expect(component.formError).toContain('organization assignments failed');
    expect(component.success).toBe('');
    expect(component.saving).toBe(false);
  });

  it.each(['TECH_FOCAL', 'SQUAD_LEAD', 'PORTFOLIO_LEAD'])('does not advertise restricted scorecards to %s', role => {
    expect(canManageScorecards([{ role_name: role } as any])).toBe(false);
  });

  it.each(['ADMIN', 'MANAGER'])('allows scorecards for %s', role => {
    expect(canManageScorecards([{ role_name: role } as any])).toBe(true);
  });

  it('prefers server role codes over a display-only administrator label', () => {
    expect(canManageScorecards([{ role_name: 'System Administrator' } as any], ['ADMIN'])).toBe(true);
    expect(canManageScorecards([{ role_name: 'Manager' } as any], ['TECH_FOCAL'])).toBe(false);
  });

  it('blocks direct scorecard navigation for reviewers without ADMIN/MANAGER endpoint roles', async () => {
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { currentUserValue: { roles: [{ role_name: 'Tech Focal' }] } } },
        { provide: Router, useValue: { createUrlTree: (paths: string[]) => paths } },
        { provide: PermissionsService, useValue: { load: () => of({ canManageScorecards: true, roles: ['TECH_FOCAL'] }) } }
      ]
    });
    const decision = TestBed.runInInjectionContext(() => scorecardGuard({} as any, {} as any));
    expect(await firstValueFrom(decision as Observable<unknown>)).toEqual(['/kpi/my-kpis']);
  });

  it('honors resolved review scope rather than granting the review screen to every logged-in employee', async () => {
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { isAuthenticated: () => true, currentUserValue: { roles: [{ role_name: 'Manager' }] } } },
        { provide: Router, useValue: { createUrlTree: (paths: string[]) => paths } },
        { provide: PermissionsService, useValue: { load: () => of({ canReviewKpis: false }) } }
      ]
    });
    const decision = TestBed.runInInjectionContext(() => managerGuard({} as any, {} as any));
    expect(await firstValueFrom(decision as Observable<unknown>)).toEqual(['/kpi/my-kpis']);
  });

  it('uses resolved administrator capabilities even when a role display name differs', async () => {
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { currentUserValue: { roles: [{ role_name: 'Custom administrator label' }] } } },
        { provide: Router, useValue: { createUrlTree: (paths: string[]) => paths } },
        { provide: PermissionsService, useValue: { load: () => of({ admin: true }) } }
      ]
    });
    const decision = TestBed.runInInjectionContext(() => adminGuard({} as any, {} as any));
    expect(await firstValueFrom(decision as Observable<unknown>)).toBe(true);
  });

  it('ignores scorecard and summary responses from a previously selected cycle', () => {
    const oldCards = new Subject<KpiScorecard[]>();
    const oldSummary = new Subject<any>();
    const service = {
      getScorecards: vi.fn().mockReturnValueOnce(oldCards).mockReturnValue(of([])),
      getScorecardSummary: vi.fn().mockReturnValueOnce(oldSummary).mockReturnValue(of({ totalScorecards: 0 }))
    };
    const component = new KpiScorecardsComponent(service as unknown as ConfigurationService);
    component.selectedCycleCode = 'OLD';
    component.loadScorecards();
    oldCards.next([]);
    component.selectedCycleCode = 'NEW';
    component.loadScorecards();
    oldCards.next([{ id: 99 } as KpiScorecard]);
    oldSummary.next({ totalScorecards: 99 });
    expect(component.scorecards).toEqual([]);
    expect(component.summary).toEqual({ totalScorecards: 0 });
  });

  it('keeps scorecards available and distinguishes a failed summary from empty data', () => {
    const service = {
      getScorecards: () => of([{ id: 1, employeeName: 'Employee' } as KpiScorecard]),
      getScorecardSummary: vi.fn().mockReturnValueOnce(throwError(() => new Error('offline')))
        .mockReturnValue(of({ totalEmployees: 1 }))
    };
    const component = new KpiScorecardsComponent(service as unknown as ConfigurationService);
    component.selectedCycleCode = 'CURRENT';
    component.loadScorecards();
    expect(component.filteredScorecards.length).toBe(1);
    expect(component.summaryLoading).toBe(false);
    expect(component.summaryError).toContain('could not be loaded');
    component.loadSummary();
    expect(component.summaryError).toBe('');
    expect(component.summary).toEqual({ totalEmployees: 1 });
  });

  it('distinguishes loading and failed cycles from a successful no-cycle scorecard state', () => {
    const pending = new Subject<ReviewCycle[]>();
    const service = { getReviewCycles: () => pending };
    const component = new KpiScorecardsComponent(service as unknown as ConfigurationService);
    component.loadCycles();
    expect(component.cyclesLoading).toBe(true);
    pending.error(new Error('offline'));
    expect(component.cyclesLoading).toBe(false);
    expect(component.cyclesError).toContain('Failed to load review cycles');
  });

  it('keeps a saving calibration modal open and prevents duplicate submissions', () => {
    const service = { calibrateScorecard: vi.fn(() => new Subject<any>()) };
    const component = new KpiScorecardsComponent(service as unknown as ConfigurationService);
    component.openCalibration({ id: 1, overallScore: 3, status: 'CALIBRATION' } as KpiScorecard);
    component.calibrationForm.notes = 'Calibration explanation';
    component.saveCalibration();
    component.closeModal();
    component.saveCalibration();
    expect(component.showModal).toBe(true);
    expect(service.calibrateScorecard).toHaveBeenCalledTimes(1);
  });

  it('rejects non-finite calibration scores', () => {
    const service = { calibrateScorecard: vi.fn() };
    const component = new KpiScorecardsComponent(service as unknown as ConfigurationService);
    component.openCalibration({ id: 1, overallScore: 3, status: 'CALIBRATION' } as KpiScorecard);
    component.calibrationForm.notes = 'Explanation';
    component.calibrationForm.overallScore = NaN;
    component.saveCalibration();
    expect(component.formMsgIsError).toBe(true);
    expect(service.calibrateScorecard).not.toHaveBeenCalled();
  });

  const configurationScreens: Array<Type<{ saving: boolean; showModal: boolean; closeModal(): void; save(): void }>> = [
    KpiCategoriesComponent, KpiFrequenciesComponent, KpiDataSourcesComponent, KpiMastersComponent, KpiReviewCyclesComponent
  ];

  it.each(configurationScreens)('keeps saving configuration dialogs open: %s', screen => {
    TestBed.configureTestingModule({
      imports: [screen],
      providers: [{ provide: ConfigurationService, useValue: {} }]
    });
    const component = TestBed.createComponent(screen).componentInstance;
    component.showModal = true;
    component.saving = true;
    component.closeModal();
    component.save();
    expect(component.showModal).toBe(true);
    expect(component.saving).toBe(true);
  });

  it.each(configurationScreens)('does not silently treat configuration loading failures as empty data: %s', screen => {
    const fail = () => throwError(() => new Error('offline'));
    TestBed.configureTestingModule({
      imports: [screen],
      providers: [{ provide: ConfigurationService, useValue: {
        getKpiCategories: fail, getKpiFrequencies: fail, getKpiDataSources: fail,
        getKpiMasters: fail, getRoles: fail, getReviewCycles: fail
      } }]
    });
    const component = TestBed.createComponent(screen).componentInstance as unknown as { load(): void; loadError: string; loading: boolean };
    component.load();
    expect(component.loading).toBe(false);
    expect(component.loadError).toContain('Failed to load');
  });

  it('downloads protected evidence using HttpClient rather than an unauthenticated window', () => {
    vi.useFakeTimers();
    const create = vi.fn(() => 'blob:evidence');
    const revoke = vi.fn();
    vi.stubGlobal('URL', class extends URL {
      static override createObjectURL = create;
      static override revokeObjectURL = revoke;
    });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    let completed = false;
    TestBed.inject(ConfigurationService).downloadEvidenceFile('/api/kpi/review/evidence/file/report.pdf')
      .subscribe(() => completed = true);
    const http = TestBed.inject(HttpTestingController);
    const request = http.expectOne('/api/kpi/review/evidence/file/report.pdf');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['evidence'], { type: 'application/pdf' }));
    expect(completed).toBe(true);
    expect(click).toHaveBeenCalledTimes(1);
    vi.runAllTimers();
    expect(revoke).toHaveBeenCalledWith('blob:evidence');
    http.verify();
  });
});
