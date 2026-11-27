import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  ConfigurationService,
  KpiCategory,
  KpiDataSource,
  KpiFrequency,
  KpiMaster,
  KpiTemplate,
  LookupValue,
  ReviewCycle,
  RoleGenerationReadiness,
  RoleOption
} from '../../services/configuration.service';
import { forkJoin } from 'rxjs';

import { extractHttpError } from '../../services/http-error.util';
import { ConfirmService } from '../../services/confirm.service';
@Component({
  selector: 'app-admin-config',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-config.component.html',
  styleUrl: './admin-config.component.css'
})
export class AdminConfigComponent implements OnInit {

  private readonly confirmService = inject(ConfirmService);
  // The real roles, not the ROLE lookup list. The lookup table is a separate,
  // stale set of demo values (it still offers an "ENGINEER" that no longer
  // exists as a role), while everything this screen does with a role —
  // readiness, generation, template ownership — is resolved against the roles
  // table. Feeding the pickers from the lookups meant offering choices the
  // server then rejected, and binding template.roleId to a lookup id that
  // happened to collide with an unrelated role.
  roles: RoleOption[] = [];
  departments: LookupValue[] = [];
  templates: KpiTemplate[] = [];
  templateSearch = '';
  templateRoleFilter = '';
  templateStatusFilter = '';
  templateActiveFilter: 'ALL' | 'ACTIVE' | 'INACTIVE' = 'ALL';
  selectedTemplateIds = new Set<number>();
  bulkDeletingTemplates = false;
  cycles: ReviewCycle[] = [];
  categories: KpiCategory[] = [];
  frequencies: KpiFrequency[] = [];
  dataSources: KpiDataSource[] = [];
  masters: KpiMaster[] = [];

  cycleStatusOptions = ['DRAFT', 'OPEN', 'IN_PROGRESS', 'UNDER_REVIEW', 'CALIBRATION', 'CLOSED'];
  kpiTypeOptions = ['PERCENTAGE', 'NUMBER', 'RATING', 'QUALITATIVE', 'BOOLEAN', 'FORMULA'];
  targetTypeOptions = ['PERCENTAGE_TARGET', 'NUMBER_TARGET', 'RATING_TARGET', 'QUALITATIVE_TARGET', 'TREND_TARGET'];
  get frequencyOptions(): string[] {
    return this.frequencies.map((f) => f.frequencyCode);
  }

  get dataSourceOptions(): string[] {
    return this.dataSources.map((s) => s.sourceName);
  }

  cycleForm = this.emptyCycleForm();
  categoryForm = this.emptyCategoryForm();
  masterForm = this.emptyMasterForm();
  templateForm = this.emptyTemplateForm();

  editingCycleId: number | null = null;
  editingCategoryId: number | null = null;
  editingMasterId: number | null = null;
  editingTemplateId: number | null = null;

  // Per-section saving flags for button disable + feedback
  savingCycle = false;
  savingCategory = false;
  savingMaster = false;
  savingTemplate = false;
  generating = false;
  checkingReadiness = false;

  selectedRole = '';
  selectedCycle = '';
  assignmentResult = '';
  adminReadiness: RoleGenerationReadiness | null = null;
  cycleResult = '';
  categoryResult = '';
  masterResult = '';
  templateResult = '';
  loading = false;

  constructor(private configService: ConfigurationService) {}

  ngOnInit(): void {
    this.refresh();
  }

  refresh(silent = false): void {
    if (!silent) this.loading = true;
    forkJoin({
      roles: this.configService.getRoles(),
      departments: this.configService.getLookups('DEPARTMENT'),
      templates: this.configService.getTemplates(),
      cycles: this.configService.getReviewCycles(),
      categories: this.configService.getKpiCategories(),
      frequencies: this.configService.getKpiFrequencies(),
      dataSources: this.configService.getKpiDataSources(),
      masters: this.configService.getKpiMasters()
    }).subscribe({
      next: ({ roles, departments, templates, cycles, categories, frequencies, dataSources, masters }) => {
        this.roles = roles.filter((role) => role.active);
        this.departments = departments;
        this.templates = templates;
        this.pruneTemplateSelection();
        this.cycles = cycles;
        this.categories = categories;
        this.frequencies = frequencies;
        this.dataSources = dataSources;
        this.masters = masters;
        this.selectedRole = this.selectedRole || this.roles[0]?.roleCode || '';
        this.selectedCycle = this.selectedCycle || this.cycles[0]?.code || '';
        if (this.selectedRole && this.selectedCycle) { this.refreshAdminReadiness(); }
        if (!this.masterForm.categoryId) {
          this.masterForm.categoryId = this.categories[0]?.id || 0;
        }
        if (!this.masterForm.frequency) {
          this.masterForm.frequency = this.frequencies[0]?.frequencyCode || 'MONTHLY';
        }
        if (!this.masterForm.dataSource) {
          this.masterForm.dataSource = this.dataSources[0]?.sourceName || 'Manual';
        }
        this.loading = false;
      },
      error: () => {
        this.flash('assignmentResult', 'Failed to load configuration data.');
        this.loading = false;
      }
    });
  }

