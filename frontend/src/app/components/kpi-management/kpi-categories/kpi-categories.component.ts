import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ConfigurationService, KpiCategory } from '../../../services/configuration.service';

import { extractHttpError } from '../../../services/http-error.util';
import { AccessibleModalDirective } from '../../../directives/accessible-modal.directive';
import { ConfirmService } from '../../../services/confirm.service';
@Component({
  selector: 'app-kpi-categories',
  standalone: true,
  imports: [AccessibleModalDirective, CommonModule, FormsModule],
  templateUrl: './kpi-categories.component.html'
})
export class KpiCategoriesComponent implements OnInit {

  private readonly confirmService = inject(ConfirmService);
  categories: KpiCategory[] = [];
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
    this.configService.getKpiCategories().subscribe({
      next: (categories) => { this.categories = categories; this.loading = false; },
      error: () => { this.loading = false; this.loadError = 'Failed to load categories.'; }
    });
  }

  private silentLoad(): void {
    this.configService.getKpiCategories().subscribe({
      next: (categories) => { this.categories = categories; this.loadError = ''; },
      error: () => { this.loadError = 'Your change was saved, but categories could not be refreshed.'; }
    });
  }

  openCreate(): void {
    this.editingId = null;
    this.form = this.emptyForm();
    this.formMsg = '';
    this.formMsgIsError = false;
    this.showModal = true;
  }

  openEdit(category: KpiCategory): void {
    this.editingId = category.id ?? null;
    this.form = {
      categoryCode: category.categoryCode,
      categoryName: category.categoryName,
      description: category.description ?? '',
      displayOrder: category.displayOrder ?? 999,
      active: category.active ?? true
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
    if (!this.form.categoryCode?.trim()) { this.setFormError('Category code is required.'); return; }
    if (!this.form.categoryName?.trim()) { this.setFormError('Category name is required.'); return; }

    this.saving = true;
    const wasEditing = !!this.editingId;
    const req$ = wasEditing
      ? this.configService.updateKpiCategory(this.editingId!, this.form)
      : this.configService.createKpiCategory(this.form as KpiCategory);

    req$.subscribe({
      next: () => {
        this.saving = false;
        this.closeModal();
        this.showPageMsg(wasEditing ? 'Category updated.' : 'Category created.', false);
        this.silentLoad();
      },
      error: (err) => { this.setFormError(this.extractError(err, 'Failed to save category.')); }
    }).add(() => { this.saving = false; });
  }

  async delete(category: KpiCategory): Promise<void> {
    if (!category.id || !await this.confirmService.confirmed({ title: 'Confirm deletion', message: `Delete category "${category.categoryCode}"?`, confirmLabel: 'Delete', dangerous: true })) return;
    this.deletingId = category.id;
    this.configService.deleteKpiCategory(category.id).subscribe({
      next: () => { this.showPageMsg('Category deleted.', false); this.silentLoad(); },
      error: (err) => { this.showPageMsg(this.extractError(err, 'Failed to delete. Remove linked KPI masters first.'), true); }
    }).add(() => { this.deletingId = null; });
  }

  emptyForm(): Partial<KpiCategory> {
    return { categoryCode: '', categoryName: '', description: '', displayOrder: 999, active: true };
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
