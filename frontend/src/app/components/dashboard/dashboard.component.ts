import { Component, OnInit, DestroyRef, inject, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { User } from '../../models/auth.model';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { isAdminUser, isManagerUser } from '../../services/role-access.util';
import { ConfigurationService, ReviewCycle } from '../../services/configuration.service';
import { DraftStorageService } from '../../services/draft-storage.service';
import { PermissionsService } from '../../services/permissions.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly draftStorage = inject(DraftStorageService);
  private readonly permissions = inject(PermissionsService);
  currentUser: User | null = null;
  loading = true;
  readonly today = new Date();
  pendingReviewUsers = 0;
  pendingReviewKpis = 0;
  pendingReviewLoadFailed = false;
  activeCycle: ReviewCycle | null = null;
  myKpiCount: number | null = null;
  dashboardDataError = '';

  constructor(
    private authService: AuthService,
    private configService: ConfigurationService,
    private router: Router
  ) {
    effect(() => {
      if (this.permissions.permissions()) this.loadPendingReviewStats();
    });
  }

  ngOnInit(): void {
    this.authService.currentUser$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(user => {
        this.currentUser = user;
        this.loading = false;
        if (!user) this.router.navigate(['/login']);
        this.loadDashboardData();
        this.loadPendingReviewStats();
      });
  }

  private loadDashboardData(): void {
    this.activeCycle = null;
    this.myKpiCount = null;
    this.dashboardDataError = '';
    if (!this.currentUser) return;

    this.configService.getReviewCycles().subscribe({
      next: (cycles) => {
        this.activeCycle = this.selectActiveCycle(cycles);
        if (this.activeCycle) {
          this.configService.getMyKpis(this.activeCycle.code).subscribe({
            next: (kpis) => this.myKpiCount = kpis.length,
            error: () => {
              this.myKpiCount = null;
              this.dashboardDataError = 'Your KPI summary could not be loaded.';
            }
          });
        }
      },
      error: () => {
        this.dashboardDataError = 'Review-cycle information could not be loaded.';
      }
    });
  }

  private selectActiveCycle(cycles: ReviewCycle[]): ReviewCycle | null {
    const statusPriority: Record<string, number> = {
      IN_PROGRESS: 0,
      OPEN: 1,
      UNDER_REVIEW: 2,
      CALIBRATION: 3
    };

    return cycles
      .filter(cycle => statusPriority[cycle.statusCode.toUpperCase()] !== undefined)
      .sort((left, right) => {
        const priorityDifference =
          statusPriority[left.statusCode.toUpperCase()] - statusPriority[right.statusCode.toUpperCase()];
        if (priorityDifference !== 0) return priorityDifference;
        const leftStart = Date.parse(left.startDate);
        const rightStart = Date.parse(right.startDate);
        return (Number.isFinite(rightStart) ? rightStart : 0)
          - (Number.isFinite(leftStart) ? leftStart : 0);
      })[0] ?? null;
  }

  private loadPendingReviewStats(): void {
    this.pendingReviewLoadFailed = false;
    if (!this.currentUser || !this.isManager()) {
      this.pendingReviewUsers = 0;
      this.pendingReviewKpis = 0;
      return;
    }
    this.configService.getPendingReviews().subscribe({
      next: (items) => {
        const list = items || [];
        this.pendingReviewKpis = list.length;
        const userKeys = new Set<string>();
        list.forEach((kpi) => {
          const userId = kpi.user?.id;
          const email = (kpi.user?.email ?? '').trim().toLowerCase();
          const name = `${kpi.user?.firstName ?? kpi.user?.first_name ?? ''} ${kpi.user?.lastName ?? kpi.user?.last_name ?? ''}`.trim().toLowerCase();
          if (userId != null) userKeys.add(`id:${userId}`);
          else if (email) userKeys.add(`email:${email}`);
          else if (name) userKeys.add(`name:${name}`);
        });
        this.pendingReviewUsers = userKeys.size;
      },
      error: () => {
        this.pendingReviewLoadFailed = true;
        this.pendingReviewUsers = 0;
        this.pendingReviewKpis = 0;
      }
    });
  }

   // noinspection JSUnusedGlobalSymbols - used in template
   getUserInitials(): string {
     const f = this.currentUser?.first_name?.[0] ?? '';
     const l = this.currentUser?.last_name?.[0] ?? '';
     return (f + l).toUpperCase() || '?';
   }

   // noinspection JSUnusedGlobalSymbols - used in template
   getRoleBadgeClass(): string {
     const role = (this.currentUser?.roles?.[0]?.role_name ?? '').toUpperCase();
     if (role.includes('ADMIN'))                             return 'bg-white text-dt-700 shadow-sm';
     if (role.includes('MANAGER') || role.includes('LEAD')) return 'bg-amber-400 text-amber-900 shadow-sm';
     return 'bg-white/25 text-white ring-1 ring-white/50';
   }

   // noinspection JSUnusedGlobalSymbols - used in template
   isAdmin(): boolean  { return this.permissions.permissions()?.admin ?? isAdminUser(this.currentUser?.roles); }
   // noinspection JSUnusedGlobalSymbols - used in template
   isManager(): boolean { return this.permissions.permissions()?.canReviewKpis ?? isManagerUser(this.currentUser?.roles); }

   // ── Navigation ──
   // noinspection JSUnusedGlobalSymbols - used in template
   logout(): void {
     // Cleared before the user id is gone; see AppComponent.logout.
     this.draftStorage.discardAllForCurrentUser();
     this.authService.logout().subscribe({
       next:  () => this.router.navigate(['/login']),
       error: () => this.router.navigate(['/login'])
     });
   }

   // noinspection JSUnusedGlobalSymbols - used in template
   goToProfile():        void { this.router.navigate(['/profile']); }
   // noinspection JSUnusedGlobalSymbols - used in template
   goToUserManagement(): void { this.router.navigate(['/users']); }
   // noinspection JSUnusedGlobalSymbols - used in template
   goToRoleManagement(): void { this.router.navigate(['/roles']); }
   // noinspection JSUnusedGlobalSymbols - used in template
   goToMyKpis():         void { this.router.navigate(['/kpi/my-kpis']); }
   // noinspection JSUnusedGlobalSymbols - used in template
   goToKpiDashboard():   void { this.router.navigate(['/kpi-dashboard']); }
   // noinspection JSUnusedGlobalSymbols - used in template
   goToManagerReview():  void { this.router.navigate(['/kpi/review']); }
   // noinspection JSUnusedGlobalSymbols - used in template
   goToTeamFlow():       void { this.router.navigate(['/kpi/team-flow']); }
   // noinspection JSUnusedGlobalSymbols - used in template
   goToNotifications():  void { this.router.navigate(['/notifications']); }
}
