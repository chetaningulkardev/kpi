import { Component, OnInit, OnDestroy, ChangeDetectorRef, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ConfigurationService, KpiInstance, KpiEvidence, TeamAssignmentFlow, ReviewDecision, ManagerReviewPayload, BulkReviewResult, KpiStatusHistoryEntry } from '../../../services/configuration.service';
import { SkeletonComponent } from '../../shared/skeleton.component';
import { finalize } from 'rxjs';

import { extractHttpError } from '../../../services/http-error.util';
import { ConfirmService } from '../../../services/confirm.service';
import { AccessibleModalDirective } from '../../../directives/accessible-modal.directive';
@Component({
  selector: 'app-kpi-manager-review',
  standalone: true,
  imports: [AccessibleModalDirective, CommonModule, FormsModule, SkeletonComponent],
  templateUrl: './kpi-manager-review.component.html'
})
export class KpiManagerReviewComponent implements OnInit, OnDestroy {

  private readonly keyboardHandler = this.handleKeyboardShortcut.bind(this);

  allPendingKpis: KpiInstance[] = [];
  filteredPendingKpis: KpiInstance[] = [];
  loading = false;
  submitting = false;

  // ✨ NEW: Filtering and search
  searchTerm = '';
  statusFilter = 'All';
  categoryFilter = 'All';
  requesterFilter = 'All';
  categories: string[] = [];

  // ✨ NEW: Progress tracking
  reviewedKpis: Set<number> = new Set();
  selectedForBulk: Set<number> = new Set();
  private requesterMetaByKey = new Map<string, { reviewerName: string; roleFlow: string }>();
  private pendingOpenKpiId: number | null = null;

  /** Review modal */
  showModal = false;
  selectedKpi: KpiInstance | null = null;
  reviewForm: { rating: number; reviewerComments: string; decision: ReviewDecision } =
    { rating: 3, reviewerComments: '', decision: 'APPROVE' };
  formMsg = '';
  formMsgIsError = false;
  evidenceForSelected: KpiEvidence[] = [];
  evidenceLoading = false;
  evidenceError = '';

  /** Audit trail for the KPI open in the modal */
  historyForSelected: KpiStatusHistoryEntry[] = [];
  historyLoading = false;
  historyError = '';

  /** Bulk review */
  bulkSubmitting = false;
  bulkResult: BulkReviewResult | null = null;
  /** Rating applied by the bulk "Approve" action. */
  bulkRating = 3;

  /** Page-level flash message */
  pageMsg = '';
  pageMsgIsError = false;

  /** Confirmation popup */
  confirmPopup = {
    visible: false,
    title: '',
    message: '',
    actionLabel: 'Confirm',
    dangerous: false,
    action: null as null | (() => void)
  };

  // ✨ NEW: UI preferences
  // noinspection JSUnusedGlobalSymbols - used in template
  showKeyboardHelp = false;
  // noinspection JSUnusedGlobalSymbols - used in template
  showStats = false;
  // noinspection JSUnusedGlobalSymbols - used in template
  ratingOptions = [1, 2, 3, 4, 5];

  constructor(
    private configService: ConfigurationService,
    private cdr: ChangeDetectorRef,
    private ngZone: NgZone,
    private route: ActivatedRoute,
    private confirmService: ConfirmService
  ) {}

  ngOnInit(): void {
    this.route.queryParamMap.subscribe((params) => {
      const raw = params.get('openKpiId');
      const parsed = raw != null ? Number(raw) : NaN;
      this.pendingOpenKpiId = Number.isFinite(parsed) && parsed > 0 ? parsed : null;
      this.tryOpenKpiFromQuery();
    });
    this.loadPendingReviews();
    // ✨ NEW: Add keyboard shortcuts listener
    document.addEventListener('keydown', this.keyboardHandler);
  }

  ngOnDestroy(): void {
    document.removeEventListener('keydown', this.keyboardHandler);
  }

