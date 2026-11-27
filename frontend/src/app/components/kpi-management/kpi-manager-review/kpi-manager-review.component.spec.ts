import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { KpiManagerReviewComponent } from './kpi-manager-review.component';
import { ConfigurationService, KpiInstance } from '../../../services/configuration.service';

function makeKpi(overrides: Partial<KpiInstance> = {}): KpiInstance {
  return {
    id: 1, kpiName: 'Sprint Velocity', kpiCode: 'SV001', categoryName: 'Delivery',
    weight: 30, target: '10', frequency: 'SPRINT', status: 'SUBMITTED',
    actualValue: 9, comments: 'Almost there', justification: null,
    rating: null, reviewerComments: null,
    user: { id: 1, firstName: 'Jane', lastName: 'Smith', email: 'jane@example.invalid' },
    ...overrides
  };
}

const configServiceStub = {
  getPendingReviews: () => of([makeKpi(), makeKpi({ id: 2, categoryName: 'Quality', status: 'UNDER_REVIEW' })]),
  getEvidenceForKpi: () => of([]),
  submitManagerReview: () => of(makeKpi({ status: 'APPROVED', rating: 4 })),
  getTeamAssignmentFlow: () => of([]),
  getKpiStatusHistory: () => of([]),
  submitBulkManagerReview: () => of({ requested: 0, succeeded: 0, failed: 0, failures: [] })
};

function setup() {
  TestBed.configureTestingModule({
    imports: [KpiManagerReviewComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: ConfigurationService, useValue: configServiceStub }
    ]
  });
  const fixture = TestBed.createComponent(KpiManagerReviewComponent);
  const comp = fixture.componentInstance;
  return { fixture, comp };
}

