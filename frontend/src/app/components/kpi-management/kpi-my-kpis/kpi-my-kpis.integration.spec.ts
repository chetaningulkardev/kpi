import { vi } from 'vitest';
/**
 * Integration Tests: kpi-my-kpis Component
 * Tests full HTTP lifecycle, submission, cache, and card/table views.
 */
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { KpiMyKpisComponent } from './kpi-my-kpis.component';
import { ConfigurationService, KpiInstance, ReviewCycle } from '../../../services/configuration.service';
import { createSpyObj } from '../../../../testing/spy-utils';
import { ConfirmService } from '../../../services/confirm.service';
import { StubConfirmService } from '../../../testing/stub-confirm.service';

// ─── Helpers ──────────────────────────────────────────────────────────────────
const cycle: ReviewCycle = {
  id: 1, code: 'Q4-2026', name: 'Q4 Review', startDate: '2026-10-01',
  endDate: '2026-12-31', statusCode: 'OPEN'
};

function makeKpi(o: Partial<KpiInstance> = {}): KpiInstance {
  return {
    id: 1, kpiName: 'Sprint Velocity', kpiCode: 'SV001', categoryName: 'Delivery',
    weight: 30, target: '50', frequency: 'SPRINT', status: 'PENDING',
    actualValue: null, comments: null, justification: null, rating: null, reviewerComments: null, ...o
  };
}

function buildSpy(o: any = {}): any {
  const spy = createSpyObj<ConfigurationService>([
    'getReviewCycles', 'getMyKpis', 'getEvidenceForKpi', 'submitSelfAssessment', 'uploadEvidenceFile'
  ]);
  spy.getReviewCycles.mockReturnValue(of([cycle]));
  spy.getMyKpis.mockReturnValue(of([
    makeKpi({ id: 1, status: 'PENDING' }),
    makeKpi({ id: 2, kpiName: 'Code Coverage', kpiCode: 'CC001', categoryName: 'Quality', weight: 20, status: 'SUBMITTED', actualValue: 85 })
  ]));
  spy.getEvidenceForKpi.mockReturnValue(of([]));
  spy.submitSelfAssessment.mockReturnValue(of(makeKpi({ id: 1, status: 'SUBMITTED', actualValue: 45 })));
  spy.uploadEvidenceFile.mockReturnValue(of({ id: 1, title: 'ev.pdf', evidenceType: 'FILE', description: '', contentUrl: '', fileType: 'pdf', fileSizeBytes: 0 }));
  Object.assign(spy, o);
  return spy;
}

function setup(spy?: any) {
  const svc = spy ?? buildSpy();
  const confirmStub = new StubConfirmService();
  TestBed.configureTestingModule({
    imports: [KpiMyKpisComponent],
    providers: [
      provideHttpClient(), provideHttpClientTesting(),
      { provide: ConfigurationService, useValue: svc },
      { provide: ConfirmService, useValue: confirmStub }
    ]
  });
  const fixture = TestBed.createComponent(KpiMyKpisComponent);
  const comp = fixture.componentInstance;
  return { fixture, comp, svc, confirmStub };
}

/**
 * `submitAssessment()`/`submitAllAssessments()` only open the in-app confirmation
 * popup; the HTTP call runs when the user confirms. These helpers do both steps.
 *
 * Awaited because submission now pauses on ConfirmService when a recommended
 * field is missing.
 */
async function submitAndConfirm(comp: KpiMyKpisComponent): Promise<void> {
  await comp.submitAssessment();
  if (comp.confirmPopup.visible) {
    comp.confirmPopupAction();
  }
}

function submitAllAndConfirm(comp: KpiMyKpisComponent): void {
  comp.submitAllAssessments();
  if (comp.confirmPopup.visible) {
    comp.confirmPopupAction();
  }
}

