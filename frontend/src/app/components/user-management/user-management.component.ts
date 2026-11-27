import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminApiService } from '../../services/admin-api.service';
import { of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { ConfirmService } from '../../services/confirm.service';
import { AccessibleModalDirective } from '../../directives/accessible-modal.directive';

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, FormsModule, AccessibleModalDirective],
  template: `
    <div class="max-w-7xl mx-auto px-4 py-8">
      <div class="flex items-center justify-between mb-6">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">User Management</h1>
          <span class="text-sm text-gray-500">{{ users.length }} users</span>
        </div>
        <button (click)="openCreate()"
          class="bg-dt-600 hover:bg-dt-700 text-white px-4 py-2 rounded-lg text-sm font-medium shadow">
          + Add User
        </button>
      </div>

      <div *ngIf="loading" class="text-center text-gray-400 py-20">Loading users...</div>
      <div *ngIf="error" class="bg-red-50 border border-red-200 text-red-700 rounded-lg p-4 mb-4">{{ error }}</div>
      <div *ngIf="success" class="bg-green-50 border border-green-200 text-green-700 rounded-lg p-4 mb-4">{{ success }}</div>

      <div *ngIf="!loading && users.length > 0" class="bg-white rounded-xl shadow overflow-x-auto">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Employee</th>
              <th class="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Email</th>
              <th class="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Role</th>
              <th class="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Team</th>
              <th class="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Reports To</th>
              <th class="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Status</th>
              <th class="px-4 py-3 text-right text-xs font-semibold text-gray-500 uppercase">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
            <tr *ngFor="let user of users" class="hover:bg-gray-50">
              <td class="px-4 py-3">
                <div class="flex items-center gap-3">
                  <div class="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center text-white text-xs font-bold">
                    {{ user.first_name?.[0] }}{{ user.last_name?.[0] }}
                  </div>
                  <div>
                    <p class="text-sm font-medium text-gray-800">{{ user.first_name }} {{ user.last_name }}</p>
                    <p class="text-xs text-gray-400">{{ user.employee_code }}</p>
                  </div>
                </div>
              </td>
              <td class="px-4 py-3 text-sm text-gray-600">{{ user.email }}</td>
              <td class="px-4 py-3">
                <span *ngFor="let role of user.roles"
                  class="inline-block px-2 py-1 bg-blue-100 text-blue-700 rounded-full text-xs font-medium mr-1">
                  {{ role.role_name }}
                </span>
              </td>
              <td class="px-4 py-3 text-sm text-gray-600">{{ user.team_name || '—' }}</td>
              <td class="px-4 py-3 text-sm text-gray-600">{{ user.reporting_manager_name || '—' }}</td>
              <td class="px-4 py-3">
                <span class="px-2 py-1 rounded-full text-xs font-semibold"
                  [ngClass]="user.active ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'">
                  {{ user.active ? 'Active' : 'Inactive' }}
                </span>
              </td>
              <td class="px-4 py-3 text-right whitespace-nowrap">
                <button (click)="openEdit(user)"
                  class="text-blue-600 hover:text-blue-800 text-sm font-medium mr-3">Edit</button>
                <button *ngIf="user.active" (click)="toggleActive(user)"
                  class="text-amber-600 hover:text-amber-800 text-sm font-medium mr-3">Disable</button>
                <button *ngIf="!user.active" (click)="toggleActive(user)"
                  class="text-green-600 hover:text-green-800 text-sm font-medium mr-3">Enable</button>
                <button (click)="remove(user)"
                  class="text-red-600 hover:text-red-800 text-sm font-medium">Delete</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div *ngIf="!loading && users.length === 0 && !error"
           class="text-center text-gray-400 py-20">No users found.</div>
    </div>

    <!-- Modal -->
    <div *ngIf="showModal" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-lg max-h-[90vh] overflow-y-auto" appAccessibleModal="User details" (dismiss)="closeModal()">
        <div class="px-6 py-4 border-b flex items-center justify-between">
          <h2 class="text-lg font-semibold text-gray-800">{{ editingId ? 'Edit User' : 'Create User' }}</h2>
          <button aria-label="Close dialog" (click)="closeModal()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
        </div>
        <div class="px-6 py-4 space-y-4">
          <div *ngIf="formError" class="bg-red-50 border border-red-200 text-red-700 rounded-lg p-3 text-sm">{{ formError }}</div>

          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">First Name</label>
              <input [(ngModel)]="form.first_name" type="text"
                class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-dt-500 focus:outline-none" />
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Last Name</label>
              <input [(ngModel)]="form.last_name" type="text"
                class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-dt-500 focus:outline-none" />
            </div>
          </div>

          <div class="grid grid-cols-2 gap-4" *ngIf="!editingId">
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Employee Code</label>
              <input [(ngModel)]="form.employee_code" type="text"
                class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-dt-500 focus:outline-none" />
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Email</label>
              <input [(ngModel)]="form.email" type="email"
                class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-dt-500 focus:outline-none" />
            </div>
          </div>

            <div *ngIf="!editingId">
              <label class="block text-xs font-medium text-gray-600 mb-1">Password</label>
              <input [(ngModel)]="form.password" type="password" placeholder="Min 8 characters"
                class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-dt-500 focus:outline-none" />
          </div>

          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Roles</label>
            <div class="flex flex-wrap gap-2">
              <label *ngFor="let role of roles"
                class="flex items-center gap-1 px-3 py-1 border rounded-full text-sm cursor-pointer"
                [ngClass]="isRoleSelected(role.role_name) ? 'bg-dt-100 border-dt-300 text-dt-700' : 'border-gray-300 text-gray-600'">
                <input type="checkbox"
                  [checked]="isRoleSelected(role.role_name)"
                  (change)="toggleRole(role.role_name)" />
                {{ role.role_name }}
              </label>
            </div>
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Team</label>
              <select [(ngModel)]="form.team_id"
                class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-dt-500 focus:outline-none">
                <option [ngValue]="null">— None —</option>
                <option *ngFor="let team of teams" [ngValue]="team.id">{{ team.team_name }}</option>
              </select>
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Reporting Manager</label>
              <select [(ngModel)]="form.reporting_manager_id"
                class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-dt-500 focus:outline-none">
                <option [ngValue]="null">— None —</option>
                <option *ngFor="let m of managerOptions()" [ngValue]="m.id">{{ m.first_name }} {{ m.last_name }}</option>
              </select>
            </div>
          </div>

          <div class="border-t pt-4">
            <p class="text-xs font-semibold text-gray-700 uppercase mb-2">Organization Assignments</p>

            <div class="mb-3">
              <label class="block text-xs font-medium text-gray-600 mb-1">Tribes</label>
              <div class="flex flex-wrap gap-2 max-h-24 overflow-y-auto">
                <span *ngIf="!tribes.length" class="text-xs text-gray-400">No tribes available</span>
                <label *ngFor="let t of tribes"
                  class="flex items-center gap-1 px-3 py-1 border rounded-full text-sm cursor-pointer"
                  [ngClass]="isSel(form.tribeIds, t.id) ? 'bg-dt-100 border-dt-300 text-dt-700' : 'border-gray-300 text-gray-600'">
                  <input type="checkbox" [checked]="isSel(form.tribeIds, t.id)" (change)="toggleSel(form.tribeIds, t.id)" />
                  {{ t.tribe_name }}
                </label>
              </div>
            </div>

            <div class="mb-3">
              <label class="block text-xs font-medium text-gray-600 mb-1">Portfolios</label>
              <div class="flex flex-wrap gap-2 max-h-24 overflow-y-auto">
                <span *ngIf="!portfolios.length" class="text-xs text-gray-400">No portfolios available</span>
                <label *ngFor="let p of portfolios"
                  class="flex items-center gap-1 px-3 py-1 border rounded-full text-sm cursor-pointer"
                  [ngClass]="isSel(form.portfolioIds, p.id) ? 'bg-dt-100 border-dt-300 text-dt-700' : 'border-gray-300 text-gray-600'">
                  <input type="checkbox" [checked]="isSel(form.portfolioIds, p.id)" (change)="toggleSel(form.portfolioIds, p.id)" />
                  {{ p.portfolio_name }}
                </label>
              </div>
            </div>

            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Squads</label>
              <div class="flex flex-wrap gap-2 max-h-24 overflow-y-auto">
                <span *ngIf="!squads.length" class="text-xs text-gray-400">No squads available</span>
                <label *ngFor="let s of squads"
                  class="flex items-center gap-1 px-3 py-1 border rounded-full text-sm cursor-pointer"
                  [ngClass]="isSel(form.squadIds, s.id) ? 'bg-dt-100 border-dt-300 text-dt-700' : 'border-gray-300 text-gray-600'">
                  <input type="checkbox" [checked]="isSel(form.squadIds, s.id)" (change)="toggleSel(form.squadIds, s.id)" />
                  {{ s.squad_name }}
                </label>
              </div>
            </div>
          </div>
        </div>
        <div class="px-6 py-4 border-t flex justify-end gap-3">
          <button (click)="closeModal()"
            class="px-4 py-2 text-sm font-medium text-gray-600 hover:text-gray-800">Cancel</button>
           <button (click)="save()" [disabled]="saving || assignmentsLoading || assignmentsLoadFailed"
             class="bg-dt-600 hover:bg-dt-700 disabled:opacity-50 text-white px-4 py-2 rounded-lg text-sm font-medium">
            {{ assignmentsLoading ? 'Loading assignments...' : saving ? 'Saving...' : (editingId ? 'Update' : 'Create') }}
          </button>
        </div>
      </div>
    </div>
  `
})
export class UserManagementComponent implements OnInit {

