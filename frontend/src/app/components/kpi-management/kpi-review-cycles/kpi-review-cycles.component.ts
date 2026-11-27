import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ConfigurationService, ReviewCycle } from '../../../services/configuration.service';

import { extractHttpError } from '../../../services/http-error.util';
import { AccessibleModalDirective } from '../../../directives/accessible-modal.directive';
@Component({
  selector: 'app-kpi-review-cycles',
  standalone: true,
  imports: [AccessibleModalDirective, CommonModule, FormsModule],
  templateUrl: './kpi-review-cycles.component.html'
})
export class KpiReviewCyclesComponent implements OnInit {
  cycles: ReviewCycle[] = [];
  loading = false;
  loadError = '';
  saving = false;
  deletingId: number | null = null;
  actingId: number | null = null;

  confirmPopup = {
    visible: false,
    title: '',
    message: '',
    actionLabel: 'Confirm',
    dangerous: false,
    action: null as null | (() => void)
  };

  /** Page-level flash message (after modal closes) */
  pageMsg = '';
  pageMsgIsError = false;

  /** Modal state */
  showModal = false;
  editingId: number | null = null;
  form = this.emptyForm();
  formMsg = '';
  formMsgIsError = false;

  statusOptions = ['DRAFT', 'OPEN', 'IN_PROGRESS', 'UNDER_REVIEW', 'CALIBRATION', 'CLOSED'];

  constructor(private configService: ConfigurationService) {}

  ngOnInit(): void {
    this.load();
  }

  /** Full load — shows loading spinner (used on first init only) */
  load(): void {
    this.loading = true;
    this.loadError = '';
    this.configService.getReviewCycles().subscribe({
      next: (cycles) => { this.cycles = cycles; this.loading = false; },
      error: () => { this.loading = false; this.loadError = 'Failed to load review cycles.'; }
    });
  }

  /** Silent reload — no spinner, used after save/delete to refresh list without flicker */
  private silentLoad(): void {
    this.configService.getReviewCycles().subscribe({
      next: (cycles) => { this.cycles = cycles; this.loadError = ''; },
      error: () => { this.loadError = 'Your change was saved, but review cycles could not be refreshed.'; }
    });
  }

  openCreate(): void {
    this.editingId = null;
    this.form = this.emptyForm();
    this.formMsg = '';
    this.formMsgIsError = false;
    this.showModal = true;
  }

  openEdit(cycle: ReviewCycle): void {
    this.editingId = cycle.id ?? null;
    this.form = {
      cycleName: cycle.name,
      cycleCode: cycle.code,
      startDate: cycle.startDate,
      endDate: cycle.endDate,
      status: cycle.statusCode,
      description: cycle.description ?? ''
    };
    this.formMsg = '';
    this.formMsgIsError = false;
    this.showModal = true;
  }

  closeModal(): void {
    if (this.saving) return;
    this.showModal = false;
    this.saving = false;
    this.editingId = null;
    this.form = this.emptyForm();
    this.formMsg = '';
    this.formMsgIsError = false;
  }

  save(): void {
    if (this.saving) return;
    this.formMsg = '';
    this.formMsgIsError = false;
    if (!this.form.cycleName?.trim()) { this.setFormErr('Cycle name is required.'); return; }
    if (!this.form.cycleCode?.trim()) { this.setFormErr('Cycle code is required.'); return; }
    if (!this.form.startDate)         { this.setFormErr('Start date is required.'); return; }
    if (!this.form.endDate)           { this.setFormErr('End date is required.'); return; }
    if (this.form.startDate >= this.form.endDate) { this.setFormErr('End date must be after start date.'); return; }

    this.saving = true;
    const wasEditing = !!this.editingId;
    const req$ = wasEditing
      ? this.configService.updateReviewCycle(this.editingId!, this.form)
      : this.configService.createReviewCycle(this.form);

    req$.subscribe({
      next: () => {
        this.saving = false;
        this.closeModal();
        this.showPageMsg(wasEditing ? '✅ Review cycle updated.' : '✅ Review cycle created.', false);
        this.silentLoad();
      },
      error: (err) => { this.setFormErr('❌ ' + this.extractError(err, 'Failed to save review cycle.')); }
    }).add(() => { this.saving = false; });
  }

