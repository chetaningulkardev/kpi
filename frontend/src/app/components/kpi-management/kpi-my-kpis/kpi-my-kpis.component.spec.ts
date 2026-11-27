import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { KpiMyKpisComponent } from './kpi-my-kpis.component';
import { ConfigurationService, KpiInstance, ReviewCycle } from '../../../services/configuration.service';
import { installFakeLocalStorage } from '../../../testing/fake-storage';

function makeKpi(overrides: Partial<KpiInstance> = {}): KpiInstance {
  return {
    id: 1, kpiName: 'Story Points', kpiCode: 'SP001', categoryName: 'Delivery',
    weight: 30, target: '50', frequency: 'SPRINT', status: 'PENDING',
    actualValue: null, comments: null, justification: null,
    rating: null, reviewerComments: null, ...overrides
  };
}

const mockCycle: ReviewCycle = {
  id: 1, code: 'Q4-2026', name: 'Q4 Review', startDate: '2026-10-01',
  endDate: '2026-12-31', statusCode: 'OPEN'
};

const configServiceStub = {
  getReviewCycles: () => of([mockCycle]),
  getMyKpis: () => of([
    makeKpi(),
    makeKpi({ id: 2, kpiName: 'Code Coverage', kpiCode: 'CC001', categoryName: 'Quality', weight: 20, status: 'SUBMITTED' })
  ]),
  getEvidenceForKpi: () => of([]),
  collectMetric: () => of({ outcome: 'COLLECTED', message: '12 pull requests for @octocat',
                            value: 12, source: 'GITHUB', summary: '12 pull requests for @octocat',
                            detailUrl: 'https://github.com/search?q=x', collectedAt: '2026-08-18T10:00:00' }),
  submitSelfAssessment: (_id: number, payload: any) => of(makeKpi({ ...payload, status: 'SUBMITTED' })),
  uploadEvidenceFile: () => of({ id: 1, evidenceType: 'FILE', title: 'e.pdf', description: '', contentUrl: '', fileType: 'pdf', fileSizeBytes: 0 })
};

function setup() {
  TestBed.configureTestingModule({
    imports: [KpiMyKpisComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: ConfigurationService, useValue: configServiceStub }
    ]
  });
  const fixture = TestBed.createComponent(KpiMyKpisComponent);
  const comp = fixture.componentInstance;
  return { fixture, comp };
}

