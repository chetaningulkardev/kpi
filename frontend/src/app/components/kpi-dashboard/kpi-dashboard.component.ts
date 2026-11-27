import { Component, OnInit, ChangeDetectorRef, NgZone, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ConfirmService } from '../../services/confirm.service';
import { RouterLink } from '@angular/router';
import { ConfigurationService, KpiInstance, ReviewCycle } from '../../services/configuration.service';
import { AuthService } from '../../services/auth.service';
import { KpiCacheService } from '../../services/kpi-cache.service';
import { SkeletonComponent } from '../shared/skeleton.component';
import { finalize, switchMap } from 'rxjs';
import { PermissionsService } from '../../services/permissions.service';
import { isManagerUser } from '../../services/role-access.util';

@Component({
  selector: 'app-kpi-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, SkeletonComponent],
  templateUrl: './kpi-dashboard.component.html',
  styleUrls: ['./kpi-dashboard.component.css']
})
export class KPIDashboardComponent implements OnInit {

  private readonly confirmService = inject(ConfirmService);
  private readonly permissions = inject(PermissionsService);
  approvalError = '';

  get canReview(): boolean {
    return this.permissions.permissions()?.canReviewKpis ?? isManagerUser(this.authService.currentUserValue?.roles);
  }

  cycles: ReviewCycle[] = [];
  selectedCycleCode = '';
  kpis: KpiInstance[] = [];
  filteredKPIs: KpiInstance[] = [];
  loading = true;
  error: string | null = null;
  selectedCategory = 'All';
  categories: string[] = [];
  statusFilter = 'All';
  searchTerm = '';

  pendingApprovals: KpiInstance[] = [];
  filteredApprovals: KpiInstance[] = [];
  approvalStatusFilter = 'All';
  approvalSearch = '';
  selectedRolePreset = 'DEFAULT';

  // Inline submit form state
  expandedKpiId: number | null = null;
  viewMode: 'cards' | 'table' = 'cards';  // ✨ NEW: view toggle
  submitForm: {
    actualValue: number | null;
    comments: string;
    justification: string;
    evidenceFile: File | null;
    evidenceTitle: string;
  } = { actualValue: null, comments: '', justification: '', evidenceFile: null, evidenceTitle: '' };
  submitting = false;
  submitError: string | null = null;
  submitSuccess: string | null = null;

  constructor(
    private configService: ConfigurationService,
    private authService: AuthService,
    private kpiCache: KpiCacheService,
    private cdr: ChangeDetectorRef,
    private ngZone: NgZone
  ) {}

  ngOnInit(): void {
    this.applyRolePreset('DEFAULT');
    this.loadCycles();
  }

