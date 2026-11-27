import { Component, OnInit, DestroyRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../services/auth.service';
import { AdminApiService } from '../../services/admin-api.service';
import { User } from '../../models/auth.model';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

interface UserAssignment {
  unit_type: 'TRIBE' | 'PORTFOLIO' | 'SQUAD';
  unit_name: string;
  primary_assignment: boolean;
}

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="max-w-2xl mx-auto px-4 py-10">
      <h1 class="text-2xl font-bold text-gray-800 mb-6">My Profile</h1>

      <div *ngIf="user" class="bg-white rounded-xl shadow p-6 space-y-4">
        <div class="flex items-center gap-4 mb-6">
          <div class="w-16 h-16 rounded-full bg-blue-600 flex items-center justify-center text-white text-2xl font-bold">
            {{ user.first_name[0] }}{{ user.last_name[0] }}
          </div>
          <div>
            <h2 class="text-xl font-semibold text-gray-800">{{ user.first_name }} {{ user.last_name }}</h2>
            <p class="text-gray-500">{{ user.roles[0]?.role_name }}</p>
          </div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 break-words">
          <div class="bg-gray-50 p-3 rounded-lg">
            <p class="text-xs text-gray-500 uppercase tracking-wide">Employee Code</p>
            <p class="font-semibold text-gray-800 mt-1">{{ user.employee_code }}</p>
          </div>
          <div class="bg-gray-50 p-3 rounded-lg">
            <p class="text-xs text-gray-500 uppercase tracking-wide">Email</p>
            <p class="font-semibold text-gray-800 mt-1">{{ user.email }}</p>
          </div>
          <div class="bg-gray-50 p-3 rounded-lg">
            <p class="text-xs text-gray-500 uppercase tracking-wide">Status</p>
            <span class="inline-block mt-1 px-2 py-1 rounded-full text-xs font-semibold"
              [ngClass]="user.active ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'">
              {{ user.active ? 'Active' : 'Inactive' }}
            </span>
          </div>
          <div class="bg-gray-50 p-3 rounded-lg">
            <p class="text-xs text-gray-500 uppercase tracking-wide">Last Login</p>
            <p class="font-semibold text-gray-800 mt-1">{{ user.last_login ? (user.last_login | date:'medium') : 'Not available' }}</p>
          </div>
        </div>

        <div class="mt-4">
          <p class="text-xs text-gray-500 uppercase tracking-wide mb-2">Roles</p>
          <div class="flex flex-wrap gap-2">
            <span *ngFor="let role of user.roles"
              class="px-3 py-1 bg-blue-100 text-blue-700 rounded-full text-sm font-medium">
              {{ role.role_name }}
            </span>
          </div>
        </div>
      </div>

      <!-- Organization Assignments -->
      <div *ngIf="user" class="bg-white rounded-xl shadow p-6 mt-6">
        <h3 class="text-lg font-semibold text-gray-800 mb-4">My Organization Assignments</h3>

        <div *ngIf="assignmentsLoading" class="text-gray-400 text-sm py-4">Loading assignments...</div>

        <p *ngIf="assignmentsError" role="alert" class="text-sm text-red-700">{{ assignmentsError }}</p>
        <div *ngIf="!assignmentsLoading && !assignmentsError" class="space-y-4">
          <div>
            <p class="text-xs text-indigo-600 font-semibold uppercase tracking-wide mb-2">Tribes</p>
            <div class="flex flex-wrap gap-2">
              <span *ngIf="!tribes.length" class="text-sm text-gray-400">No tribe assignments</span>
              <span *ngFor="let a of tribes" class="px-3 py-1 bg-indigo-50 text-indigo-700 rounded-full text-sm">
                {{ a.unit_name }}<span *ngIf="a.primary_assignment" class="ml-1 text-xs text-green-600">(primary)</span>
              </span>
            </div>
          </div>
          <div>
            <p class="text-xs text-sky-600 font-semibold uppercase tracking-wide mb-2">Portfolios</p>
            <div class="flex flex-wrap gap-2">
              <span *ngIf="!portfolios.length" class="text-sm text-gray-400">No portfolio assignments</span>
              <span *ngFor="let a of portfolios" class="px-3 py-1 bg-sky-50 text-sky-700 rounded-full text-sm">
                {{ a.unit_name }}<span *ngIf="a.primary_assignment" class="ml-1 text-xs text-green-600">(primary)</span>
              </span>
            </div>
          </div>
          <div>
            <p class="text-xs text-teal-600 font-semibold uppercase tracking-wide mb-2">Squads</p>
            <div class="flex flex-wrap gap-2">
              <span *ngIf="!squads.length" class="text-sm text-gray-400">No squad assignments</span>
              <span *ngFor="let a of squads" class="px-3 py-1 bg-teal-50 text-teal-700 rounded-full text-sm">
                {{ a.unit_name }}<span *ngIf="a.primary_assignment" class="ml-1 text-xs text-green-600">(primary)</span>
              </span>
            </div>
          </div>
        </div>
      </div>

      <p *ngIf="profileError" role="alert" class="text-sm text-red-700">{{ profileError }}</p>
      <div *ngIf="!user && !profileError" class="text-center text-gray-400 py-20">Loading profile...</div>
    </div>
  `
})
export class ProfileComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  user: User | null = null;
  assignmentsLoading = true;
  assignmentsError = '';
  profileError = '';
  tribes: UserAssignment[] = [];
  portfolios: UserAssignment[] = [];
  squads: UserAssignment[] = [];

  constructor(private authService: AuthService, private api: AdminApiService) {}

  ngOnInit(): void {
    this.authService.currentUser$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(user => {
      this.user = user;
      if (user?.id) this.loadAssignments(user.id);
    });

    if (!this.authService.currentUserValue) {
      this.authService
        .getCurrentUserProfile()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: u => {
            this.user = u;
            if (u?.id) this.loadAssignments(u.id);
          },
          error: () => this.profileError = 'Failed to load your profile.'
      });
    }
  }

  private loadAssignments(userId: number): void {
    this.assignmentsLoading = true;
    this.assignmentsError = '';
    this.api.getUserAssignments(userId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: res => {
        const data: UserAssignment[] = res.data || [];
        this.tribes = data.filter(a => a.unit_type === 'TRIBE');
        this.portfolios = data.filter(a => a.unit_type === 'PORTFOLIO');
        this.squads = data.filter(a => a.unit_type === 'SQUAD');
        this.assignmentsLoading = false;
      },
      error: () => { this.assignmentsError = 'Failed to load organization assignments.'; this.assignmentsLoading = false; }
    });
  }
}
