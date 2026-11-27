import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminApiService } from '../../services/admin-api.service';
import { ConfirmService } from '../../services/confirm.service';
import { AccessibleModalDirective } from '../../directives/accessible-modal.directive';

@Component({
  selector: 'app-assignment-management',
  standalone: true,
  imports: [CommonModule, FormsModule, AccessibleModalDirective],
  template: `
    <div class="max-w-6xl mx-auto px-4 py-8">
      <div class="flex items-center justify-between mb-6">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">Assignment Management</h1>
          <span class="text-sm text-gray-500">{{ assignments.length }} assignments</span>
        </div>
        <button (click)="openCreate()"
          class="bg-dt-600 hover:bg-dt-700 text-white px-4 py-2 rounded-lg text-sm font-medium shadow">
          + New Assignment
        </button>
      </div>

      <div *ngIf="loading" class="text-center text-gray-400 py-20">Loading assignments...</div>
      <div *ngIf="error" class="bg-red-50 border border-red-200 text-red-700 rounded-lg p-4 mb-4">{{ error }}</div>
      <div *ngIf="success" class="bg-green-50 border border-green-200 text-green-700 rounded-lg p-4 mb-4">{{ success }}</div>

      <div *ngIf="!loading && assignments.length > 0" class="bg-white rounded-xl shadow overflow-x-auto">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">User</th>
              <th class="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Type</th>
              <th class="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Unit</th>
              <th class="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Start</th>
              <th class="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">End</th>
              <th class="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Primary</th>
              <th class="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Status</th>
              <th class="px-4 py-3 text-right text-xs font-semibold text-gray-500 uppercase">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
            <tr *ngFor="let a of assignments" class="hover:bg-gray-50">
              <td class="px-4 py-3 text-sm font-medium text-gray-800">{{ a.user_name }}</td>
              <td class="px-4 py-3">
                <span class="px-2 py-1 rounded-full text-xs font-semibold"
                  [ngClass]="{
                    'bg-indigo-100 text-indigo-700': a.unit_type === 'TRIBE',
                    'bg-sky-100 text-sky-700': a.unit_type === 'PORTFOLIO',
                    'bg-teal-100 text-teal-700': a.unit_type === 'SQUAD'
                  }">{{ a.unit_type }}</span>
              </td>
              <td class="px-4 py-3 text-sm text-gray-600">{{ a.unit_name }}</td>
              <td class="px-4 py-3 text-sm text-gray-500">{{ a.start_date || '—' }}</td>
              <td class="px-4 py-3 text-sm text-gray-500">{{ a.end_date || '—' }}</td>
              <td class="px-4 py-3 text-sm">
                <span *ngIf="a.primary_assignment" class="text-green-600 font-medium">Primary</span>
                <span *ngIf="!a.primary_assignment" class="text-gray-400">—</span>
              </td>
              <td class="px-4 py-3">
                <span class="px-2 py-1 rounded-full text-xs font-semibold"
                  [ngClass]="a.active ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'">
                  {{ a.active ? 'Active' : 'Inactive' }}
                </span>
              </td>
              <td class="px-4 py-3 text-right">
                <button (click)="remove(a)" class="text-red-600 hover:text-red-800 text-sm font-medium">Remove</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div *ngIf="!loading && assignments.length === 0 && !error" class="text-center text-gray-400 py-20">
        <p>No assignments yet. Assign a user to a tribe, portfolio, or squad to establish their review scope.</p>
        <button (click)="openCreate()" class="mt-3 text-dt-700 underline font-semibold">Create the first assignment</button>
      </div>
    </div>

    <div *ngIf="showModal" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-md max-h-[90vh] overflow-y-auto" appAccessibleModal="New assignment" (dismiss)="closeModal()">
        <div class="px-6 py-4 border-b flex items-center justify-between">
          <h2 class="text-lg font-semibold text-gray-800">New Assignment</h2>
          <button aria-label="Close dialog" (click)="closeModal()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
        </div>
        <div class="px-6 py-4 space-y-4">
          <div *ngIf="formError" class="bg-red-50 border border-red-200 text-red-700 rounded-lg p-3 text-sm">{{ formError }}</div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">User</label>
            <select [(ngModel)]="form.user_id" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm">
              <option [ngValue]="null">— Select user —</option>
              <option *ngFor="let u of users" [ngValue]="u.id">{{ u.first_name }} {{ u.last_name }}</option>
            </select>
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Unit Type</label>
            <select [(ngModel)]="form.unit_type" (ngModelChange)="onTypeChange()" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-dt-500 focus:outline-none">
              <option value="TRIBE">Tribe</option>
              <option value="PORTFOLIO">Portfolio</option>
              <option value="SQUAD">Squad</option>
            </select>
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Organizational Unit</label>
            <select [(ngModel)]="form.unit_id" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm">
              <option [ngValue]="null">— Select unit —</option>
              <option *ngFor="let u of unitOptions()" [ngValue]="u.id">{{ u.label }}</option>
            </select>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Start Date</label>
              <input [(ngModel)]="form.start_date" type="date" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-dt-500 focus:outline-none" />
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">End Date</label>
              <input [(ngModel)]="form.end_date" type="date" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-dt-500 focus:outline-none" />
            </div>
          </div>
          <div class="flex items-center gap-4">
            <label class="flex items-center gap-2 text-sm text-gray-700">
              <input type="checkbox" [(ngModel)]="form.primary_assignment" /> Primary Assignment
            </label>
            <label class="flex items-center gap-2 text-sm text-gray-700">
              <input type="checkbox" [(ngModel)]="form.active" /> Active
            </label>
          </div>
        </div>
        <div class="px-6 py-4 border-t flex justify-end gap-3">
          <button (click)="closeModal()" class="px-4 py-2 text-sm font-medium text-gray-600 hover:text-gray-800">Cancel</button>
           <button (click)="save()" [disabled]="saving" class="bg-dt-600 hover:bg-dt-700 disabled:opacity-50 text-white px-4 py-2 rounded-lg text-sm font-medium">
            {{ saving ? 'Saving...' : 'Assign' }}
          </button>
        </div>
      </div>
    </div>
  `
})
export class AssignmentManagementComponent implements OnInit {

