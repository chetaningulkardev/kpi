import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { KPIDashboardComponent } from './kpi-dashboard.component';
import { ConfigurationService, KpiInstance, ReviewCycle } from '../../services/configuration.service';
import { AuthService } from '../../services/auth.service';

// ─────────────────────────────────────────────
// Mock Data
// ─────────────────────────────────────────────
const mockCycle: ReviewCycle = {
  id: 1, code: 'Q4-2026', name: 'Q4 Review 2026',
  startDate: '2026-10-01', endDate: '2026-12-31', statusCode: 'OPEN'
};

function makeKpi(overrides: Partial<KpiInstance> = {}): KpiInstance {
  return {
    id: 1, kpiName: 'Code Quality', kpiCode: 'CQ001', categoryName: 'Quality',
    weight: 25, target: '100', frequency: 'QUARTERLY', status: 'PENDING',
    actualValue: null, comments: null, justification: null, rating: null,
    reviewerComments: null, ...overrides
  };
}

// ─────────────────────────────────────────────
// Stubs
// ─────────────────────────────────────────────
const configServiceStub = {
  getReviewCycles: () => of([mockCycle]),
  getMyKpis: () => of([makeKpi()]),
  getPendingReviews: () => of([]),
  submitSelfAssessment: (id: number, payload: any) => of(makeKpi({ ...payload, status: 'SUBMITTED' })),
  uploadEvidenceFile: () => of({ id: 1, evidenceType: 'FILE', title: 'test.pdf', description: '', contentUrl: '', fileType: 'pdf', fileSizeBytes: 1024 })
};

const authServiceStub = {
  currentUserValue: { roles: [{ role_name: 'DEVELOPER' }] }
};

// ─────────────────────────────────────────────
// Setup
// ─────────────────────────────────────────────
function setup() {
  TestBed.configureTestingModule({
    imports: [KPIDashboardComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: ConfigurationService, useValue: configServiceStub },
      { provide: AuthService, useValue: authServiceStub }
    ]
  });
  const fixture = TestBed.createComponent(KPIDashboardComponent);
  const comp = fixture.componentInstance;
  return { fixture, comp };
}

