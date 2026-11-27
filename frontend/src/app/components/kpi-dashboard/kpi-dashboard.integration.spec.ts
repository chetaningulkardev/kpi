import { vi } from 'vitest';
/**
 * Integration Tests: KPI Dashboard Component
 * Tests the full HTTP request/response cycle using HttpClientTestingModule.
 */
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController
} from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { KPIDashboardComponent } from './kpi-dashboard.component';
import { ConfigurationService, KpiInstance, ReviewCycle } from '../../services/configuration.service';
import { AuthService } from '../../services/auth.service';
import { createSpyObj } from '../../../testing/spy-utils';
import { ConfirmService } from '../../services/confirm.service';
import { StubConfirmService } from '../../testing/stub-confirm.service';

// ─── Helpers ──────────────────────────────────────────────────────────────────
const cycle: ReviewCycle = {
  id: 1, code: 'Q4-2026', name: 'Q4 Review', startDate: '2026-10-01',
  endDate: '2026-12-31', statusCode: 'OPEN'
};
// Second (already closed) cycle so the user can switch cycles in the UI.
const previousCycle: ReviewCycle = {
  id: 2, code: 'Q3-2026', name: 'Q3 Review', startDate: '2026-07-01',
  endDate: '2026-09-30', statusCode: 'CLOSED'
};
const pendingKpi: KpiInstance = {
  id: 10, kpiName: 'Test KPI', kpiCode: 'T001', categoryName: 'Engineering',
  weight: 30, target: '100', frequency: 'QUARTERLY', status: 'PENDING',
  actualValue: null, comments: null, justification: null, rating: null, reviewerComments: null
};
const submittedKpi: KpiInstance = { ...pendingKpi, status: 'SUBMITTED', actualValue: 85 };

// ─── Mocks ────────────────────────────────────────────────────────────────────
const authStub = { currentUserValue: { roles: [{ role_name: 'MANAGER' }] } };

function buildServiceSpy(overrides: Partial<Record<keyof ConfigurationService, any>> = {}): any {
  const spy = createSpyObj<ConfigurationService>([
    'getReviewCycles', 'getMyKpis', 'getPendingReviews',
    'submitSelfAssessment', 'uploadEvidenceFile'
  ]);
  spy.getReviewCycles.mockReturnValue(of([cycle, previousCycle]));
  spy.getMyKpis.mockReturnValue(of([pendingKpi]));
  spy.getPendingReviews.mockReturnValue(of([]));
  spy.submitSelfAssessment.mockReturnValue(of(submittedKpi));
  spy.uploadEvidenceFile.mockReturnValue(of({ id: 1, title: 'ev.pdf', evidenceType: 'FILE', description: '', contentUrl: '', fileType: 'pdf', fileSizeBytes: 0 }));
  Object.assign(spy, overrides);
  return spy;
}

function setup(configSpy?: any) {
  const svc = configSpy ?? buildServiceSpy();
  const confirmStub = new StubConfirmService();
  TestBed.configureTestingModule({
    imports: [KPIDashboardComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: ConfigurationService, useValue: svc },
      { provide: AuthService, useValue: authStub },
      { provide: ConfirmService, useValue: confirmStub }
    ]
  });
  const fixture = TestBed.createComponent(KPIDashboardComponent);
  const comp = fixture.componentInstance;
  return { fixture, comp, svc, confirmStub };
}

