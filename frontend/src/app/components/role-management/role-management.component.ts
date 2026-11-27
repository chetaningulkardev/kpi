import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminApiService } from '../../services/admin-api.service';
import { ConfirmService } from '../../services/confirm.service';
import { AccessibleModalDirective } from '../../directives/accessible-modal.directive';

@Component({
  selector: 'app-role-management',
  standalone: true,
  imports: [CommonModule, FormsModule, AccessibleModalDirective],
  template: `
    <div class="max-w-5xl mx-auto px-4 py-8">
      <div class="flex items-center justify-between mb-6">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">Role Management</h1>
          <span class="text-sm text-gray-500">{{ roles.length }} roles</span>
        </div>
        <button (click)="openCreate()"
          class="bg-dt-600 hover:bg-dt-700 text-white px-4 py-2 rounded-lg text-sm font-medium shadow">
          + Add Role
        </button>
      </div>

      <div *ngIf="loading" class="text-center text-gray-400 py-20">Loading roles...</div>
      <div *ngIf="error" class="bg-red-50 border border-red-200 text-red-700 rounded-lg p-4 mb-4">{{ error }}</div>
      <div *ngIf="success" class="bg-green-50 border border-green-200 text-green-700 rounded-lg p-4 mb-4">{{ success }}</div>

      <div *ngIf="!loading && roles.length > 0" class="bg-white rounded-xl shadow overflow-x-auto">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Role Name</th>
              <th class="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Role Code</th>
              <th class="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Reports To</th>
              <th class="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Description</th>
              <th class="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Status</th>
              <th class="px-4 py-3 text-right text-xs font-semibold text-gray-500 uppercase">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
            <tr *ngFor="let role of roles" class="hover:bg-gray-50">
              <td class="px-4 py-3 text-sm font-medium text-gray-800">{{ role.role_name }}</td>
              <td class="px-4 py-3">
                <span class="px-2 py-1 bg-purple-100 text-purple-700 rounded text-xs font-mono">{{ role.role_code }}</span>
              </td>
              <td class="px-4 py-3 text-sm text-gray-600">
                <span *ngIf="role.parent_role_name" class="flex items-center gap-1">
                  <span class="text-gray-400">↑</span> {{ role.parent_role_name }}
                </span>
                <span *ngIf="!role.parent_role_name" class="text-gray-400">Top level</span>
              </td>
              <td class="px-4 py-3 text-sm text-gray-600">{{ role.description }}</td>
              <td class="px-4 py-3">
                <span class="px-2 py-1 rounded-full text-xs font-semibold"
                  [ngClass]="role.active ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'">
                  {{ role.active ? 'Active' : 'Inactive' }}
                </span>
              </td>
              <td class="px-4 py-3 text-right whitespace-nowrap">
                <button (click)="openEdit(role)"
                  class="text-blue-600 hover:text-blue-800 text-sm font-medium mr-3">Edit</button>
                <button *ngIf="role.active" (click)="toggleActive(role)"
                  class="text-amber-600 hover:text-amber-800 text-sm font-medium mr-3">Disable</button>
                <button *ngIf="!role.active" (click)="toggleActive(role)"
                  class="text-green-600 hover:text-green-800 text-sm font-medium mr-3">Enable</button>
                <button (click)="remove(role)"
                  class="text-red-600 hover:text-red-800 text-sm font-medium">Delete</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div *ngIf="!loading && roles.length === 0 && !error"
           class="text-center text-gray-400 py-20">No roles found.</div>
    </div>

    <!-- Modal -->
    <div *ngIf="showModal" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-md" appAccessibleModal="Role details" (dismiss)="closeModal()">
        <div class="px-6 py-4 border-b flex items-center justify-between">
          <h2 class="text-lg font-semibold text-gray-800">{{ editingId ? 'Edit Role' : 'Create Role' }}</h2>
          <button aria-label="Close dialog" (click)="closeModal()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
        </div>
        <div class="px-6 py-4 space-y-4">
          <div *ngIf="formError" class="bg-red-50 border border-red-200 text-red-700 rounded-lg p-3 text-sm">{{ formError }}</div>

          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Role Name</label>
            <input [(ngModel)]="form.role_name" type="text"
              class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-dt-500 focus:outline-none" />
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Role Code</label>
            <input [(ngModel)]="form.role_code" type="text" placeholder="e.g. SENIOR_DEVELOPER"
              class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm font-mono focus:ring-2 focus:ring-dt-500 focus:outline-none" />
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Reports To (Parent Role)</label>
            <select [(ngModel)]="form.parent_role_id"
              class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-dt-500 focus:outline-none">
              <option [ngValue]="null">— Top level —</option>
              <option *ngFor="let r of parentOptions()" [ngValue]="r.id">{{ r.role_name }}</option>
            </select>
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Description</label>
            <textarea [(ngModel)]="form.description" rows="2"
              class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-dt-500 focus:outline-none"></textarea>
          </div>
        </div>
        <div class="px-6 py-4 border-t flex justify-end gap-3">
          <button (click)="closeModal()"
            class="px-4 py-2 text-sm font-medium text-gray-600 hover:text-gray-800">Cancel</button>
          <button (click)="save()" [disabled]="saving"
            class="bg-dt-600 hover:bg-dt-700 disabled:opacity-50 text-white px-4 py-2 rounded-lg text-sm font-medium">
            {{ saving ? 'Saving...' : (editingId ? 'Update' : 'Create') }}
          </button>
        </div>
      </div>
    </div>
  `
})
export class RoleManagementComponent implements OnInit {

