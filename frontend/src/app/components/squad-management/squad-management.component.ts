import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminApiService } from '../../services/admin-api.service';
import { ConfirmService } from '../../services/confirm.service';
import { AccessibleModalDirective } from '../../directives/accessible-modal.directive';

@Component({
  selector: 'app-squad-management',
  standalone: true,
  imports: [CommonModule, FormsModule, AccessibleModalDirective],
  template: `
    <div class="max-w-6xl mx-auto px-4 py-8">
      <div class="flex items-center justify-between mb-6">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">Squad Management</h1>
          <span class="text-sm text-gray-500">{{ squads.length }} squads</span>
        </div>
        <button (click)="openCreate()"
          class="bg-dt-600 hover:bg-dt-700 text-white px-4 py-2 rounded-lg text-sm font-medium shadow">
          + Add Squad
        </button>
      </div>

      <div *ngIf="loading" class="text-center text-gray-400 py-20">Loading squads...</div>
      <div *ngIf="error" class="bg-red-50 border border-red-200 text-red-700 rounded-lg p-4 mb-4">{{ error }}</div>
      <div *ngIf="success" class="bg-green-50 border border-green-200 text-green-700 rounded-lg p-4 mb-4">{{ success }}</div>

      <div *ngIf="!loading && squads.length > 0" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div *ngFor="let s of squads" class="bg-white rounded-xl shadow p-5">
          <div class="flex items-start justify-between mb-2">
            <div>
              <h3 class="text-base font-semibold text-gray-800">{{ s.squad_name }}</h3>
              <span class="text-xs text-gray-400 font-mono">{{ s.squad_code }}</span>
            </div>
            <span class="px-2 py-1 rounded-full text-xs font-semibold"
              [ngClass]="s.active ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'">
              {{ s.active ? 'Active' : 'Inactive' }}
            </span>
          </div>
          <p class="text-xs text-teal-700 mb-1">{{ s.tribe_name }} › {{ s.portfolio_name || '— Unassigned —' }}</p>
          <p class="text-sm text-gray-500 mb-3">{{ s.description || 'No description' }}</p>
          <div class="text-xs text-gray-600 border-t pt-2 mb-3">
            <div><span class="font-medium">Leads:</span>
              <span *ngIf="!s.leads?.length" class="text-gray-400">None</span>
              <span *ngFor="let l of s.leads" class="inline-block px-2 py-0.5 bg-teal-50 text-teal-700 rounded-full ml-1">{{ l.name }}</span>
            </div>
            <div class="mt-1 text-gray-400">{{ s.member_count }} members</div>
          </div>
          <div class="flex items-center gap-3 pt-2 border-t">
            <button (click)="openEdit(s)" class="text-blue-600 hover:text-blue-800 text-sm font-medium">Edit</button>
            <button *ngIf="s.active" (click)="toggle(s)" class="text-amber-600 hover:text-amber-800 text-sm font-medium">Disable</button>
            <button *ngIf="!s.active" (click)="toggle(s)" class="text-green-600 hover:text-green-800 text-sm font-medium">Enable</button>
            <button (click)="remove(s)" class="text-red-600 hover:text-red-800 text-sm font-medium ml-auto">Delete</button>
          </div>
        </div>
      </div>

      <div *ngIf="!loading && squads.length === 0 && !error" class="text-center text-gray-400 py-20">No squads yet.</div>
    </div>

    <div *ngIf="showModal" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-md max-h-[90vh] overflow-y-auto" appAccessibleModal="Squad details" (dismiss)="closeModal()">
        <div class="px-6 py-4 border-b flex items-center justify-between">
          <h2 class="text-lg font-semibold text-gray-800">{{ editingId ? 'Edit Squad' : 'Create Squad' }}</h2>
          <button aria-label="Close dialog" (click)="closeModal()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
        </div>
        <div class="px-6 py-4 space-y-4">
          <div *ngIf="formError" class="bg-red-50 border border-red-200 text-red-700 rounded-lg p-3 text-sm">{{ formError }}</div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Squad Name</label>
            <input [(ngModel)]="form.squad_name" type="text" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-dt-500 focus:outline-none" />
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Squad Code</label>
            <input [(ngModel)]="form.squad_code" type="text" placeholder="e.g. BACKEND" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm font-mono focus:ring-2 focus:ring-dt-500 focus:outline-none" />
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Portfolio</label>
            <select [(ngModel)]="form.portfolio_id" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-dt-500 focus:outline-none">
              <option [ngValue]="null">— Select portfolio —</option>
              <option *ngFor="let p of portfolios" [ngValue]="p.id">{{ p.tribe_name }} › {{ p.portfolio_name }}</option>
            </select>
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Squad Leads <span class="text-red-500">*</span> (at least one)</label>
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
export class SquadManagementComponent implements OnInit {

  private readonly confirmService = inject(ConfirmService);
  squads: any[] = [];
  portfolios: any[] = [];
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

  emptyForm() { return { squad_name: '', squad_code: '', description: '', portfolio_id: null, lead_ids: [] as number[] }; }

  loadAll(): void {
    this.loading = true;
    this.error = '';
    this.api.getSquads().subscribe({
      next: res => { this.squads = res.data || []; this.loading = false; },
      error: () => { this.error = 'Failed to load squads.'; this.loading = false; }
    });
    this.api.getPortfolios().subscribe({ next: res => this.portfolios = res.data || [], error: () => this.error = 'Failed to load portfolio options. Refresh before editing.' });
    this.api.getUsers().subscribe({ next: res => this.users = res.data || [], error: () => this.error = 'Failed to load lead options. Refresh before editing.' });
  }

  isLead(id: number): boolean { return this.form.lead_ids.includes(id); }
  toggleLead(id: number): void {
    const i = this.form.lead_ids.indexOf(id);
    if (i >= 0) this.form.lead_ids.splice(i, 1); else this.form.lead_ids.push(id);
  }

  openCreate(): void { this.editingId = null; this.form = this.emptyForm(); this.formError = ''; this.showModal = true; }
  openEdit(s: any): void {
    this.editingId = s.id;
    this.form = { squad_name: s.squad_name, squad_code: s.squad_code, description: s.description ?? '', portfolio_id: s.portfolio_id ?? null, lead_ids: (s.leads || []).map((l: any) => l.id) };
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
     if (!this.form.squad_name || !this.form.squad_code) { this.formError = 'Name and code are required.'; return; }
     if (!this.form.portfolio_id) { this.formError = 'Please associate a portfolio.'; return; }
     if (!this.form.lead_ids.length) { this.formError = 'Every squad must have at least one Squad Lead.'; return; }
     this.saving = true;
     const wasEditing = !!this.editingId;
     const req = this.editingId ? this.api.updateSquad(this.editingId, this.form) : this.api.createSquad(this.form);
     req.subscribe({
       next: () => {
         this.showModal = false;
         this.formError = '';
         this.form = this.emptyForm();
         this.editingId = null;
         this.flash(wasEditing ? 'Squad updated.' : 'Squad created.');
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
      || 'Failed to save squad.';
  }

  toggle(s: any): void {
    const req = s.active ? this.api.disableSquad(s.id) : this.api.enableSquad(s.id);
    req.subscribe({ next: () => { this.flash('Status updated.'); this.loadAll(); }, error: () => this.error = 'Failed to update status.' });
  }

  async remove(s: any): Promise<void> {
    if (!await this.confirmService.confirmed({ title: 'Confirm deletion', message: `Delete squad ${s.squad_name}?`, confirmLabel: 'Delete', dangerous: true })) return;
    this.api.deleteSquad(s.id).subscribe({ next: () => { this.flash('Squad deleted.'); this.loadAll(); }, error: err => this.error = err?.error?.error || 'Failed to delete squad.' });
  }

  private flash(msg: string): void { this.success = msg; setTimeout(() => this.success = '', 3000); }
}