describe('KpiMyKpis Integration Tests', () => {

  afterEach(() => TestBed.resetTestingModule());

  // ── Data Loading ───────────────────────────────────────────────────────────
  describe('Data Loading', () => {
    it('should load cycles on init', () => {
      const { comp, svc } = setup();
      comp.ngOnInit();
      expect(svc.getReviewCycles).toHaveBeenCalledTimes(1);
      expect(comp.cycles.length).toBe(1);
    });

    it('should auto-select first cycle and load KPIs', () => {
      const { comp, svc } = setup();
      comp.ngOnInit();
      expect(comp.selectedCycleCode).toBe('Q4-2026');
      expect(svc.getMyKpis).toHaveBeenCalledWith('Q4-2026');
      expect(comp.allMyKpis.length).toBe(2);
    });

    it('should populate filteredKpis on load', () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(comp.filteredKpis.length).toBe(2);
    });

    it('should extract unique categories', () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(comp.categories).toContain('Delivery');
      expect(comp.categories).toContain('Quality');
    });

    it('should reload KPIs when cycle changes', () => {
      const { comp, svc } = setup();
      comp.ngOnInit();
      svc.getMyKpis.mockClear();
      comp.selectedCycleCode = 'Q4-2026';
      comp.onCycleChange();
      expect(svc.getMyKpis).toHaveBeenCalledTimes(1);
    });

    it('should show page error on cycle load failure', () => {
      const svc = buildSpy({
        getReviewCycles: vi.fn().mockReturnValue(throwError(() => new Error('fail')))
      });
      const { comp } = setup(svc);
      comp.ngOnInit();
      expect(comp.pageMsg).toBeTruthy();
      expect(comp.pageMsgIsError).toBe(true);
    });
  });

  // ── Filtering & Search ─────────────────────────────────────────────────────
  describe('Filtering & Search', () => {
    it('should filter by status', () => {
      const { comp } = setup();
      comp.ngOnInit();
      comp.statusFilter = 'SUBMITTED';
      comp.onStatusFilterChange();
      expect(comp.filteredKpis.length).toBe(1);
      expect(comp.filteredKpis[0].status).toBe('SUBMITTED');
    });

    it('should filter by category', () => {
      const { comp } = setup();
      comp.ngOnInit();
      comp.categoryFilter = 'Delivery';
      comp.onCategoryFilterChange();
      expect(comp.filteredKpis.length).toBe(1);
    });

    it('should search by KPI name', () => {
      const { comp } = setup();
      comp.ngOnInit();
      comp.searchTerm = 'sprint';
      comp.onSearchChange();
      expect(comp.filteredKpis.length).toBe(1);
      expect(comp.filteredKpis[0].kpiName).toBe('Sprint Velocity');
    });

    it('should search by KPI code', () => {
      const { comp } = setup();
      comp.ngOnInit();
      comp.searchTerm = 'CC001';
      comp.onSearchChange();
      expect(comp.filteredKpis.length).toBe(1);
    });

    it('should combine search + status + category filters', () => {
      const { comp } = setup();
      comp.ngOnInit();
      comp.searchTerm = 'sprint';
      comp.statusFilter = 'PENDING';
      comp.categoryFilter = 'Delivery';
      comp.onSearchChange();
      expect(comp.filteredKpis.length).toBe(1);
    });

    it('should show all KPIs after clearFilters()', () => {
      const { comp } = setup();
      comp.ngOnInit();
      comp.statusFilter = 'SUBMITTED';
      comp.onStatusFilterChange();
      expect(comp.filteredKpis.length).toBe(1);
      comp.clearFilters();
      expect(comp.filteredKpis.length).toBe(2);
    });
  });

  // ── Assessment Submission ──────────────────────────────────────────────────
  describe('Assessment Submission', () => {
    it('should call submitSelfAssessment with correct payload', async () => {
      const { comp, svc } = setup();
      comp.ngOnInit();

      comp.openAssess(makeKpi({ id: 1 }));
      comp.assessForm.actualValue = 45;
      comp.assessForm.comments = 'Completed';

      await submitAndConfirm(comp);

      expect(svc.submitSelfAssessment).toHaveBeenCalledWith(
        1,
        expect.objectContaining({ actualValue: 45, comments: 'Completed' })
      );
    });

    it('should update KPI in allMyKpis after submission', async () => {
      const { comp } = setup();
      comp.ngOnInit();

      comp.openAssess(makeKpi({ id: 1 }));
      comp.assessForm.actualValue = 45;
      await submitAndConfirm(comp);

      const updated = comp.allMyKpis.find(k => k.id === 1);
      expect(updated?.status).toBe('SUBMITTED');
    });

    it('should NOT submit when actualValue is null', async () => {
      const { comp, svc } = setup();
      comp.ngOnInit();

      comp.openAssess(makeKpi({ id: 1 }));
      comp.assessForm.actualValue = null;
      await comp.submitAssessment();

      expect(svc.submitSelfAssessment).not.toHaveBeenCalled();
      expect(comp.formMsgIsError).toBe(true);
    });

    it('should NOT submit when actualValue is negative', async () => {
      const { comp, svc } = setup();
      comp.ngOnInit();

      comp.openAssess(makeKpi({ id: 1 }));
      comp.assessForm.actualValue = -1;
      await comp.submitAssessment();

      expect(svc.submitSelfAssessment).not.toHaveBeenCalled();
    });

    it('should upload evidence file after successful submission', async () => {
      const { comp, svc } = setup();
      comp.ngOnInit();

      comp.openAssess(makeKpi({ id: 1, weight: 25 }));
      comp.assessForm.actualValue = 45;
      comp.assessForm.evidenceFile = new File(['data'], 'report.pdf', { type: 'application/pdf' });

      await submitAndConfirm(comp);

      expect(svc.uploadEvidenceFile).toHaveBeenCalledWith(1, expect.any(File), expect.any(String));
    });

    it('should show warning when comments missing and allow override', async () => {
      const { comp, svc, confirmStub } = setup();
      comp.ngOnInit();

      comp.openAssess(makeKpi({ id: 1 }));
      comp.assessForm.actualValue = 45;
      comp.assessForm.comments = '';

      await submitAndConfirm(comp);

      // Asked, then allowed through — a recommendation, not a rule.
      expect(confirmStub.asked.length).toBeGreaterThan(0);
      expect(svc.submitSelfAssessment).toHaveBeenCalled();
    });

    it('should cancel submission when user declines warning', async () => {
      const { comp, svc, confirmStub } = setup();
      comp.ngOnInit();

      comp.openAssess(makeKpi({ id: 1 }));
      comp.assessForm.actualValue = 45;
      comp.assessForm.comments = '';

      confirmStub.answer = { confirmed: false };
      await comp.submitAssessment();

      expect(svc.submitSelfAssessment).not.toHaveBeenCalled();
    });

    it('should set error on API failure', async () => {
      const svc = buildSpy({
        submitSelfAssessment: vi.fn().mockReturnValue(
          throwError(() => ({ error: { message: 'Server error' } }))
        )
      });
      const { comp } = setup(svc);
      comp.ngOnInit();

      comp.openAssess(makeKpi({ id: 1 }));
      comp.assessForm.actualValue = 45;
      await submitAndConfirm(comp);

      expect(comp.formMsg).toContain('Server error');
      expect(comp.formMsgIsError).toBe(true);
    });
  });

  // ── Bulk Submit ────────────────────────────────────────────────────────────
  describe('Bulk Submit', () => {
    it('should block bulk submit when no pending KPIs have actualValue', () => {
      const { comp, svc } = setup();
      comp.ngOnInit();
      // PENDING kpi has no actualValue
      comp.submitAllAssessments();
      expect(comp.pageMsg).toContain('Actual Value');
      expect(svc.submitSelfAssessment).not.toHaveBeenCalled();
    });

    it('should submit pending KPIs that have actualValue', () => {
      const svc = buildSpy({
        getMyKpis: vi.fn().mockReturnValue(of([
          makeKpi({ id: 1, status: 'PENDING', actualValue: 40 }),
          makeKpi({ id: 2, status: 'SUBMITTED', actualValue: 85 })
        ]))
      });
      const { comp } = setup(svc);
      comp.ngOnInit();

      vi.spyOn(window, 'confirm').mockReturnValue(true); // confirmation popup
      submitAllAndConfirm(comp);

      // Only 1 PENDING with actualValue
      expect(svc.submitSelfAssessment).toHaveBeenCalledTimes(1);
    });
  });

  // ── Modal ──────────────────────────────────────────────────────────────────
  describe('Modal', () => {
    it('should open modal and populate form from kpi', () => {
      const { comp } = setup();
      comp.ngOnInit();

      const kpi = makeKpi({ id: 1, actualValue: 42, comments: 'Done', justification: 'All good' });
      comp.openAssess(kpi);
      expect(comp.showModal).toBe(true);
      expect(comp.assessForm.actualValue).toBe(42);
      expect(comp.assessForm.comments).toBe('Done');
    });

    it('should close modal and clear all form state', () => {
      const { comp } = setup();
      comp.openAssess(makeKpi());
      comp.assessForm.actualValue = 99;
      comp.closeModal();
      expect(comp.showModal).toBe(false);
      expect(comp.selectedKpi).toBeNull();
      expect(comp.assessForm.actualValue).toBeNull();
      expect(comp.assessForm.evidenceFile).toBeNull();
    });
  });

  // ── Summary Counts ─────────────────────────────────────────────────────────
  describe('Summary Counts', () => {
    it('should count totalWeight correctly', () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(comp.totalWeight).toBe(50); // 30 + 20
    });

    it('should count pendingCount correctly', () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(comp.pendingCount).toBe(1);
    });

    it('should count submittedCount correctly', () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(comp.submittedCount).toBe(1);
    });

    it('should count actionNeededCount (PENDING + REWORK)', () => {
      const svc = buildSpy({
        getMyKpis: vi.fn().mockReturnValue(of([
          makeKpi({ id: 1, status: 'PENDING' }),
          makeKpi({ id: 2, status: 'REWORK_REQUIRED' }),
          makeKpi({ id: 3, status: 'APPROVED' })
        ]))
      });
      const { comp } = setup(svc);
      comp.ngOnInit();
      expect(comp.actionNeededCount).toBe(2);
    });
  });

  // ── View Toggle ────────────────────────────────────────────────────────────
  describe('View Toggle', () => {
    it('should default to card view (tableView=false)', () => {
      const { comp } = setup();
      expect(comp.tableView).toBe(false);
    });

    it('should switch to table view', () => {
      const { comp } = setup();
      comp.tableView = true;
      expect(comp.tableView).toBe(true);
    });

    it('should switch back to card view', () => {
      const { comp } = setup();
      comp.tableView = true;
      comp.tableView = false;
      expect(comp.tableView).toBe(false);
    });
  });

  // ── Deadline ───────────────────────────────────────────────────────────────
  describe('Cycle Deadline', () => {
    it('should return positive days for future cycle', () => {
      const { comp } = setup();
      comp.ngOnInit();
      const days = comp.cycleDaysRemaining;
      expect(days).not.toBeNull();
      if (days) expect(days).toBeGreaterThan(0);
    });

    it('should return null when no cycle selected', () => {
      const { comp } = setup();
      comp.selectedCycleCode = '';
      expect(comp.cycleDaysRemaining).toBeNull();
    });
  });

  // ── File Validation ────────────────────────────────────────────────────────
  describe('Evidence File Validation', () => {
    it('should reject disallowed file types', () => {
      const { comp } = setup();
      const file = new File(['x'], 'test.exe', { type: 'application/exe' });
      const event = { target: { files: [file], value: '' } } as unknown as Event;
      comp.onEvidenceFileSelected(event);
      expect(comp.assessForm.evidenceFile).toBeNull();
      expect(comp.formMsgIsError).toBe(true);
    });

    it('should accept PDF files', () => {
      const { comp } = setup();
      const file = new File(['x'], 'report.pdf', { type: 'application/pdf' });
      const event = { target: { files: [file], value: '' } } as unknown as Event;
      comp.onEvidenceFileSelected(event);
      expect(comp.assessForm.evidenceFile).toBeTruthy();
    });

    it('should auto-set evidenceTitle from filename', () => {
      const { comp } = setup();
      const file = new File(['x'], 'q4_report.pdf', { type: 'application/pdf' });
      const event = { target: { files: [file], value: '' } } as unknown as Event;
      comp.assessForm.evidenceTitle = '';
      comp.onEvidenceFileSelected(event);
      expect(comp.assessForm.evidenceTitle).toBe('q4_report.pdf');
    });
  });

  // ── capAt100 ───────────────────────────────────────────────────────────────
  describe('capAt100()', () => {
    it('should cap value at 100', () => {
      const { comp } = setup();
      expect(comp.capAt100(150)).toBe(100);
    });

    it('should pass through values under 100', () => {
      const { comp } = setup();
      expect(comp.capAt100(75)).toBe(75);
    });

    it('should return exactly 100 for 100', () => {
      const { comp } = setup();
      expect(comp.capAt100(100)).toBe(100);
    });
  });
});

