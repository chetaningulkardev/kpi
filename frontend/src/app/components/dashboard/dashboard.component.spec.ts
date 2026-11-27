import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { DashboardComponent } from './dashboard.component';
import { AuthService } from '../../services/auth.service';
import { ConfigurationService } from '../../services/configuration.service';
import { DraftStorageService } from '../../services/draft-storage.service';
import { PermissionsService } from '../../services/permissions.service';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let configService: {
    getReviewCycles: ReturnType<typeof vi.fn>;
    getMyKpis: ReturnType<typeof vi.fn>;
    getPendingReviews: ReturnType<typeof vi.fn>;
  };

  const user = {
    id: 7,
    employee_code: 'KPI-7',
    first_name: 'Maya',
    last_name: 'Chen',
    email: 'maya@example.com',
    roles: [{ id: 2, role_name: 'MANAGER', description: '', active: true }],
    active: true,
    last_login: '',
    created_date: '',
    updated_date: ''
  };

  beforeEach(async () => {
    configService = {
      getReviewCycles: vi.fn().mockReturnValue(of([
        { code: 'OLD', name: 'Old cycle', startDate: '2025-01-01', endDate: '2025-03-31', statusCode: 'CLOSED' },
        { code: 'FY26_Q4', name: 'Q4 2026 Review', startDate: '2026-10-01', endDate: '2026-12-31', statusCode: 'IN_PROGRESS' }
      ])),
      getMyKpis: vi.fn().mockReturnValue(of([{ id: 1 }, { id: 2 }])),
      getPendingReviews: vi.fn().mockReturnValue(of([
        { id: 11, user: { id: 3 } },
        { id: 12, user: { id: 3 } },
        { id: 13, user: { id: 4 } }
      ]))
    };

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideRouter([]),
        { provide: PermissionsService, useValue: { permissions: () => null } },
        { provide: AuthService, useValue: { currentUser$: of(user), logout: () => of(null) } },
        { provide: ConfigurationService, useValue: configService },
        { provide: DraftStorageService, useValue: { discardAllForCurrentUser: vi.fn() } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
  });

  it('loads the active cycle and the signed-in user KPI count', () => {
    expect(configService.getMyKpis).toHaveBeenCalledWith('FY26_Q4');
    expect(fixture.componentInstance.activeCycle?.name).toBe('Q4 2026 Review');
    expect(fixture.componentInstance.myKpiCount).toBe(2);
  });

  it('shows review shortcuts only to managers and counts distinct pending employees', () => {
    expect(fixture.componentInstance.pendingReviewKpis).toBe(3);
    expect(fixture.componentInstance.pendingReviewUsers).toBe(2);
    const buttons = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'));
    expect(buttons.some(button => button.textContent?.includes('Pending Users'))).toBe(true);
    expect(buttons.some(button => button.textContent?.includes('Pending KPIs'))).toBe(true);
  });

  it('hides manager-only shortcuts from employee accounts', () => {
    fixture.componentInstance.currentUser = {
      ...user,
      roles: [{ id: 3, role_name: 'EMPLOYEE', description: '', active: true }]
    };
    fixture.detectChanges();

    const buttons = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'));
    expect(buttons.some(button => button.textContent?.includes('Pending Users'))).toBe(false);
    expect(buttons.some(button => button.textContent?.includes('Manager Review'))).toBe(false);
  });
});
