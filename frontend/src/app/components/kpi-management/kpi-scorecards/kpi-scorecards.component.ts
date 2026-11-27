import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import {
  ConfigurationService,
  KpiScorecard,
  ReviewCycle,
  ScorecardSummary,
  CalibrationPayload
} from '../../../services/configuration.service';
import { SkeletonComponent } from '../../shared/skeleton.component';

import { extractHttpError } from '../../../services/http-error.util';
import { AccessibleModalDirective } from '../../../directives/accessible-modal.directive';
type CalibrationStatus = 'CALIBRATION' | 'FINAL' | 'PUBLISHED';

/**
 * Scorecard review and calibration.
 *
 * Calibration is the step where managers normalise ratings across a cycle, so
 * the table is deliberately sortable by score and shows the distribution up
 * front — outliers are what a calibration session is looking for.
 */
@Component({
  selector: 'app-kpi-scorecards',
  standalone: true,
  imports: [AccessibleModalDirective, CommonModule, FormsModule, SkeletonComponent],
  templateUrl: './kpi-scorecards.component.html'
})
export class KpiScorecardsComponent implements OnInit {

  cycles: ReviewCycle[] = [];
  selectedCycleCode = '';

  scorecards: KpiScorecard[] = [];
  filteredScorecards: KpiScorecard[] = [];
  summary: ScorecardSummary | null = null;

  loading = false;
  loadFailed = false;
  cyclesLoading = false;
  cyclesError = '';
  summaryLoading = false;
  summaryError = '';
  private loadVersion = 0;
  generating = false;
  exporting = false;

  searchTerm = '';
  statusFilter = 'All';
  sortBy: 'name' | 'score' = 'score';

  /** Calibration modal */
  showModal = false;
  selected: KpiScorecard | null = null;
  calibrationForm: { overallScore: number | null; notes: string; status: CalibrationStatus } =
    { overallScore: null, notes: '', status: 'CALIBRATION' };
  saving = false;
  formMsg = '';
  formMsgIsError = false;

  pageMsg = '';
  pageMsgIsError = false;

  readonly statusOptions: CalibrationStatus[] = ['CALIBRATION', 'FINAL', 'PUBLISHED'];

  constructor(private configService: ConfigurationService) {}

  ngOnInit(): void {
    this.loadCycles();
  }

  loadCycles(): void {
    this.cyclesLoading = true;
    this.cyclesError = '';
    this.configService.getReviewCycles().subscribe({
      next: (cycles) => {
        this.cyclesLoading = false;
        this.cycles = cycles ?? [];
        if (this.cycles.length > 0) {
          this.selectedCycleCode = this.cycles[0].code;
          this.loadScorecards();
        }
      },
      error: () => {
        this.cyclesLoading = false;
        this.cyclesError = 'Failed to load review cycles.';
      }
    });
  }

  onCycleChange(): void {
    this.loadScorecards();
  }

  loadScorecards(): void {
    const version = ++this.loadVersion;
    const cycleCode = this.selectedCycleCode;
    this.scorecards = [];
    this.filteredScorecards = [];
    this.summary = null;
    this.summaryError = '';
    this.summaryLoading = false;
    if (!cycleCode) {
      this.loading = false;
      return;
    }

    this.loading = true;
    this.loadFailed = false;

    this.configService.getScorecards(cycleCode)
      .pipe(finalize(() => { if (version === this.loadVersion) this.loading = false; }))
      .subscribe({
        next: (cards) => {
          if (version !== this.loadVersion) return;
          this.scorecards = cards ?? [];
          this.applyFilters();
          this.loadSummary();
        },
        error: (err) => {
          if (version !== this.loadVersion) return;
          this.loadFailed = true;
          this.scorecards = [];
          this.filteredScorecards = [];
          this.showPageMsg(this.extractError(err, 'Failed to load scorecards.'), true);
        }
      });
  }

  loadSummary(): void {
    const version = this.loadVersion;
    this.summaryLoading = true;
    this.summaryError = '';
    this.configService.getScorecardSummary(this.selectedCycleCode).subscribe({
      next: (s) => {
        if (version !== this.loadVersion) return;
        this.summary = s;
        this.summaryLoading = false;
      },
      error: () => {
        if (version !== this.loadVersion) return;
        this.summary = null;
        this.summaryLoading = false;
        this.summaryError = 'Scorecard summary could not be loaded. The scorecards below remain available.';
      }
    });
  }

  generate(): void {
    if (!this.selectedCycleCode || this.generating) return;

    this.generating = true;
    this.configService.generateScorecards(this.selectedCycleCode)
      .pipe(finalize(() => { this.generating = false; }))
      .subscribe({
        next: (res) => {
          this.showPageMsg(`✅ Generated ${res.scorecards} scorecard(s) for ${res.cycleCode}.`, false);
          this.loadScorecards();
        },
        error: (err) => this.showPageMsg(this.extractError(err, 'Failed to generate scorecards.'), true)
      });
  }