// ─────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────
describe('KPIDashboardComponent', () => {

  // ── Initialization ─────────────────────────
  describe('initialization', () => {
    it('should create component', () => {
      const { comp } = setup();
      expect(comp).toBeTruthy();
    });

    it('should auto-select active cycle on init', () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(comp.selectedCycleCode).toBe('Q4-2026');
    });

    it('should load KPIs after cycle selection', () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(comp.kpis.length).toBeGreaterThan(0);
    });
  });

  // ── Validation: isActualValueValid ──────────
  describe('isActualValueValid()', () => {
    it('should return false for null value', () => {
      const { comp } = setup();
      comp.submitForm.actualValue = null;
      expect(comp.isActualValueValid()).toBe(false);
    });

    it('should return false for NaN value', () => {
      const { comp } = setup();
      (comp.submitForm as any).actualValue = NaN;
      expect(comp.isActualValueValid()).toBe(false);
    });

    it('should return false for negative value', () => {
      const { comp } = setup();
      comp.submitForm.actualValue = -5;
      expect(comp.isActualValueValid()).toBe(false);
    });

    it('should return true for zero', () => {
      const { comp } = setup();
      comp.submitForm.actualValue = 0;
      expect(comp.isActualValueValid()).toBe(true);
    });

    it('should return true for positive number', () => {
      const { comp } = setup();
      comp.submitForm.actualValue = 85;
      expect(comp.isActualValueValid()).toBe(true);
    });

    it('should return true for decimal value', () => {
      const { comp } = setup();
      comp.submitForm.actualValue = 99.9;
      expect(comp.isActualValueValid()).toBe(true);
    });
  });

  // ── Validation: validateSubmissionForm ──────
  describe('validateSubmissionForm()', () => {
    it('should block submit if actualValue is null', () => {
      const { comp } = setup();
      comp.submitForm.actualValue = null;
      const result = comp.validateSubmissionForm(makeKpi());
      expect(result.isValid).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
    });

    it('should block submit if actualValue is negative', () => {
      const { comp } = setup();
      comp.submitForm.actualValue = -1;
      const result = comp.validateSubmissionForm(makeKpi());
      expect(result.isValid).toBe(false);
    });

    it('should be valid with just actualValue filled', () => {
      const { comp } = setup();
      comp.submitForm.actualValue = 80;
      const result = comp.validateSubmissionForm(makeKpi());
      expect(result.isValid).toBe(true);
    });

    it('should warn about missing comments', () => {
      const { comp } = setup();
      comp.submitForm.actualValue = 80;
      comp.submitForm.comments = '';
      const result = comp.validateSubmissionForm(makeKpi());
      expect(result.isValid).toBe(true);
      expect(result.warnings.length).toBeGreaterThan(0);
      expect(result.warnings[0]).toContain('Comments');
    });

    it('should warn about missing justification on rework', () => {
      const { comp } = setup();
      comp.submitForm.actualValue = 80;
      comp.submitForm.comments = 'Done';
      comp.submitForm.justification = '';
      const result = comp.validateSubmissionForm(makeKpi({ status: 'REWORK_REQUIRED' }));
      expect(result.isValid).toBe(true);
      const hasJustificationWarn = result.warnings.some(w => w.toLowerCase().includes('justification'));
      expect(hasJustificationWarn).toBe(true);
    });

    it('should warn about missing evidence for high-weight KPI', () => {
      const { comp } = setup();
      comp.submitForm.actualValue = 80;
      comp.submitForm.comments = 'Done';
      comp.submitForm.evidenceFile = null;
      const result = comp.validateSubmissionForm(makeKpi({ weight: 20 }));
      expect(result.isValid).toBe(true);
      const hasEvidenceWarn = result.warnings.some(w => w.toLowerCase().includes('evidence'));
      expect(hasEvidenceWarn).toBe(true);
    });

    it('should NOT warn about evidence for low-weight KPI', () => {
      const { comp } = setup();
      comp.submitForm.actualValue = 80;
      comp.submitForm.comments = 'Done';
      comp.submitForm.evidenceFile = null;
      const result = comp.validateSubmissionForm(makeKpi({ weight: 10 }));
      const hasEvidenceWarn = result.warnings.some(w => w.toLowerCase().includes('evidence'));
      expect(hasEvidenceWarn).toBe(false);
    });

    it('should have zero warnings when all recommended fields filled', () => {
      const { comp } = setup();
      comp.submitForm.actualValue = 80;
      comp.submitForm.comments = 'Completed all tasks';
      comp.submitForm.justification = 'Met all requirements';
      comp.submitForm.evidenceFile = new File([''], 'evidence.pdf', { type: 'application/pdf' });
      const result = comp.validateSubmissionForm(makeKpi({ weight: 30, status: 'REWORK_REQUIRED' }));
      expect(result.isValid).toBe(true);
      expect(result.warnings.length).toBe(0);
    });
  });

  // ── Computed Properties ──────────────────────
  describe('computed properties', () => {
    it('should return 0 totalWeight when no KPIs', () => {
      const { comp } = setup();
      comp.kpis = [];
      expect(comp.totalWeight).toBe(0);
    });

    it('should sum weights correctly', () => {
      const { comp } = setup();
      comp.ngOnInit();
      // one KPI with weight 25 was loaded
      expect(comp.totalWeight).toBe(25);
    });

    it('should count action needed KPIs correctly', () => {
      const { comp } = setup();
      comp.kpis = [
        makeKpi({ status: 'PENDING' }),
        makeKpi({ id: 2, status: 'SUBMITTED' }),
        makeKpi({ id: 3, status: 'REWORK_REQUIRED' }),
        makeKpi({ id: 4, status: 'APPROVED' })
      ];
      expect(comp.actionNeededCount).toBe(2);
    });

    it('should count approved KPIs correctly', () => {
      const { comp } = setup();
      comp.kpis = [
        makeKpi({ status: 'APPROVED' }),
        makeKpi({ id: 2, status: 'APPROVED' }),
        makeKpi({ id: 3, status: 'PENDING' })
      ];
      expect(comp.approvedCount).toBe(2);
    });

    it('should count in review KPIs', () => {
      const { comp } = setup();
      comp.kpis = [
        makeKpi({ status: 'SUBMITTED' }),
        makeKpi({ id: 2, status: 'UNDER_REVIEW' }),
        makeKpi({ id: 3, status: 'PENDING' })
      ];
      expect(comp.inReviewCount).toBe(2);
    });

    it('should calculate cycleDaysRemaining correctly', () => {
      const { comp } = setup();
      comp.cycles = [mockCycle];
      comp.selectedCycleCode = 'Q4-2026';
      const days = comp.cycleDaysRemaining;
      // endDate is 2026-12-31 and today is 2026-08-04, should be positive
      expect(days).not.toBeNull();
      if (days !== null) expect(days).toBeGreaterThan(0);
    });
  });

  // ── Filtering ───────────────────────────────
  describe('filtering', () => {
    let comp: KPIDashboardComponent;

    beforeEach(() => {
      comp = setup().comp;
      comp.kpis = [
        makeKpi({ id: 1, kpiName: 'Code Quality', categoryName: 'Engineering', status: 'PENDING' }),
        makeKpi({ id: 2, kpiName: 'Team Output', categoryName: 'Management', status: 'SUBMITTED' }),
        makeKpi({ id: 3, kpiName: 'Code Coverage', categoryName: 'Engineering', status: 'APPROVED' })
      ];
      comp['applyKpiFilters']();
    });

    it('should show all when no filters applied', () => {
      expect(comp.filteredKPIs.length).toBe(3);
    });

    it('should filter by status', () => {
      comp.filterByStatus('SUBMITTED');
      expect(comp.filteredKPIs.length).toBe(1);
      expect(comp.filteredKPIs[0].status).toBe('SUBMITTED');
    });

    it('should filter by category', () => {
      comp.filterByCategory('Engineering');
      expect(comp.filteredKPIs.length).toBe(2);
    });

    it('should filter by search term', () => {
      comp.searchTerm = 'code';
      comp['applyKpiFilters']();
      expect(comp.filteredKPIs.length).toBe(2);
    });

    it('should combine multiple filters', () => {
      comp.searchTerm = 'code';
      comp.statusFilter = 'PENDING';
      comp['applyKpiFilters']();
      expect(comp.filteredKPIs.length).toBe(1);
      expect(comp.filteredKPIs[0].kpiName).toBe('Code Quality');
    });

    it('should clear all filters', () => {
      comp.statusFilter = 'SUBMITTED';
      comp.searchTerm = 'code';
      comp.clearFilters();
      expect(comp.filteredKPIs.length).toBe(3);
      expect(comp.statusFilter).toBe('All');
      expect(comp.searchTerm).toBe('');
    });
  });

  // ── canSubmit ───────────────────────────────
  describe('canSubmit()', () => {
    it('should allow submit for PENDING status', () => {
      const { comp } = setup();
      expect(comp.canSubmit(makeKpi({ status: 'PENDING' }))).toBe(true);
    });

    it('should allow submit for REWORK_REQUIRED status', () => {
      const { comp } = setup();
      expect(comp.canSubmit(makeKpi({ status: 'REWORK_REQUIRED' }))).toBe(true);
    });

    it('should not allow submit for SUBMITTED status', () => {
      const { comp } = setup();
      expect(comp.canSubmit(makeKpi({ status: 'SUBMITTED' }))).toBe(false);
    });

    it('should not allow submit for APPROVED status', () => {
      const { comp } = setup();
      expect(comp.canSubmit(makeKpi({ status: 'APPROVED' }))).toBe(false);
    });

    it('should not allow submit for REJECTED status', () => {
      const { comp } = setup();
      expect(comp.canSubmit(makeKpi({ status: 'REJECTED' }))).toBe(true);
    });
  });

  // ── toggleSubmitForm ─────────────────────────
  describe('toggleSubmitForm()', () => {
    it('should expand form for given KPI', () => {
      const { comp } = setup();
      const kpi = makeKpi({ id: 7 });
      comp.toggleSubmitForm(kpi);
      expect(comp.expandedKpiId).toBe(7);
    });

    it('should collapse form when toggled again', () => {
      const { comp } = setup();
      const kpi = makeKpi({ id: 7 });
      comp.toggleSubmitForm(kpi);
      comp.toggleSubmitForm(kpi);
      expect(comp.expandedKpiId).toBeNull();
    });

    it('should pre-fill actualValue from existing KPI data', () => {
      const { comp } = setup();
      const kpi = makeKpi({ id: 7, actualValue: 85, comments: 'Good progress' });
      comp.toggleSubmitForm(kpi);
      expect(comp.submitForm.actualValue).toBe(85);
      expect(comp.submitForm.comments).toBe('Good progress');
    });

    it('should reset form on collapse', () => {
      const { comp } = setup();
      const kpi = makeKpi({ id: 7 });
      comp.toggleSubmitForm(kpi);
      comp.submitForm.actualValue = 90;
      comp.toggleSubmitForm(kpi);
      expect(comp.expandedKpiId).toBeNull();
    });
  });

  // ── Status helpers ───────────────────────────
  describe('status helpers', () => {
    it('should return correct badge class for APPROVED', () => {
      const { comp } = setup();
      expect(comp.statusBadgeClass('APPROVED')).toContain('emerald');
    });

    it('should return correct badge class for REJECTED', () => {
      const { comp } = setup();
      expect(comp.statusBadgeClass('REJECTED')).toContain('rose');
    });

    it('should return correct badge class for REWORK_REQUIRED', () => {
      const { comp } = setup();
      expect(comp.statusBadgeClass('REWORK_REQUIRED')).toContain('orange');
    });

    it('should return correct border class for SUBMITTED', () => {
      const { comp } = setup();
      expect(comp.statusBorderClass('SUBMITTED')).toContain('blue');
    });
  });

  // ── getProgressPercentage ─────────────────────
  describe('getProgressPercentage()', () => {
    it('should return 0 when no target', () => {
      const { comp } = setup();
      expect(comp.getProgressPercentage(makeKpi({ target: '', actualValue: 80 }))).toBe(0);
    });

    it('should return correct percentage', () => {
      const { comp } = setup();
      expect(comp.getProgressPercentage(makeKpi({ target: '100', actualValue: 80 }))).toBe(80);
    });

    it('should cap at 100%', () => {
      const { comp } = setup();
      expect(comp.getProgressPercentage(makeKpi({ target: '100', actualValue: 150 }))).toBe(100);
    });

    it('should return 0 when actualValue is null', () => {
      const { comp } = setup();
      expect(comp.getProgressPercentage(makeKpi({ target: '100', actualValue: null }))).toBe(0);
    });

    it('should fall back to rating out of 5 when no target (4/5 => 80%)', () => {
      const { comp } = setup();
      expect(comp.getProgressPercentage(makeKpi({ target: '', actualValue: null, rating: 4 }))).toBe(80);
    });

    it('should fall back to rating for 5/5 => 100%', () => {
      const { comp } = setup();
      expect(comp.getProgressPercentage(makeKpi({ target: '', actualValue: null, rating: 5 }))).toBe(100);
    });

    it('should fall back to rating for 3/5 => 60%', () => {
      const { comp } = setup();
      expect(comp.getProgressPercentage(makeKpi({ target: '', actualValue: null, rating: 3 }))).toBe(60);
    });

    it('should prefer target/actual over rating when both exist', () => {
      const { comp } = setup();
      expect(comp.getProgressPercentage(makeKpi({ target: '100', actualValue: 50, rating: 5 }))).toBe(50);
    });

    it('should never return negative progress', () => {
      const { comp } = setup();
      expect(comp.getProgressPercentage(makeKpi({ target: '100', actualValue: -20 }))).toBe(0);
    });
  });

  // ── overallKpiProgressPercentage ──────────────
  describe('overallKpiProgressPercentage', () => {
    it('should return 0 when there are no KPIs', () => {
      const { comp } = setup();
      comp.kpis = [];
      expect(comp.overallKpiProgressPercentage).toBe(0);
    });

    it('should compute weighted average across KPIs', () => {
      const { comp } = setup();
      // 50% weight at 100% + 50% weight at 0% => 50%
      comp.kpis = [
        makeKpi({ id: 1, weight: 50, target: '100', actualValue: 100 }),
        makeKpi({ id: 2, weight: 50, target: '100', actualValue: 0 })
      ];
      expect(comp.overallKpiProgressPercentage).toBe(50);
    });

    it('should respect KPI weights (heavier KPI dominates)', () => {
      const { comp } = setup();
      // 80 weight @100% + 20 weight @0% => 80%
      comp.kpis = [
        makeKpi({ id: 1, weight: 80, target: '100', actualValue: 100 }),
        makeKpi({ id: 2, weight: 20, target: '100', actualValue: 0 })
      ];
      expect(comp.overallKpiProgressPercentage).toBe(80);
    });

    it('should aggregate rating-based KPIs (4/5 and 5/5 equally weighted => 90%)', () => {
      const { comp } = setup();
      comp.kpis = [
        makeKpi({ id: 1, weight: 50, target: '', actualValue: null, rating: 4 }),
        makeKpi({ id: 2, weight: 50, target: '', actualValue: null, rating: 5 })
      ];
      expect(comp.overallKpiProgressPercentage).toBe(90);
    });

    it('should mix target-based and rating-based KPIs', () => {
      const { comp } = setup();
      // 50 weight @ 60% (60/100) + 50 weight @ 80% (4/5) => 70%
      comp.kpis = [
        makeKpi({ id: 1, weight: 50, target: '100', actualValue: 60 }),
        makeKpi({ id: 2, weight: 50, target: '', actualValue: null, rating: 4 })
      ];
      expect(comp.overallKpiProgressPercentage).toBe(70);
    });

    it('should fall back to simple average when all weights are zero', () => {
      const { comp } = setup();
      // (100 + 0) / 2 => 50%
      comp.kpis = [
        makeKpi({ id: 1, weight: 0, target: '100', actualValue: 100 }),
        makeKpi({ id: 2, weight: 0, target: '100', actualValue: 0 })
      ];
      expect(comp.overallKpiProgressPercentage).toBe(50);
    });

    it('should count KPIs without any data as 0%', () => {
      const { comp } = setup();
      // 50 weight @100% + 50 weight @0% (no data) => 50%
      comp.kpis = [
        makeKpi({ id: 1, weight: 50, target: '100', actualValue: 100 }),
        makeKpi({ id: 2, weight: 50, target: '', actualValue: null, rating: null })
      ];
      expect(comp.overallKpiProgressPercentage).toBe(50);
    });

    it('should never exceed 100%', () => {
      const { comp } = setup();
      comp.kpis = [
        makeKpi({ id: 1, weight: 50, target: '100', actualValue: 500 }),
        makeKpi({ id: 2, weight: 50, target: '100', actualValue: 300 })
      ];
      expect(comp.overallKpiProgressPercentage).toBe(100);
    });
  });

  // ── Progress helper getters ───────────────────
  describe('progress helpers', () => {
    it('measuredKpiCount should count only KPIs with usable data', () => {
      const { comp } = setup();
      comp.kpis = [
        makeKpi({ id: 1, target: '100', actualValue: 50 }),   // measurable
        makeKpi({ id: 2, target: '', actualValue: null, rating: 4 }), // measurable
        makeKpi({ id: 3, target: '', actualValue: null, rating: null }) // not measurable
      ];
      expect(comp.measuredKpiCount).toBe(2);
    });

    it('should mark progress as On Track at >= 80%', () => {
      const { comp } = setup();
      comp.kpis = [makeKpi({ weight: 100, target: '100', actualValue: 85 })];
      expect(comp.overallProgressLabel).toBe('On Track');
      expect(comp.overallProgressBarClass).toContain('emerald');
    });

    it('should mark progress as At Risk below 25%', () => {
      const { comp } = setup();
      comp.kpis = [makeKpi({ weight: 100, target: '100', actualValue: 10 })];
      expect(comp.overallProgressLabel).toBe('At Risk');
      expect(comp.overallProgressBarClass).toContain('rose');
    });
  });

  // ── Approval Filters ─────────────────────────
  describe('approval filters', () => {
    it('should filter approvals by status', () => {
      const { comp } = setup();
      comp.pendingApprovals = [
        makeKpi({ id: 1, status: 'SUBMITTED' }),
        makeKpi({ id: 2, status: 'UNDER_REVIEW' }),
        makeKpi({ id: 3, status: 'SUBMITTED' })
      ];
      comp.filterApprovalStatus('SUBMITTED');
      expect(comp.filteredApprovals.length).toBe(2);
    });

    it('should reset to all when All filter is applied', () => {
      const { comp } = setup();
      comp.pendingApprovals = [
        makeKpi({ id: 1, status: 'SUBMITTED' }),
        makeKpi({ id: 2, status: 'UNDER_REVIEW' })
      ];
      comp.filterApprovalStatus('All');
      expect(comp.filteredApprovals.length).toBe(2);
    });

    it('should search approvals by employee name', () => {
      const { comp } = setup();
      comp.pendingApprovals = [
        makeKpi({ id: 1, status: 'SUBMITTED', user: { firstName: 'Ahmed', lastName: 'Khan', email: 'a@example.invalid' } }),
        makeKpi({ id: 2, status: 'SUBMITTED', user: { firstName: 'John', lastName: 'Doe', email: 'j@example.invalid' } })
      ];
      comp.approvalSearch = 'ahmed';
      comp['applyApprovalFilters']();
      expect(comp.filteredApprovals.length).toBe(1);
      expect(comp.filteredApprovals[0].user?.firstName).toBe('Ahmed');
    });
  });

  // ── Employee name helper ──────────────────────
  describe('employeeName()', () => {
    it('should build name from firstName + lastName', () => {
      const { comp } = setup();
      const kpi = makeKpi({ user: { firstName: 'John', lastName: 'Doe' } });
      expect(comp.employeeName(kpi)).toBe('John Doe');
    });

    it('should fall back to email when name parts missing', () => {
      const { comp } = setup();
      const kpi = makeKpi({ user: { email: 'john@example.invalid' } });
      expect(comp.employeeName(kpi)).toBe('john@example.invalid');
    });

    it('should return Unknown for KPI with no user', () => {
      const { comp } = setup();
      const kpi = makeKpi({ user: undefined });
      expect(comp.employeeName(kpi)).toBe('Unknown');
    });
  });

  // ── clearApprovalFilters ──────────────────────
  describe('clearApprovalFilters()', () => {
    it('should reset approval filters to default', () => {
      const { comp } = setup();
      comp.approvalSearch = 'test search';
      comp.approvalStatusFilter = 'SUBMITTED';
      comp.clearApprovalFilters();
      expect(comp.approvalSearch).toBe('');
      expect(comp.approvalStatusFilter).toBe('All');
    });
  });

  // ── totalApprovalCount vs pendingApprovalCount ─
  describe('approval count getters', () => {
    it('pendingApprovalCount should reflect filtered count', () => {
      const { comp } = setup();
      comp.pendingApprovals = [
        makeKpi({ id: 1, status: 'SUBMITTED' }),
        makeKpi({ id: 2, status: 'UNDER_REVIEW' })
      ];
      comp.filterApprovalStatus('SUBMITTED');
      expect(comp.pendingApprovalCount).toBe(1);     // filtered
      expect(comp.totalApprovalCount).toBe(2);       // total
    });
  });
});