  loadCycles(): void {
    this.loading = true;
    this.error = null;
    this.kpiCache.getReviewCycles().pipe(
      finalize(() => {
        if (this.cycles.length === 0) {
          this.loading = false;
          this.cdr.detectChanges();
        }
      })
    ).subscribe({
      next: (cycles) => {
        this.cycles = cycles.filter(c =>
          ['OPEN', 'IN_PROGRESS', 'UNDER_REVIEW', 'CALIBRATION', 'CLOSED'].includes(c.statusCode)
        );
        if (this.cycles.length > 0) {
          const active = this.cycles.find(c => ['OPEN', 'IN_PROGRESS'].includes(c.statusCode));
          this.selectedCycleCode = active?.code ?? this.cycles[0].code;
          this.loadKPIs();
        } else {
          this.loading = false;
          this.cdr.detectChanges();
        }
      },
      error: () => {
        this.error = 'Failed to load review cycles.';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  loadKPIs(): void {
    if (!this.selectedCycleCode) return;
    this.loading = true;
    this.error = null;
    this.kpiCache.getMyKpis(this.selectedCycleCode).pipe(
      finalize(() => this.cdr.detectChanges())
    ).subscribe({
      next: (data) => {
        this.kpis = data;
        this.extractCategories();
        this.applyKpiFilters();
        this.loadPendingApprovals();
      },
      error: () => {
        this.error = 'Failed to load your KPIs for this cycle.';
        this.loading = false;
      }
    });
  }

  onCycleChange(): void {
    this.expandedKpiId = null;
    this.selectedCategory = 'All';
    this.statusFilter = 'All';
    this.searchTerm = '';
    this.approvalStatusFilter = 'All';
    this.approvalSearch = '';
    this.applyRolePreset(this.selectedRolePreset);
    this.loadKPIs();
  }

   get currentRoleNames(): string[] {
     const roles = this.authService.currentUserValue?.roles ?? [];
     return roles.map((r) => r.role_name).filter((name): name is string => !!name);
   }

   // noinspection JSUnusedGlobalSymbols - used in template
   get rolePresetOptions(): Array<{ code: string; label: string }> {
     const fromUser = this.currentRoleNames.map((name) => ({ code: this.normalizeRole(name), label: name }));
     return [{ code: 'DEFAULT', label: 'Default View' }, ...fromUser];
   }

   // noinspection JSUnusedGlobalSymbols - used in template
   onRolePresetChange(): void {
    this.applyRolePreset(this.selectedRolePreset);
     this.applyKpiFilters();
     this.applyApprovalFilters();
  }

  private applyRolePreset(roleCode: string): void {
    if (!roleCode || roleCode === 'DEFAULT') {
      this.statusFilter = 'All';
      this.approvalStatusFilter = 'All';
      return;
    }

    const normalized = this.normalizeRole(roleCode);
    const isReviewer = /LEAD|MANAGER|HEAD|OWNER|ADMIN/.test(normalized);
    const isContributor = /DEV|ENGINEER|TECH_FOCAL|ANALYST|QA/.test(normalized);

    if (isReviewer && !isContributor) {
      this.statusFilter = 'SUBMITTED';
      this.approvalStatusFilter = 'SUBMITTED';
      return;
    }

    if (isContributor && !isReviewer) {
      this.statusFilter = 'PENDING';
      this.approvalStatusFilter = 'All';
      return;
    }

    this.statusFilter = 'All';
    this.approvalStatusFilter = 'All';
  }

  private normalizeRole(value: string): string {
    return String(value || '')
      .trim()
      .toUpperCase()
      .replace(/[^A-Z0-9]+/g, '_')
      .replace(/^_+|_+$/g, '');
  }

  extractCategories(): void {
    const set = new Set(this.kpis.map(k => k.categoryName ?? 'Other'));
    this.categories = Array.from(set).sort();
  }

  filterByCategory(category: string): void {
    this.selectedCategory = category;
    this.applyKpiFilters();
  }

  filterByStatus(status: string): void {
    this.statusFilter = status;
    this.applyKpiFilters();
  }

  onSearchChange(): void {
    this.applyKpiFilters();
  }

  private applyKpiFilters(): void {
    const term = this.searchTerm.trim().toLowerCase();
    this.filteredKPIs = this.kpis.filter((kpi) => {
      const categoryMatch = this.selectedCategory === 'All' || (kpi.categoryName ?? 'Other') === this.selectedCategory;
      const statusMatch = this.statusFilter === 'All' || kpi.status === this.statusFilter;
      const text = `${kpi.kpiName ?? ''} ${kpi.kpiCode ?? ''} ${kpi.categoryName ?? ''}`.toLowerCase();
      const searchMatch = !term || text.includes(term);
      return categoryMatch && statusMatch && searchMatch;
    });
  }

  loadPendingApprovals(): void {
    this.approvalError = '';
    if (!this.canReview) {
      this.pendingApprovals = [];
      this.filteredApprovals = [];
      this.loading = false;
      return;
    }
    this.configService.getPendingReviews().pipe(
      finalize(() => {
        this.ngZone.run(() => {
          this.loading = false;
          this.cdr.markForCheck();
        });
      })
    ).subscribe({
      next: (data) => {
        this.ngZone.run(() => {
          this.pendingApprovals = data || [];
          this.applyApprovalFilters();
        });
      },
      error: () => {
        this.approvalError = 'Failed to load pending approvals. Please refresh.';
        this.ngZone.run(() => {
          this.pendingApprovals = [];
          this.filteredApprovals = [];
        });
      }
    });
  }

   filterApprovalStatus(status: string): void {
     this.approvalStatusFilter = status;
     this.applyApprovalFilters();
   }

   // noinspection JSUnusedGlobalSymbols - used in template
   onApprovalSearchChange(): void {
     this.applyApprovalFilters();
   }

  private applyApprovalFilters(): void {
    const term = this.approvalSearch.trim().toLowerCase();
    this.filteredApprovals = this.pendingApprovals.filter((kpi) => {
      const statusMatch = this.approvalStatusFilter === 'All' || kpi.status === this.approvalStatusFilter;
      const employeeText = `${this.employeeName(kpi)} ${kpi.user?.email ?? ''} ${kpi.kpiName ?? ''}`.toLowerCase();
      const searchMatch = !term || employeeText.includes(term);
      return statusMatch && searchMatch;
    });
  }

   get actionNeededCount(): number {
     return this.kpis.filter(k => this.canSubmit(k)).length;
   }

   get pendingCount(): number {
     return this.kpis.filter(k => k.status === 'PENDING').length;
   }

   get totalWeight(): number {
     return this.kpis.reduce((s, k) => s + (k.weight ?? 0), 0);
   }

   // noinspection JSUnusedGlobalSymbols - used in template
   get submittedCount(): number {
     return this.kpis.filter(k => ['SUBMITTED', 'UNDER_REVIEW', 'APPROVED'].includes(k.status)).length;
   }

  get approvedCount(): number {
    return this.kpis.filter(k => k.status === 'APPROVED').length;
  }

  get inReviewCount(): number {
    return this.kpis.filter(k => ['SUBMITTED', 'UNDER_REVIEW'].includes(k.status)).length;
  }

  get weightedScoreTotal(): number {
    return this.kpis.reduce((sum, k) => sum + (((k.rating ?? 0) * (k.weight ?? 0)) / 100), 0);
  }

  get pendingApprovalCount(): number {
    return this.filteredApprovals.length;
  }

  get totalApprovalCount(): number {
    return this.pendingApprovals.length;
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

  clearFilters(): void {
    this.searchTerm = '';
    this.statusFilter = 'All';
    this.selectedCategory = 'All';
    this.applyKpiFilters();
  }

   clearApprovalFilters(): void {
     this.approvalSearch = '';
     this.approvalStatusFilter = 'All';
     this.applyApprovalFilters();
   }

   // noinspection JSUnusedGlobalSymbols - used in template
   onEvidenceFileChange(event: Event): void {
     const input = event.target as HTMLInputElement;
      const file = input.files?.[0] ?? null;
      if (file && (!['application/pdf', 'image/jpeg', 'image/png'].includes(file.type) || file.size > 10 * 1024 * 1024)) {
        this.submitForm.evidenceFile = null;
        input.value = '';
        this.submitError = 'Choose a PDF, JPG or PNG file up to 10 MB.';
        return;
      }
      this.submitError = null;
      this.submitForm.evidenceFile = file;
     if (!this.submitForm.evidenceTitle && input.files?.[0]) {
       this.submitForm.evidenceTitle = input.files[0].name;
     }
   }

  isActualValueValid(): boolean {
    const v = this.submitForm.actualValue;
    return v !== null && v !== undefined && Number.isFinite(Number(v)) && Number(v) >= 0;
  }

  /**
   * Comprehensive validation for KPI submission.
   * Returns validation state object with individual field checks + overall validity.
   */
  validateSubmissionForm(kpi: KpiInstance): {
    isValid: boolean;
    errors: string[];
    warnings: string[];
  } {
    const errors: string[] = [];
    const warnings: string[] = [];

    // ✅ MANDATORY: Actual Value (must be ≥ 0)
    if (!this.isActualValueValid()) {
      errors.push('Actual value is required and must be ≥ 0');
    }

    // ⚠️  HIGHLY RECOMMENDED: Comments (for context)
    if (!this.submitForm.comments?.trim()) {
      warnings.push('Comments recommended to provide context for your submission');
    }

    // ⚠️  RECOMMENDED: Justification (for rework cases)
    if (kpi.status === 'REWORK_REQUIRED' && !this.submitForm.justification?.trim()) {
      warnings.push('Justification recommended when resubmitting after rework');
    }

    // ⚠️  RECOMMENDED: Evidence (for high-impact KPIs)
    if ((kpi.weight ?? 0) >= 20 && !this.submitForm.evidenceFile) {
      warnings.push('Consider uploading evidence for high-weight KPIs (≥20%)');
    }

    return {
      isValid: errors.length === 0,
      errors,
      warnings
    };
  }

  employeeName(kpi: KpiInstance): string {
    const u = kpi.user;
    if (!u) return 'Unknown';
    const first = u.firstName ?? u.first_name ?? '';
    const last = u.lastName ?? u.last_name ?? '';
    return [first, last].filter(Boolean).join(' ') || u.email || 'Unknown';
  }

  get overallKpiProgressPercentage(): number {
    if (this.kpis.length === 0) return 0;

    // Weighted average across all user KPIs
    const weighted = this.kpis
      .map((kpi) => ({
        weight: Number(kpi.weight ?? 0),
        progress: this.getProgressPercentage(kpi)
      }))
      .filter((entry) => entry.weight > 0);

    const totalWeight = weighted.reduce((sum, entry) => sum + entry.weight, 0);
    if (totalWeight <= 0) {
      // Fallback when weights are missing/zero: simple average across KPIs.
      const avg = this.kpis.reduce((sum, kpi) => sum + this.getProgressPercentage(kpi), 0) / this.kpis.length;
      return Math.min(Math.round(avg), 100);
    }

    const weightedProgress = weighted.reduce((sum, entry) => sum + (entry.progress * entry.weight), 0);
    return Math.min(Math.round(weightedProgress / totalWeight), 100);
  }

  getProgressPercentage(kpi: KpiInstance): number {
    const target = Number(kpi.target ?? 0);
    const actual = Number(kpi.actualValue ?? 0);
    if (target > 0) {
      return Math.min(Math.max((actual / target) * 100, 0), 100);
    }

    // Fallback to manager rating when target is unavailable (e.g. 4/5 => 80%).
    const rating = Number(kpi.rating ?? 0);
    if (rating > 0) {
      return Math.min(Math.max((rating / 5) * 100, 0), 100);
    }

    return 0;
  }

  /** True when a KPI has enough data (target+actual, or rating) to compute progress. */
  hasMeasurableProgress(kpi: KpiInstance): boolean {
    const target = Number(kpi.target ?? 0);
    const rating = Number(kpi.rating ?? 0);
    return (target > 0 && kpi.actualValue != null) || rating > 0;
  }

  /** Number of KPIs that actually contribute a measured value to the overall progress. */
  get measuredKpiCount(): number {
    return this.kpis.filter((kpi) => this.hasMeasurableProgress(kpi)).length;
  }

  /** Colour band for the overall progress bar based on achievement level. */
  get overallProgressBarClass(): string {
    const pct = this.overallKpiProgressPercentage;
    if (pct >= 80) return 'from-emerald-500 to-emerald-600';
    if (pct >= 50) return 'from-dt-500 to-dt-600';
    if (pct >= 25) return 'from-amber-400 to-amber-500';
    return 'from-rose-400 to-rose-500';
  }

  /** Human-readable label for the current overall achievement level. */
  get overallProgressLabel(): string {
    const pct = this.overallKpiProgressPercentage;
    if (pct >= 80) return 'On Track';
    if (pct >= 50) return 'Progressing';
    if (pct >= 25) return 'Needs Attention';
    return 'At Risk';
  }

  statusBadgeClass(status: string): string {
    switch (status) {
      case 'APPROVED':         return 'bg-emerald-100 text-emerald-700';
      case 'UNDER_REVIEW':     return 'bg-sky-100 text-sky-700';
      case 'SUBMITTED':        return 'bg-blue-100 text-blue-700';
      case 'REWORK_REQUIRED':  return 'bg-orange-100 text-orange-700';
      case 'REJECTED':         return 'bg-rose-100 text-rose-700';
      default:                 return 'bg-slate-100 text-slate-600';
    }
  }

   statusBorderClass(status: string): string {
     switch (status) {
       case 'APPROVED':        return 'border-emerald-200';
       case 'UNDER_REVIEW':    return 'border-sky-200';
       case 'SUBMITTED':       return 'border-blue-200';
       case 'REWORK_REQUIRED': return 'border-orange-200';
       case 'REJECTED':        return 'border-rose-200';
       default:                return 'border-slate-200';
     }
   }

   // noinspection JSUnusedGlobalSymbols - used in template
   progressBarClass(status: string): string {
     switch (status) {
       case 'APPROVED':        return 'from-emerald-500 to-emerald-600';
       case 'UNDER_REVIEW':    return 'from-sky-500 to-sky-600';
       case 'SUBMITTED':       return 'from-blue-500 to-blue-600';
       case 'REWORK_REQUIRED': return 'from-orange-400 to-orange-500';
       case 'REJECTED':        return 'from-rose-500 to-rose-600';
       default:                return 'from-dt-500 to-dt-600';
     }
   }

   // noinspection JSUnusedGlobalSymbols - used in template trackBy
   trackByCategory = (_: number, c: string): string => c;
   // noinspection JSUnusedGlobalSymbols - used in template trackBy
   trackByKpiId = (_: number, k: KpiInstance): number => k.id;
   // noinspection JSUnusedGlobalSymbols - used in template trackBy
   trackByApprovalId = (_: number, k: KpiInstance): number => k.id;

  canSubmit(kpi: KpiInstance): boolean {
    return ['PENDING', 'REWORK_REQUIRED', 'REJECTED'].includes(kpi.status)
      && (!this.selectedCycle || ['OPEN', 'IN_PROGRESS', 'UNDER_REVIEW'].includes(this.selectedCycle.statusCode));
  }

  toggleSubmitForm(kpi: KpiInstance): void {
    if (this.submitting) return;
    if (this.expandedKpiId === kpi.id) {
      this.expandedKpiId = null;
      this.submitError = null;
      this.submitSuccess = null;
    } else {
      this.expandedKpiId = kpi.id;
      this.submitForm = {
        actualValue: kpi.actualValue ?? null,
        comments: kpi.comments ?? '',
        justification: kpi.justification ?? '',
        evidenceFile: null,
        evidenceTitle: ''
      };
      this.submitError = null;
      this.submitSuccess = null;
    }
  }

  async submitKpi(kpi: KpiInstance): Promise<void> {
    if (this.submitting) return;

    // ✅ Comprehensive validation
    const validation = this.validateSubmissionForm(kpi);

    if (!validation.isValid) {
      this.submitError = validation.errors[0] || 'Please fix the errors above.';
      return;
    }

    // Recommended fields are missing. Worth asking about, not worth blocking.
    if (validation.warnings.length > 0) {
      const proceed = await this.confirmService.confirmed({
        title: 'Submit without everything filled in?',
        message: validation.warnings[0],
        confirmLabel: 'Submit anyway',
        cancelLabel: 'Keep editing'
      });
      if (!proceed) {
        this.submitError = null;
        return;
      }
    }

    this.submitting = true;
    this.submitError = null;
    this.submitSuccess = null;

    const payload: { actualValue?: number; comments?: string; justification?: string } = {};
    if (this.submitForm.actualValue != null) {
      payload.actualValue = Number(this.submitForm.actualValue);
    }
    if (this.submitForm.comments?.trim()) {
      payload.comments = this.submitForm.comments.trim();
    }
    if (this.submitForm.justification?.trim()) {
      payload.justification = this.submitForm.justification.trim();
    }

    const file = this.submitForm.evidenceFile;
    const submission = file
      ? this.configService.uploadEvidenceFile(kpi.id, file, this.submitForm.evidenceTitle || file.name).pipe(
          switchMap(() => {
            this.submitForm.evidenceFile = null;
            return this.configService.submitSelfAssessment(kpi.id, payload);
          }))
      : this.configService.submitSelfAssessment(kpi.id, payload);
    submission.subscribe({
      next: (updated) => {
        const idx = this.kpis.findIndex(k => k.id === kpi.id);
        if (idx !== -1) this.kpis[idx] = updated;
        this.applyKpiFilters();
        // ✨ Optimistic cache update — no need to re-fetch
        this.kpiCache.updateKpiInCache(this.selectedCycleCode, updated);
        this.submitSuccess = '✅ KPI submitted! ' +
          (this.statusFilter === 'PENDING' ? 'Switch to "Submitted" filter to see it.' : '');
        this.submitting = false;
        setTimeout(() => {
          this.expandedKpiId = null;
          this.submitSuccess = null;
        }, 2400);
      },
      error: (err) => {
        this.submitError = err?.error?.message ?? 'Failed to upload evidence or submit. Please try again.';
        this.submitting = false;
      }
    });
  }
}