describe('KpiManagerReviewComponent', () => {

  // ── Initialization ─────────────────────────
  describe('initialization', () => {
    it('should create', () => {
      const { comp } = setup();
      expect(comp).toBeTruthy();
    });

    it('should load pending reviews on init', () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(comp.allPendingKpis.length).toBe(2);
    });

    it('should extract categories from loaded KPIs', () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(comp.categories).toContain('Delivery');
      expect(comp.categories).toContain('Quality');
    });
  });

  // ── Search & Filtering ──────────────────────
  describe('search and filtering', () => {
    it('should filter by employee name', () => {
      const { comp } = setup();
      comp.ngOnInit();
      comp.searchTerm = 'jane';
      comp['applyFilters']();
      expect(comp.filteredPendingKpis.length).toBe(2); // both KPIs belong to Jane
    });

    it('should filter by status', () => {
      const { comp } = setup();
      comp.ngOnInit();
      comp.statusFilter = 'UNDER_REVIEW';
      comp['applyFilters']();
      expect(comp.filteredPendingKpis.length).toBe(1);
    });

    it('should filter by category', () => {
      const { comp } = setup();
      comp.ngOnInit();
      comp.categoryFilter = 'Delivery';
      comp['applyFilters']();
      expect(comp.filteredPendingKpis.length).toBe(1);
    });

    it('should show all when cleared', () => {
      const { comp } = setup();
      comp.ngOnInit();
      comp.statusFilter = 'UNDER_REVIEW';
      comp['applyFilters']();
      comp.clearFilters();
      expect(comp.filteredPendingKpis.length).toBe(2);
    });

    it('should combine search + status filter', () => {
      const { comp } = setup();
      comp.ngOnInit();
      comp.searchTerm = 'velocity';
      comp.statusFilter = 'SUBMITTED';
      comp['applyFilters']();
      expect(comp.filteredPendingKpis.length).toBe(1);
    });
  });

  // ── Progress tracking ───────────────────────
  describe('progress tracking', () => {
    it('should start with totalReviewed = 0', () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(comp.totalReviewed).toBe(0);
    });

    it('should calculate progress percentage', () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(comp.progressPercentage).toBe(0);
    });

    it('should track reviewed KPIs correctly', () => {
      const { comp } = setup();
      comp.ngOnInit();
      comp.reviewedKpis.add(1);
      comp.allPendingKpis = comp.allPendingKpis.filter(kpi => kpi.id !== 1);
      expect(comp.totalReviewed).toBe(1);
      expect(comp.progressPercentage).toBe(50); // 1 of 2
    });

    it('keeps completed review progress at 100% after the queue is empty', () => {
      const { comp } = setup();
      comp.reviewedKpis = new Set([1, 2]);
      comp.allPendingKpis = [];
      expect(comp.progressPercentage).toBe(100);
    });
  });

  // ── validateReview ──────────────────────────
  describe('validateReview()', () => {
    it('should fail for rating below 1', () => {
      const { comp } = setup();
      comp.reviewForm.rating = 0;
      const result = comp['validateReview']();
      expect(result.isValid).toBe(false);
    });

    it('should fail for rating above 5', () => {
      const { comp } = setup();
      comp.reviewForm.rating = 6;
      const result = comp['validateReview']();
      expect(result.isValid).toBe(false);
    });

    it('should pass for rating 1-5', () => {
      const { comp } = setup();
      [1, 2, 3, 4, 5].forEach(r => {
        comp.reviewForm.rating = r;
        comp.reviewForm.reviewerComments = 'Some comment';
        const result = comp['validateReview']();
        expect(result.isValid).toBe(true);
      });
    });

    it('should warn if extreme rating (1-2) without comments', () => {
      const { comp } = setup();
      comp.reviewForm.rating = 1;
      comp.reviewForm.reviewerComments = '';
      const result = comp['validateReview']();
      expect(result.isValid).toBe(true); // still valid, just warns
      expect(result.warnings.length).toBeGreaterThan(0);
    });

    it('should warn if extreme rating (4-5) without comments', () => {
      const { comp } = setup();
      comp.reviewForm.rating = 5;
      comp.reviewForm.reviewerComments = '';
      const result = comp['validateReview']();
      expect(result.isValid).toBe(true);
      expect(result.warnings.length).toBeGreaterThan(0);
    });

    it('should NOT warn for mid-range rating (3) without comments', () => {
      const { comp } = setup();
      comp.reviewForm.rating = 3;
      comp.reviewForm.reviewerComments = '';
      const result = comp['validateReview']();
      expect(result.isValid).toBe(true);
      expect(result.warnings.length).toBe(0);
    });
  });

  // ── Modal behavior ──────────────────────────
  describe('modal behavior', () => {
    it('should open modal with selected KPI', () => {
      const { comp } = setup();
      const kpi = makeKpi();
      comp.openReview(kpi);
      expect(comp.showModal).toBe(true);
      expect(comp.selectedKpi).toBe(kpi);
    });

    it('should reset form on open', () => {
      const { comp } = setup();
      comp.reviewForm = { rating: 5, reviewerComments: 'Old comment', decision: 'REJECT' };
      comp.openReview(makeKpi());
      expect(comp.reviewForm.rating).toBe(3);
      expect(comp.reviewForm.reviewerComments).toBe('');
      expect(comp.reviewForm.decision).toBe('APPROVE');
    });

    it('should close modal and reset', () => {
      const { comp } = setup();
      comp.openReview(makeKpi());
      comp.closeModal();
      expect(comp.showModal).toBe(false);
      expect(comp.selectedKpi).toBeNull();
    });
  });

  // ── ratingLabel ─────────────────────────────
  describe('ratingLabel()', () => {
    const cases: [number, string][] = [
      [1, 'Far Below Expectations'],
      [2, 'Below Expectations'],
      [3, 'Meets Expectations'],
      [4, 'Exceeds Expectations'],
      [5, 'Outstanding']
    ];
    cases.forEach(([rating, label]) => {
      it(`should return "${label}" for rating ${rating}`, () => {
        const { comp } = setup();
        expect(comp.ratingLabel(rating)).toBe(label);
      });
    });
  });

  // ── weightedScorePreview ────────────────────
  describe('weightedScorePreview()', () => {
    it('should calculate weighted score correctly', () => {
      const { comp } = setup();
      expect(comp.weightedScorePreview(25, 4)).toBeCloseTo(1.0);
    });

    it('should return 0 when weight is undefined', () => {
      const { comp } = setup();
      expect(comp.weightedScorePreview(undefined, 4)).toBe(0);
    });
  });

  // ── CSV export ──────────────────────────────
  describe('downloadAsCSV()', () => {
    it('should not throw when called with no filtered KPIs', () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(() => comp.downloadAsCSV()).not.toThrow();
    });
  });

  // ── employeeName ────────────────────────────
  describe('employeeName()', () => {
    it('should concatenate first and last name', () => {
      const { comp } = setup();
      const kpi = makeKpi({ user: { firstName: 'Jane', lastName: 'Smith' } });
      expect(comp.employeeName(kpi)).toBe('Jane Smith');
    });

    it('should handle snake_case field names', () => {
      const { comp } = setup();
      const kpi = makeKpi({ user: { first_name: 'Bob', last_name: 'Jones' } as any });
      expect(comp.employeeName(kpi)).toBe('Bob Jones');
    });

    it('should return Unknown for missing user', () => {
      const { comp } = setup();
      const kpi = makeKpi({ user: undefined });
      expect(comp.employeeName(kpi)).toBe('Unknown');
    });
  });

  // ── Bulk select ─────────────────────────────
  describe('bulk selection', () => {
    it('should toggle individual KPI selection', () => {
      const { comp } = setup();
      comp.ngOnInit();
      comp.toggleBulkSelect(1);
      expect(comp.selectedForBulk.has(1)).toBe(true);
      comp.toggleBulkSelect(1);
      expect(comp.selectedForBulk.has(1)).toBe(false);
    });

    it('should select all filtered KPIs', () => {
      const { comp } = setup();
      comp.ngOnInit();
      comp.toggleSelectAll();
      expect(comp.selectedForBulk.size).toBe(comp.filteredPendingKpis.length);
    });

    it('should deselect all when all are selected', () => {
      const { comp } = setup();
      comp.ngOnInit();
      comp.toggleSelectAll(); // select all
      comp.toggleSelectAll(); // deselect all
      expect(comp.selectedForBulk.size).toBe(0);
    });
  });
});