  loadPendingReviews(): void {
    this.loading = true;
    this.loadFailed = false;
    this.ngZone.run(() => {
      this.configService.getPendingReviews().pipe(
        finalize(() => {
          this.ngZone.run(() => {
            this.loading = false;
            this.cdr.markForCheck();
          });
        })
      ).subscribe({
        next: (kpis) => {
          this.ngZone.run(() => {
            this.allPendingKpis = kpis;
            this.selectedForBulk = new Set([...this.selectedForBulk].filter(id => kpis.some(kpi => kpi.id === id)));
            this.loadTeamFlowMeta();
            this.extractCategories();
            this.applyFilters();
            this.tryOpenKpiFromQuery();
          });
        },
        error: () => {
          this.ngZone.run(() => {
            this.loadFailed = true;
            this.showPageMsg('Failed to load pending reviews.', true);
          });
        }
      });
    });
  }

  // ✨ NEW METHODS ========================
  loadFailed = false;

  /** Extract unique categories from KPIs */
  private extractCategories(): void {
    const cats = new Set(this.allPendingKpis.map(k => k.categoryName ?? 'Other'));
    this.categories = Array.from(cats).sort();
  }

  /** Apply all active filters */
  private applyFilters(): void {
    const term = this.searchTerm.trim().toLowerCase();
    this.filteredPendingKpis = this.allPendingKpis.filter(kpi => {
      // Status filter
      if (this.statusFilter !== 'All' && kpi.status !== this.statusFilter) {
        return false;
      }

      // Category filter
      if (this.categoryFilter !== 'All' && (kpi.categoryName ?? 'Other') !== this.categoryFilter) {
        return false;
      }

      // Requester filter (unique user-based queue)
      if (this.requesterFilter !== 'All' && this.requesterKey(kpi) !== this.requesterFilter) {
        return false;
      }

      // Search filter
      if (term) {
        const searchText = `${kpi.kpiName ?? ''} ${kpi.kpiCode ?? ''} ${this.employeeName(kpi)} ${kpi.user?.email ?? ''}`.toLowerCase();
        if (!searchText.includes(term)) {
          return false;
        }
      }

      return true;
    });
  }

  /** Handle filter changes */
  onSearchChange(): void {
    this.applyFilters();
  }

  onStatusFilterChange(): void {
    this.applyFilters();
  }