  generate(): void {
    if (this.generating) return;
    this.assignmentResult = '';
    if (!this.selectedRole || !this.selectedCycle) {
      this.assignmentResult = 'Please select both role and review cycle.';
      return;
    }
    this.generating = true;
    this.configService.generateAssignments(this.selectedRole, this.selectedCycle).subscribe({
      next: (response) => {
        const processed = response.processedUsers ?? 0;
        const skipped = response.skippedUsers ?? 0;
        const failed = response.failedUsers ?? 0;
        if (response.status === 'FAILED') {
          this.assignmentResult = response.error || 'Assignment generation failed.';
          return;
        }
        if (response.status === 'PARTIAL_SUCCESS') {
          this.flash('assignmentResult', `⚠️ Created ${response.createdAssignments} KPI records (processed ${processed}, skipped ${skipped}, failed ${failed}).`);
          return;
        }
        this.flash('assignmentResult', `✅ Created ${response.createdAssignments} KPI records (processed ${processed}, skipped ${skipped}).`);
        this.refreshAdminReadiness();
      },
      error: (err) => {
        this.flash('assignmentResult', this.extractError(err, 'Failed to generate assignments.'));
      }
    }).add(() => { this.generating = false; });
  }

  refreshAdminReadiness(): void {
    const role = this.selectedRole;
    const cycle = this.selectedCycle;
    if (!this.selectedRole || !this.selectedCycle) {
      this.adminReadiness = null;
      return;
    }
    this.checkingReadiness = true;
    this.configService.getRoleGenerationReadiness(this.selectedRole, this.selectedCycle).subscribe({
      next: (r) => {
        if (role === this.selectedRole && cycle === this.selectedCycle) this.adminReadiness = r;
      },
      error: () => {
        if (role !== this.selectedRole || cycle !== this.selectedCycle) return;
        this.adminReadiness = {
          roleCode: this.selectedRole, cycleCode: this.selectedCycle,
          ready: false, cycleEligible: false, activeUsersInRole: 0,
          reasons: ['Could not evaluate readiness — check backend connectivity.'],
          templates: []
        };
      }
    }).add(() => { this.checkingReadiness = false; });
  }

  // ── Review Cycle ──────────────────────────────────────────

  editCycle(cycle: ReviewCycle): void {
    this.editingCycleId = cycle.id ?? null;
    this.cycleForm = {
      cycleName: cycle.name,
      cycleCode: cycle.code,
      startDate: cycle.startDate,
      endDate: cycle.endDate,
      status: cycle.statusCode,
      description: cycle.description ?? ''
    };
    this.cycleResult = '';
  }

  cancelEditCycle(): void {
    this.editingCycleId = null;
    this.cycleForm = this.emptyCycleForm();
    this.cycleResult = '';
  }

  saveCycle(): void {
    if (this.savingCycle) return;
    this.cycleResult = '';
    if (!this.cycleForm.cycleName || !this.cycleForm.cycleCode || !this.cycleForm.startDate || !this.cycleForm.endDate) {
      this.cycleResult = 'Cycle name, code, start date and end date are required.';
      return;
    }
    if (this.cycleForm.startDate >= this.cycleForm.endDate) {
      this.cycleResult = 'End date must be after start date.';
      return;
    }
    this.savingCycle = true;
    const wasEditing = !!this.editingCycleId;
    const req$ = wasEditing
      ? this.configService.updateReviewCycle(this.editingCycleId!, this.cycleForm)
      : this.configService.createReviewCycle(this.cycleForm);

    req$.subscribe({
      next: () => {
        this.flash('cycleResult', wasEditing ? 'Review cycle updated.' : 'Review cycle created.');
        this.cycleForm = this.emptyCycleForm();
        this.editingCycleId = null;
        this.refresh(true);
      },
      error: (err) => {
        this.cycleResult = this.extractError(err, 'Failed to save review cycle.');
      }
    }).add(() => { this.savingCycle = false; });
  }