  exportCsv(): void {
    if (!this.selectedCycleCode || this.exporting) return;

    this.exporting = true;
    this.configService.exportScorecards(this.selectedCycleCode)
      .pipe(finalize(() => { this.exporting = false; }))
      .subscribe({
        next: (blob) => {
          const url = window.URL.createObjectURL(blob);
          const link = document.createElement('a');
          link.href = url;
          link.download = `scorecards-${this.selectedCycleCode}.csv`;
          link.click();
          window.URL.revokeObjectURL(url);
          this.showPageMsg('✅ Export downloaded.', false);
        },
        error: (err) => this.showPageMsg(this.extractError(err, 'Export failed.'), true)
      });
  }

  // ── Filtering & sorting ────────────────────────────────────────────────────

  applyFilters(): void {
    const term = this.searchTerm.trim().toLowerCase();

    const rows = this.scorecards.filter((s) => {
      if (this.statusFilter !== 'All' && s.status !== this.statusFilter) return false;
      if (term) {
        const haystack = `${s.employeeName ?? ''} ${s.role ?? ''} ${s.employeeLevel ?? ''}`.toLowerCase();
        if (!haystack.includes(term)) return false;
      }
      return true;
    });

    this.filteredScorecards = rows.sort((a, b) => {
      if (this.sortBy === 'name') {
        return (a.employeeName ?? '').localeCompare(b.employeeName ?? '');
      }
      // Unscored rows sink to the bottom rather than sorting as zero.
      const aScore = a.overallScore ?? -1;
      const bScore = b.overallScore ?? -1;
      return bScore - aScore;
    });
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.statusFilter = 'All';
    this.applyFilters();
  }

  get statusKeys(): string[] {
    return this.summary ? Object.keys(this.summary.statusDistribution ?? {}) : [];
  }

  get ratingKeys(): string[] {
    return this.summary ? Object.keys(this.summary.ratingDistribution ?? {}) : [];
  }

  categoryEntries(card: KpiScorecard): Array<{ name: string; score: number }> {
    const scores = card.categoryScores ?? {};
    return Object.keys(scores).map(name => ({ name, score: scores[name] }));
  }

  // ── Calibration modal ──────────────────────────────────────────────────────

  openCalibration(card: KpiScorecard): void {
    this.selected = card;
    this.calibrationForm = {
      overallScore: card.overallScore,
      notes: card.calibrationNotes ?? '',
      status: (this.statusOptions.includes(card.status as CalibrationStatus)
        ? card.status
        : 'CALIBRATION') as CalibrationStatus
    };
    this.formMsg = '';
    this.formMsgIsError = false;
    this.showModal = true;
  }

  closeModal(): void {
    if (this.saving) return;
    this.showModal = false;
    this.selected = null;
    this.formMsg = '';
    this.formMsgIsError = false;
  }

  saveCalibration(): void {
    if (!this.selected || this.saving) return;

    const notes = this.calibrationForm.notes?.trim() ?? '';
    // An unexplained score override is exactly what an audit will ask about.
    if (!notes) {
      this.formMsg = 'Calibration notes are required — explain the adjustment.';
      this.formMsgIsError = true;
      return;
    }

    const score = this.calibrationForm.overallScore;
    if (score != null && (!Number.isFinite(score) || score < 0 || score > 5)) {
      this.formMsg = 'Overall score must be between 0 and 5.';
      this.formMsgIsError = true;
      return;
    }

    const payload: CalibrationPayload = {
      overallScore: score,
      notes,
      status: this.calibrationForm.status
    };

    this.saving = true;
    this.configService.calibrateScorecard(this.selected.id, payload)
      .pipe(finalize(() => { this.saving = false; }))
      .subscribe({
        next: (updated) => {
          this.scorecards = this.scorecards.map(s => s.id === updated.id ? updated : s);
          this.applyFilters();
          this.loadSummary();
          this.saving = false;
          this.closeModal();
          this.showPageMsg(`✅ Calibration saved for ${updated.employeeName}.`, false);
        },
        error: (err) => {
          this.formMsg = this.extractError(err, 'Failed to save calibration.');
          this.formMsgIsError = true;
        }
      });
  }

  // ── Presentation helpers ───────────────────────────────────────────────────

  statusClass(status: string): string {
    switch (status) {
      case 'PUBLISHED':   return 'bg-green-100 text-green-700 border border-green-300';
      case 'FINAL':       return 'bg-blue-100 text-blue-700 border border-blue-300';
      case 'CALIBRATION': return 'bg-amber-100 text-amber-700 border border-amber-300';
      default:            return 'bg-slate-100 text-slate-600 border border-slate-300';
    }
  }

  scoreClass(score: number | null): string {
    if (score == null) return 'text-slate-400';
    if (score < 2) return 'text-rose-600';
    if (score < 3) return 'text-orange-500';
    if (score < 4) return 'text-amber-600';
    return 'text-emerald-600';
  }

  trackByScorecardId = (_: number, card: KpiScorecard): number => card.id;
  trackByName = (_: number, item: { name: string }): string => item.name;
  trackByKey = (_: number, key: string): string => key;

  private showPageMsg(msg: string, isError: boolean): void {
    this.pageMsg = msg;
    this.pageMsgIsError = isError;
    if (!isError) { setTimeout(() => { this.pageMsg = ''; }, 6000); }
  }

  private extractError(err: any, fallback: string): string {
    return extractHttpError(err, fallback);
  }
}
