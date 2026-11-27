import { vi } from 'vitest';
/**
 * Integration Tests: KPI Manager Review Component
 */
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { KpiManagerReviewComponent } from './kpi-manager-review.component';
import { ConfigurationService, KpiInstance, KpiEvidence } from '../../../services/configuration.service';
import { createSpyObj } from '../../../../testing/spy-utils';
import { ConfirmService } from '../../../services/confirm.service';
import { StubConfirmService } from '../../../testing/stub-confirm.service';

const makeKpi = (o: Partial<KpiInstance> = {}): KpiInstance => ({
  id: 1, kpiName: 'Code Quality', kpiCode: 'CQ001', categoryName: 'Engineering',
  weight: 25, target: '100', frequency: 'QUARTERLY', status: 'SUBMITTED',
  actualValue: 85, comments: 'On track', justification: null,
  rating: null, reviewerComments: null,
  user: { id: 1, firstName: 'Alice', lastName: 'Smith', email: 'alice@example.invalid' }, ...o
});

const makeEvidence = (): KpiEvidence => ({
  id: 1, evidenceType: 'FILE', title: 'proof.pdf', description: '',
  contentUrl: 'http://example.com/proof.pdf', fileType: 'pdf', fileSizeBytes: 1024
});

function buildSpy(o: any = {}): any {
  const spy = createSpyObj<ConfigurationService>([
    'getPendingReviews', 'getEvidenceForKpi', 'submitManagerReview', 'getTeamAssignmentFlow',
    'getKpiStatusHistory', 'submitBulkManagerReview'
  ]);
  spy.getPendingReviews.mockReturnValue(of([makeKpi(), makeKpi({ id: 2, categoryName: 'Quality', status: 'UNDER_REVIEW' })]));
  spy.getEvidenceForKpi.mockReturnValue(of([]));
  spy.submitManagerReview.mockReturnValue(of(makeKpi({ status: 'APPROVED', rating: 4 })));
  spy.getTeamAssignmentFlow.mockReturnValue(of([]));
  spy.getKpiStatusHistory.mockReturnValue(of([]));
  spy.submitBulkManagerReview.mockReturnValue(of({ requested: 0, succeeded: 0, failed: 0, failures: [] }));
  Object.assign(spy, o);
  return spy;
}

/**
 * `submitReview()` only opens the in-app confirmation popup; the HTTP call runs
 * when the user confirms. This helper performs both steps.
 *
 * Awaited because submission now pauses on ConfirmService when an extreme
 * rating carries no comment.
 */
async function submitAndConfirm(comp: KpiManagerReviewComponent): Promise<void> {
  await comp.submitReview();
  if (comp.confirmPopup.visible) {
    comp.confirmPopupAction();
  }
}

function setup(spy?: any) {
  const svc = spy ?? buildSpy();
  const confirmStub = new StubConfirmService();
  TestBed.configureTestingModule({
    imports: [KpiManagerReviewComponent],
    providers: [
      provideHttpClient(), provideHttpClientTesting(),
      provideRouter([]),
      { provide: ConfigurationService, useValue: svc },
      { provide: ConfirmService, useValue: confirmStub }
    ]
  });
  const fixture = TestBed.createComponent(KpiManagerReviewComponent);
  const comp = fixture.componentInstance;
  return { fixture, comp, svc, confirmStub };
}

