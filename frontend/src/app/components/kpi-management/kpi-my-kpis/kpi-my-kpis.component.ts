import { Component, OnInit, OnDestroy, ChangeDetectorRef, NgZone, HostListener, inject, DestroyRef } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ConfigurationService, ReviewCycle, KpiInstance, KpiEvidence, MetricCollectionResult } from '../../../services/configuration.service';
import { SkeletonComponent } from '../../shared/skeleton.component';
import { DraftStorageService, RestoredDraft } from '../../../services/draft-storage.service';
import { finalize, switchMap } from 'rxjs';

import { extractHttpError } from '../../../services/http-error.util';
import { ConfirmService } from '../../../services/confirm.service';
import { AccessibleModalDirective } from '../../../directives/accessible-modal.directive';

/**
 * The part of the assessment form worth preserving locally. The chosen file is
 * deliberately excluded: a File cannot be serialised, and re-picking it is a
 * click, whereas re-typing a justification is not.
 */
interface AssessmentDraft {
  actualValue: number | null;
  comments: string;
  justification: string;
}
@Component({
  selector: 'app-kpi-my-kpis',
  standalone: true,
  imports: [AccessibleModalDirective, CommonModule, FormsModule, SkeletonComponent],
  templateUrl: './kpi-my-kpis.component.html'
})
export class KpiMyKpisComponent implements OnInit, OnDestroy {

  private readonly drafts = inject(DraftStorageService);
  private readonly confirmService = inject(ConfirmService);
  private readonly route = inject(ActivatedRoute, { optional: true });
  private readonly destroyRef = inject(DestroyRef);
  private pendingOpenKpiId: number | null = null;
  private loadVersion = 0;
  loadError = '';

  /** Namespace for this form's drafts within local storage. */
  private static readonly DRAFT_KIND = 'self-assessment';

  /** Long enough to not thrash storage on every keystroke, short enough that
   *  almost nothing is lost if the tab dies mid-sentence. */
  private static readonly AUTOSAVE_DEBOUNCE_MS = 800;

  private autosaveTimer: ReturnType<typeof setTimeout> | null = null;

  /** What the server holds for the KPI being edited, used to decide whether
   *  the form actually diverges from the saved record. */
  private serverSnapshot: AssessmentDraft | null = null;

  /** A recovered draft awaiting the user's decision. Never applied silently —
   *  overwriting what someone is looking at is its own kind of data loss. */
  pendingDraft: RestoredDraft<AssessmentDraft> | null = null;

  /** Timestamp of the last autosave, surfaced so the user can trust it. */
  draftSavedAt: Date | null = null;

  // ── Automated actuals ───────────────────────────────────────────────────
  /** In-flight collection request, so the button can show progress. */
  collectingMetric = false;

  /** Outcome of the last collection attempt for the open KPI. */
  metricResult: MetricCollectionResult | null = null;

  // ...existing cycles and loading states...
  cycles: ReviewCycle[] = [];
  selectedCycleCode = '';
  allMyKpis: KpiInstance[] = [];
  filteredKpis: KpiInstance[] = [];
  cyclesLoading = false;
  kpisLoading = false;
  submitting = false;
  bulkSubmitting = false;

  // ✨ NEW: View toggle
  tableView = false;

  // ✨ NEW: Search and filtering
  searchTerm = '';
  statusFilter = 'All';
  categoryFilter = 'All';
  categories: string[] = [];

  // ✨ NEW: Submission form with evidence file
  showModal = false;
  selectedKpi: KpiInstance | null = null;
  assessForm: {
    actualValue: number | null;
    comments: string;
    justification: string;
    evidenceFile: File | null;
    evidenceTitle: string;
  } = {
    actualValue: null,
    comments: '',
    justification: '',
    evidenceFile: null,
    evidenceTitle: ''
   };
   formMsg = '';
   formMsgIsError = false;
   // noinspection JSUnusedGlobalSymbols - used in template
   readonly acceptedEvidenceTypes = '.pdf,.jpg,.jpeg,.png';
   evidenceByKpi: Record<number, KpiEvidence[]> = {};
   evidenceLoading: Record<number, boolean> = {};
   evidenceErrors: Record<number, string> = {};

  // ...existing page message and confirmation...
  pageMsg = '';
  pageMsgIsError = false;

  confirmPopup = {
    visible: false,
    title: '',
    message: '',
    actionLabel: 'Confirm',
    dangerous: false,
    action: null as null | (() => void)
  };

