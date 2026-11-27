import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { ConfigurationService, KpiDataSource } from '../../../services/configuration.service';

import { extractHttpError } from '../../../services/http-error.util';
import { AccessibleModalDirective } from '../../../directives/accessible-modal.directive';
import { ConfirmService } from '../../../services/confirm.service';
@Component({
  selector: 'app-kpi-data-sources',
  standalone: true,
  imports: [AccessibleModalDirective, CommonModule, FormsModule],
  templateUrl: './kpi-data-sources.component.html'
})
export class KpiDataSourcesComponent implements OnInit {

  private readonly confirmService = inject(ConfirmService);
  dataSources: KpiDataSource[] = [];
  sourceUsage: Record<number, number> = {};
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
      sources: this.configService.getKpiDataSources(),
      masters: this.configService.getKpiMasters()
    }).subscribe({
      next: ({ sources, masters }) => {
        this.dataSources = sources;
        const usageByName: Record<string, number> = {};
        for (const master of masters) {
          const source = (master.dataSource ?? '').trim();
          if (source) usageByName[source.toLowerCase()] = (usageByName[source.toLowerCase()] ?? 0) + 1;
        }
        this.sourceUsage = {};
        for (const source of sources) {
          if (source.id) {
            this.sourceUsage[source.id] = usageByName[source.sourceName.toLowerCase()] ?? 0;
          }
        }
        this.loading = false;
      },
      error: () => { this.loading = false; this.loadError = 'Failed to load data sources and KPI usage.'; }
    });
  }

  private silentLoad(): void {
    this.loadError = '';
    forkJoin({
      sources: this.configService.getKpiDataSources(),
      masters: this.configService.getKpiMasters()
    }).subscribe({
      next: ({ sources, masters }) => {
        this.dataSources = sources;
        const usageByName: Record<string, number> = {};
        for (const master of masters) {
          const source = (master.dataSource ?? '').trim();
          if (source) usageByName[source.toLowerCase()] = (usageByName[source.toLowerCase()] ?? 0) + 1;
        }
        this.sourceUsage = {};
        for (const source of sources) {
          if (source.id) {
            this.sourceUsage[source.id] = usageByName[source.sourceName.toLowerCase()] ?? 0;
          }
        }
      },
      error: () => { this.loadError = 'Your change was saved, but data sources and usage could not be refreshed.'; }
    });
  }

  openCreate(): void {
    this.editingId = null;
    this.form = this.emptyForm();
    this.formMsg = '';
    this.formMsgIsError = false;
    this.showModal = true;
  }

  openEdit(source: KpiDataSource): void {
    this.editingId = source.id ?? null;
    this.form = {
      sourceCode: source.sourceCode,
      sourceName: source.sourceName,
      description: source.description ?? '',
      displayOrder: source.displayOrder ?? 999,
      active: source.active ?? true
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
    if (!this.form.sourceCode?.trim()) { this.setFormError('Data source code is required.'); return; }
    if (!this.form.sourceName?.trim()) { this.setFormError('Data source name is required.'); return; }

    const code = this.form.sourceCode.trim().toUpperCase();
    const name = this.form.sourceName.trim().toLowerCase();
    const duplicateCode = this.dataSources.some((s) => s.id !== this.editingId && s.sourceCode.toUpperCase() === code);
    const duplicateName = this.dataSources.some((s) => s.id !== this.editingId && s.sourceName.toLowerCase() === name);
    if (duplicateCode) { this.setFormError('Data source code already exists.'); return; }
    if (duplicateName) { this.setFormError('Data source name already exists.'); return; }

    this.saving = true;
    const wasEditing = !!this.editingId;
    const req$ = wasEditing
      ? this.configService.updateKpiDataSource(this.editingId!, this.form)
      : this.configService.createKpiDataSource(this.form as KpiDataSource);

    req$.subscribe({
      next: () => {
        this.saving = false;
        this.closeModal();
        this.showPageMsg(wasEditing ? 'Data source updated.' : 'Data source created.', false);
        this.silentLoad();
      },
      error: (err) => { this.setFormError(this.extractError(err, 'Failed to save data source.')); }
    }).add(() => { this.saving = false; });
  }

  async delete(source: KpiDataSource): Promise<void> {
    if (!source.id || !await this.confirmService.confirmed({ title: 'Confirm deletion', message: `Delete data source "${source.sourceCode}"?`, confirmLabel: 'Delete', dangerous: true })) return;
    const inUse = this.sourceUsage[source.id] ?? 0;
    if (inUse > 0) {
      this.showPageMsg(`Cannot delete. This data source is used by ${inUse} KPI master(s).`, true);
      return;
    }
    this.deletingId = source.id;
    this.configService.deleteKpiDataSource(source.id).subscribe({
      next: () => { this.showPageMsg('Data source deleted.', false); this.silentLoad(); },
      error: (err) => { this.showPageMsg(this.extractError(err, 'Failed to delete data source.'), true); }
    }).add(() => { this.deletingId = null; });
  }

  getUsageCount(source: KpiDataSource): number {
    if (!source.id) return 0;
    return this.sourceUsage[source.id] ?? 0;
  }

  emptyForm(): Partial<KpiDataSource> {
    return { sourceCode: '', sourceName: '', description: '', displayOrder: 999, active: true };
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