  onCategoryFilterChange(): void {
    this.applyFilters();
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  onRequesterFilterChange(): void {
    this.applyFilters();
  }

  /** Clear all filters */
  clearFilters(): void {
    this.searchTerm = '';
    this.statusFilter = 'All';
    this.categoryFilter = 'All';
    this.requesterFilter = 'All';
    this.applyFilters();
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  get requesterOptions(): Array<{ key: string; label: string }> {
    return this.pendingRequestsByUser.map(g => ({
      key: g.key,
      label: `${g.employeeName} (${g.kpiCount})`
    }));
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  get pendingRequesterCount(): number {
    return this.pendingRequestsByUser.length;
  }

  get pendingRequestsByUser(): Array<{
    key: string;
    employeeName: string;
    email: string;
    reviewerName: string;
    roleFlow: string;
    kpiCount: number;
    categories: string[];
    statuses: string[];
  }> {
    const grouped = new Map<string, {
      key: string;
      employeeName: string;
      email: string;
      reviewerNames: Set<string>;
      fromRoles: Set<string>;
      toRoles: Set<string>;
      kpiCount: number;
      categories: Set<string>;
      statuses: Set<string>;
    }>();

    this.allPendingKpis.forEach((kpi) => {
      const key = this.requesterKey(kpi);
      const existing = grouped.get(key) ?? {
        key,
        employeeName: this.employeeName(kpi),
        email: kpi.user?.email ?? '',
        reviewerNames: new Set<string>(),
        fromRoles: new Set<string>(),
        toRoles: new Set<string>(),
        kpiCount: 0,
        categories: new Set<string>(),
        statuses: new Set<string>()
      };

      existing.kpiCount += 1;
      existing.categories.add(kpi.categoryName ?? 'Other');
      existing.statuses.add(kpi.status ?? 'UNKNOWN');

      const meta = this.requesterMetaByKey.get(key);
      const reviewer = meta?.reviewerName || this.reviewerName(kpi);
      if (reviewer !== 'Unassigned reviewer') {
        existing.reviewerNames.add(reviewer);
      }

      if (meta?.roleFlow) {
        const parts = meta.roleFlow.split('->').map(p => p.trim());
        if (parts[0]) existing.fromRoles.add(parts[0]);
        if (parts[1]) existing.toRoles.add(parts[1]);
      } else {
        const fromRole = this.resolveRoleFromSnapshot(kpi, ['employeeRole', 'employeeRoleCode', 'submitterRole', 'roleCode']);
        if (fromRole) existing.fromRoles.add(fromRole);

        const toRole = this.resolveRoleFromSnapshot(kpi, ['reviewerRole', 'reviewerRoleCode', 'managerRole']);
        if (toRole) existing.toRoles.add(toRole);
      }

      grouped.set(key, existing);
    });

    return Array.from(grouped.values())
      .map((g) => {
        const fromLabel = g.fromRoles.size > 0 ? Array.from(g.fromRoles).join(' / ') : 'Employee';
        const toLabel = g.toRoles.size > 0 ? Array.from(g.toRoles).join(' / ') : 'Reviewer';
        return {
          key: g.key,
          employeeName: g.employeeName,
          email: g.email,
          reviewerName: g.reviewerNames.size > 0 ? Array.from(g.reviewerNames).join(', ') : 'Unassigned reviewer',
          roleFlow: `${fromLabel} -> ${toLabel}`,
          kpiCount: g.kpiCount,
          categories: Array.from(g.categories).sort(),
          statuses: Array.from(g.statuses).sort()
        };
      })
      .sort((a, b) => b.kpiCount - a.kpiCount || a.employeeName.localeCompare(b.employeeName));
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  focusRequester(key: string): void {
    this.requesterFilter = key;
    this.applyFilters();
  }

  /** Get progress stats */
  get totalPending(): number {
    return this.allPendingKpis.length;
  }

  get totalReviewed(): number {
    return this.reviewedKpis.size;
  }

  get progressPercentage(): number {
    const total = this.totalPending + this.totalReviewed;
    return total > 0 ? Math.round((this.totalReviewed / total) * 100) : 0;
  }

  /** Get statistics */
  // noinspection JSUnusedGlobalSymbols - used in template
  get ratingDistribution(): { [key: number]: number } {
    const dist: { [key: number]: number } = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 };
    this.allPendingKpis.forEach(kpi => {
      const rating = kpi.rating ?? 0;
      if (rating > 0 && rating <= 5) {
        dist[rating]++;
      }
    });
    return dist;
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  get averageRating(): number {
    const reviewed = this.allPendingKpis.filter(k => k.rating);
    if (reviewed.length === 0) return 0;
    const sum = reviewed.reduce((s, k) => s + (k.rating ?? 0), 0);
    return sum / reviewed.length;
  }

  /** Toggle bulk selection */
  toggleBulkSelect(kpiId: number): void {
    if (this.selectedForBulk.has(kpiId)) {
      this.selectedForBulk.delete(kpiId);
    } else {
      this.selectedForBulk.add(kpiId);
    }
  }

  /** Toggle all in filtered view */
  toggleSelectAll(): void {
    const allSelected = this.filteredPendingKpis.every(k => this.selectedForBulk.has(k.id));
    if (allSelected) {
      this.filteredPendingKpis.forEach(k => this.selectedForBulk.delete(k.id));
    } else {
      this.filteredPendingKpis.forEach(k => this.selectedForBulk.add(k.id));
    }
  }

  /** Download reviews as CSV */
  downloadAsCSV(): void {
    const headers = ['Employee', 'Email', 'KPI', 'Category', 'Weight', 'Target', 'Actual', 'Status'];
    const rows = this.filteredPendingKpis.map(kpi => [
      this.employeeName(kpi),
      kpi.user?.email ?? '',
      kpi.kpiName,
      kpi.categoryName ?? 'Other',
      kpi.weight ?? '',
      kpi.target ?? '',
      kpi.actualValue ?? '',
      kpi.status
    ]);

    const csv = [
      headers.join(','),
      ...rows.map(row => row.map(cell => `"${String(cell).replace(/"/g, '""')}"`).join(','))
    ].join('\n');

    const blob = new Blob([csv], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `manager-reviews-${new Date().toISOString().split('T')[0]}.csv`;
    link.click();
    window.URL.revokeObjectURL(url);

    this.showPageMsg('✅ CSV downloaded successfully', false);
  }

  /** Keyboard shortcuts */
  private handleKeyboardShortcut(event: KeyboardEvent): void {
    if (!this.showModal) return;

    // Ctrl/Cmd + Enter = Submit review
    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
      event.preventDefault();
      this.submitReview();
    }

    // Escape = Close modal.
    //
    // Kept as a fallback, not as a duplicate of AccessibleModalDirective. The
    // directive stops Escape at the dialog panel, so this listener — bound to
    // document, on the bubble phase — never sees the event while focus is
    // inside the dialog. It only fires when focus has landed on <body>, which
    // happens after a click on non-focusable text in the panel. The two paths
    // are mutually exclusive; the modal cannot be closed twice.
    if (event.key === 'Escape') {
      event.preventDefault();
      this.closeModal();
    }
  }

  /** Validate review data */
  private validateReview(): { isValid: boolean; warnings: string[]; error?: string } {
    const warnings: string[] = [];
    const decision = this.reviewForm.decision;
    const hasComments = !!this.reviewForm.reviewerComments?.trim();

    // Sending work back must explain why — the employee has to act on it.
    if (decision !== 'APPROVE') {
      if (!hasComments) {
        return {
          isValid: false,
          warnings: [],
          error: decision === 'REWORK'
            ? 'Please explain what needs reworking.'
            : 'Please explain why this KPI is rejected.'
        };
      }
      return { isValid: true, warnings };
    }

    // Approving requires a rating in range.
    if (!this.reviewForm.rating || this.reviewForm.rating < 1 || this.reviewForm.rating > 5) {
      return { isValid: false, warnings: [], error: 'Rating must be between 1 and 5.' };
    }

    // Recommend comments for extreme ratings
    if ((this.reviewForm.rating < 2 || this.reviewForm.rating > 4) && !hasComments) {
      warnings.push(
        `For ${this.reviewForm.rating < 2 ? 'low' : 'high'} ratings (${this.reviewForm.rating}/5), ` +
        `commenting is recommended to provide feedback.`
      );
    }

    return { isValid: true, warnings };
  }

  openReview(kpi: KpiInstance): void {
    this.selectedKpi = kpi;
    this.reviewForm = { rating: 3, reviewerComments: '', decision: 'APPROVE' };
    this.formMsg = '';
    this.formMsgIsError = false;
    this.loadEvidence(kpi.id);
    this.loadHistory(kpi.id);
    this.showModal = true;
  }

  closeModal(): void {
    if (this.submitting) return;
    this.showModal = false;
    this.selectedKpi = null;
    this.reviewForm = { rating: 3, reviewerComments: '', decision: 'APPROVE' };
    this.formMsg = '';
    this.formMsgIsError = false;
    this.evidenceForSelected = [];
    this.evidenceLoading = false;
    this.evidenceError = '';
    this.historyForSelected = [];
    this.historyLoading = false;
    this.historyError = '';
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  setDecision(decision: ReviewDecision): void {
    this.reviewForm.decision = decision;
    this.formMsg = '';
    this.formMsgIsError = false;
  }

  private loadHistory(kpiId: number): void {
    this.historyLoading = true;
    this.historyError = '';
    this.configService.getKpiStatusHistory(kpiId).subscribe({
      next: (entries) => {
        if (this.selectedKpi?.id !== kpiId) return;
        this.historyForSelected = entries || [];
        this.historyLoading = false;
      },
      error: () => {
        if (this.selectedKpi?.id !== kpiId) return;
        this.historyError = 'Failed to load status history.';
        this.historyForSelected = [];
        this.historyLoading = false;
      }
    });
  }

  private loadEvidence(kpiId: number): void {
    this.evidenceLoading = true;
    this.evidenceError = '';
    this.configService.getEvidenceForKpi(kpiId).subscribe({
      next: (evidence) => {
        if (this.selectedKpi?.id !== kpiId) return;
        this.evidenceForSelected = evidence;
        this.evidenceLoading = false;
      },
      error: () => {
        if (this.selectedKpi?.id !== kpiId) return;
        this.evidenceError = 'Failed to load evidence. Reopen this KPI to retry.';
        this.evidenceForSelected = [];
        this.evidenceLoading = false;
      }
    });
  }

  submitReview(): void {
    if (!this.selectedKpi || this.submitting || this.confirmPopup.visible) return;

    // ✨ NEW: Use comprehensive validation
    const validation = this.validateReview();
    if (!validation.isValid) {
      this.formMsg = validation.error || 'Please fix the errors above.';
      this.formMsgIsError = true;
      return;
    }

    // Comments are recommended, not required, for an extreme rating. Asking
    // rather than blocking, and defaulting to "go back and add one".
    if (validation.warnings.length > 0) {
      this.confirmService.ask({
        title: 'Submit without comments?',
        message: validation.warnings[0],
        confirmLabel: 'Submit anyway',
        cancelLabel: 'Add a comment'
      }).subscribe(outcome => {
        if (outcome.confirmed) {
          this.confirmAndSubmitReview();
        }
      });
      return;
    }

    this.confirmAndSubmitReview();
  }

  /** The confirmation and request, once validation has been satisfied. */
  private confirmAndSubmitReview(): void {
    if (!this.selectedKpi) return;

    const kpi = this.selectedKpi;
    const employeeName = this.employeeName(kpi);
    const decision = this.reviewForm.decision;

    this.openConfirmPopup({
      title: this.decisionTitle(decision),
      message: this.decisionMessage(decision, kpi.kpiName, employeeName),
      actionLabel: this.decisionActionLabel(decision),
      dangerous: decision === 'REJECT',
      action: () => {
        this.submitting = true;
        const payload: ManagerReviewPayload = { decision };
        if (decision === 'APPROVE') {
          payload.rating = this.reviewForm.rating;
        }
        if (this.reviewForm.reviewerComments?.trim()) {
          payload.reviewerComments = this.reviewForm.reviewerComments.trim();
        }

        this.configService.submitManagerReview(kpi.id, payload).subscribe({
          next: () => {
            // ✨ NEW: Track reviewed KPI
            this.reviewedKpis.add(kpi.id);

            // ✨ NEW: Remove from all lists
            this.allPendingKpis = this.allPendingKpis.filter(k => k.id !== kpi.id);
            this.selectedForBulk.delete(kpi.id);
            this.applyFilters();

            this.submitting = false;
            this.closeModal();
            this.showPageMsg(`✅ ${this.decisionPastTense(decision)} for ${employeeName} — ${kpi.kpiName}.`, false);
          },
          error: (err) => {
            this.formMsg = '❌ ' + this.extractError(err, 'Failed to submit review.');
            this.formMsgIsError = true;
          }
        }).add(() => { this.submitting = false; });
      }
    });
  }

  // ── Bulk review ───────────────────────────────────────────────────────────

  // noinspection JSUnusedGlobalSymbols - used in template
  get selectedForBulkCount(): number {
    return this.selectedForBulk.size;
  }

  /** Label for the modal's primary button, driven by the chosen decision. */
  // noinspection JSUnusedGlobalSymbols - used in template
  get submitButtonLabel(): string {
    switch (this.reviewForm.decision) {
      case 'REWORK': return '↩ Send Back';
      case 'REJECT': return '✕ Reject KPI';
      default:       return '⭐ Approve & Rate';
    }
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  clearBulkSelection(): void {
    this.selectedForBulk.clear();
  }

  /**
   * Apply one decision to every selected KPI. Rework/reject need a reason, so
   * those are collected up front rather than per KPI.
   */
  // noinspection JSUnusedGlobalSymbols - used in template
  bulkReview(decision: ReviewDecision, rating?: number): void {
    const ids = Array.from(this.selectedForBulk);
    if (ids.length === 0 || this.bulkSubmitting) {
      return;
    }

    const effectiveRating = rating ?? 3;

    // Approving needs no explanation; sending work back does. The reason is
    // collected in the same dialog that confirms the action, so there is one
    // decision point rather than two, and the text is validated before
    // anything is sent.
    this.confirmService.ask({
      title: `${this.decisionTitle(decision)} (${ids.length})`,
      message: decision === 'APPROVE'
        ? `Approve ${ids.length} KPI(s) with a rating of ${effectiveRating}/5?`
        : `${this.decisionActionLabel(decision)} for ${ids.length} KPI(s)? Everyone affected will see this reason.`,
      confirmLabel: this.decisionActionLabel(decision),
      dangerous: decision === 'REJECT',
      reason: decision === 'APPROVE' ? undefined : {
        label: decision === 'REWORK'
          ? `Why do these ${ids.length} KPI(s) need rework?`
          : `Why are these ${ids.length} KPI(s) rejected?`,
        required: true,
        maxLength: 2000
      }
    }).subscribe(outcome => {
      if (!outcome.confirmed) {
        return;
      }

      this.bulkSubmitting = true;
      const payload: ManagerReviewPayload = { decision };
      if (decision === 'APPROVE') {
        payload.rating = effectiveRating;
      }
      if (outcome.reason) {
        payload.reviewerComments = outcome.reason;
      }

      this.configService.submitBulkManagerReview(ids, payload).subscribe({
        next: (result) => {
          this.bulkResult = result;
          const failedIds = new Set(result.failures.map(f => f.kpiId));
          const processed = ids.filter(id => !failedIds.has(id));

          processed.forEach(id => {
            this.reviewedKpis.add(id);
            this.selectedForBulk.delete(id);
          });
          this.allPendingKpis = this.allPendingKpis.filter(k => !processed.includes(k.id));
          this.applyFilters();

          if (result.failed > 0) {
            this.showPageMsg(
              `${result.succeeded} of ${result.requested} processed. ${result.failed} failed — see details below.`,
              true
            );
          } else {
            this.showPageMsg(`✅ ${result.succeeded} KPI(s) processed successfully.`, false);
          }
        },
        error: (err) => {
          this.showPageMsg('❌ ' + this.extractError(err, 'Bulk review failed.'), true);
        }
      }).add(() => { this.bulkSubmitting = false; });
    });
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  dismissBulkResult(): void {
    this.bulkResult = null;
  }

  private decisionTitle(decision: ReviewDecision): string {
    switch (decision) {
      case 'REWORK': return 'Request Rework';
      case 'REJECT': return 'Reject KPI';
      default:       return 'Submit Manager Review';
    }
  }

  private decisionActionLabel(decision: ReviewDecision): string {
    switch (decision) {
      case 'REWORK': return 'Send Back';
      case 'REJECT': return 'Reject';
      default:       return 'Submit Review';
    }
  }

  private decisionPastTense(decision: ReviewDecision): string {
    switch (decision) {
      case 'REWORK': return 'Rework requested';
      case 'REJECT': return 'KPI rejected';
      default:       return 'Review submitted';
    }
  }

  private decisionMessage(decision: ReviewDecision, kpiName: string, employeeName: string): string {
    switch (decision) {
      case 'REWORK':
        return `Send "${kpiName}" back to ${employeeName} for rework?`;
      case 'REJECT':
        return `Reject "${kpiName}" for ${employeeName}? The employee can revise and resubmit while the cycle is active.`;
      default:
        return `Rate "${kpiName}" for ${employeeName} with ${this.reviewForm.rating}/5?`;
    }
  }

  employeeName(kpi: KpiInstance): string {
    const u = kpi.user;
    if (!u) return 'Unknown';
    const first = u.firstName ?? u.first_name ?? '';
    const last = u.lastName ?? u.last_name ?? '';
    return [first, last].filter(Boolean).join(' ') || u.email || 'Unknown';
  }

  reviewerName(kpi: KpiInstance): string {
    const r = kpi.reviewer;
    if (!r) return 'Unassigned reviewer';
    const first = r.firstName ?? r.first_name ?? '';
    const last = r.lastName ?? r.last_name ?? '';
    return [first, last].filter(Boolean).join(' ') || 'Unassigned reviewer';
  }

  ratingLabel(r: number): string {
    switch (r) {
      case 1: return 'Far Below Expectations';
      case 2: return 'Below Expectations';
      case 3: return 'Meets Expectations';
      case 4: return 'Exceeds Expectations';
      case 5: return 'Outstanding';
      default: return '';
    }
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  ratingColor(r: number): string {
    switch (r) {
      case 1: return 'text-rose-600';
      case 2: return 'text-orange-500';
      case 3: return 'text-amber-500';
      case 4: return 'text-blue-600';
      case 5: return 'text-green-600';
      default: return 'text-slate-500';
    }
  }

  weightedScorePreview(weight: number | undefined, rating: number): number {
    return ((weight ?? 0) * rating) / 100;
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  openEvidence(url: string): void {
    if (!url) return;
    if (url.startsWith('/api/kpi/review/evidence/file/')) {
      this.configService.downloadEvidenceFile(url).subscribe({
        error: err => { this.formMsg = this.extractError(err, 'Failed to download evidence.'); this.formMsgIsError = true; }
      });
      return;
    }
    window.open(url, '_blank', 'noopener,noreferrer');
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  statusClass(status: string): string {
    switch (status) {
      case 'SUBMITTED':    return 'bg-blue-100 text-blue-700 border border-blue-300';
      case 'UNDER_REVIEW': return 'bg-amber-100 text-amber-700 border border-amber-300';
      default:             return 'bg-slate-100 text-slate-500';
    }
  }

  // ✨ NEW: TrackBy functions for performance - used in template
  // noinspection JSUnusedGlobalSymbols - used in template trackBy
  trackByKpiId = (_: number, kpi: KpiInstance): number => kpi.id;
  // noinspection JSUnusedGlobalSymbols - used in template trackBy
  trackByCategory = (_: number, c: string): string => c;
  // noinspection JSUnusedGlobalSymbols - used in template trackBy
  trackByRequesterKey = (_: number, g: { key: string }): string => g.key;

  private requesterKey(kpi: KpiInstance): string {
    const id = kpi.user?.id;
    if (id != null) return `id:${id}`;
    const email = (kpi.user?.email ?? '').trim().toLowerCase();
    if (email) return `email:${email}`;
    return `name:${this.employeeName(kpi).trim().toLowerCase()}`;
  }

  private loadTeamFlowMeta(): void {
    this.configService.getTeamAssignmentFlow().subscribe({
      next: (flows) => {
        this.requesterMetaByKey.clear();
        (flows || []).forEach((flow: TeamAssignmentFlow) => {
          const roleFlow = `${flow.fromRole?.roleName ?? 'Employee'} -> ${flow.toRole?.roleName ?? 'Reviewer'}`;
          (flow.requests || []).forEach((request) => {
            const key = request.userId != null
              ? `id:${request.userId}`
              : (request.email ? `email:${request.email.trim().toLowerCase()}` : `name:${request.employeeName.trim().toLowerCase()}`);
            this.requesterMetaByKey.set(key, {
              reviewerName: request.reviewerName || 'Unassigned reviewer',
              roleFlow
            });
          });
        });
      },
      error: () => {
        this.requesterMetaByKey.clear();
      }
    });
  }

  private resolveRoleFromSnapshot(kpi: KpiInstance, keys: string[]): string {
    if (!kpi.snapshotData) return '';
    try {
      const parsed = JSON.parse(kpi.snapshotData);
      for (const key of keys) {
        const value = parsed?.[key];
        if (typeof value === 'string' && value.trim()) {
          return value.trim();
        }
      }
      return '';
    } catch {
      return '';
    }
  }

  private tryOpenKpiFromQuery(): void {
    if (!this.pendingOpenKpiId) {
      return;
    }
    const target = this.allPendingKpis.find(k => k.id === this.pendingOpenKpiId);
    if (!target) {
      return;
    }
    this.openReview(target);
    this.pendingOpenKpiId = null;
  }

  private showPageMsg(msg: string, isError: boolean): void {
    this.pageMsg = msg;
    this.pageMsgIsError = isError;
    if (!isError) { setTimeout(() => { this.pageMsg = ''; }, 6000); }
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
