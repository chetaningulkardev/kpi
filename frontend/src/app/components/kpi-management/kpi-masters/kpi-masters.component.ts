import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ConfigurationService, KpiCategory, KpiDataSource, KpiFrequency, KpiMaster, RoleOption } from '../../../services/configuration.service';
import { forkJoin } from 'rxjs';

import { extractHttpError } from '../../../services/http-error.util';
import { AccessibleModalDirective } from '../../../directives/accessible-modal.directive';
import { ConfirmService } from '../../../services/confirm.service';
@Component({
  selector: 'app-kpi-masters',
  standalone: true,
  imports: [AccessibleModalDirective, CommonModule, FormsModule, RouterLink],
  templateUrl: './kpi-masters.component.html'
})
export class KpiMastersComponent implements OnInit {

  private readonly confirmService = inject(ConfirmService);
  masters: KpiMaster[] = [];
  categories: KpiCategory[] = [];
  frequencies: KpiFrequency[] = [];
  dataSources: KpiDataSource[] = [];
  roles: RoleOption[] = [];
  loading = false;
  loadError = '';
  saving = false;
  deletingId: number | null = null;

  roleFilterId: number | 'ALL' = 'ALL';
  categoryFilterId: number | 'ALL' = 'ALL';
  activeFilter: 'ALL' | 'ACTIVE' | 'INACTIVE' = 'ALL';
  searchTerm = '';

  pageMsg = '';
  pageMsgIsError = false;

  showModal = false;
  editingId: number | null = null;
  form = this.emptyForm();
  formMsg = '';
  formMsgIsError = false;

  kpiTypeOptions = ['PERCENTAGE', 'NUMBER', 'RATING', 'QUALITATIVE', 'BOOLEAN', 'FORMULA'];
  targetTypeOptions = ['PERCENTAGE_TARGET', 'NUMBER_TARGET', 'RATING_TARGET', 'QUALITATIVE_TARGET', 'TREND_TARGET'];
  get frequencyOptions(): string[] {
    return this.frequencies.map((f) => f.frequencyCode);
  }

  get dataSourceOptions(): string[] {
    return this.dataSources.map((source) => source.sourceName);
  }

  constructor(private configService: ConfigurationService) {}

  get filteredMasters(): KpiMaster[] {
    const q = this.searchTerm.trim().toLowerCase();
    return this.masters.filter((master) => {
      const roleMatch = this.roleFilterId === 'ALL'
        || (master.roleIds ?? []).includes(Number(this.roleFilterId));
      const categoryMatch = this.categoryFilterId === 'ALL'
        || master.categoryId === Number(this.categoryFilterId);
      const activeMatch = this.activeFilter === 'ALL'
        || (this.activeFilter === 'ACTIVE' ? !!master.active : !master.active);
      const textMatch = !q || [
        master.kpiCode,
        master.kpiName,
        master.categoryName,
        ...(master.roleNames ?? [])
      ].join(' ').toLowerCase().includes(q);
      return roleMatch && categoryMatch && activeMatch && textMatch;
    });
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.loadError = '';
    forkJoin({
      masters: this.configService.getKpiMasters(),
      categories: this.configService.getKpiCategories(),
      frequencies: this.configService.getKpiFrequencies(),
      dataSources: this.configService.getKpiDataSources(),
      roles: this.configService.getRoles()
    }).subscribe({
      next: ({ masters, categories, frequencies, dataSources, roles }) => {
        this.masters = masters;
        this.categories = categories;
        this.frequencies = frequencies;
        this.dataSources = dataSources;
        this.roles = (roles || []).filter((role) => role.active);
        if (!this.form.categoryId) {
          this.form.categoryId = this.categories[0]?.id || 0;
        }
        if (!this.form.frequency) {
          this.form.frequency = this.frequencies[0]?.frequencyCode || 'MONTHLY';
        }
        if (!this.form.dataSource) {
          this.form.dataSource = this.dataSources[0]?.sourceName || 'Manual';
        }
        this.loading = false;
      },
      error: () => { this.loadError = 'Failed to load KPI masters and configuration options.'; this.loading = false; }
    });
  }

  private silentLoad(): void {
    this.loadError = '';
    forkJoin({
      masters: this.configService.getKpiMasters(),
      categories: this.configService.getKpiCategories(),
      frequencies: this.configService.getKpiFrequencies(),
      dataSources: this.configService.getKpiDataSources(),
      roles: this.configService.getRoles()
    }).subscribe({
      next: ({ masters, categories, frequencies, dataSources, roles }) => {
        this.masters = masters;
        this.categories = categories;
        this.frequencies = frequencies;
        this.dataSources = dataSources;
        this.roles = (roles || []).filter((role) => role.active);
      },
      error: () => { this.loadError = 'Your change was saved, but KPI masters and options could not be refreshed.'; }
    });
  }

