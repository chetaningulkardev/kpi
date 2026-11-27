import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  ConfigurationService,
  KpiMaster,
  KpiTemplate,
  RoleOption,
  KpiTemplateItemCreateRequest
} from '../../../services/configuration.service';
import { forkJoin } from 'rxjs';
import { timeout } from 'rxjs/operators';

import { extractHttpError } from '../../../services/http-error.util';
import { AccessibleModalDirective } from '../../../directives/accessible-modal.directive';
@Component({
  selector: 'app-kpi-templates',
  standalone: true,
  imports: [AccessibleModalDirective, CommonModule, FormsModule],
  templateUrl: './kpi-templates.component.html',
  changeDetection: ChangeDetectionStrategy.Default
})
export class KpiTemplatesComponent implements OnInit {
  templates: KpiTemplate[] = [];
  masters: KpiMaster[] = [];
  roles: RoleOption[] = [];
  searchTerm = '';
  filterRoleCode = '';
  filterStatus = '';
  filterActive: 'ALL' | 'ACTIVE' | 'INACTIVE' = 'ALL';
  selectedTemplateIds = new Set<number>();
  bulkDeleting = false;
  loading = false;
  loadError = '';
  mastersLoading = false;
  mastersError = '';
  rolesLoading = false;
  rolesError = '';
  saving = false;
  result = '';
  editingId: number | null = null;
  form = this.emptyForm();

  // Template form modal
  showTemplateModal = false;
  templateModalTitle = '';

  showItemModal = false;
  selectedTemplate: KpiTemplate | null = null;
  itemForm = this.emptyItemForm();
  itemSaving = false;
  itemMsg = '';

  confirmPopup = {
    visible: false,
    title: '',
    message: '',
    actionLabel: 'Confirm',
    dangerous: false,
    action: null as null | (() => void)
  };

  constructor(private configService: ConfigurationService) {}

  ngOnInit(): void {
    this.load();
    this.loadMasters();
    this.loadRoles();
  }

  load(): void {
    this.loading = true;
    this.loadError = '';
    this.configService.getTemplates().subscribe({
      next: (templates) => {
        this.templates = templates;
        this.pruneSelection();
        this.loading = false;
      },
      error: () => { this.loadError = 'Failed to load templates.'; this.loading = false; }
    });
  }

  get filteredTemplates(): KpiTemplate[] {
    const q = this.searchTerm.trim().toLowerCase();
    return this.templates.filter((t) => {
      const name = (t.name ?? '').toLowerCase();
      const code = (t.code ?? '').toLowerCase();
      const desc = (t.description ?? '').toLowerCase();
      const role = (t.roleCode ?? '').toLowerCase();
      const status = (String((t as any).status ?? '')).toLowerCase();
      const active = !!(t as any).active;

      const byQuery = !q || name.includes(q) || code.includes(q) || desc.includes(q) || role.includes(q);
      const byRole = !this.filterRoleCode || t.roleCode === this.filterRoleCode;
      const byStatus = !this.filterStatus || String((t as any).status ?? '') === this.filterStatus;
      const byActive = this.filterActive === 'ALL'
        || (this.filterActive === 'ACTIVE' && active)
        || (this.filterActive === 'INACTIVE' && !active);

      return byQuery && byRole && byStatus && byActive;
    });
  }

  get availableStatuses(): string[] {
    return [...new Set(this.templates
      .map((t) => String((t as any).status ?? '').trim())
      .filter((s) => !!s))];
  }

  get allFilteredSelected(): boolean {
    const ids = this.filteredTemplates.map(t => t.id).filter((id): id is number => id != null);
    return ids.length > 0 && ids.every(id => this.selectedTemplateIds.has(id));
  }

  get selectedCount(): number {
    return this.selectedTemplateIds.size;
  }