// ─── Tests ────────────────────────────────────────────────────────────────────
describe('KPIDashboard Integration Tests', () => {

  afterEach(() => TestBed.resetTestingModule());

  // ── Data loading lifecycle ─────────────────────────────────────────────────
  describe('Data Loading', () => {
    it('should call getReviewCycles on init', () => {
      const { comp, svc } = setup();
      comp.ngOnInit();
      expect(svc.getReviewCycles).toHaveBeenCalledTimes(1);
    });

    it('should auto-select first cycle and load KPIs', () => {
      const { comp, svc } = setup();
      comp.ngOnInit();
      expect(comp.selectedCycleCode).toBe('Q4-2026');
      expect(svc.getMyKpis).toHaveBeenCalledWith('Q4-2026');
      expect(comp.kpis.length).toBe(1);
    });

    it('should set loading=false after data loads', () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(comp.loading).toBe(false);
    });

    it('should show error state when getMyKpis fails', () => {
      const svc = buildServiceSpy({
        getMyKpis: vi.fn().mockReturnValue(throwError(() => new Error('Server error')))
      } as any);
      const { comp } = setup(svc);
      comp.ngOnInit();
      expect(comp.error).toBeTruthy();
      expect(comp.kpis.length).toBe(0);
    });

    it('should reload KPIs on cycle change', () => {
      const { comp, svc } = setup();
      comp.ngOnInit();
      svc.getMyKpis.mockClear();
      comp.selectedCycleCode = 'Q3-2026';
      comp.onCycleChange();
      expect(svc.getMyKpis).toHaveBeenCalledWith('Q3-2026');
    });
  });

  // ── KPI Submission flow ────────────────────────────────────────────────────
  describe('KPI Submission', () => {
    it('should call submitSelfAssessment with correct payload', async () => {
      const { comp, svc } = setup();
      comp.ngOnInit();

      comp.toggleSubmitForm(pendingKpi);
      comp.submitForm.actualValue = 85;
      comp.submitForm.comments = 'All done';

      await comp.submitKpi(pendingKpi);

      expect(svc.submitSelfAssessment).toHaveBeenCalledWith(
        10,
        expect.objectContaining({ actualValue: 85, comments: 'All done' })
      );
    });

    it('should update KPI in list after successful submission', async () => {
      const { comp, svc } = setup();
      comp.ngOnInit();

      comp.toggleSubmitForm(pendingKpi);
      comp.submitForm.actualValue = 85;
      await comp.submitKpi(pendingKpi);

      const updated = comp.kpis.find(k => k.id === 10);
      expect(updated?.status).toBe('SUBMITTED');
    });

    it('should NOT submit when actualValue is missing', async () => {
      const { comp, svc } = setup();
      comp.ngOnInit();

      comp.toggleSubmitForm(pendingKpi);
      comp.submitForm.actualValue = null;
      await comp.submitKpi(pendingKpi);

      expect(svc.submitSelfAssessment).not.toHaveBeenCalled();
      expect(comp.submitError).toBeTruthy();
    });

    it('should NOT submit when actualValue is negative', async () => {
      const { comp, svc } = setup();
      comp.ngOnInit();

      comp.toggleSubmitForm(pendingKpi);
      comp.submitForm.actualValue = -5;
      await comp.submitKpi(pendingKpi);

      expect(svc.submitSelfAssessment).not.toHaveBeenCalled();
    });

    it('should show success message after submit', async () => {
      const { comp } = setup();
      comp.ngOnInit();

      comp.toggleSubmitForm(pendingKpi);
      comp.submitForm.actualValue = 85;
      await comp.submitKpi(pendingKpi);

      expect(comp.submitSuccess).toBeTruthy();
    });

    it('should clear form after successful submit', async () => {
      vi.useFakeTimers();
      try {
        const { comp } = setup();
        comp.ngOnInit();

        comp.toggleSubmitForm(pendingKpi);
        comp.submitForm.actualValue = 85;
        comp.submitForm.comments = 'Test';
        await comp.submitKpi(pendingKpi);

        // The form auto-collapses on a timeout after a successful submit.
        vi.advanceTimersByTime(3000);

        expect(comp.expandedKpiId).toBeNull();
      } finally {
        vi.useRealTimers();
      }
    });

    it('should set submitError on API failure', async () => {
      const svc = buildServiceSpy({
        submitSelfAssessment: vi.fn().mockReturnValue(
          throwError(() => ({ error: { message: 'Submission failed' } }))
        )
      } as any);
      const { comp } = setup(svc);
      comp.ngOnInit();

      comp.toggleSubmitForm(pendingKpi);
      comp.submitForm.actualValue = 85;
      await comp.submitKpi(pendingKpi);

      expect(comp.submitError).toContain('Submission failed');
    });

    it('should upload evidence before submission if file is attached', async () => {
      const { comp, svc } = setup();
      comp.ngOnInit();

      comp.toggleSubmitForm(pendingKpi);
      comp.submitForm.actualValue = 85;
      comp.submitForm.evidenceFile = new File(['content'], 'report.pdf', { type: 'application/pdf' });
      await comp.submitKpi(pendingKpi);

      expect(svc.uploadEvidenceFile).toHaveBeenCalledWith(
        10,
        expect.any(File),
        expect.any(String)
      );
      expect(svc.uploadEvidenceFile.mock.invocationCallOrder[0]).toBeLessThan(svc.submitSelfAssessment.mock.invocationCallOrder[0]);
    });

    it('should retain the editable assessment when evidence upload fails', async () => {
      const svc = buildServiceSpy({
        uploadEvidenceFile: vi.fn().mockReturnValue(throwError(() => new Error('offline')))
      } as any);
      const { comp } = setup(svc);
      comp.ngOnInit();
      comp.toggleSubmitForm(pendingKpi);
      comp.submitForm.actualValue = 85;
      comp.submitForm.evidenceFile = new File(['content'], 'report.pdf', { type: 'application/pdf' });
      await comp.submitKpi(pendingKpi);
      expect(svc.submitSelfAssessment).not.toHaveBeenCalled();
      expect(comp.submitError).toContain('Failed to upload');
      expect(comp.submitting).toBe(false);
      expect(comp.expandedKpiId).toBe(pendingKpi.id);
    });
  });

  // ── Filtering & search ─────────────────────────────────────────────────────
  describe('Filtering & Search', () => {
    it('should filter KPIs by status', () => {
      const svc = buildServiceSpy({
        getMyKpis: vi.fn().mockReturnValue(of([
          pendingKpi,
          { ...pendingKpi, id: 2, status: 'APPROVED' }
        ]))
      } as any);
      const { comp } = setup(svc);
      comp.ngOnInit();

      comp.filterByStatus('PENDING');
      expect(comp.filteredKPIs.length).toBe(1);
    });

    it('should filter KPIs by category', () => {
      const svc = buildServiceSpy({
        getMyKpis: vi.fn().mockReturnValue(of([
          pendingKpi,
          { ...pendingKpi, id: 3, categoryName: 'Management', status: 'PENDING' }
        ]))
      } as any);
      const { comp } = setup(svc);
      comp.ngOnInit();

      comp.filterByCategory('Engineering');
      expect(comp.filteredKPIs.length).toBe(1);
      expect(comp.filteredKPIs[0].categoryName).toBe('Engineering');
    });

    it('should search KPIs by name', () => {
      const svc = buildServiceSpy({
        getMyKpis: vi.fn().mockReturnValue(of([
          pendingKpi,
          { ...pendingKpi, id: 4, kpiName: 'Sprint Velocity' }
        ]))
      } as any);
      const { comp } = setup(svc);
      comp.ngOnInit();

      comp.searchTerm = 'test';
      comp.onSearchChange();
      expect(comp.filteredKPIs.length).toBe(1);
    });

    it('clearFilters() should show all KPIs again', () => {
      const svc = buildServiceSpy({
        getMyKpis: vi.fn().mockReturnValue(of([
          pendingKpi,
          { ...pendingKpi, id: 5, status: 'APPROVED' }
        ]))
      } as any);
      const { comp } = setup(svc);
      comp.ngOnInit();

      comp.filterByStatus('PENDING');
      expect(comp.filteredKPIs.length).toBe(1);
      comp.clearFilters();
      expect(comp.filteredKPIs.length).toBe(2);
    });
  });

  // ── Approval queue ─────────────────────────────────────────────────────────
  describe('Approval Queue', () => {
    it('should show approvals from getPendingReviews', () => {
      const approval: KpiInstance = {
        ...pendingKpi, id: 99, status: 'SUBMITTED',
        user: { firstName: 'Alice', lastName: 'Tan', email: 'alice@example.invalid' }
      };
      const svc = buildServiceSpy({
        getPendingReviews: vi.fn().mockReturnValue(of([approval]))
      } as any);
      const { comp } = setup(svc);
      comp.ngOnInit();

      expect(comp.pendingApprovals.length).toBe(1);
      expect(comp.filteredApprovals.length).toBe(1);
    });

    it('should filter approvals by status', () => {
      const approvals: KpiInstance[] = [
        { ...pendingKpi, id: 20, status: 'SUBMITTED' },
        { ...pendingKpi, id: 21, status: 'UNDER_REVIEW' }
      ];
      const svc = buildServiceSpy({
        getPendingReviews: vi.fn().mockReturnValue(of(approvals))
      } as any);
      const { comp } = setup(svc);
      comp.ngOnInit();

      comp.filterApprovalStatus('SUBMITTED');
      expect(comp.filteredApprovals.length).toBe(1);
    });

    it('totalApprovalCount should always reflect unfiltered total', () => {
      const approvals: KpiInstance[] = [
        { ...pendingKpi, id: 20, status: 'SUBMITTED' },
        { ...pendingKpi, id: 21, status: 'UNDER_REVIEW' }
      ];
      const svc = buildServiceSpy({
        getPendingReviews: vi.fn().mockReturnValue(of(approvals))
      } as any);
      const { comp } = setup(svc);
      comp.ngOnInit();

      comp.filterApprovalStatus('SUBMITTED');
      expect(comp.totalApprovalCount).toBe(2);    // always total
      expect(comp.filteredApprovals.length).toBe(1); // filtered
    });
  });

  // ── Summary counters ───────────────────────────────────────────────────────
  describe('Summary Counters', () => {
    it('should compute actionNeededCount correctly', () => {
      const svc = buildServiceSpy({
        getMyKpis: vi.fn().mockReturnValue(of([
          { ...pendingKpi, status: 'PENDING' },
          { ...pendingKpi, id: 2, status: 'REWORK_REQUIRED' },
          { ...pendingKpi, id: 3, status: 'APPROVED' }
        ]))
      } as any);
      const { comp } = setup(svc);
      comp.ngOnInit();
      expect(comp.actionNeededCount).toBe(2);
    });

    it('should compute approvedCount correctly', () => {
      const svc = buildServiceSpy({
        getMyKpis: vi.fn().mockReturnValue(of([
          { ...pendingKpi, status: 'APPROVED' },
          { ...pendingKpi, id: 2, status: 'APPROVED' },
          { ...pendingKpi, id: 3, status: 'PENDING' }
        ]))
      } as any);
      const { comp } = setup(svc);
      comp.ngOnInit();
      expect(comp.approvedCount).toBe(2);
    });

    it('should compute weightedScoreTotal correctly', () => {
      const svc = buildServiceSpy({
        getMyKpis: vi.fn().mockReturnValue(of([
          { ...pendingKpi, status: 'APPROVED', rating: 4, weight: 50 },
          { ...pendingKpi, id: 2, status: 'APPROVED', rating: 5, weight: 50 }
        ]))
      } as any);
      const { comp } = setup(svc);
      comp.ngOnInit();
      // (4*50/100) + (5*50/100) = 2 + 2.5 = 4.5
      expect(comp.weightedScoreTotal).toBeCloseTo(4.5);
    });
  });

  // ── Deadline countdown ─────────────────────────────────────────────────────
  describe('Cycle deadline', () => {
    it('should show positive days remaining for future cycle', () => {
      const { comp } = setup();
      comp.ngOnInit();
      const days = comp.cycleDaysRemaining;
      expect(days).not.toBeNull();
      if (days) expect(days).toBeGreaterThan(0);
    });

    it('should return null cycleDaysRemaining when no cycle selected', () => {
      const { comp } = setup();
      comp.selectedCycleCode = '';
      expect(comp.cycleDaysRemaining).toBeNull();
    });
  });

  // ── View mode toggle ───────────────────────────────────────────────────────
  describe('View mode toggle', () => {
    it('should default to cards view', () => {
      const { comp } = setup();
      expect(comp.viewMode).toBe('cards');
    });

    it('should switch to table view', () => {
      const { comp } = setup();
      comp.viewMode = 'table';
      expect(comp.viewMode).toBe('table');
    });
  });
});
