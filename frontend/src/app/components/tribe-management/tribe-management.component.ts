import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminApiService } from '../../services/admin-api.service';
import { ConfirmService } from '../../services/confirm.service';
import { AccessibleModalDirective } from '../../directives/accessible-modal.directive';

@Component({
  selector: 'app-tribe-management',
  standalone: true,
  imports: [CommonModule, FormsModule, AccessibleModalDirective],
  template: `
    <div class="max-w-6xl mx-auto px-4 py-8">
      <div class="flex items-center justify-between mb-6">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">Tribe Management</h1>
          <span class="text-sm text-gray-500">{{ tribes.length }} tribes</span>
        </div>
        <button (click)="openCreate()"
          class="bg-dt-600 hover:bg-dt-700 text-white px-4 py-2 rounded-lg text-sm font-medium shadow">
          + Add Tribe
        </button>
      </div>

      <div *ngIf="loading" class="text-center text-gray-400 py-20">Loading tribes...</div>
      <div *ngIf="error" class="bg-red-50 border border-red-200 text-red-700 rounded-lg p-4 mb-4">{{ error }}</div>
      <div *ngIf="success" class="bg-green-50 border border-green-200 text-green-700 rounded-lg p-4 mb-4">{{ success }}</div>

      <div *ngIf="!loading && tribes.length > 0" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div *ngFor="let t of tribes" class="bg-white rounded-xl shadow p-5">
          <div class="flex items-start justify-between mb-2">
            <div>
              <h3 class="text-base font-semibold text-gray-800">{{ t.tribe_name }}</h3>
              <span class="text-xs text-gray-400 font-mono">{{ t.tribe_code }}</span>
            </div>
            <span class="px-2 py-1 rounded-full text-xs font-semibold"
              [ngClass]="t.active ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'">
              {{ t.active ? 'Active' : 'Inactive' }}
            </span>
          </div>
          <p class="text-sm text-gray-500 mb-3">{{ t.description || 'No description' }}</p>
          <div class="text-xs text-gray-600 border-t pt-2 mb-3">
            <div><span class="font-medium">Leads:</span>
              <span *ngIf="!t.leads?.length" class="text-gray-400">None</span>
              <span *ngFor="let l of t.leads" class="inline-block px-2 py-0.5 bg-indigo-50 text-indigo-700 rounded-full ml-1">{{ l.name }}</span>
            </div>
            <div class="mt-1 text-gray-400">{{ t.portfolio_count }} portfolios</div>
          </div>
          <div class="flex items-center gap-3 pt-2 border-t">
            <button (click)="openEdit(t)" class="text-blue-600 hover:text-blue-800 text-sm font-medium">Edit</button>
            <button *ngIf="t.active" (click)="toggle(t)" class="text-amber-600 hover:text-amber-800 text-sm font-medium">Disable</button>
            <button *ngIf="!t.active" (click)="toggle(t)" class="text-green-600 hover:text-green-800 text-sm font-medium">Enable</button>
            <button (click)="remove(t)" class="text-red-600 hover:text-red-800 text-sm font-medium ml-auto">Delete</button>
          </div>
        </div>
      </div>

      <div *ngIf="!loading && tribes.length === 0 && !error" class="text-center text-gray-400 py-20">No tribes yet. Create one to start.</div>
    </div>

    <div *ngIf="showModal" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-md max-h-[90vh] overflow-y-auto" appAccessibleModal="Tribe details" (dismiss)="closeModal()">
        <div class="px-6 py-4 border-b flex items-center justify-between">
          <h2 class="text-lg font-semibold text-gray-800">{{ editingId ? 'Edit Tribe' : 'Create Tribe' }}</h2>
          <button aria-label="Close dialog" (click)="closeModal()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
        </div>
        <div class="px-6 py-4 space-y-4">
          <div *ngIf="formError" class="bg-red-50 border border-red-200 text-red-700 rounded-lg p-3 text-sm">{{ formError }}</div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Tribe Name</label>
            <input [(ngModel)]="form.tribe_name" type="text" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-dt-500 focus:outline-none" />
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Tribe Code</label>
            <input [(ngModel)]="form.tribe_code" type="text" placeholder="e.g. DIGITAL_BANKING" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm font-mono focus:ring-2 focus:ring-dt-500 focus:outline-none" />
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Tribe Leads</label>
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
export class TribeManagementComponent implements OnInit {

  private readonly confirmService = inject(ConfirmService);
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

  emptyForm() { return { tribe_name: '', tribe_code: '', description: '', lead_ids: [] as number[] }; }

  loadAll(): void {
    this.loading = true;
    this.error = '';
    this.api.getTribes().subscribe({
      next: res => { this.tribes = res.data || []; this.loading = false; },
      error: () => { this.error = 'Failed to load tribes.'; this.loading = false; }
    });
    this.api.getUsers().subscribe({ next: res => this.users = res.data || [], error: () => this.error = 'Failed to load lead options. Refresh before editing.' });
  }

  isLead(id: number): boolean { return this.form.lead_ids.includes(id); }
  toggleLead(id: number): void {
    const i = this.form.lead_ids.indexOf(id);
    if (i >= 0) this.form.lead_ids.splice(i, 1); else this.form.lead_ids.push(id);
  }

  openCreate(): void { this.editingId = null; this.form = this.emptyForm(); this.formError = ''; this.showModal = true; }
  openEdit(t: any): void {
    this.editingId = t.id;
    this.form = { tribe_name: t.tribe_name, tribe_code: t.tribe_code, description: t.description ?? '', lead_ids: (t.leads || []).map((l: any) => l.id) };
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
     if (!this.form.tribe_name || !this.form.tribe_code) {
       this.formError = 'Name and code are required.';
       return;
     }
     this.saving = true;
     const wasEditing = !!this.editingId;
     const req = this.editingId
       ? this.api.updateTribe(this.editingId, this.form)
       : this.api.createTribe(this.form);

     req.subscribe({
       next: () => {
         this.showModal = false;
         this.formError = '';
         this.form = this.emptyForm();
         this.editingId = null;
         this.flash(wasEditing ? 'Tribe updated.' : 'Tribe created.');
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
      || 'Failed to save tribe.';
  }

  toggle(t: any): void {
    const req = t.active ? this.api.disableTribe(t.id) : this.api.enableTribe(t.id);
    req.subscribe({ next: () => { this.flash('Status updated.'); this.loadAll(); }, error: () => this.error = 'Failed to update status.' });
  }

  async remove(t: any): Promise<void> {
    if (!await this.confirmService.confirmed({ title: 'Confirm deletion', message: `Delete tribe ${t.tribe_name}?`, confirmLabel: 'Delete', dangerous: true })) return;
    this.api.deleteTribe(t.id).subscribe({ next: () => { this.flash('Tribe deleted.'); this.loadAll(); }, error: err => this.error = err?.error?.error || 'Failed to delete tribe.' });
  }

  private flash(msg: string): void { this.success = msg; setTimeout(() => this.success = '', 3000); }
}