  private readonly confirmService = inject(ConfirmService);
  assignments: any[] = [];
  users: any[] = [];
  tribes: any[] = [];
  portfolios: any[] = [];
  squads: any[] = [];
  loading = true;
  error = '';
  success = '';
  showModal = false;
  saving = false;
  formError = '';
  form: any = this.emptyForm();
  private cachedUnitOptions: any[] = [];
  private lastUnitType = '';

   constructor(private api: AdminApiService) {}

  ngOnInit(): void { this.loadAll(); }

  emptyForm() {
    return { user_id: null, unit_type: 'SQUAD', unit_id: null, start_date: null, end_date: null, primary_assignment: false, active: true };
  }

  loadAll(): void {
    this.loading = true;
    this.error = '';
    this.cachedUnitOptions = [];
    this.lastUnitType = '';
    this.api.getAllAssignments().subscribe({
      next: res => { this.assignments = res.data || []; this.loading = false; },
      error: () => { this.error = 'Failed to load assignments.'; this.loading = false; }
    });
    this.api.getUsers().subscribe({ next: res => this.users = res.data || [], error: () => this.error = 'Failed to load user options. Refresh before assigning.' });
    this.api.getTribes().subscribe({ next: res => this.tribes = res.data || [], error: () => this.error = 'Failed to load tribe options. Refresh before assigning.' });
    this.api.getPortfolios().subscribe({ next: res => this.portfolios = res.data || [], error: () => this.error = 'Failed to load portfolio options. Refresh before assigning.' });
    this.api.getSquads().subscribe({ next: res => this.squads = res.data || [], error: () => this.error = 'Failed to load squad options. Refresh before assigning.' });
  }