  clearFilters(): void {
    this.roleFilterId = 'ALL';
    this.categoryFilterId = 'ALL';
    this.activeFilter = 'ALL';
    this.searchTerm = '';
  }

  openCreate(): void {
    this.editingId = null;
    this.form = this.emptyForm();
    this.form.categoryId = this.categories[0]?.id || 0;
    this.form.roleIds = [];
    this.formMsg = '';
    this.formMsgIsError = false;
    this.showModal = true;
  }

  openEdit(master: KpiMaster): void {
    this.editingId = master.id ?? null;
    this.form = {
      kpiName: master.kpiName,
      kpiCode: master.kpiCode,
      categoryId: master.categoryId,
      description: master.description ?? '',
      kpiType: master.kpiType,
      targetType: master.targetType,
      measurementUnit: master.measurementUnit,
      frequency: master.frequency,
      dataSource: master.dataSource,
      defaultWeight: master.defaultWeight,
      active: master.active,
      roleIds: [...(master.roleIds ?? [])]
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
    this.form.categoryId = this.categories[0]?.id || 0;
    this.formMsg = '';
    this.formMsgIsError = false;
  }

  save(): void {
    if (this.saving) return;
    this.formMsg = '';
    this.formMsgIsError = false;
    if (!this.form.kpiName?.trim()) { this.setFormError('KPI name is required.'); return; }
    if (!this.form.kpiCode?.trim()) { this.setFormError('KPI code is required.'); return; }
    if (!this.form.categoryId) { this.setFormError('Category is required.'); return; }

    const weight = Number(this.form.defaultWeight);
    if (Number.isNaN(weight) || weight < 0 || weight > 100) {
      this.setFormError('Default weight must be between 0 and 100.');
      return;
    }

    this.saving = true;
    const wasEditing = !!this.editingId;
    const payload = {
      ...this.form,
      categoryId: Number(this.form.categoryId),
      frequencyCode: this.form.frequency,
      defaultWeight: weight,
      roleIds: [...new Set((this.form.roleIds ?? []).map((id) => Number(id)).filter((id) => !Number.isNaN(id)))]
    };
    const req$ = wasEditing
      ? this.configService.updateKpiMaster(this.editingId!, payload)
      : this.configService.createKpiMaster(payload as KpiMaster);

    req$.subscribe({
      next: () => {
        this.saving = false;
        this.closeModal();
        this.showPageMsg(wasEditing ? 'KPI master updated.' : 'KPI master created.', false);
        this.silentLoad();
      },
      error: (err) => { this.setFormError(this.extractError(err, 'Failed to save KPI master.')); }
    }).add(() => { this.saving = false; });
  }

  async delete(master: KpiMaster): Promise<void> {
    if (!master.id || !await this.confirmService.confirmed({ title: 'Confirm deletion', message: `Delete KPI master "${master.kpiCode}"?`, confirmLabel: 'Delete', dangerous: true })) return;
    this.deletingId = master.id;
    this.configService.deleteKpiMaster(master.id).subscribe({
      next: () => { this.showPageMsg('KPI master deleted.', false); this.silentLoad(); },
      error: (err) => { this.showPageMsg(this.extractError(err, 'Failed to delete KPI master.'), true); }
    }).add(() => { this.deletingId = null; });
  }

  emptyForm(): Partial<KpiMaster> {
    return {
      kpiName: '', kpiCode: '', categoryId: 0, description: '',
      kpiType: 'NUMBER', targetType: 'NUMBER_TARGET', measurementUnit: '',
      frequency: this.frequencies[0]?.frequencyCode ?? 'MONTHLY',
      dataSource: this.dataSources[0]?.sourceName ?? 'Manual',
      defaultWeight: 10, active: true, roleIds: []
    };
  }

  isRoleMapped(roleId: number): boolean {
    return (this.form.roleIds ?? []).includes(roleId);
  }

  toggleRoleInForm(roleId: number, checked: boolean): void {
    const current = new Set(this.form.roleIds ?? []);
    if (checked) {
      current.add(roleId);
    } else {
      current.delete(roleId);
    }
    this.form.roleIds = Array.from(current);
  }

  get allRolesSelected(): boolean {
    const mapped = this.form.roleIds ?? [];
    return this.roles.length > 0 && this.roles.every((role) => mapped.includes(role.id));
  }

  toggleAllRolesInForm(checked: boolean): void {
    this.form.roleIds = checked ? this.roles.map((role) => role.id) : [];
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