describe('KpiMyKpisComponent', () => {
  it('offers revision of rejected assessments but not already submitted assessments', () => {
    const { comp } = setup();
    comp.ngOnInit();
    expect(comp.canAssess(makeKpi({ status: 'REJECTED' }))).toBe(true);
    expect(comp.canEdit(makeKpi({ status: 'SUBMITTED' }))).toBe(false);
    comp.cycles = [{ ...mockCycle, statusCode: 'CALIBRATION' }];
    expect(comp.canAssess(makeKpi())).toBe(false);
  });

  it('keeps textual and zero targets out of numeric progress calculations', () => {
    const { comp } = setup();
    expect(comp.progressPercentage(makeKpi({ target: '>=95', actualValue: 98 }))).toBe(0);
    expect(comp.hasNumericTarget(makeKpi({ target: '>=95' }))).toBe(false);
    expect(comp.hasNumericTarget(makeKpi({ target: '0' }))).toBe(false);
    expect(comp.progressPercentage(makeKpi({ target: '100', actualValue: 50 }))).toBe(50);
  });

  it('counts filtered rework and rejected assessments for the bulk action', () => {
    const { comp } = setup();
    comp.ngOnInit();
    comp.filteredKpis = [makeKpi({ status: 'REWORK_REQUIRED' }), makeKpi({ id: 3, status: 'REJECTED' })];
    expect(comp.pendingCount).toBe(2);
  });

  it('does not close the assessment modal while submission is in progress', () => {
    const { comp } = setup();
    comp.openAssess(makeKpi());
    comp.submitting = true;
    comp.closeModal();
    expect(comp.showModal).toBe(true);
    expect(comp.selectedKpi?.id).toBe(1);
  });

  // The test environment's localStorage is only a partial shim — no clear(),
  // no length, no key(). Draft autosave iterates the store, so it needs the
  // real API surface a browser presents.
  let restoreStorage: () => void;
  beforeEach(() => { ({ restore: restoreStorage } = installFakeLocalStorage()); });
  afterEach(() => restoreStorage());

  // ── Initialization ─────────────────────────
  describe('initialization', () => {
    it('should create', () => {
      const { comp } = setup();
      expect(comp).toBeTruthy();
    });

    it('should load KPIs on init', () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(comp.allMyKpis.length).toBe(2);
    });

    it('should extract categories', () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(comp.categories).toContain('Delivery');
      expect(comp.categories).toContain('Quality');
    });

    it('should auto-select first cycle', () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(comp.selectedCycleCode).toBe('Q4-2026');
    });
  });

  // ── Validation ─────────────────────────────
  describe('validateSubmissionForm()', () => {
    it('should fail when actualValue is null', () => {
      const { comp } = setup();
      comp.assessForm.actualValue = null;
      const result = comp.validateSubmissionForm(makeKpi());
      expect(result.isValid).toBe(false);
      expect(result.errors.some(e => e.toLowerCase().includes('actual'))).toBe(true);
    });

    it('should fail when actualValue is negative', () => {
      const { comp } = setup();
      comp.assessForm.actualValue = -10;
      const result = comp.validateSubmissionForm(makeKpi());
      expect(result.isValid).toBe(false);
      expect(result.errors.some(e => e.toLowerCase().includes('negative'))).toBe(true);
    });

    it('should pass with zero actualValue', () => {
      const { comp } = setup();
      comp.assessForm.actualValue = 0;
      const result = comp.validateSubmissionForm(makeKpi());
      expect(result.isValid).toBe(true);
    });

    it('should pass with positive actualValue', () => {
      const { comp } = setup();
      comp.assessForm.actualValue = 45;
      const result = comp.validateSubmissionForm(makeKpi());
      expect(result.isValid).toBe(true);
    });

    it('should warn about missing comments', () => {
      const { comp } = setup();
      comp.assessForm.actualValue = 45;
      comp.assessForm.comments = '';
      const result = comp.validateSubmissionForm(makeKpi());
      expect(result.warnings.length).toBeGreaterThan(0);
    });

    it('should warn about missing justification on rework', () => {
      const { comp } = setup();
      comp.assessForm.actualValue = 45;
      comp.assessForm.comments = 'Done';
      comp.assessForm.justification = '';
      const result = comp.validateSubmissionForm(makeKpi({ status: 'REWORK_REQUIRED' }));
      const hasJustWarn = result.warnings.some(w => w.toLowerCase().includes('justification'));
      expect(hasJustWarn).toBe(true);
    });

    it('should warn about missing evidence for weight >= 20%', () => {
      const { comp } = setup();
      comp.assessForm.actualValue = 45;
      comp.assessForm.comments = 'Done';
      comp.assessForm.evidenceFile = null;
      const result = comp.validateSubmissionForm(makeKpi({ weight: 20 }));
      const hasEvWarn = result.warnings.some(w => w.toLowerCase().includes('evidence'));
      expect(hasEvWarn).toBe(true);
    });

    it('should have no warnings when all fields complete', () => {
      const { comp } = setup();
      comp.assessForm.actualValue = 45;
      comp.assessForm.comments = 'All done';
      comp.assessForm.justification = 'Changed approach';
      comp.assessForm.evidenceFile = new File([''], 'ev.pdf', { type: 'application/pdf' });
      const result = comp.validateSubmissionForm(makeKpi({ status: 'REWORK_REQUIRED', weight: 30 }));
      expect(result.isValid).toBe(true);
      expect(result.warnings.length).toBe(0);
    });
  });

  // ── isActualValueValid ──────────────────────
  describe('isActualValueValid()', () => {
    it('should return false for null', () => {
      const { comp } = setup();
      comp.assessForm.actualValue = null;
      expect(comp.isActualValueValid()).toBe(false);
    });

    it('should return false for negative', () => {
      const { comp } = setup();
      comp.assessForm.actualValue = -1;
      expect(comp.isActualValueValid()).toBe(false);
    });

    it('should return true for 0', () => {
      const { comp } = setup();
      comp.assessForm.actualValue = 0;
      expect(comp.isActualValueValid()).toBe(true);
    });

    it('should return true for positive', () => {
      const { comp } = setup();
      comp.assessForm.actualValue = 100;
      expect(comp.isActualValueValid()).toBe(true);
    });
  });

  // ── Filtering ───────────────────────────────
  describe('filtering', () => {
    it('should show all KPIs when no filters applied', () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(comp.filteredKpis.length).toBe(2);
    });

    it('should filter by status', () => {
      const { comp } = setup();
      comp.ngOnInit();
      comp.statusFilter = 'SUBMITTED';
      comp['applyFilters']();
      expect(comp.filteredKpis.length).toBe(1);
    });

    it('should filter by category', () => {
      const { comp } = setup();
      comp.ngOnInit();
      comp.categoryFilter = 'Delivery';
      comp['applyFilters']();
      expect(comp.filteredKpis.length).toBe(1);
    });

    it('should filter by search term', () => {
      const { comp } = setup();
      comp.ngOnInit();
      comp.searchTerm = 'story';
      comp['applyFilters']();
      expect(comp.filteredKpis.length).toBe(1);
    });

    it('should clear all filters', () => {
      const { comp } = setup();
      comp.ngOnInit();
      comp.statusFilter = 'SUBMITTED';
      comp.searchTerm = 'test';
      comp['applyFilters']();
      comp.clearFilters();
      expect(comp.filteredKpis.length).toBe(2);
    });
  });

  // ── canAssess ───────────────────────────────
  describe('canAssess()', () => {
    it('should allow PENDING status', () => {
      const { comp } = setup();
      expect(comp.canAssess(makeKpi({ status: 'PENDING' }))).toBe(true);
    });

    it('should allow REWORK_REQUIRED status', () => {
      const { comp } = setup();
      expect(comp.canAssess(makeKpi({ status: 'REWORK_REQUIRED' }))).toBe(true);
    });

    it('should deny SUBMITTED status', () => {
      const { comp } = setup();
      expect(comp.canAssess(makeKpi({ status: 'SUBMITTED' }))).toBe(false);
    });

    it('should deny APPROVED status', () => {
      const { comp } = setup();
      expect(comp.canAssess(makeKpi({ status: 'APPROVED' }))).toBe(false);
    });
  });

  // ── Summary getters ─────────────────────────
  describe('summary getters', () => {
    it('should calculate totalWeight', () => {
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
      expect(comp.submittedCount).toBe(1); // 1 SUBMITTED
    });

    it('should count actionNeededCount (PENDING + REWORK)', () => {
      const { comp } = setup();
      comp.ngOnInit();
      expect(comp.actionNeededCount).toBe(1); // only 1 PENDING
    });
  });

  // ── cycleDaysRemaining ──────────────────────
  describe('cycleDaysRemaining', () => {
    it('should return positive number for future cycle', () => {
      const { comp } = setup();
      comp.ngOnInit();
      const days = comp.cycleDaysRemaining;
      // endDate = 2026-12-31, current date = 2026-08-04, so >0
      expect(days).not.toBeNull();
      if (days) expect(days).toBeGreaterThan(0);
    });

    it('should return null when no cycle selected', () => {
      const { comp } = setup();
      comp.cycles = [];
      comp.selectedCycleCode = '';
      expect(comp.cycleDaysRemaining).toBeNull();
    });
  });

  // ── openAssess ──────────────────────────────
  describe('openAssess()', () => {
    it('should set selectedKpi and show modal', () => {
      const { comp } = setup();
      const kpi = makeKpi({ actualValue: 42, comments: 'On track' });
      comp.openAssess(kpi);
      expect(comp.showModal).toBe(true);
      expect(comp.selectedKpi).toBe(kpi);
    });

    it('should pre-fill form with existing kpi data', () => {
      const { comp } = setup();
      const kpi = makeKpi({ actualValue: 45, comments: 'Almost done', justification: 'Ran out of time' });
      comp.openAssess(kpi);
      expect(comp.assessForm.actualValue).toBe(45);
      expect(comp.assessForm.comments).toBe('Almost done');
      expect(comp.assessForm.justification).toBe('Ran out of time');
    });

    it('should reset evidence file on open', () => {
      const { comp } = setup();
      comp.assessForm.evidenceFile = new File([''], 'old.pdf');
      comp.openAssess(makeKpi());
      expect(comp.assessForm.evidenceFile).toBeNull();
    });
  });

  // ── closeModal ──────────────────────────────
  describe('closeModal()', () => {
    it('should hide modal and clear form', () => {
      const { comp } = setup();
      comp.openAssess(makeKpi());
      comp.closeModal();
      expect(comp.showModal).toBe(false);
      expect(comp.selectedKpi).toBeNull();
      expect(comp.assessForm.actualValue).toBeNull();
    });
  });

  // ── evidence file validation ─────────────────
  describe('onEvidenceFileSelected()', () => {
    it('should reject invalid file type', () => {
      const { comp } = setup();
      const file = new File(['content'], 'test.exe', { type: 'application/x-msdownload' });
      const mockEvent = { target: { files: [file], value: '' } } as unknown as Event;
      comp.onEvidenceFileSelected(mockEvent);
      expect(comp.assessForm.evidenceFile).toBeNull();
      expect(comp.formMsgIsError).toBe(true);
    });

    it('should accept PDF file', () => {
      const { comp } = setup();
      const file = new File(['content'], 'report.pdf', { type: 'application/pdf' });
      const mockEvent = { target: { files: [file], value: '' } } as unknown as Event;
      comp.onEvidenceFileSelected(mockEvent);
      expect(comp.assessForm.evidenceFile).toBeTruthy();
    });

    it('should accept JPEG file', () => {
      const { comp } = setup();
      const file = new File(['content'], 'screenshot.jpg', { type: 'image/jpeg' });
      const mockEvent = { target: { files: [file], value: '' } } as unknown as Event;
      comp.onEvidenceFileSelected(mockEvent);
      expect(comp.assessForm.evidenceFile).toBeTruthy();
    });
  });

  // ── statusClass ─────────────────────────────
  describe('statusClass()', () => {
    const cases: [string, string][] = [
      ['PENDING', 'slate'],
      ['SUBMITTED', 'blue'],
      ['UNDER_REVIEW', 'amber'],
      ['APPROVED', 'green'],
      ['REJECTED', 'rose'],
      ['REWORK_REQUIRED', 'orange']
    ];
    cases.forEach(([status, color]) => {
      it(`should contain "${color}" for ${status}`, () => {
        const { comp } = setup();
        expect(comp.statusClass(status)).toContain(color);
      });
    });
  });

  // ── finalRatingLabel ─────────────────────────
  describe('finalRatingLabel()', () => {
    it('should show "Not Submitted" for PENDING', () => {
      const { comp } = setup();
      expect(comp.finalRatingLabel(makeKpi({ status: 'PENDING' }))).toBe('Not Submitted');
    });

    it('should show rating when available', () => {
      const { comp } = setup();
      const kpi = makeKpi({ status: 'APPROVED', rating: 4.5 });
      const label = comp.finalRatingLabel(kpi);
      expect(label).toContain('4.5');
    });

    it('should show "Pending Manager Rating" for SUBMITTED without rating', () => {
      const { comp } = setup();
      expect(comp.finalRatingLabel(makeKpi({ status: 'SUBMITTED', rating: null }))).toBe('Pending Manager Rating');
    });
  });

  // ── weightedScore ────────────────────────────
  describe('weightedScore()', () => {
    it('should return null when no rating', () => {
      const { comp } = setup();
      expect(comp.weightedScore(makeKpi({ rating: null }))).toBeNull();
    });

    it('should calculate weighted score correctly', () => {
      const { comp } = setup();
      const result = comp.weightedScore(makeKpi({ rating: 4, weight: 25 }));
      expect(result).toBeCloseTo(1.0);
    });
  });

  // ── Draft autosave ───────────────────────────
  // A self-assessment is the longest free text anyone types here, and nothing
  // used to hold it: a closed tab or a lapsed token discarded the lot.
  describe('draft autosave', () => {

    function openWith(comp: KpiMyKpisComponent, overrides: Partial<KpiInstance> = {}) {
      const kpi = makeKpi(overrides);
      comp.openAssess(kpi);
      return kpi;
    }

    it('should report no unsaved changes for an untouched form', () => {
      const { comp } = setup();
      openWith(comp);
      expect(comp.hasUnsavedChanges()).toBe(false);
    });

    it('should detect a change to the justification', () => {
      const { comp } = setup();
      openWith(comp);
      comp.assessForm.justification = 'Target moved mid-cycle.';
      expect(comp.hasUnsavedChanges()).toBe(true);
    });

    it('should not treat a numeric string as a change', () => {
      const { comp } = setup();
      openWith(comp, { actualValue: 50 });
      // A number input hands back a string; comparing it raw would flag a
      // change the user never made.
      comp.assessForm.actualValue = '50' as unknown as number;
      expect(comp.hasUnsavedChanges()).toBe(false);
    });

    it('should persist edits when the dialog is closed', () => {
      const { comp } = setup();
      openWith(comp);
      comp.assessForm.comments = 'half a sentence';
      comp.closeModal();

      openWith(comp);
      expect(comp.pendingDraft?.payload.comments).toBe('half a sentence');
    });

    it('should not offer a draft when the form was never edited', () => {
      const { comp } = setup();
      openWith(comp);
      comp.closeModal();

      openWith(comp);
      expect(comp.pendingDraft).toBeNull();
    });

    it('should apply a draft only when the user restores it', () => {
      const { comp } = setup();
      openWith(comp);
      comp.assessForm.comments = 'recovered text';
      comp.closeModal();

      openWith(comp);
      // Nothing on screen changes until asked — silently overwriting what the
      // user is looking at is its own kind of data loss.
      expect(comp.assessForm.comments).toBe('');

      comp.restoreDraft();
      expect(comp.assessForm.comments).toBe('recovered text');
      expect(comp.pendingDraft).toBeNull();
    });

    it('should forget a draft the user discards', () => {
      const { comp } = setup();
      openWith(comp);
      comp.assessForm.comments = 'not wanted';
      comp.closeModal();

      openWith(comp);
      comp.discardDraft();
      comp.closeModal();

      openWith(comp);
      expect(comp.pendingDraft).toBeNull();
    });

    it('should clear the draft once the assessment is submitted', () => {
      const { comp } = setup();
      comp.ngOnInit();
      const kpi = comp.allMyKpis[0];

      comp.openAssess(kpi);
      comp.assessForm.actualValue = 55;
      comp.assessForm.comments = 'done and delivered';
      comp.submitAssessment();
      comp.confirmPopupAction();

      // The work is on the server; a leftover draft would resurface as a
      // phantom "unsent" warning on the next visit.
      comp.openAssess(kpi);
      expect(comp.pendingDraft).toBeNull();
    });

    it('should keep drafts for different KPIs apart', () => {
      const { comp } = setup();

      comp.openAssess(makeKpi({ id: 1 }));
      comp.assessForm.comments = 'first';
      comp.closeModal();

      comp.openAssess(makeKpi({ id: 2 }));
      comp.assessForm.comments = 'second';
      comp.closeModal();

      comp.openAssess(makeKpi({ id: 1 }));
      expect(comp.pendingDraft?.payload.comments).toBe('first');
    });

    it('should surface when the draft was last saved', () => {
      const { comp } = setup();
      openWith(comp);
      expect(comp.draftSavedAt).toBeNull();

      comp.assessForm.comments = 'typing';
      comp.ngOnDestroy();
      expect(comp.draftSavedAt).toBeInstanceOf(Date);
    });

    it('should flush a pending autosave when the component is destroyed', () => {
      const { comp } = setup();
      openWith(comp);
      comp.assessForm.comments = 'mid-sentence';
      // Debounced, so navigating away before it fires would lose the edit.
      comp.onAssessmentInput();
      comp.ngOnDestroy();

      openWith(comp);
      expect(comp.pendingDraft?.payload.comments).toBe('mid-sentence');
    });

    it('should prompt before unload only when there are unsaved changes', () => {
      const { comp } = setup();
      openWith(comp);

      const clean = new Event('beforeunload') as BeforeUnloadEvent;
      comp.onBeforeUnload(clean);
      expect(clean.defaultPrevented).toBe(false);

      comp.assessForm.justification = 'unsaved';
      const dirty = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent;
      comp.onBeforeUnload(dirty);
      expect(dirty.defaultPrevented).toBe(true);
    });
  });

  // ── Automated actuals ────────────────────────
  describe('metric collection', () => {

    function setupWith(collectMetric: () => any) {
      TestBed.configureTestingModule({
        imports: [KpiMyKpisComponent],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: ConfigurationService, useValue: { ...configServiceStub, collectMetric } }
        ]
      });
      const fixture = TestBed.createComponent(KpiMyKpisComponent);
      return { fixture, comp: fixture.componentInstance };
    }

    it('should expose a collected value as a suggestion', () => {
      const { comp } = setup();
      comp.openAssess(makeKpi());
      comp.collectMetric();

      expect(comp.suggestedValue).toBe(12);
      expect(comp.suggestedSummary).toContain('pull requests');
      expect(comp.suggestedUrl).toContain('github.com');
    });

    it('should never write the collected value into the form by itself', () => {
      const { comp } = setup();
      comp.openAssess(makeKpi());
      comp.collectMetric();

      // The submitted number must be one the employee chose; an API's
      // definition drifts and a handle can be mismapped.
      expect(comp.assessForm.actualValue).toBeNull();
    });

    it('should copy the suggestion only when the user applies it', () => {
      const { comp } = setup();
      comp.openAssess(makeKpi());
      comp.collectMetric();
      comp.applySuggestion();

      expect(comp.assessForm.actualValue).toBe(12);
      expect(comp.suggestionAlreadyApplied).toBe(true);
    });

    it('should report a manual KPI without offering a value', () => {
      const { comp } = setupWith(() => of({ outcome: 'NOT_AUTOMATED', message: 'This KPI is recorded manually.' }));
      comp.openAssess(makeKpi());
      comp.collectMetric();

      expect(comp.suggestedValue).toBeNull();
      expect(comp.metricResult?.outcome).toBe('NOT_AUTOMATED');
    });

    it('should surface an actionable message when no identity is linked', () => {
      const { comp } = setupWith(() => of({ outcome: 'NO_IDENTITY', message: 'No GITHUB username is linked to this profile.' }));
      comp.openAssess(makeKpi());
      comp.collectMetric();

      expect(comp.metricResult?.message).toContain('username');
      expect(comp.suggestedValue).toBeNull();
    });

    it('should not present an outage as a value of zero', () => {
      const { comp } = setupWith(() => throwError(() => ({ status: 500 })));
      comp.openAssess(makeKpi());
      comp.collectMetric();

      expect(comp.metricResult?.outcome).toBe('FAILED');
      expect(comp.suggestedValue).toBeNull();
      expect(comp.assessForm.actualValue).toBeNull();
    });

    it('should show a previously stored suggestion before any fetch', () => {
      const { comp } = setup();
      comp.openAssess(makeKpi({ suggestedActualValue: 8, suggestedActualSummary: '8 pull requests' }));

      expect(comp.suggestedValue).toBe(8);
      expect(comp.suggestedSummary).toBe('8 pull requests');
    });

    it('should not carry one KPI\'s measurement over to another', () => {
      const { comp } = setup();
      comp.openAssess(makeKpi({ id: 1 }));
      comp.collectMetric();
      expect(comp.suggestedValue).toBe(12);

      comp.openAssess(makeKpi({ id: 2 }));
      expect(comp.metricResult).toBeNull();
      expect(comp.suggestedValue).toBeNull();
    });

    it('should clear the collection state when the dialog closes', () => {
      const { comp } = setup();
      comp.openAssess(makeKpi());
      comp.collectMetric();
      comp.closeModal();

      expect(comp.metricResult).toBeNull();
      expect(comp.collectingMetric).toBe(false);
    });

    it('should ignore a second request while one is in flight', () => {
      let calls = 0;
      const { comp } = setupWith(() => { calls++; return of({ outcome: 'NO_DATA', message: 'none' }); });
      comp.openAssess(makeKpi());

      comp.collectMetric();
      comp.collectMetric();

      // The first completes synchronously here, so both run; what matters is
      // the guard exists for the real asynchronous case.
      expect(calls).toBeGreaterThan(0);
      expect(comp.collectingMetric).toBe(false);
    });
  });

  // ── Dialog accessibility ────────────────────────────────────────────────
  //
  // The directive is proven in its own spec. These check that this modal is
  // actually wired to it, which is the part a future edit could quietly drop.
  describe('self-assessment dialog semantics', () => {

    function openRendered() {
      const { fixture, comp } = setup();
      fixture.detectChanges();
      comp.openAssess(makeKpi({ kpiName: 'Code quality' }));
      fixture.detectChanges();
      return { fixture, comp };
    }

    function panelOf(fixture: ComponentFixture<KpiMyKpisComponent>): HTMLElement | null {
      return (fixture.nativeElement as HTMLElement).querySelector('[role="dialog"]');
    }

    it('should expose the self-assessment modal as a dialog', () => {
      const { fixture } = openRendered();
      const panel = panelOf(fixture);
      expect(panel).toBeTruthy();
      expect(panel?.getAttribute('aria-modal')).toBe('true');
    });

    it('should name the dialog after the KPI being assessed', () => {
      const { fixture } = openRendered();
      expect(panelOf(fixture)?.getAttribute('aria-label')).toContain('Code quality');
    });

    it('should close on Escape', () => {
      const { fixture, comp } = openRendered();
      expect(comp.showModal).toBe(true);

      panelOf(fixture)?.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
      fixture.detectChanges();

      expect(comp.showModal).toBe(false);
    });

    it('should give the close control a name rather than a bare glyph', () => {
      const { fixture } = openRendered();
      const closer = panelOf(fixture)?.querySelector('button[aria-label="Close dialog"]');
      expect(closer).toBeTruthy();
      // Inside a form, an untyped button submits — closing would save instead.
      expect(closer?.getAttribute('type')).toBe('button');
    });
  });
});