  private readonly confirmService = inject(ConfirmService);
  users: any[] = [];
  roles: any[] = [];
  teams: any[] = [];
  tribes: any[] = [];
  portfolios: any[] = [];
  squads: any[] = [];
  loading = true;
  error = '';
  success = '';

  showModal = false;
  saving = false;
  formError = '';
  assignmentsLoading = false;
  assignmentsLoadFailed = false;
  editingId: number | null = null;
  form: any = this.emptyForm();

   constructor(private api: AdminApiService) {}

  ngOnInit(): void {
    this.loadAll();
  }

  emptyForm() {
    return {
      employee_code: '',
      first_name: '',
      last_name: '',
      email: '',
      password: '',
      roleNames: [] as string[],
      team_id: null,
      reporting_manager_id: null,
      tribeIds: [] as number[],
      portfolioIds: [] as number[],
      squadIds: [] as number[]
    };
  }

  loadAll(): void {
    this.loading = true;
    this.error = '';
    this.api.getUsers().subscribe({
      next: res => { this.users = res.data || []; this.loading = false; },
      error: () => { this.error = 'Failed to load users.'; this.loading = false; }
    });
    this.api.getRoles().subscribe({ next: res => this.roles = res.data || [], error: () => this.error = 'Failed to load role options. Refresh before editing users.' });
    this.api.getTeams().subscribe({ next: res => this.teams = res.data || [], error: () => this.error = 'Failed to load team options. Refresh before editing users.' });
    this.api.getTribes().subscribe({ next: res => this.tribes = res.data || [], error: () => this.error = 'Failed to load tribe options. Refresh before editing users.' });
    this.api.getPortfolios().subscribe({ next: res => this.portfolios = res.data || [], error: () => this.error = 'Failed to load portfolio options. Refresh before editing users.' });
    this.api.getSquads().subscribe({ next: res => this.squads = res.data || [], error: () => this.error = 'Failed to load squad options. Refresh before editing users.' });
  }