  async deleteCycle(cycle: ReviewCycle): Promise<void> {
    if (!cycle.id || !await this.confirmService.confirmed({ title: 'Confirm deletion', message: `Delete cycle "${cycle.code}"?`, confirmLabel: 'Delete', dangerous: true })) return;
    this.configService.deleteReviewCycle(cycle.id).subscribe({
      next: () => { this.flash('cycleResult', 'Review cycle deleted.'); this.refresh(true); },
      error: (err) => { this.cycleResult = this.extractError(err, 'Failed to delete review cycle.'); }
    });
  }

  // ── KPI Category ──────────────────────────────────────────

  editCategory(category: KpiCategory): void {
    this.editingCategoryId = category.id ?? null;
    this.categoryForm = {
      categoryCode: category.categoryCode,
      categoryName: category.categoryName,
      description: category.description ?? '',
      displayOrder: category.displayOrder ?? 999,
      active: category.active ?? true
    };
    this.categoryResult = '';
  }

  cancelEditCategory(): void {
    this.editingCategoryId = null;
    this.categoryForm = this.emptyCategoryForm();
    this.categoryResult = '';
  }

  saveCategory(): void {
    if (this.savingCategory) return;
    this.categoryResult = '';
    if (!this.categoryForm.categoryCode || !this.categoryForm.categoryName) {
      this.categoryResult = 'Category code and name are required.';
      return;
    }
    this.savingCategory = true;
    const wasEditing = !!this.editingCategoryId;
    const req$ = wasEditing
      ? this.configService.updateKpiCategory(this.editingCategoryId!, this.categoryForm)
      : this.configService.createKpiCategory(this.categoryForm);

    req$.subscribe({
      next: () => {
        this.flash('categoryResult', wasEditing ? 'Category updated.' : 'Category created.');
        this.categoryForm = this.emptyCategoryForm();
        this.editingCategoryId = null;
        this.refresh(true);
      },
      error: (err) => {
        this.categoryResult = this.extractError(err, 'Failed to save category.');
      }
    }).add(() => { this.savingCategory = false; });
  }

  async deleteCategory(category: KpiCategory): Promise<void> {
    if (!category.id || !await this.confirmService.confirmed({ title: 'Confirm deletion', message: `Delete category "${category.categoryCode}"?`, confirmLabel: 'Delete', dangerous: true })) return;
    this.configService.deleteKpiCategory(category.id).subscribe({
      next: () => { this.flash('categoryResult', 'Category deleted.'); this.refresh(true); },
      error: (err) => { this.categoryResult = this.extractError(err, 'Failed to delete category. Remove linked KPI masters first.'); }
    });
  }

  // ── KPI Master ────────────────────────────────────────────

  editMaster(master: KpiMaster): void {
    this.editingMasterId = master.id ?? null;
    this.masterForm = {
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
      active: master.active
    };
    this.masterResult = '';
  }

  cancelEditMaster(): void {
    this.editingMasterId = null;
    this.masterForm = this.emptyMasterForm();
    this.masterForm.categoryId = this.categories[0]?.id || 0;
    this.masterResult = '';
  }

  saveMaster(): void {
    if (this.savingMaster) return;
    this.masterResult = '';
    if (!this.masterForm.kpiName || !this.masterForm.kpiCode || !this.masterForm.categoryId) {
      this.masterResult = 'KPI name, code and category are required.';
      return;
    }
    if (!Number.isFinite(Number(this.masterForm.defaultWeight)) || Number(this.masterForm.defaultWeight) < 0 || Number(this.masterForm.defaultWeight) > 100) {
      this.masterResult = 'Default weight must be between 0 and 100.';
      return;
    }
    this.savingMaster = true;
    const wasEditing = !!this.editingMasterId;
    const payload = {
      ...this.masterForm,
      categoryId: Number(this.masterForm.categoryId),
      frequencyCode: this.masterForm.frequency,
      defaultWeight: Number(this.masterForm.defaultWeight)
    };
    const req$ = wasEditing
      ? this.configService.updateKpiMaster(this.editingMasterId!, payload)
      : this.configService.createKpiMaster(payload as any);

    req$.subscribe({
      next: () => {
        this.flash('masterResult', wasEditing ? 'KPI master updated.' : 'KPI master created.');
        this.masterForm = this.emptyMasterForm();
        this.masterForm.categoryId = this.categories[0]?.id || 0;
        this.editingMasterId = null;
        this.refresh(true);
      },
      error: (err) => {
        this.masterResult = this.extractError(err, 'Failed to save KPI master.');
      }
    }).add(() => { this.savingMaster = false; });
  }

