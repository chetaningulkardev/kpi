import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminApiService } from '../../services/admin-api.service';
import { ConfirmService } from '../../services/confirm.service';
import { AccessibleModalDirective } from '../../directives/accessible-modal.directive';

@Component({
  selector: 'app-portfolio-management',
  standalone: true,
  imports: [CommonModule, FormsModule, AccessibleModalDirective],
  template: `
    <div class="max-w-6xl mx-auto px-4 py-8">
      <div class="flex items-center justify-between mb-6">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">Portfolio Management</h1>
          <span class="text-sm text-gray-500">{{ portfolios.length }} portfolios</span>
        </div>
        <button (click)="openCreate()"
          class="bg-dt-600 hover:bg-dt-700 text-white px-4 py-2 rounded-lg text-sm font-medium shadow">
          + Add Portfolio
        </button>
      </div>

      <div *ngIf="loading" class="text-center text-gray-400 py-20">Loading portfolios...</div>
      <div *ngIf="error" class="bg-red-50 border border-red-200 text-red-700 rounded-lg p-4 mb-4">{{ error }}</div>
      <div *ngIf="success" class="bg-green-50 border border-green-200 text-green-700 rounded-lg p-4 mb-4">{{ success }}</div>

      <div *ngIf="!loading && portfolios.length > 0" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div *ngFor="let p of portfolios" class="bg-white rounded-xl shadow p-5">
          <div class="flex items-start justify-between mb-2">
            <div>
              <h3 class="text-base font-semibold text-gray-800">{{ p.portfolio_name }}</h3>
              <span class="text-xs text-gray-400 font-mono">{{ p.portfolio_code }}</span>
            </div>
            <span class="px-2 py-1 rounded-full text-xs font-semibold"
              [ngClass]="p.active ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'">
              {{ p.active ? 'Active' : 'Inactive' }}
            </span>
          </div>
          <p class="text-xs text-sky-700 mb-2">Tribe: {{ p.tribe_name || '— Unassigned —' }}</p>
          <p class="text-sm text-gray-500 mb-3">{{ p.description || 'No description' }}</p>
          <div class="text-xs text-gray-600 border-t pt-2 mb-3">
            <div><span class="font-medium">Leads:</span>
              <span *ngIf="!p.leads?.length" class="text-gray-400">None</span>
              <span *ngFor="let l of p.leads" class="inline-block px-2 py-0.5 bg-sky-50 text-sky-700 rounded-full ml-1">{{ l.name }}</span>
            </div>
            <div class="mt-1 text-gray-400">{{ p.squad_count }} squads</div>
          </div>
          <div class="flex items-center gap-3 pt-2 border-t">
            <button (click)="openEdit(p)" class="text-blue-600 hover:text-blue-800 text-sm font-medium">Edit</button>
            <button *ngIf="p.active" (click)="toggle(p)" class="text-amber-600 hover:text-amber-800 text-sm font-medium">Disable</button>
            <button *ngIf="!p.active" (click)="toggle(p)" class="text-green-600 hover:text-green-800 text-sm font-medium">Enable</button>
            <button (click)="remove(p)" class="text-red-600 hover:text-red-800 text-sm font-medium ml-auto">Delete</button>
          </div>
        </div>
      </div>

      <div *ngIf="!loading && portfolios.length === 0 && !error" class="text-center text-gray-400 py-20">No portfolios yet.</div>
    </div>

    <div *ngIf="showModal" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-md max-h-[90vh] overflow-y-auto" appAccessibleModal="Portfolio details" (dismiss)="closeModal()">
        <div class="px-6 py-4 border-b flex items-center justify-between">
          <h2 class="text-lg font-semibold text-gray-800">{{ editingId ? 'Edit Portfolio' : 'Create Portfolio' }}</h2>
          <button aria-label="Close dialog" (click)="closeModal()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
        </div>
        <div class="px-6 py-4 space-y-4">
          <div *ngIf="formError" class="bg-red-50 border border-red-200 text-red-700 rounded-lg p-3 text-sm">{{ formError }}</div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Portfolio Name</label>
            <input [(ngModel)]="form.portfolio_name" type="text" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-dt-500 focus:outline-none" />
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Portfolio Code</label>
            <input [(ngModel)]="form.portfolio_code" type="text" placeholder="e.g. DIGITAL" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm font-mono focus:ring-2 focus:ring-dt-500 focus:outline-none" />
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Tribe</label>
            <select [(ngModel)]="form.tribe_id" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-dt-500 focus:outline-none">
              <option [ngValue]="null">— Select tribe —</option>
              <option *ngFor="let t of tribes" [ngValue]="t.id">{{ t.tribe_name }}</option>
            </select>
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Portfolio Leads</label>
            <div class="flex flex-wrap gap-2 max-h-32 overflow-y-auto">
            <label *ngFor="let u of users" class="flex items-center gap-1 px-3 py-1 border rounded-full text-sm cursor-pointer"
              [ngClass]="isLead(u.id) ? 'bg-dt-100 border-dt-300 text-dt-700' : 'border-gray-300 text-gray-600'">
                <input type="checkbox" [checked]="isLead(u.id)" (change)="toggleLead(u.id)" />
                {{ u.first_name }} {{ u.last_name }}
              </label>
            </div>
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Description</label>
            <textarea [(ngModel)]="form.description" rows="2" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-dt-500 focus:outline-none"></textarea>
          </div>
        </div>
        <div class="px-6 py-4 border-t flex justify-end gap-3">
          <button (click)="closeModal()" class="px-4 py-2 text-sm font-medium text-gray-600 hover:text-gray-800">Cancel</button>
          <button (click)="save()" [disabled]="saving" class="bg-dt-600 hover:bg-dt-700 disabled:opacity-50 text-white px-4 py-2 rounded-lg text-sm font-medium">
            {{ saving ? 'Saving...' : (editingId ? 'Update' : 'Create') }}
          </button>
        </div>
      </div>
    </div>
  `
})
export class PortfolioManagementComponent implements OnInit {

