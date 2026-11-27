import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { ConfigurationService, KpiFrequency } from '../../../services/configuration.service';

import { extractHttpError } from '../../../services/http-error.util';
import { AccessibleModalDirective } from '../../../directives/accessible-modal.directive';
import { ConfirmService } from '../../../services/confirm.service';
@Component({
  selector: 'app-kpi-frequencies',
  standalone: true,
  imports: [AccessibleModalDirective, CommonModule, FormsModule],
  templateUrl: './kpi-frequencies.component.html'
})
export class KpiFrequenciesComponent implements OnInit {

  private readonly confirmService = inject(ConfirmService);
  frequencies: KpiFrequency[] = [];
  frequencyUsage: Record<number, number> = {};
  loading = false;
  loadError = '';
  saving = false;
  deletingId: number | null = null;

  pageMsg = '';
  pageMsgIsError = false;

  showModal = false;
  editingId: number | null = null;
  form = this.emptyForm();
  formMsg = '';
  formMsgIsError = false;

  constructor(private configService: ConfigurationService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.loadError = '';
    forkJoin({
      frequencies: this.configService.getKpiFrequencies(),
      masters: this.configService.getKpiMasters()
    }).subscribe({
      next: ({ frequencies, masters }) => {
        this.frequencies = frequencies;
        const usageByCode: Record<string, number> = {};
        for (const master of masters) {
          const code = master.frequency;
          if (code) usageByCode[code] = (usageByCode[code] ?? 0) + 1;
        }
        this.frequencyUsage = {};
        for (const frequency of frequencies) {
          if (frequency.id) {
            this.frequencyUsage[frequency.id] = usageByCode[frequency.frequencyCode] ?? 0;
          }
        }
        this.loading = false;
      },
      error: () => { this.loading = false; this.loadError = 'Failed to load frequencies and KPI usage.'; }
    });
  }

  private silentLoad(): void {
    this.loadError = '';
    forkJoin({
      frequencies: this.configService.getKpiFrequencies(),
      masters: this.configService.getKpiMasters()
    }).subscribe({
      next: ({ frequencies, masters }) => {
        this.frequencies = frequencies;
        const usageByCode: Record<string, number> = {};
        for (const master of masters) {
          const code = master.frequency;
          if (code) usageByCode[code] = (usageByCode[code] ?? 0) + 1;
        }
        this.frequencyUsage = {};
        for (const frequency of frequencies) {
          if (frequency.id) {
            this.frequencyUsage[frequency.id] = usageByCode[frequency.frequencyCode] ?? 0;
          }
        }
      },
      error: () => { this.loadError = 'Your change was saved, but frequencies and usage could not be refreshed.'; }
    });
  }

  openCreate(): void {
    this.editingId = null;
    this.form = this.emptyForm();
    this.formMsg = '';
    this.formMsgIsError = false;
    this.showModal = true;
  }

  openEdit(frequency: KpiFrequency): void {
    this.editingId = frequency.id ?? null;
    this.form = {
      frequencyCode: frequency.frequencyCode,
      frequencyName: frequency.frequencyName,
      description: frequency.description ?? '',
      displayOrder: frequency.displayOrder ?? 999,
      active: frequency.active ?? true
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
    if (!this.form.frequencyCode?.trim()) { this.setFormError('Frequency code is required.'); return; }
    if (!this.form.frequencyName?.trim()) { this.setFormError('Frequency name is required.'); return; }

    const code = this.form.frequencyCode.trim().toUpperCase();
    const name = this.form.frequencyName.trim().toLowerCase();
    const duplicateCode = this.frequencies.some((f) => f.id !== this.editingId && f.frequencyCode.toUpperCase() === code);
    const duplicateName = this.frequencies.some((f) => f.id !== this.editingId && f.frequencyName.toLowerCase() === name);
    if (duplicateCode) { this.setFormError('Frequency code already exists.'); return; }
    if (duplicateName) { this.setFormError('Frequency name already exists.'); return; }

    this.saving = true;
    const wasEditing = !!this.editingId;
    const req$ = wasEditing
      ? this.configService.updateKpiFrequency(this.editingId!, this.form)
      : this.configService.createKpiFrequency(this.form as KpiFrequency);

    req$.subscribe({
      next: () => {
        this.saving = false;
        this.closeModal();
        this.showPageMsg(wasEditing ? 'Frequency updated.' : 'Frequency created.', false);
        this.silentLoad();
      },
      error: (err) => { this.setFormError(this.extractError(err, 'Failed to save frequency.')); }
    }).add(() => { this.saving = false; });
  }

  async delete(frequency: KpiFrequency): Promise<void> {
    if (!frequency.id || !await this.confirmService.confirmed({ title: 'Confirm deletion', message: `Delete frequency "${frequency.frequencyCode}"?`, confirmLabel: 'Delete', dangerous: true })) return;
    const inUse = this.frequencyUsage[frequency.id] ?? 0;
    if (inUse > 0) {
      this.showPageMsg(`Cannot delete. This frequency is used by ${inUse} KPI master(s).`, true);
      return;
    }
    this.deletingId = frequency.id;
    this.configService.deleteKpiFrequency(frequency.id).subscribe({
      next: () => { this.showPageMsg('Frequency deleted.', false); this.silentLoad(); },
      error: (err) => { this.showPageMsg(this.extractError(err, 'Failed to delete frequency.'), true); }
    }).add(() => { this.deletingId = null; });
  }

  getUsageCount(frequency: KpiFrequency): number {
    if (!frequency.id) return 0;
    return this.frequencyUsage[frequency.id] ?? 0;
  }

  emptyForm(): Partial<KpiFrequency> {
    return { frequencyCode: '', frequencyName: '', description: '', displayOrder: 999, active: true };
  }

  private setFormError(msg: string): void {
    this.formMsg = msg;
    this.formMsgIsError = true;
  }

  private showPageMsg(msg: string, isError: boolean): void {
    this.pageMsg = msg;
    this.pageMsgIsError = isError;
    if (!isError) {
      setTimeout(() => { this.pageMsg = ''; }, 4000);
    }
  }

  private extractError(err: any, fallback: string): string {
    return extractHttpError(err, fallback);
  }
}