  async deleteMaster(master: KpiMaster): Promise<void> {
    if (!master.id || !await this.confirmService.confirmed({ title: 'Confirm deletion', message: `Delete KPI master "${master.kpiCode}"?`, confirmLabel: 'Delete', dangerous: true })) return;
    this.configService.deleteKpiMaster(master.id).subscribe({
      next: () => { this.flash('masterResult', 'KPI master deleted.'); this.refresh(true); },
      error: (err) => { this.masterResult = this.extractError(err, 'Failed to delete KPI master.'); }
    });
  }

  // ── KPI Template ──────────────────────────────────────────

  get filteredTemplates(): KpiTemplate[] {
    const q = this.templateSearch.trim().toLowerCase();
    return this.templates.filter((t) => {
      const name = (t.name ?? '').toLowerCase();
      const code = (t.code ?? '').toLowerCase();
      const desc = (t.description ?? '').toLowerCase();
      const role = (t.roleCode ?? '').toLowerCase();
      const status = String((t as any).status ?? '');
      const active = !!(t as any).active;

      const byQuery = !q || name.includes(q) || code.includes(q) || desc.includes(q) || role.includes(q);
      const byRole = !this.templateRoleFilter || t.roleCode === this.templateRoleFilter;
      const byStatus = !this.templateStatusFilter || status === this.templateStatusFilter;
      const byActive = this.templateActiveFilter === 'ALL'
        || (this.templateActiveFilter === 'ACTIVE' && active)
        || (this.templateActiveFilter === 'INACTIVE' && !active);

      return byQuery && byRole && byStatus && byActive;
    });
  }

  get templateStatuses(): string[] {
    return [...new Set(this.templates
      .map((t) => String((t as any).status ?? '').trim())
      .filter((s) => !!s))];
  }

  get allFilteredTemplatesSelected(): boolean {
    const ids = this.filteredTemplates.map(t => t.id).filter((id): id is number => id != null);
    return ids.length > 0 && ids.every(id => this.selectedTemplateIds.has(id));
  }

  get selectedTemplatesCount(): number {
    return this.selectedTemplateIds.size;
  }

  toggleSelectAllFilteredTemplates(checked: boolean): void {
    this.filteredTemplates.forEach((t) => {
      if (!t.id) return;
      if (checked) this.selectedTemplateIds.add(t.id);
      else this.selectedTemplateIds.delete(t.id);
    });
  }

  toggleTemplateSelection(template: KpiTemplate, checked: boolean): void {
    if (!template.id) return;
    if (checked) this.selectedTemplateIds.add(template.id);
    else this.selectedTemplateIds.delete(template.id);
  }

  isTemplateSelected(template: KpiTemplate): boolean {
    return !!template.id && this.selectedTemplateIds.has(template.id);
  }

  clearTemplateFilters(): void {
    this.templateSearch = '';
    this.templateRoleFilter = '';
    this.templateStatusFilter = '';
    this.templateActiveFilter = 'ALL';
  }

  clearTemplateSelection(): void {
    this.selectedTemplateIds.clear();
  }

  editTemplate(template: KpiTemplate): void {
    this.editingTemplateId = template.id ?? null;
    this.templateForm = {
      templateName: template.name,
      templateCode: template.code,
      description: template.description ?? '',
      roleId: template.roleId ?? null
    };
    this.templateResult = '';
  }

  cancelEditTemplate(): void {
    this.editingTemplateId = null;
    this.templateForm = this.emptyTemplateForm();
    this.templateResult = '';
  }