  managerOptions(): any[] {
    return this.users.filter(u => u.id !== this.editingId);
  }

  isRoleSelected(name: string): boolean {
    return this.form.roleNames.includes(name);
  }

  toggleRole(name: string): void {
    const idx = this.form.roleNames.indexOf(name);
    if (idx >= 0) this.form.roleNames.splice(idx, 1);
    else this.form.roleNames.push(name);
  }

  isSel(list: number[], id: number): boolean {
    return list.includes(id);
  }

  toggleSel(list: number[], id: number): void {
    const idx = list.indexOf(id);
    if (idx >= 0) list.splice(idx, 1);
    else list.push(id);
  }

  openCreate(): void {
    this.editingId = null;
    this.form = this.emptyForm();
    this.formError = '';
    this.assignmentsLoading = false;
    this.assignmentsLoadFailed = false;
    this.showModal = true;
  }

  openEdit(user: any): void {
    this.editingId = user.id;
    this.form = {
      first_name: user.first_name,
      last_name: user.last_name,
      roleNames: (user.roles || []).map((r: any) => r.role_name),
      team_id: user.team_id ?? null,
      reporting_manager_id: user.reporting_manager_id ?? null,
      tribeIds: [],
      portfolioIds: [],
      squadIds: []
    };
    this.formError = '';
    this.assignmentsLoading = true;
    this.assignmentsLoadFailed = false;
    const editForm = this.form;
    this.api.getUserAssignments(user.id).subscribe({
      next: res => {
        if (this.form !== editForm) return;
        const data = res.data || [];
        this.form.tribeIds = data.filter((a: any) => a.unit_type === 'TRIBE').map((a: any) => a.unit_id);
        this.form.portfolioIds = data.filter((a: any) => a.unit_type === 'PORTFOLIO').map((a: any) => a.unit_id);
        this.form.squadIds = data.filter((a: any) => a.unit_type === 'SQUAD').map((a: any) => a.unit_id);
        this.assignmentsLoading = false;
      },
      error: () => {
        if (this.form !== editForm) return;
        this.assignmentsLoading = false;
        this.assignmentsLoadFailed = true;
        this.formError = 'Failed to load existing assignments. Reopen this user before saving to avoid losing assignments.';
      }
    });
    this.showModal = true;
  }