  unitOptions(): any[] {
    // Cache unitOptions to avoid recalculating on every render
    if (this.lastUnitType === this.form.unit_type && this.cachedUnitOptions.length > 0) {
      return this.cachedUnitOptions;
    }

    this.lastUnitType = this.form.unit_type;

    if (this.form.unit_type === 'TRIBE') {
      this.cachedUnitOptions = this.tribes.map(t => ({ id: t.id, label: t.tribe_name }));
    } else if (this.form.unit_type === 'PORTFOLIO') {
      this.cachedUnitOptions = this.portfolios.map(p => ({ id: p.id, label: `${p.tribe_name} › ${p.portfolio_name}` }));
    } else {
      this.cachedUnitOptions = this.squads.map(s => ({ id: s.id, label: `${s.portfolio_name} › ${s.squad_name}` }));
    }

    return this.cachedUnitOptions;
  }

  onTypeChange(): void {
    this.form.unit_id = null;
    this.cachedUnitOptions = [];
    this.lastUnitType = '';
  }

  openCreate(): void { this.form = this.emptyForm(); this.formError = ''; this.showModal = true; }
  closeModal(): void {
    if (this.saving) return;
    this.showModal = false;
    this.saving = false;
    this.formError = '';
    this.form = this.emptyForm();
    this.cachedUnitOptions = [];
    this.lastUnitType = '';
  }

   save(): void {
     if (this.saving) return;
     this.formError = '';

     // Validation: Check required fields
     if (!this.form.user_id || !this.form.unit_id) {
       this.formError = 'Select a user and a unit.';
       return;
     }

     // Validation: Date logic - if end_date is provided, ensure it's after start_date
     if (this.form.start_date && this.form.end_date) {
       const startDate = new Date(this.form.start_date);
       const endDate = new Date(this.form.end_date);

       if (endDate <= startDate) {
         this.formError = 'End date must be after start date.';
         return;
       }
     }

     // Validation: Warn if start_date is in the past (but allow it)
     if (this.form.start_date) {
       const startDate = new Date(this.form.start_date);
       const today = new Date();
       today.setHours(0, 0, 0, 0);

       if (startDate < today) {
         // Allow past dates but we could add a warning if needed
         console.warn('Assignment start date is in the past');
       }
     }

     this.saving = true;
     const payload = {
       user_id: this.form.user_id,
       unit_id: this.form.unit_id,
       start_date: this.form.start_date || null,
       end_date: this.form.end_date || null,
       primary_assignment: this.form.primary_assignment,
       active: this.form.active
     };
     let req;
     if (this.form.unit_type === 'TRIBE') req = this.api.assignUserToTribe(payload);
     else if (this.form.unit_type === 'PORTFOLIO') req = this.api.assignUserToPortfolio(payload);
     else req = this.api.assignUserToSquad(payload);

     req.subscribe({
       next: () => {
         this.showModal = false;
         this.formError = '';
         this.form = this.emptyForm();
         this.cachedUnitOptions = [];
         this.lastUnitType = '';
         this.flash('Assignment created successfully.');
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
      || 'Failed to create assignment.';
  }

  async remove(a: any): Promise<void> {
    if (!await this.confirmService.confirmed({ title: 'Confirm removal', message: `Remove ${a.user_name}'s assignment to ${a.unit_name}?`, confirmLabel: 'Remove', dangerous: true })) return;
    let req;
    if (a.unit_type === 'TRIBE') req = this.api.removeTribeAssignment(a.id);
    else if (a.unit_type === 'PORTFOLIO') req = this.api.removePortfolioAssignment(a.id);
    else req = this.api.removeSquadAssignment(a.id);
    req.subscribe({
      next: () => { this.flash('Assignment removed successfully.'); this.loadAll(); },
      error: () => this.error = 'Failed to remove assignment.'
    });
  }

  private flash(msg: string): void { this.success = msg; setTimeout(() => this.success = '', 3000); }
}