  private readonly confirmService = inject(ConfirmService);
  roles: any[] = [];
  loading = true;
  error = '';
  success = '';

  showModal = false;
  saving = false;
  formError = '';
  editingId: number | null = null;
  form: any = this.emptyForm();

   constructor(private api: AdminApiService) {}

  ngOnInit(): void {
    this.load();
  }

  emptyForm() {
    return { role_name: '', role_code: '', parent_role_id: null, description: '' };
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.api.getRoleHierarchy().subscribe({
      next: res => { this.roles = res.data || []; this.loading = false; },
      error: () => { this.error = 'Failed to load roles.'; this.loading = false; }
    });
  }

  parentOptions(): any[] {
    return this.roles.filter(r => r.id !== this.editingId);
  }

  openCreate(): void {
    this.editingId = null;
    this.form = this.emptyForm();
    this.formError = '';
    this.showModal = true;
  }

  openEdit(role: any): void {
    this.editingId = role.id;
    this.form = {
      role_name: role.role_name,
      role_code: role.role_code,
      parent_role_id: role.parent_role_id ?? null,
      description: role.description ?? ''
    };
    this.formError = '';
    this.showModal = true;
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
     if (!this.form.role_name || !this.form.role_code) {
       this.formError = 'Role name and code are required.';
       return;
     }
     this.saving = true;
     const wasEditing = !!this.editingId;
     const req = this.editingId
       ? this.api.updateRole(this.editingId, this.form)
       : this.api.createRole(this.form);

     req.subscribe({
       next: () => {
         this.showModal = false;
         this.formError = '';
         this.form = this.emptyForm();
         this.editingId = null;
         this.flash(wasEditing ? 'Role updated successfully.' : 'Role created successfully.');
         this.load();
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
      || 'Failed to save role.';
  }

  toggleActive(role: any): void {
    const req = role.active ? this.api.disableRole(role.id) : this.api.enableRole(role.id);
    req.subscribe({
      next: () => { this.flash('Status updated.'); this.load(); },
      error: () => this.error = 'Failed to update status.'
    });
  }

  async remove(role: any): Promise<void> {
    if (!await this.confirmService.confirmed({ title: 'Confirm deletion', message: `Delete role ${role.role_name}?`, confirmLabel: 'Delete', dangerous: true })) return;
    this.api.deleteRole(role.id).subscribe({
      next: () => { this.flash('Role deleted.'); this.load(); },
      error: err => this.error = err?.error?.error || 'Failed to delete role.'
    });
  }

  private flash(msg: string): void {
    this.success = msg;
    setTimeout(() => this.success = '', 3000);
  }
}