  toggleSelectAllFiltered(checked: boolean): void {
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

  clearSelection(): void {
    this.selectedTemplateIds.clear();
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.filterRoleCode = '';
    this.filterStatus = '';
    this.filterActive = 'ALL';
  }

  deleteSelected(): void {
    const ids = [...this.selectedTemplateIds];
    if (!ids.length) {
      this.flash('Select at least one template to delete.');
      return;
    }
    this.openConfirmPopup({
      title: 'Delete Selected Templates',
      message: `Delete ${ids.length} selected template(s)? This cannot be undone.`,
      actionLabel: 'Delete Selected',
      dangerous: true,
      action: () => {
        this.bulkDeleting = true;
        forkJoin(ids.map((id) => this.configService.deleteTemplate(id))).subscribe({
          next: () => {
            this.flash(`Deleted ${ids.length} template(s).`);
            this.selectedTemplateIds.clear();
            this.load();
          },
          error: (err) => {
            this.result = this.extractError(err, 'Failed to delete selected templates.');
            this.load();
          }
        }).add(() => { this.bulkDeleting = false; });
      }
    });
  }

  loadMasters(): void {
    this.mastersLoading = true;
    this.mastersError = '';
    this.configService.getKpiMasters().subscribe({
      next: (masters) => { this.masters = (masters || []).filter(m => m.id != null && m.active); this.mastersLoading = false; },
      error: () => { this.mastersError = 'Failed to load KPI master options.'; this.mastersLoading = false; }
    });
  }

  loadRoles(): void {
    this.rolesLoading = true;
    this.rolesError = '';
    this.configService.getRoles().subscribe({
      next: (roles) => {
        this.roles = (roles || []).filter((r) => r.active);
        this.rolesLoading = false;
      },
      error: () => { this.rolesError = 'Failed to load role options.'; this.rolesLoading = false; }
    });
  }

  get availableMastersForSelectedTemplate(): KpiMaster[] {
    const roleId = this.selectedTemplate?.roleId;
    if (!roleId) {
      return this.masters;
    }
    return this.masters.filter((m) => (m.roleIds ?? []).includes(roleId));
  }

  edit(template: KpiTemplate): void {
    this.editingId = template.id ?? null;
    this.form = {
      templateName: template.name,
      templateCode: template.code,
      description: template.description ?? '',
      roleId: template.roleId ?? null
    };
    this.templateModalTitle = 'Edit Template';
    this.showTemplateModal = true;
    this.result = '';
  }

  newTemplate(): void {
    this.editingId = null;
    this.form = this.emptyForm();
    this.templateModalTitle = 'Add Template';
    this.showTemplateModal = true;
    this.result = '';
  }

  closeTemplateModal(): void {
    if (this.saving) return;
    this.showTemplateModal = false;
    this.editingId = null;
    this.form = this.emptyForm();
    this.result = '';
  }

  save(): void {
    if (this.saving || this.confirmPopup.visible) return;
    this.result = '';
    const templateName = (this.form.templateName ?? '').trim();
    const templateCode = (this.form.templateCode ?? '').trim();
    const description = (this.form.description ?? '').trim();

    if (!templateName || !templateCode) {
      this.result = 'Template name and code are required.';
      return;
    }
    if (!this.form.roleId) {
      this.result = 'Role is required.';
      return;
    }

    const wasEditing = !!this.editingId;

    // Prevent obvious duplicate-code failures before calling backend.
    if (!wasEditing) {
      const codeExists = this.templates.some(t => (t.code ?? '').toUpperCase() === templateCode.toUpperCase());
      if (codeExists) {
        this.result = 'Template code already exists. Please use a unique code.';
        return;
      }
    }

    const action = wasEditing ? 'Update' : 'Create';

    // Show confirmation popup before saving (using templateName instead of templateCode)
    this.openConfirmPopup({
      title: `${action} Template`,
      message: `${action} template "${this.form.templateName}"?`,
      actionLabel: action,
      dangerous: false,
      action: () => {
        this.saving = true;
        const req$ = wasEditing
          ? this.configService.updateTemplate(this.editingId!, {
              templateName,
              description,
              roleId: this.form.roleId
            })
          : this.configService.createTemplate({
              templateName,
              templateCode,
              description,
              roleId: this.form.roleId
            });

        req$.pipe(timeout(15000)).subscribe({
          next: () => {
            this.saving = false;
            this.form = this.emptyForm();
            this.editingId = null;
            this.closeTemplateModal();
            this.flash(wasEditing ? 'Template updated.' : 'Template created.');
            this.load();
          },
          error: (err) => {
            this.result = this.extractError(err, 'Failed to save template.');
            this.saving = false;
          }
        }).add(() => { this.saving = false; });
      }
    });
  }

  delete(template: KpiTemplate): void {
    if (!template.id) return;
    this.openConfirmPopup({
      title: 'Delete Template',
      message: `Delete template "${template.code}"? This cannot be undone.`,
      actionLabel: 'Delete',
      dangerous: true,
      action: () => {
        this.configService.deleteTemplate(template.id!).subscribe({
          next: () => {
            this.selectedTemplateIds.delete(template.id!);
            this.flash('Template deleted.');
            this.load();
          },
          error: (err) => { this.result = this.extractError(err, 'Failed to delete template.'); }
        });
      }
    });
  }

  openItemsPopup(template: KpiTemplate): void {
    if (!template.id) return;
    this.itemMsg = '';
    this.itemForm = this.emptyItemForm();
    this.configService.getTemplateById(template.id).subscribe({
      next: (full) => {
        this.selectedTemplate = full;
        this.showItemModal = true;
      },
      error: (err) => { this.result = this.extractError(err, 'Failed to load template details.'); }
    });
  }

  closeItemsPopup(): void {
    this.showItemModal = false;
    this.selectedTemplate = null;
    this.itemSaving = false;
    this.itemMsg = '';
    this.itemForm = this.emptyItemForm();
  }

  addTemplateItem(): void {
    if (!this.selectedTemplate?.id) return;
    if (!this.itemForm.kpiMasterId || !this.itemForm.weight || this.itemForm.weight <= 0) {
      this.itemMsg = 'Select KPI master and provide valid weight (> 0).';
      return;
    }

    // Find the KPI master to show its name in confirmation
    const selectedMaster = this.availableMastersForSelectedTemplate.find(m => m.id === this.itemForm.kpiMasterId);
    const masterName = selectedMaster?.kpiName ?? 'Unknown KPI';

    // Show confirmation popup before adding
    this.openConfirmPopup({
      title: 'Add Template Item',
      message: `Add "${masterName}" with weight ${this.itemForm.weight}% to template "${this.selectedTemplate.code}"?`,
      actionLabel: 'Add Item',
      dangerous: false,
      action: () => {
        const payload: KpiTemplateItemCreateRequest = {
          kpiMasterId: this.itemForm.kpiMasterId!,
          weight: this.itemForm.weight!,
          target: this.itemForm.target
        };

        this.itemSaving = true;
        this.configService.addTemplateItem(this.selectedTemplate!.id!, payload).subscribe({
          next: () => {
            this.itemMsg = 'Item added.';
            this.itemForm = this.emptyItemForm();
            this.reloadSelectedTemplate();
          },
          error: (err) => { this.itemMsg = this.extractError(err, 'Failed to add item.'); }
        }).add(() => { this.itemSaving = false; });
      }
    });
  }

  removeTemplateItem(itemId?: number): void {
    if (!this.selectedTemplate?.id || !itemId) return;
    this.openConfirmPopup({
      title: 'Remove Template Item',
      message: 'Remove this KPI item from the template?',
      actionLabel: 'Remove',
      dangerous: true,
      action: () => {
        this.configService.removeTemplateItem(this.selectedTemplate!.id!, itemId).subscribe({
          next: () => {
            this.itemMsg = 'Item removed.';
            this.reloadSelectedTemplate();
          },
          error: (err) => { this.itemMsg = this.extractError(err, 'Failed to remove item.'); }
        });
      }
    });
  }

  activateTemplate(template: KpiTemplate): void {
    if (!template.id) return;
    this.openConfirmPopup({
      title: 'Activate Template',
      message: `Activate template "${template.code}"? It must have valid 100% total weight.`,
      actionLabel: 'Activate',
      dangerous: false,
      action: () => {
        this.configService.activateTemplate(template.id!).subscribe({
          next: () => { this.flash('Template activated.'); this.load(); },
          error: (err) => { this.result = this.extractError(err, 'Failed to activate template.'); }
        });
      }
    });
  }

  cloneTemplate(template: KpiTemplate): void {
    if (!template.id) return;
    this.openConfirmPopup({
      title: 'Clone Template',
      message: `Clone template "${template.code}" to create a new version?`,
      actionLabel: 'Clone',
      dangerous: false,
      action: () => {
        this.configService.cloneTemplate(template.id!).subscribe({
          next: () => { this.flash('Template cloned to new version.'); this.load(); },
          error: (err) => { this.result = this.extractError(err, 'Failed to clone template.'); }
        });
      }
    });
  }

  archiveTemplate(template: KpiTemplate): void {
    if (!template.id) return;
    this.openConfirmPopup({
      title: 'Archive Template',
      message: `Archive template "${template.code}"?`,
      actionLabel: 'Archive',
      dangerous: false,
      action: () => {
        this.configService.archiveTemplate(template.id!).subscribe({
          next: () => { this.flash('Template archived.'); this.load(); },
          error: (err) => { this.result = this.extractError(err, 'Failed to archive template.'); }
        });
      }
    });
  }

  totalWeight(template: KpiTemplate | null): number {
    if (!template?.items?.length) return 0;
    return template.items.reduce((sum, i) => sum + Number(i.weight || 0), 0);
  }

  emptyForm(): { templateName: string; templateCode: string; description: string; roleId: number | null } {
    return { templateName: '', templateCode: '', description: '', roleId: null };
  }

  emptyItemForm(): { kpiMasterId: number | null; weight: number | null; target: string } {
    return { kpiMasterId: null, weight: null, target: '' };
  }

  private reloadSelectedTemplate(): void {
    if (!this.selectedTemplate?.id) return;
    this.configService.getTemplateById(this.selectedTemplate.id).subscribe({
      next: (full) => {
        this.selectedTemplate = full;
        this.load();
      },
      error: () => {
        this.itemMsg = 'Your change was saved, but template details could not be refreshed. Reopen the template to reload before making another change.';
        this.result = this.itemMsg;
      }
    });
  }

  private pruneSelection(): void {
    const validIds = new Set(this.templates.map(t => t.id).filter((id): id is number => id != null));
    this.selectedTemplateIds.forEach((id) => {
      if (!validIds.has(id)) this.selectedTemplateIds.delete(id);
    });
  }

  private flash(msg: string): void {
    this.result = msg;
    setTimeout(() => { this.result = ''; }, 4000);
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