   closeModal(): void {
     if (this.saving) return;
     this.showModal = false;
     this.saving = false;
     this.form = this.emptyForm();
     this.formError = '';
     this.editingId = null;
   }

   save(): void {
     if (this.saving || this.assignmentsLoading || this.assignmentsLoadFailed) return;
     this.formError = '';
     if (!this.form.first_name || !this.form.last_name) {
       this.formError = 'First and last name are required.';
       return;
     }
     if (!this.editingId && (!this.form.employee_code || !this.form.email || !this.form.password)) {
       this.formError = 'Employee code, email and password are required.';
       return;
     }
     if (!this.form.roleNames || this.form.roleNames.length === 0) {
       this.formError = 'Select at least one role.';
       return;
     }

     this.saving = true;
     const wasEditing = !!this.editingId;
     let savedUserId = this.editingId;
     const req = this.editingId
       ? this.api.updateUser(this.editingId, {
           first_name: this.form.first_name,
           last_name: this.form.last_name,
           roleNames: this.form.roleNames,
           team_id: this.form.team_id,
           reporting_manager_id: this.form.reporting_manager_id
         })
       : this.api.createUser(this.form);

     req.pipe(
       switchMap((res: any) => {
         const userId = this.editingId ?? res?.data?.id;
         savedUserId = userId;
         if (!userId) {
           return of(false);
         }

         return this.api.setUserAssignments(userId, {
           tribeIds: this.form.tribeIds,
           portfolioIds: this.form.portfolioIds,
           squadIds: this.form.squadIds
         }).pipe(map(() => true), catchError(() => of(false)));
       })
     ).subscribe({
       next: assignmentsSaved => {
         if (!assignmentsSaved) {
           this.editingId = savedUserId ?? null;
           this.formError = 'User details saved, but organization assignments failed. Retry Update to save the assignments.';
           this.loadAll();
           return;
         }
         this.showModal = false;
         this.form = this.emptyForm();
         this.formError = '';
         this.editingId = null;
         this.flash(wasEditing ? 'User updated successfully.' : 'User created successfully.');
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
      || 'Failed to save user.';
  }

  toggleActive(user: any): void {
    const req = user.active ? this.api.deactivateUser(user.id) : this.api.activateUser(user.id);
    req.subscribe({
      next: () => { this.flash('Status updated.'); this.loadAll(); },
      error: () => this.error = 'Failed to update status.'
    });
  }

  async remove(user: any): Promise<void> {
    if (!await this.confirmService.confirmed({ title: 'Confirm deletion', message: `Delete user ${user.first_name} ${user.last_name}?`, confirmLabel: 'Delete', dangerous: true })) return;
    this.api.deleteUser(user.id).subscribe({
      next: () => { this.flash('User deleted.'); this.loadAll(); },
      error: () => this.error = 'Failed to delete user.'
    });
  }

  private flash(msg: string): void {
    this.success = msg;
    setTimeout(() => this.success = '', 3000);
  }
}
