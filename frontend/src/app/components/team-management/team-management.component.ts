import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminApiService } from '../../services/admin-api.service';
import { ConfirmService } from '../../services/confirm.service';
import { AccessibleModalDirective } from '../../directives/accessible-modal.directive';

@Component({
  selector: 'app-team-management',
  standalone: true,
  imports: [CommonModule, FormsModule, AccessibleModalDirective],
  template: `
    <div class="max-w-5xl mx-auto px-4 py-8">
      <div class="flex items-center justify-between mb-6">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">Team Management</h1>
          <span class="text-sm text-gray-500">{{ teams.length }} teams</span>
        </div>
        <button (click)="openCreate()"
          class="bg-dt-600 hover:bg-dt-700 text-white px-4 py-2 rounded-lg text-sm font-medium shadow">
          + Add Team
        </button>
      </div>

      <div *ngIf="loading" class="text-center text-gray-400 py-20">Loading teams...</div>
      <div *ngIf="error" class="bg-red-50 border border-red-200 text-red-700 rounded-lg p-4 mb-4">{{ error }}</div>
      <div *ngIf="success" class="bg-green-50 border border-green-200 text-green-700 rounded-lg p-4 mb-4">{{ success }}</div>

      <div *ngIf="!loading && teams.length > 0" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div *ngFor="let team of teams"
          class="bg-white rounded-xl shadow p-5 hover:shadow-md transition">
          <div class="flex items-start justify-between mb-3">
            <div>
              <h3 class="text-base font-semibold text-gray-800">{{ team.team_name }}</h3>
              <span class="text-xs text-gray-400 font-mono">{{ team.team_code }}</span>
            </div>
            <span class="px-2 py-1 rounded-full text-xs font-semibold"
              [ngClass]="team.active ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'">
              {{ team.active ? 'Active' : 'Inactive' }}
            </span>
          </div>
          <p class="text-sm text-gray-500 mb-4">{{ team.description || 'No description' }}</p>
          <div class="flex items-center justify-between text-sm text-gray-600 border-t pt-3">
            <span>
              <span class="font-medium">Lead:</span>
              {{ team.team_lead_name || 'Not assigned' }}
            </span>
            <span class="text-gray-400">{{ team.member_count }} members</span>
          </div>
          <div class="flex items-center gap-3 mt-4 pt-3 border-t">
            <button (click)="openEdit(team)"
              class="text-blue-600 hover:text-blue-800 text-sm font-medium">Edit</button>
            <button *ngIf="team.active" (click)="toggleActive(team)"
              class="text-amber-600 hover:text-amber-800 text-sm font-medium">Disable</button>
            <button *ngIf="!team.active" (click)="toggleActive(team)"
              class="text-green-600 hover:text-green-800 text-sm font-medium">Enable</button>
            <button (click)="remove(team)"
              class="text-red-600 hover:text-red-800 text-sm font-medium ml-auto">Delete</button>
          </div>
        </div>
      </div>

      <div *ngIf="!loading && teams.length === 0 && !error"
           class="text-center text-gray-400 py-20">No teams found.</div>
    </div>

    <!-- Modal -->
    <div *ngIf="showModal" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-md" appAccessibleModal="Team details" (dismiss)="closeModal()">
        <div class="px-6 py-4 border-b flex items-center justify-between">
          <h2 class="text-lg font-semibold text-gray-800">{{ editingId ? 'Edit Team' : 'Create Team' }}</h2>
          <button aria-label="Close dialog" (click)="closeModal()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
        </div>
        <div class="px-6 py-4 space-y-4">
          <div *ngIf="formError" class="bg-red-50 border border-red-200 text-red-700 rounded-lg p-3 text-sm">{{ formError }}</div>

          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Team Name</label>
            <input [(ngModel)]="form.team_name" type="text"
              class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-dt-500 focus:outline-none" />
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Team Code</label>
            <input [(ngModel)]="form.team_code" type="text" placeholder="e.g. ENG"
              class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm font-mono focus:ring-2 focus:ring-dt-500 focus:outline-none" />
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Team Lead</label>
            <select [(ngModel)]="form.team_lead_id"
              class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-dt-500 focus:outline-none">
              <option [ngValue]="null">— Not assigned —</option>
              <option *ngFor="let u of users" [ngValue]="u.id">{{ u.first_name }} {{ u.last_name }}</option>
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
export class TeamManagementComponent implements OnInit {

  private readonly confirmService = inject(ConfirmService);
  teams: any[] = [];
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

  ngOnInit(): void {
    this.loadAll();
  }

  emptyForm() {
    return { team_name: '', team_code: '', description: '', team_lead_id: null };
  }

  loadAll(): void {
    this.loading = true;
    this.error = '';
    this.api.getTeams().subscribe({
      next: res => { this.teams = res.data || []; this.loading = false; },
      error: () => { this.error = 'Failed to load teams.'; this.loading = false; }
    });
    this.api.getUsers().subscribe({ next: res => this.users = res.data || [], error: () => this.error = 'Failed to load lead options. Refresh before editing.' });
  }

  openCreate(): void {
    this.editingId = null;
    this.form = this.emptyForm();
    this.formError = '';
    this.showModal = true;
  }

  openEdit(team: any): void {
    this.editingId = team.id;
    this.form = {
      team_name: team.team_name,
      team_code: team.team_code,
      description: team.description ?? '',
      team_lead_id: team.team_lead_id ?? null
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
     if (!this.form.team_name || !this.form.team_code) {
       this.formError = 'Team name and code are required.';
       return;
     }
     this.saving = true;
     const wasEditing = !!this.editingId;

     const req = this.editingId
       ? this.api.updateTeam(this.editingId, this.form)
       : this.api.createTeam(this.form);

     req.subscribe({
       next: () => {
         this.showModal = false;
         this.form = this.emptyForm();
         this.formError = '';
          this.editingId = null;
          this.flash(wasEditing ? 'Team updated successfully.' : 'Team created successfully.');
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
      || 'Failed to save team.';
  }

  toggleActive(team: any): void {
    const req = team.active ? this.api.disableTeam(team.id) : this.api.enableTeam(team.id);
    req.subscribe({
      next: () => { this.flash('Status updated.'); this.loadAll(); },
      error: () => this.error = 'Failed to update status.'
    });
  }

  async remove(team: any): Promise<void> {
    if (!await this.confirmService.confirmed({ title: 'Confirm deletion', message: `Delete team ${team.team_name}?`, confirmLabel: 'Delete', dangerous: true })) return;
    this.api.deleteTeam(team.id).subscribe({
      next: () => { this.flash('Team deleted.'); this.loadAll(); },
      error: err => this.error = err?.error?.error || 'Failed to delete team.'
    });
  }

  private flash(msg: string): void {
    this.success = msg;
    setTimeout(() => this.success = '', 3000);
  }
}