describe('KpiManagerReview Integration Tests', () => {

  afterEach(() => TestBed.resetTestingModule());

  // ── Loading ────────────────────────────────────────────────────────────────
  describe('Data Loading', () => {
    it('should call getPendingReviews on init', () => {
      const { comp, svc } = setup();
      comp.ngOnInit();
      expect(svc.getPendingReviews).toHaveBeenCalledTimes(1);
    });

    it('should populate allPendingKpis and filteredPendingKpis', () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(comp.allPendingKpis.length).toBe(2);
      expect(comp.filteredPendingKpis.length).toBe(2);
    });

    it('should extract categories correctly', () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(comp.categories).toContain('Engineering');
      expect(comp.categories).toContain('Quality');
    });

    it('should handle loading error gracefully', () => {
      const svc = buildSpy({
        getPendingReviews: vi.fn().mockReturnValue(throwError(() => new Error('Network error')))
      });
      const { comp } = setup(svc);
      comp.ngOnInit();
      expect(comp.allPendingKpis.length).toBe(0);
      expect(comp.pageMsg).toBeTruthy();
    });
  });

  // ── Review Submission ──────────────────────────────────────────────────────
  describe('Review Submission', () => {
    it('should call submitManagerReview with rating and comments', async () => {
      const { comp, svc } = setup();
      comp.ngOnInit();

      const kpi = makeKpi();
      comp.openReview(kpi);
      comp.reviewForm.rating = 4;
      comp.reviewForm.reviewerComments = 'Good work';

      await submitAndConfirm(comp);

      expect(svc.submitManagerReview).toHaveBeenCalledWith(
        1,
        expect.objectContaining({ rating: 4, reviewerComments: 'Good work' })
      );
    });

    it('should remove KPI from list after successful review', async () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(comp.allPendingKpis.length).toBe(2);

      comp.openReview(makeKpi());
      comp.reviewForm.rating = 3;
      await submitAndConfirm(comp);

      expect(comp.allPendingKpis.length).toBe(1);
    });

    it('should track reviewed KPI id', async () => {
      const { comp } = setup();
      comp.ngOnInit();

      comp.openReview(makeKpi({ id: 1 }));
      comp.reviewForm.rating = 5;
      await submitAndConfirm(comp);

      expect(comp.reviewedKpis.has(1)).toBe(true);
    });

    it('should NOT submit when rating is 0', async () => {
      const { comp, svc } = setup();
      comp.ngOnInit();

      comp.openReview(makeKpi());
      comp.reviewForm.rating = 0;
      await comp.submitReview();

      expect(svc.submitManagerReview).not.toHaveBeenCalled();
      expect(comp.formMsg).toBeTruthy();
    });

    it('should warn for extreme ratings without comments', async () => {
      const { comp, svc, confirmStub } = setup();
      comp.ngOnInit();

      confirmStub.answer = { confirmed: false };
      comp.openReview(makeKpi());
      comp.reviewForm.rating = 1;
      comp.reviewForm.reviewerComments = '';
      await comp.submitReview();

      // The reviewer is asked, and the wording names the problem rather than
      // just saying "are you sure".
      expect(confirmStub.asked.length).toBe(1);
      expect(confirmStub.lastAsked?.message).toContain('commenting is recommended');

      // Declining must stop the submission, not merely warn about it.
      expect(svc.submitManagerReview).not.toHaveBeenCalled();
    });

    it('should load evidence when modal opens', () => {
      const svc = buildSpy({
        getEvidenceForKpi: vi.fn().mockReturnValue(of([makeEvidence()]))
      });
      const { comp } = setup(svc);
      comp.openReview(makeKpi());

      expect(svc.getEvidenceForKpi).toHaveBeenCalledWith(1);
      expect(comp.evidenceForSelected.length).toBe(1);
    });

    it('should show page success message after review', async () => {
      const { comp } = setup();
      comp.ngOnInit();

      comp.openReview(makeKpi());
      comp.reviewForm.rating = 3;
      await submitAndConfirm(comp);

      expect(comp.pageMsg).toContain('Review submitted');
    });

    it('should show error on submitManagerReview failure', async () => {
      const svc = buildSpy({
        submitManagerReview: vi.fn().mockReturnValue(
          throwError(() => ({ error: { message: 'Server error' } }))
        )
      });
      const { comp } = setup(svc);
      comp.ngOnInit();

      comp.openReview(makeKpi());
      comp.reviewForm.rating = 3;
      await submitAndConfirm(comp);

      expect(comp.formMsg).toContain('Server error');
    });
  });

  // ── Filtering ──────────────────────────────────────────────────────────────
  describe('Search & Filter', () => {
    it('should filter by employee name', () => {
      const { comp } = setup();
      comp.ngOnInit();

      comp.searchTerm = 'alice';
      comp.onSearchChange();
      expect(comp.filteredPendingKpis.length).toBe(2); // both KPIs have user Alice
    });

    it('should filter by status', () => {
      const { comp } = setup();
      comp.ngOnInit();

      comp.statusFilter = 'UNDER_REVIEW';
      comp.onStatusFilterChange();
      expect(comp.filteredPendingKpis.length).toBe(1);
    });

    it('should filter by category', () => {
      const { comp } = setup();
      comp.ngOnInit();

      comp.categoryFilter = 'Engineering';
      comp.onCategoryFilterChange();
      expect(comp.filteredPendingKpis.length).toBe(1);
    });

    it('should combine filters', () => {
      const { comp } = setup();
      comp.ngOnInit();

      comp.statusFilter = 'SUBMITTED';
      comp.categoryFilter = 'Engineering';
      comp.onStatusFilterChange();
      expect(comp.filteredPendingKpis.length).toBe(1);
    });

    it('clearFilters() restores full list', () => {
      const { comp } = setup();
      comp.ngOnInit();

      comp.statusFilter = 'UNDER_REVIEW';
      comp.onStatusFilterChange();
      expect(comp.filteredPendingKpis.length).toBe(1);

      comp.clearFilters();
      expect(comp.filteredPendingKpis.length).toBe(2);
    });
  });

  // ── Progress tracking ──────────────────────────────────────────────────────
  describe('Progress Tracking', () => {
    it('should update progress after review', async () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(comp.progressPercentage).toBe(0);

      comp.openReview(makeKpi({ id: 1 }));
      comp.reviewForm.rating = 4;
      await submitAndConfirm(comp);

      expect(comp.totalReviewed).toBe(1);
    });
  });

  // ── Bulk selection ─────────────────────────────────────────────────────────
  describe('Bulk Selection', () => {
    it('should select all when none selected', () => {
      const { comp } = setup();
      comp.ngOnInit();

      comp.toggleSelectAll();
      expect(comp.selectedForBulk.size).toBe(2);
    });

    it('should deselect all when all already selected', () => {
      const { comp } = setup();
      comp.ngOnInit();

      comp.toggleSelectAll();
      comp.toggleSelectAll();
      expect(comp.selectedForBulk.size).toBe(0);
    });

    it('should toggle individual KPI selection', () => {
      const { comp } = setup();
      comp.ngOnInit();

      comp.toggleBulkSelect(1);
      expect(comp.selectedForBulk.has(1)).toBe(true);
      comp.toggleBulkSelect(1);
      expect(comp.selectedForBulk.has(1)).toBe(false);
    });
  });

  // ── Modal ──────────────────────────────────────────────────────────────────
  describe('Modal', () => {
    it('should open modal and set selectedKpi', () => {
      const { comp } = setup();
      const kpi = makeKpi();
      comp.openReview(kpi);
      expect(comp.showModal).toBe(true);
      expect(comp.selectedKpi).toBe(kpi);
    });

    it('should reset form to defaults on open', () => {
      const { comp } = setup();
      comp.reviewForm = { rating: 5, reviewerComments: 'old comment', decision: 'APPROVE' };
      comp.openReview(makeKpi());
      expect(comp.reviewForm.rating).toBe(3);
      expect(comp.reviewForm.reviewerComments).toBe('');
    });

    it('should close and clear state', () => {
      const { comp } = setup();
      comp.openReview(makeKpi());
      comp.closeModal();
      expect(comp.showModal).toBe(false);
      expect(comp.selectedKpi).toBeNull();
      expect(comp.evidenceForSelected.length).toBe(0);
    });
  });

  // ── CSV Export ─────────────────────────────────────────────────────────────
  describe('CSV Export', () => {
    it('downloadAsCSV() should not throw errors', () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(() => comp.downloadAsCSV()).not.toThrow();
    });

    it('should show success message after CSV download', () => {
      const { comp } = setup();
      comp.ngOnInit();
      comp.downloadAsCSV();
      expect(comp.pageMsg).toContain('CSV');
    });
  });
});