  saveTemplate(): void {
    if (this.savingTemplate) return;
    this.templateResult = '';
    if (!this.templateForm.templateName || !this.templateForm.templateCode) {
      this.templateResult = 'Template name and code are required.';
      return;
    }
    if (!this.templateForm.roleId) {
      this.templateResult = 'Template role is required.';
      return;
    }
    this.savingTemplate = true;
    const wasEditing = !!this.editingTemplateId;
    const req$ = wasEditing
      ? this.configService.updateTemplate(this.editingTemplateId!, {
          templateName: this.templateForm.templateName,
          description: this.templateForm.description,
          roleId: this.templateForm.roleId ?? null
        })
      : this.configService.createTemplate({
          templateName: this.templateForm.templateName,
          templateCode: this.templateForm.templateCode,
          description: this.templateForm.description,
          roleId: this.templateForm.roleId ?? null
        });

    req$.subscribe({
      next: () => {
        this.flash('templateResult', wasEditing ? 'Template updated.' : 'Template created.');
        this.templateForm = this.emptyTemplateForm();
        this.editingTemplateId = null;
        this.refresh(true);
      },
      error: (err) => {
        this.templateResult = this.extractError(err, 'Failed to save template.');
      }
    }).add(() => { this.savingTemplate = false; });
  }

  async deleteTemplate(template: KpiTemplate): Promise<void> {
    if (!template.id || !await this.confirmService.confirmed({ title: 'Confirm deletion', message: `Delete template "${template.code}"?`, confirmLabel: 'Delete', dangerous: true })) return;
    this.configService.deleteTemplate(template.id).subscribe({
      next: () => {
        this.selectedTemplateIds.delete(template.id!);
        this.flash('templateResult', 'Template deleted.');
        this.refresh(true);
      },
      error: (err) => { this.templateResult = this.extractError(err, 'Failed to delete template.'); }
    });
  }

  async activateTemplate(template: KpiTemplate): Promise<void> {
    if (!template.id) return;
    if (!await this.confirmService.confirmed({ title: 'Activate template', message: `Activate template "${template.code}"? This sets its status to ACTIVE and makes it available for generation.`, confirmLabel: 'Activate' })) return;
    this.configService.activateTemplate(template.id).subscribe({
      next: () => {
        this.flash('templateResult', `✅ Template "${template.code}" activated successfully.`);
        this.refresh(true);
      },
      error: (err) => { this.templateResult = this.extractError(err, 'Failed to activate template.'); }
    });
  }

  async deleteSelectedTemplates(): Promise<void> {
    const ids = [...this.selectedTemplateIds];
    if (!ids.length) {
      this.templateResult = 'Select at least one template to delete.';
      return;
    }
    if (!await this.confirmService.confirmed({ title: 'Confirm deletion', message: `Delete ${ids.length} selected template(s)? This cannot be undone.`, confirmLabel: 'Delete', dangerous: true })) return;

    this.bulkDeletingTemplates = true;
    forkJoin(ids.map((id) => this.configService.deleteTemplate(id))).subscribe({
      next: () => {
        this.selectedTemplateIds.clear();
        this.flash('templateResult', `Deleted ${ids.length} template(s).`);
        this.refresh(true);
      },
      error: (err) => {
        this.templateResult = this.extractError(err, 'Failed to delete selected templates.');
      }
    }).add(() => { this.bulkDeletingTemplates = false; });
  }

  // ── Form factories ────────────────────────────────────────

  emptyCycleForm() {
    return { cycleName: '', cycleCode: '', startDate: '', endDate: '', status: 'DRAFT', description: '' };
  }

  emptyCategoryForm() {
    return { categoryCode: '', categoryName: '', description: '', displayOrder: 999, active: true };
  }

  emptyMasterForm() {
    return {
      kpiName: '', kpiCode: '', categoryId: 0, description: '',
      kpiType: 'NUMBER', targetType: 'NUMBER_TARGET', measurementUnit: '',
      frequency: this.frequencies[0]?.frequencyCode ?? 'MONTHLY',
      dataSource: this.dataSources[0]?.sourceName ?? 'Manual',
      defaultWeight: 10, active: true
    };
  }

  emptyTemplateForm() {
    return { templateName: '', templateCode: '', description: '', roleId: null as number | null };
  }

  // ── Helpers ───────────────────────────────────────────────

  private flash(field: keyof this, msg: string): void {
    (this as any)[field] = msg;
    setTimeout(() => { (this as any)[field] = ''; }, 4000);
  }

  private extractError(err: any, fallback: string): string {
    return extractHttpError(err, fallback);
  }

  private pruneTemplateSelection(): void {
    const validIds = new Set(this.templates.map(t => t.id).filter((id): id is number => id != null));
    this.selectedTemplateIds.forEach((id) => {
      if (!validIds.has(id)) this.selectedTemplateIds.delete(id);
    });
  }
}