  private readonly confirmService = inject(ConfirmService);
  portfolios: any[] = [];
  tribes: any[] = [];
  users: any[] = [];
  loading = true;
  error = '';
  success = '';
  showModal = false;
  saving = false;
  formError = '';
  editingId: number | null = null;
  form: any = this.emptyForm();

   constructor(private api: AdminApiService) {}

  ngOnInit(): void { this.loadAll(); }

  emptyForm() { return { portfolio_name: '', portfolio_code: '', description: '', tribe_id: null, lead_ids: [] as number[] }; }

  loadAll(): void {
    this.loading = true;
    this.error = '';
    this.api.getPortfolios().subscribe({
      next: res => { this.portfolios = res.data || []; this.loading = false; },
      error: () => { this.error = 'Failed to load portfolios.'; this.loading = false; }
    });
    this.api.getTribes().subscribe({ next: res => this.tribes = res.data || [], error: () => this.error = 'Failed to load tribe options. Refresh before editing.' });
    this.api.getUsers().subscribe({ next: res => this.users = res.data || [], error: () => this.error = 'Failed to load lead options. Refresh before editing.' });
  }

  isLead(id: number): boolean { return this.form.lead_ids.includes(id); }
  toggleLead(id: number): void {
    const i = this.form.lead_ids.indexOf(id);
    if (i >= 0) this.form.lead_ids.splice(i, 1); else this.form.lead_ids.push(id);
  }

  openCreate(): void { this.editingId = null; this.form = this.emptyForm(); this.formError = ''; this.showModal = true; }
  openEdit(p: any): void {
    this.editingId = p.id;
    this.form = { portfolio_name: p.portfolio_name, portfolio_code: p.portfolio_code, description: p.description ?? '', tribe_id: p.tribe_id ?? null, lead_ids: (p.leads || []).map((l: any) => l.id) };
    this.formError = ''; this.showModal = true;
  }
  closeModal(): void {
    if (this.saving) return;
    this.showModal = false;
    this.saving = false;
    this.formError = '';
    this.form = this.emptyForm();
    this.editingId = null;
  }

   save(): void {
     if (this.saving) return;
     this.formError = '';
     if (!this.form.portfolio_name || !this.form.portfolio_code) { this.formError = 'Name and code are required.'; return; }
     if (!this.form.tribe_id) { this.formError = 'Please associate a tribe.'; return; }
     this.saving = true;
     const wasEditing = !!this.editingId;
     const req = this.editingId ? this.api.updatePortfolio(this.editingId, this.form) : this.api.createPortfolio(this.form);
     req.subscribe({
       next: () => {
         this.showModal = false;
         this.formError = '';
         this.form = this.emptyForm();
         this.editingId = null;
         this.flash(wasEditing ? 'Portfolio updated.' : 'Portfolio created.');
         this.loadAll();
       },
       error: err => {
         this.formError = this.getSaveError(err);
       }
     }).add(() => {
       this.saving = false;
     });
   }

  private getSaveError(err: any): string {
    return err?.error?.error
      || err?.error?.message
      || err?.message
      || 'Failed to save portfolio.';
  }

  toggle(p: any): void {
    const req = p.active ? this.api.disablePortfolio(p.id) : this.api.enablePortfolio(p.id);
    req.subscribe({ next: () => { this.flash('Status updated.'); this.loadAll(); }, error: () => this.error = 'Failed to update status.' });
  }

  async remove(p: any): Promise<void> {
    if (!await this.confirmService.confirmed({ title: 'Confirm deletion', message: `Delete portfolio ${p.portfolio_name}?`, confirmLabel: 'Delete', dangerous: true })) return;
    this.api.deletePortfolio(p.id).subscribe({ next: () => { this.flash('Portfolio deleted.'); this.loadAll(); }, error: err => this.error = err?.error?.error || 'Failed to delete portfolio.' });
  }

  private flash(msg: string): void { this.success = msg; setTimeout(() => this.success = '', 3000); }
}