  delete(cycle: ReviewCycle): void {
    if (!cycle.id) return;
    this.openConfirmPopup({
      title: 'Delete Review Cycle',
      message: `Delete cycle ${cycle.code}? This cannot be undone.`,
      actionLabel: 'Delete',
      dangerous: true,
      action: () => {
        this.deletingId = cycle.id!;
        this.configService.deleteReviewCycle(cycle.id!).subscribe({
          next: () => { this.showPageMsg('✅ Review cycle deleted.', false); this.silentLoad(); },
          error: (err) => { this.showPageMsg('❌ ' + this.extractError(err, 'Failed to delete review cycle.'), true); }
        }).add(() => { this.deletingId = null; });
      }
    });
  }

  openCycle(cycle: ReviewCycle): void {
    if (!cycle.id) return;
    this.openConfirmPopup({
      title: 'Open Review Cycle',
      message: `Open cycle ${cycle.code}?`,
      actionLabel: 'Open Cycle',
      dangerous: false,
      action: () => {
        this.actingId = cycle.id!;
        this.configService.openReviewCycle(cycle.id!).subscribe({
          next: () => {
            this.showPageMsg(`✅ Cycle ${cycle.code} opened.`, false);
            this.silentLoad();
          },
          error: (err) => this.showPageMsg('❌ ' + this.extractError(err, 'Failed to open cycle.'), true)
        }).add(() => { this.actingId = null; });
      }
    });
  }

  closeCycle(cycle: ReviewCycle): void {
    if (!cycle.id) return;
    this.openConfirmPopup({
      title: 'Close Review Cycle',
      message: `Close cycle ${cycle.code}?`,
      actionLabel: 'Close Cycle',
      dangerous: false,
      action: () => {
        this.actingId = cycle.id!;
        this.configService.closeReviewCycle(cycle.id!).subscribe({
          next: () => {
            this.showPageMsg(`✅ Cycle ${cycle.code} closed.`, false);
            this.silentLoad();
          },
          error: (err) => this.showPageMsg('❌ ' + this.extractError(err, 'Failed to close cycle.'), true)
        }).add(() => { this.actingId = null; });
      }
    });
  }

  generateKpis(cycle: ReviewCycle): void {
    if (!cycle.id) return;
    this.openConfirmPopup({
      title: 'Generate KPIs',
      message: `Generate KPIs for ${cycle.code}? This operation can take some time.`,
      actionLabel: 'Generate',
      dangerous: false,
      action: () => {
        this.actingId = cycle.id!;
        this.configService.generateCycleKpis(cycle.id!).subscribe({
          next: (msg) => {
            this.showPageMsg('✅ ' + (msg || `KPI generation triggered for ${cycle.code}.`), false);
            this.silentLoad();
          },
          error: (err) => this.showPageMsg('❌ ' + this.extractError(err, 'Failed to generate KPIs.'), true)
        }).add(() => { this.actingId = null; });
      }
    });
  }

  canOpen(cycle: ReviewCycle): boolean {
    return cycle.statusCode === 'DRAFT';
  }

  canClose(cycle: ReviewCycle): boolean {
    return ['OPEN', 'IN_PROGRESS', 'UNDER_REVIEW', 'CALIBRATION'].includes(cycle.statusCode);
  }

  canGenerate(cycle: ReviewCycle): boolean {
    return !cycle.kpisGenerated && ['OPEN', 'IN_PROGRESS'].includes(cycle.statusCode);
  }

  /** Returns Tailwind badge classes based on cycle status */
  statusClass(status: string): string {
    switch (status) {
      case 'DRAFT':        return 'bg-slate-100 text-slate-600 border border-slate-300';
      case 'OPEN':         return 'bg-green-100 text-green-700 border border-green-300';
      case 'IN_PROGRESS':  return 'bg-blue-100 text-blue-700 border border-blue-300';
      case 'UNDER_REVIEW': return 'bg-amber-100 text-amber-700 border border-amber-300';
      case 'CALIBRATION':  return 'bg-orange-100 text-orange-700 border border-orange-300';
      case 'CLOSED':       return 'bg-rose-100 text-rose-700 border border-rose-300';
      default:             return 'bg-slate-100 text-slate-500';
    }
  }

  emptyForm() {
    return { cycleName: '', cycleCode: '', startDate: '', endDate: '', status: 'DRAFT', description: '' };
  }

  private setFormErr(msg: string): void { this.formMsg = msg; this.formMsgIsError = true; }

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
    this.confirmPopup = {
      visible: true,
      title: config.title,
      message: config.message,
      actionLabel: config.actionLabel,
      dangerous: config.dangerous,
      action: config.action
    };
  }

  cancelConfirmPopup(): void {
    this.confirmPopup.visible = false;
    this.confirmPopup.action = null;
  }

  confirmPopupAction(): void {
    const action = this.confirmPopup.action;
    this.cancelConfirmPopup();
    if (action) action();
  }
}