  constructor(private configService: ConfigurationService, private cdr: ChangeDetectorRef, private ngZone: NgZone) {}

  ngOnInit(): void {
    this.route?.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const id = Number(params.get('openKpiId'));
      this.pendingOpenKpiId = Number.isFinite(id) && id > 0 ? id : null;
      if (!this.kpisLoading && this.allMyKpis.length) this.openNotificationKpi();
    });
    // Clear anything past its TTL before adding more, so an abandoned cycle's
    // drafts cannot accumulate towards the storage quota.
    this.drafts.sweepExpired();
    this.loadCycles();
  }

  ngOnDestroy(): void {
    // A pending debounce would otherwise drop the last edits on navigation.
    this.flushAutosave();
  }

  /**
   * Warns before a tab close or reload discards edits. Browsers ignore custom
   * text here and show their own wording, so only the fact of the prompt is
   * under our control.
   */
  @HostListener('window:beforeunload', ['$event'])
  onBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.showModal && this.hasUnsavedChanges()) {
      this.flushAutosave();
      event.preventDefault();
      event.returnValue = '';
    }
  }

  loadCycles(): void {
    this.cyclesLoading = true;
    this.loadError = '';
    this.configService.getReviewCycles().pipe(
      finalize(() => this.cdr.detectChanges())
    ).subscribe({
      next: (cycles) => {
        this.cycles = cycles.filter(c =>
          ['OPEN', 'IN_PROGRESS', 'UNDER_REVIEW', 'CALIBRATION'].includes(c.statusCode)
        );
        this.cyclesLoading = false;
        if (this.cycles.length > 0) {
          this.selectedCycleCode = this.cycles[0].code;
          this.loadMyKpis();
        }
      },
      error: () => {
        this.cyclesLoading = false;
        this.loadError = 'Failed to load review cycles.';
        this.showPageMsg('Failed to load review cycles.', true);
      }
    });
  }

  loadMyKpis(): void {
    const version = ++this.loadVersion;
    if (!this.selectedCycleCode) return;
    this.loadError = '';
    this.kpisLoading = true;
    this.ngZone.run(() => {
      this.configService.getMyKpis(this.selectedCycleCode).pipe(
        finalize(() => {
          this.ngZone.run(() => {
            if (version !== this.loadVersion) return;
            this.kpisLoading = false;
            this.cdr.markForCheck();
          });
        })
      ).subscribe({
        next: (kpis) => {
          this.ngZone.run(() => {
            if (version !== this.loadVersion) return;
            this.allMyKpis = kpis;
            this.extractCategories();
            this.applyFilters();
            this.loadEvidenceForVisibleKpis();
            this.openNotificationKpi();
          });
        },
        error: () => {
          this.ngZone.run(() => {
            if (version !== this.loadVersion) return;
            this.allMyKpis = [];
            this.filteredKpis = [];
            this.loadError = 'Failed to load your KPIs.';
            this.showPageMsg('Failed to load your KPIs.', true);
          });
        }

      });
    });
  }

  private openNotificationKpi(): void {
    if (!this.pendingOpenKpiId) return;
    const kpi = this.allMyKpis.find(k => k.id === this.pendingOpenKpiId);
    this.pendingOpenKpiId = null;
    if (!kpi) {
      this.showPageMsg('This KPI is not in the selected active cycle. Choose its cycle or check My Performance for past cycles.', true);
      return;
    }
    this.clearFilters();
    this.searchTerm = kpi.kpiCode;
    this.applyFilters();
    if (this.canAssess(kpi)) this.openAssess(kpi);
    else this.showPageMsg(`${kpi.kpiName}: ${kpi.status}${kpi.reviewerComments ? ' — ' + kpi.reviewerComments : ''}`, false);
  }

  // ✨ NEW METHODS ========================

  private extractCategories(): void {
    const cats = new Set(this.allMyKpis.map(k => k.categoryName ?? 'Other'));
    this.categories = Array.from(cats).sort();
  }

  private applyFilters(): void {
    const term = this.searchTerm.trim().toLowerCase();
    this.filteredKpis = this.allMyKpis.filter(kpi => {
      // Status filter
      if (this.statusFilter !== 'All' && kpi.status !== this.statusFilter) {
        return false;
      }

      // Category filter
      if (this.categoryFilter !== 'All' && (kpi.categoryName ?? 'Other') !== this.categoryFilter) {
        return false;
      }

      // Search filter
      if (term) {
        const searchText = `${kpi.kpiName ?? ''} ${kpi.kpiCode ?? ''} ${kpi.categoryName ?? ''}`.toLowerCase();
        if (!searchText.includes(term)) {
          return false;
        }
      }

      return true;
    });
  }

  onSearchChange(): void {
    this.applyFilters();
  }

  onStatusFilterChange(): void {
    this.applyFilters();
  }

  onCategoryFilterChange(): void {
    this.applyFilters();
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.statusFilter = 'All';
    this.categoryFilter = 'All';
    this.applyFilters();
  }

  /** Comprehensive validation for submission */
  validateSubmissionForm(kpi: KpiInstance): {
    isValid: boolean;
    errors: string[];
    warnings: string[];
  } {
    const errors: string[] = [];
    const warnings: string[] = [];

    // ✅ MANDATORY: Actual Value (must be ≥ 0)
    const actualValue = this.assessForm.actualValue;
    if (actualValue === null || actualValue === undefined || !Number.isFinite(Number(actualValue))) {
      errors.push('Actual value is required');
    } else if (Number(actualValue) < 0) {
      errors.push('Actual value cannot be negative');
    }

    // ⚠️ RECOMMENDED: Comments
    if (!this.assessForm.comments?.trim()) {
      warnings.push('Comments recommended to provide context');
    }

    // ⚠️ RECOMMENDED: Justification (on rework)
    if (kpi.status === 'REWORK_REQUIRED' && !this.assessForm.justification?.trim()) {
      warnings.push('Justification recommended when resubmitting after rework');
    }

    // ⚠️ RECOMMENDED: Evidence (high-weight)
    if ((kpi.weight ?? 0) >= 20 && !this.assessForm.evidenceFile) {
      warnings.push('Consider uploading evidence for high-weight KPIs (≥20%)');
    }

    return {
      isValid: errors.length === 0,
      errors,
      warnings
    };
  }

  isActualValueValid(): boolean {
    const v = this.assessForm.actualValue;
    return v !== null && v !== undefined && Number.isFinite(Number(v)) && Number(v) >= 0;
  }

  get selectedCycle(): ReviewCycle | undefined {
    return this.cycles.find(c => c.code === this.selectedCycleCode);
  }

  get cycleDaysRemaining(): number | null {
    const cycle = this.selectedCycle;
    if (!cycle?.endDate) return null;
    const diff = new Date(cycle.endDate).getTime() - Date.now();
    return Math.ceil(diff / (1000 * 60 * 60 * 24));
  }

  get actionNeededCount(): number {
    return this.allMyKpis.filter(k => this.canAssess(k)).length;
  }

  private loadEvidenceForVisibleKpis(): void {
    this.evidenceByKpi = {};
    this.evidenceLoading = {};
    this.evidenceErrors = {};
    const version = this.loadVersion;
    this.allMyKpis.forEach((kpi) => {
      this.evidenceLoading[kpi.id] = true;
      this.configService.getEvidenceForKpi(kpi.id).subscribe({
        next: (evidence) => {
          if (version !== this.loadVersion) return;
          this.evidenceByKpi[kpi.id] = evidence;
          this.evidenceLoading[kpi.id] = false;
        },
        error: () => {
          if (version !== this.loadVersion) return;
          this.evidenceLoading[kpi.id] = false;
          this.evidenceErrors[kpi.id] = 'Evidence unavailable. Refresh KPIs to retry.';
        }
      });
    });
  }

  onCycleChange(): void {
    this.allMyKpis = [];
    this.filteredKpis = [];
    this.searchTerm = '';
    this.statusFilter = 'All';
    this.categoryFilter = 'All';
    this.loadMyKpis();
  }

  openAssess(kpi: KpiInstance): void {
    this.selectedKpi = kpi;
    this.assessForm = {
      actualValue: kpi.actualValue ?? null,
      comments: kpi.comments ?? '',
      justification: kpi.justification ?? '',
      evidenceFile: null,
      evidenceTitle: ''
    };
    this.serverSnapshot = this.snapshotOf(this.assessForm);
    this.draftSavedAt = null;
    // Belongs to the previous KPI; keeping it would attribute one KPI's
    // measurement to another.
    this.metricResult = null;

    // Offered, not applied. The user may well prefer what the server holds.
    this.pendingDraft = this.drafts.restore<AssessmentDraft>(
      KpiMyKpisComponent.DRAFT_KIND, kpi.id, this.serverSnapshot
    );

    this.formMsg = '';
    this.formMsgIsError = false;
    this.showModal = true;
  }

  /** Applies the recovered draft over the form. */
  // noinspection JSUnusedGlobalSymbols - used in template
  restoreDraft(): void {
    if (!this.pendingDraft) return;
    const { actualValue, comments, justification } = this.pendingDraft.payload;
    this.assessForm = { ...this.assessForm, actualValue, comments, justification };
    this.draftSavedAt = this.pendingDraft.savedAt;
    this.pendingDraft = null;
  }

  /** Rejects the recovered draft and removes it for good. */
  // noinspection JSUnusedGlobalSymbols - used in template
  discardDraft(): void {
    if (this.selectedKpi) {
      this.drafts.discard(KpiMyKpisComponent.DRAFT_KIND, this.selectedKpi.id);
    }
    this.pendingDraft = null;
    this.draftSavedAt = null;
  }

  /**
   * Called on every edit. Debounced so a burst of typing writes once.
   */
  // noinspection JSUnusedGlobalSymbols - used in template
  onAssessmentInput(): void {
    if (this.autosaveTimer) {
      clearTimeout(this.autosaveTimer);
    }
    this.autosaveTimer = setTimeout(
      () => this.flushAutosave(),
      KpiMyKpisComponent.AUTOSAVE_DEBOUNCE_MS
    );
  }

  /** Writes the draft immediately, cancelling any pending debounce. */
  private flushAutosave(): void {
    if (this.autosaveTimer) {
      clearTimeout(this.autosaveTimer);
      this.autosaveTimer = null;
    }
    if (!this.showModal || !this.selectedKpi) return;

    // Storing a draft identical to the server record would show "unsaved
    // changes" to someone who has changed nothing.
    if (!this.hasUnsavedChanges()) {
      this.drafts.discard(KpiMyKpisComponent.DRAFT_KIND, this.selectedKpi.id);
      this.draftSavedAt = null;
      return;
    }

    this.drafts.save(KpiMyKpisComponent.DRAFT_KIND, this.selectedKpi.id, this.snapshotOf(this.assessForm));
    this.draftSavedAt = new Date();
  }

  /** Whether the form diverges from what the server last confirmed. */
  hasUnsavedChanges(): boolean {
    if (!this.serverSnapshot) return false;
    return JSON.stringify(this.snapshotOf(this.assessForm)) !== JSON.stringify(this.serverSnapshot);
  }

  // ── Automated actuals ───────────────────────────────────────────────────

  /**
   * Asks the connected system what this KPI currently measures.
   *
   * <p>The result is only ever displayed. Writing it into the form would make
   * the employee's submission something they did not author, which is the one
   * thing a performance record cannot afford.</p>
   */
  // noinspection JSUnusedGlobalSymbols - used in template
  collectMetric(): void {
    if (!this.selectedKpi || this.collectingMetric) return;

    this.collectingMetric = true;
    this.metricResult = null;
    const kpi = this.selectedKpi;

    this.configService.collectMetric(kpi.id).pipe(
      finalize(() => {
        this.collectingMetric = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: (result) => {
        if (this.selectedKpi !== kpi) return;
        this.metricResult = result;
        if (result.outcome === 'COLLECTED' && this.selectedKpi) {
          // Mirror onto the instance so the badge survives closing and
          // reopening the dialog without another round trip.
          this.selectedKpi.suggestedActualValue = result.value ?? null;
          this.selectedKpi.suggestedActualSource = result.source ?? null;
          this.selectedKpi.suggestedActualSummary = result.summary ?? null;
          this.selectedKpi.suggestedActualUrl = result.detailUrl ?? null;
          this.selectedKpi.suggestedActualAt = result.collectedAt ?? null;
        }
      },
      error: (err) => {
        if (this.selectedKpi !== kpi) return;
        this.metricResult = {
          outcome: 'FAILED',
          message: this.extractError(err, 'Could not reach the connected system.')
        };
      }
    });
  }

  /** The suggestion worth showing: freshly collected, or stored from before. */
  get suggestedValue(): number | null {
    if (this.metricResult?.outcome === 'COLLECTED') {
      return this.metricResult.value ?? null;
    }
    return this.selectedKpi?.suggestedActualValue ?? null;
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  get suggestedSummary(): string | null {
    return this.metricResult?.outcome === 'COLLECTED'
      ? (this.metricResult.summary ?? null)
      : (this.selectedKpi?.suggestedActualSummary ?? null);
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  get suggestedUrl(): string | null {
    return this.metricResult?.outcome === 'COLLECTED'
      ? (this.metricResult.detailUrl ?? null)
      : (this.selectedKpi?.suggestedActualUrl ?? null);
  }

  /** True once the employee's entry already matches the suggestion. */
  // noinspection JSUnusedGlobalSymbols - used in template
  get suggestionAlreadyApplied(): boolean {
    const suggested = this.suggestedValue;
    if (suggested === null || this.assessForm.actualValue == null) return false;
    return Number(this.assessForm.actualValue) === suggested;
  }

  /**
   * Copies the suggestion into the form. An explicit act, which is what makes
   * the submitted number something the employee stands behind.
   */
  // noinspection JSUnusedGlobalSymbols - used in template
  applySuggestion(): void {
    const suggested = this.suggestedValue;
    if (suggested === null) return;
    this.assessForm.actualValue = suggested;
    this.onAssessmentInput();
  }

  private snapshotOf(form: { actualValue: number | null; comments: string; justification: string }): AssessmentDraft {
    return {
      // Normalised so "5" typed into a number input never looks different
      // from the numeric 5 the API returned.
      actualValue: form.actualValue === null || form.actualValue === undefined || form.actualValue as unknown === ''
        ? null
        : Number(form.actualValue),
      comments: form.comments ?? '',
      justification: form.justification ?? ''
    };
  }

  closeModal(): void {
    if (this.submitting) return;
    // Persist before tearing down: closing the dialog is the most common way
    // to lose work, and the draft is exactly what makes it recoverable.
    this.flushAutosave();

    this.showModal = false;
    this.selectedKpi = null;
    this.serverSnapshot = null;
    this.pendingDraft = null;
    this.draftSavedAt = null;
    this.metricResult = null;
    this.assessForm = {
      actualValue: null,
      comments: '',
      justification: '',
      evidenceFile: null,
      evidenceTitle: ''
    };
    this.formMsg = '';
    this.formMsgIsError = false;
  }


  onEvidenceFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (!file) {
      this.assessForm.evidenceFile = null;
      return;
    }

    const allowed = ['application/pdf', 'image/jpeg', 'image/png'];
    if (!allowed.includes(file.type) || file.size > 10 * 1024 * 1024) {
      this.formMsg = 'Choose a PDF, JPG or PNG file up to 10 MB.';
      this.formMsgIsError = true;
      this.assessForm.evidenceFile = null;
      input.value = '';
      return;
    }

    this.assessForm.evidenceFile = file;
    if (!this.assessForm.evidenceTitle && file) {
      this.assessForm.evidenceTitle = file.name;
    }
  }

  async submitAssessment(): Promise<void> {
    if (!this.selectedKpi || this.submitting || this.confirmPopup.visible) return;
    const kpi = this.selectedKpi;

    // ✨ NEW: Comprehensive validation
    const validation = this.validateSubmissionForm(kpi);
    if (!validation.isValid) {
      this.formMsg = validation.errors[0] || 'Please fix the errors above.';
      this.formMsgIsError = true;
      return;
    }

    // Recommended fields are missing. Worth asking about, not worth blocking:
    // the employee may have a reason, and the default is to go back and finish.
    if (validation.warnings.length > 0) {
      const proceed = await this.confirmService.confirmed({
        title: 'Submit without everything filled in?',
        message: validation.warnings[0],
        confirmLabel: 'Submit anyway',
        cancelLabel: 'Keep editing'
      });
      if (!proceed) {
        this.formMsg = '';
        return;
      }
    }

    this.openConfirmPopup({
      title: 'Submit Self-Assessment',
      message: `Submit self-assessment for "${kpi.kpiName}"?`,
      actionLabel: 'Submit',
      dangerous: false,
      action: () => {
        this.submitting = true;
        const payload: { actualValue?: number; comments?: string; justification?: string } = {};
        if (this.assessForm.actualValue !== null && this.assessForm.actualValue !== undefined) {
          payload.actualValue = Number(this.assessForm.actualValue);
        }
        if (this.assessForm.comments?.trim()) payload.comments = this.assessForm.comments.trim();
        if (this.assessForm.justification?.trim()) payload.justification = this.assessForm.justification.trim();

        const file = this.assessForm.evidenceFile;
        const submission = file
          ? this.configService.uploadEvidenceFile(kpi.id, file, this.assessForm.evidenceTitle || file.name).pipe(
              switchMap(evidence => {
                this.assessForm.evidenceFile = null;
                this.evidenceByKpi[kpi.id] = [...(this.evidenceByKpi[kpi.id] ?? []), evidence];
                return this.configService.submitSelfAssessment(kpi.id, payload);
              }))
          : this.configService.submitSelfAssessment(kpi.id, payload);
        submission.subscribe({
          next: (updated) => {
            // The work is on the server now, so the local copy is obsolete.
            // Cleared before closeModal so its flush cannot re-persist it.
            this.drafts.discard(KpiMyKpisComponent.DRAFT_KIND, kpi.id);
            this.serverSnapshot = null;

            const finalize = (message: string) => {
              const idx = this.allMyKpis.findIndex(existing => existing.id === kpi.id);
              if (idx !== -1) this.allMyKpis[idx] = updated;
              this.applyFilters();
              this.submitting = false;
              this.closeModal();
              this.showPageMsg(message, false);
            };

            finalize(file ? '✅ Self-assessment and evidence submitted successfully.' : '✅ Self-assessment submitted successfully.');
          },
          error: (err) => {
            this.formMsg = '❌ ' + this.extractError(err, 'Failed to submit self-assessment.');
            this.formMsgIsError = true;
          }
        }).add(() => { this.submitting = false; });
      }
    });
  }

  submitAllAssessments(): void {
    const pending = this.filteredKpis.filter(k => this.canAssess(k));
    if (pending.length === 0 || this.bulkSubmitting) {
      return;
    }

    const missingActual = pending.filter(k => k.actualValue == null);
    if (missingActual.length > 0) {
      this.showPageMsg(`Please add Actual Value for ${missingActual.length} KPI(s) before bulk submit.`, true);
      return;
    }

    this.openConfirmPopup({
      title: 'Submit All Assessments',
      message: `Submit ${pending.length} KPI self-assessments at once?`,
      actionLabel: 'Submit All',
      dangerous: false,
      action: () => {
        this.bulkSubmitting = true;
        this.submitAllSequential(pending, 0, 0, 0);
      }
    });
  }

  private submitAllSequential(items: KpiInstance[], index: number, successCount: number, failedCount: number): void {
    if (index >= items.length) {
      this.bulkSubmitting = false;
      const isError = failedCount > 0;
      const msg = isError
        ? `Submitted ${successCount} KPI(s), ${failedCount} failed.`
        : `✅ Submitted all ${successCount} KPI(s) successfully.`;
      this.showPageMsg(msg, isError);
      this.loadMyKpis();
      return;
    }

    const current = items[index];
    const payload: { actualValue?: number; comments?: string; justification?: string } = {};
    if (current.actualValue != null) payload.actualValue = Number(current.actualValue);
    if (current.comments?.trim()) payload.comments = current.comments.trim();
    if (current.justification?.trim()) payload.justification = current.justification.trim();

    this.configService.submitSelfAssessment(current.id, payload).subscribe({
      next: () => {
        this.drafts.discard(KpiMyKpisComponent.DRAFT_KIND, current.id);
        this.submitAllSequential(items, index + 1, successCount + 1, failedCount);
      },
      error: () => this.submitAllSequential(items, index + 1, successCount, failedCount + 1)
    });
  }

  /** Whether a KPI is editable for self-assessment */
  canAssess(kpi: KpiInstance): boolean {
    return ['PENDING', 'REWORK_REQUIRED', 'REJECTED'].includes(kpi.status)
      && (!this.selectedCycle || ['OPEN', 'IN_PROGRESS', 'UNDER_REVIEW'].includes(this.selectedCycle.statusCode));
  }

   /** Whether the KPI was already submitted and can be re-assessed */
   // noinspection JSUnusedGlobalSymbols - used in template
   canEdit(kpi: KpiInstance): boolean {
    return this.canAssess(kpi);
  }

  get totalWeight(): number {
    return this.allMyKpis.reduce((sum, k) => sum + (k.weight ?? 0), 0);
  }

  get submittedCount(): number {
    return this.allMyKpis.filter(k => ['SUBMITTED', 'UNDER_REVIEW', 'APPROVED'].includes(k.status)).length;
  }

  get pendingCount(): number {
    return this.filteredKpis.filter(k => this.canAssess(k)).length;
  }

   // ✨ NEW: TrackBy functions for performance
   // noinspection JSUnusedGlobalSymbols - used in template trackBy
   trackByKpiId = (_: number, kpi: KpiInstance): number => kpi.id;
   // noinspection JSUnusedGlobalSymbols - used in template trackBy
   trackByCategory = (_: number, c: string): string => c;

   capAt100(value: number): number {
     return Math.min(value, 100);
   }

   progressPercentage(kpi: KpiInstance): number {
     const target = Number(kpi.target);
     const actual = Number(kpi.actualValue);
     if (!Number.isFinite(target) || target <= 0 || !Number.isFinite(actual)) return 0;
     return Math.max(0, Math.min(actual / target * 100, 100));
   }

   hasNumericTarget(kpi: KpiInstance): boolean {
     return Number.isFinite(Number(kpi.target)) && Number(kpi.target) > 0;
   }

   // noinspection JSUnusedGlobalSymbols - used in template
   kpiScore(kpi: KpiInstance): number | null {
     return kpi.rating ?? null;
   }

   // noinspection JSUnusedGlobalSymbols - used in template
   weightedScore(kpi: KpiInstance): number | null {
     if (kpi.rating == null) return null;
     return (kpi.rating * (kpi.weight ?? 0)) / 100;
   }

   // noinspection JSUnusedGlobalSymbols - used in template
   get totalWeightedScore(): number {
    return this.allMyKpis.reduce((sum, k) => sum + (this.weightedScore(k) ?? 0), 0);
  }

   // noinspection JSUnusedGlobalSymbols - used in template
   evidenceCount(kpiId: number): number {
     return this.evidenceByKpi[kpiId]?.length ?? 0;
   }

   // noinspection JSUnusedGlobalSymbols - used in template
   openEvidence(url: string): void {
    if (!url) return;
    if (url.startsWith('/api/kpi/review/evidence/file/')) {
      this.configService.downloadEvidenceFile(url).subscribe({
        error: err => this.showPageMsg(this.extractError(err, 'Failed to download evidence.'), true)
      });
      return;
    }
    window.open(url, '_blank', 'noopener,noreferrer');
  }

  statusClass(status: string): string {
    switch (status) {
      case 'PENDING':          return 'bg-slate-100 text-slate-600 border border-slate-300';
      case 'SUBMITTED':        return 'bg-blue-100 text-blue-700 border border-blue-300';
      case 'UNDER_REVIEW':     return 'bg-amber-100 text-amber-700 border border-amber-300';
      case 'APPROVED':         return 'bg-green-100 text-green-700 border border-green-300';
      case 'REJECTED':         return 'bg-rose-100 text-rose-700 border border-rose-300';
      case 'REWORK_REQUIRED':  return 'bg-orange-100 text-orange-700 border border-orange-300';
      default:                 return 'bg-slate-100 text-slate-500';
    }
  }

  ratingDisplay(rating: number | null | undefined): string {
    if (rating == null) return 'Pending Review';
    return `${rating.toFixed(1)} / 5.0`;
  }

  finalRatingLabel(kpi: KpiInstance): string {
    if (kpi.rating != null) return this.ratingDisplay(kpi.rating);
    if (kpi.status === 'PENDING' || kpi.status === 'REWORK_REQUIRED') return 'Not Submitted';
    return 'Pending Manager Rating';
  }

  private showPageMsg(msg: string, isError: boolean): void {
    this.pageMsg = msg;
    this.pageMsgIsError = isError;
    if (!isError) { setTimeout(() => { this.pageMsg = ''; }, 5000); }
  }

  private extractError(err: any, fallback: string): string {
    return extractHttpError(err, fallback);
  }

  private openConfirmPopup(config: {
    title: string;
    message: string;
    actionLabel: string;
    dangerous: boolean;
    action: () => void;
  }): void {
    this.confirmPopup = { visible: true, ...config };
  }

   cancelConfirmPopup(): void {
     this.confirmPopup.visible = false;
     this.confirmPopup.action = null;
   }

   // noinspection JSUnusedGlobalSymbols - used in template
   confirmPopupAction(): void {
     const action = this.confirmPopup.action;
     this.cancelConfirmPopup();
     if (action) action();
   }
}
