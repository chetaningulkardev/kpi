import { Component, HostListener, DestroyRef, Injector, inject } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, NavigationEnd } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from './services/auth.service';
import { Router } from '@angular/router';
import { UiGuardService } from './services/ui-guard.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { canManageScorecards, isAdminUser, isManagerUser } from './services/role-access.util';
import { NotificationService, AppNotification } from './services/notification.service';
import { PermissionsService } from './services/permissions.service';
import { DraftStorageService } from './services/draft-storage.service';
import { filter } from 'rxjs/operators';
import { timer } from 'rxjs';
import { ToastComponent } from './components/shared/toast.component';
import { ConfirmDialogComponent } from './components/shared/confirm-dialog.component';
import { ConfirmService } from './services/confirm.service';
import { ToastService } from './services/toast.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, CommonModule, ToastComponent, ConfirmDialogComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  readonly confirmations = inject(ConfirmService);
  readonly toasts = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private lastPendingFetchAt = 0;
  private readonly pendingFetchDebounceMs = 10000;
  private readonly unreadNotificationPollMs = 30000;
  mobileMenuOpen = false;
  mobilePerfOpen = true;
  mobileOrgOpen = false;
  mobileAdminOpen = false;
  mobileAccountOpen = false;
  orgMenuOpen = false;
  adminMenuOpen = false;
  perfMenuOpen = false;
  userMenuOpen = false;
  notifDropdownOpen = false;
  notifDropdownLoading = false;
  notifDropdownError = '';
  recentNotifications: AppNotification[] = [];
  isUiBusy = false;
  pendingReviewKpiCount = 0;
  pendingReviewCount = 0;
  unreadNotificationCount = 0;

  constructor(
    public authService: AuthService,
    private router: Router,
    private uiGuardService: UiGuardService,
    private notificationService: NotificationService,
    private permissionsService: PermissionsService,
    private draftStorage: DraftStorageService
  ) {
    this.uiGuardService.isBlocking$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(v => this.isUiBusy = v);

    this.authService.currentUser$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((user) => {
        // Capabilities are per-user, so refresh them whenever identity changes.
        if (user) {
          this.lastPendingFetchAt = 0;
          this.permissionsService.load().subscribe(() => this.refreshPendingReviewCount());
        } else {
          this.permissionsService.clear();
        }
        this.refreshPendingReviewCount();
        this.refreshUnreadNotificationCount();
      });

    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(() => {
        this.refreshPendingReviewCount();
        this.refreshUnreadNotificationCount();
        this.syncMobileSectionsForCurrentRoute();
      });

    // Keep notification badge fresh even when user stays on the same route.
    timer(0, this.unreadNotificationPollMs)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.refreshUnreadNotificationCount());

    this.refreshPendingReviewCount();
    this.refreshUnreadNotificationCount();
    this.syncMobileSectionsForCurrentRoute();
  }

  get isLoggedIn(): boolean {
    return this.authService.isAuthenticated();
  }

  get currentUser() {
    return this.authService.currentUserValue;
  }

  get displayUserName(): string {
    const user = this.currentUser;
    const firstName = String(user?.first_name ?? '').trim();
    const lastName = String(user?.last_name ?? '').trim();
    const fullName = [firstName, lastName].filter(Boolean).join(' ').trim();

    if (fullName) {
      return fullName;
    }

    const email = String(user?.email ?? '').trim();
    if (email) {
      return email;
    }

    const roleName = (user?.roles ?? [])
      .map((role: any) => String(role?.role_name ?? '').trim())
      .find(Boolean);

    return roleName || 'User';
  }

  get isAdmin(): boolean {
    // Server-resolved when available; role names are only the offline fallback.
    return this.permissionsService.permissions()?.admin ?? isAdminUser(this.currentUser?.roles);
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  get canSeeOrganizationMenu(): boolean {
    return this.isAdmin;
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  get canSeeAdministrationMenu(): boolean {
    return this.isAdmin;
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  get canSeePerformanceMenu(): boolean {
    return this.isLoggedIn;
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  get canReviewTeamKpis(): boolean {
    if (!this.isLoggedIn) {
      return false;
    }
    // The server derives this from real squad/portfolio/tribe assignments, so it
    // covers Tech Focals and Portfolio Leads without naming them anywhere.
    const perms = this.permissionsService.permissions();
    if (perms) {
      return perms.canReviewKpis;
    }
    return this.isAdmin || isManagerUser(this.currentUser?.roles);
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  get canManageTeamFlow(): boolean {
    return this.permissionsService.permissions()?.canViewTeamFlow ?? this.canReviewTeamKpis;
  }

  get canManageScorecards(): boolean {
    return canManageScorecards(this.currentUser?.roles, this.permissionsService.permissions()?.roles);
  }

  get isOrgSectionActive(): boolean {
    return this.isAnyRouteActive(['/tribes', '/portfolios', '/squads', '/teams', '/assignments', '/organization', '/org-chart']);
  }

  get isAdminSectionActive(): boolean {
    return this.isAnyRouteActive(['/users', '/roles', '/admin/config']);
  }

  get isPerformanceSectionActive(): boolean {
    return this.isAnyRouteActive([
      '/kpi-dashboard',
      '/kpi/my-kpis',
      '/kpi/my-performance',
      '/kpi/scorecards',
      '/kpi/review',
      '/kpi/team-flow',
      '/kpi/cycles',
      '/kpi/categories',
      '/kpi/frequencies',
      '/kpi/data-sources',
      '/kpi/masters',
      '/kpi/templates',
      '/kpi/generate',
      '/notifications'
    ]);
  }

  private get shouldTrackPendingReviews(): boolean {
    return this.canReviewTeamKpis;
  }

  private refreshPendingReviewCount(): void {
    if (!this.isLoggedIn || !this.shouldTrackPendingReviews) {
      this.pendingReviewKpiCount = 0;
      this.pendingReviewCount = 0;
      return;
    }

    // Avoid spamming endpoint on rapid route changes.
    const now = Date.now();
    if (now - this.lastPendingFetchAt < this.pendingFetchDebounceMs) {
      return;
    }
    this.lastPendingFetchAt = now;

    const userId = this.currentUser?.id;
    // The configuration/workflow API is only needed once a reviewer signs in.
    void import('./services/configuration.service').then(({ ConfigurationService }) => {
      if (this.destroyRef.destroyed || !this.isLoggedIn || this.currentUser?.id !== userId || !this.shouldTrackPendingReviews) return;
      this.injector.get(ConfigurationService).getPendingReviews().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: (list) => {
          if (this.currentUser?.id !== userId) return;
          const currentUserId = this.currentUser?.id;
          const scoped = (list || []).filter((kpi: any) => {
            // If reviewer is present, count only items assigned to current reviewer.
            if (kpi?.reviewer?.id != null && currentUserId != null) {
              return Number(kpi.reviewer.id) === Number(currentUserId);
            }
            // Fallback to backend-scoped list when reviewer is not part of payload.
            return true;
          });
          const uniqueIds = new Set<number>();
          const requesterKeys = new Set<string>();
          scoped.forEach((kpi: any) => {
            if (kpi?.id != null) uniqueIds.add(Number(kpi.id));
            const userId = kpi?.user?.id;
            const email = String(kpi?.user?.email ?? '').trim().toLowerCase();
            const name = `${kpi?.user?.firstName ?? kpi?.user?.first_name ?? ''} ${kpi?.user?.lastName ?? kpi?.user?.last_name ?? ''}`.trim().toLowerCase();
            if (userId != null) requesterKeys.add(`id:${Number(userId)}`);
            else if (email) requesterKeys.add(`email:${email}`);
            else if (name) requesterKeys.add(`name:${name}`);
          });
          // Keep both metrics: menu badge uses unique users, KPI count remains available for labels/tooltips.
          this.pendingReviewKpiCount = uniqueIds.size > 0 ? uniqueIds.size : scoped.length;
          this.pendingReviewCount = requesterKeys.size > 0 ? requesterKeys.size : this.pendingReviewKpiCount;
        },
        error: () => {
          if (this.currentUser?.id !== userId) return;
          this.pendingReviewKpiCount = 0;
          this.pendingReviewCount = 0;
        }
      });
    }).catch(() => {
      this.lastPendingFetchAt = 0;
    });
  }

  private refreshUnreadNotificationCount(): void {
    if (!this.isLoggedIn) {
      this.unreadNotificationCount = 0;
      return;
    }
    this.notificationService.getUnreadCount().subscribe({
      next: (count) => {
        this.unreadNotificationCount = Number(count ?? 0);
      },
      error: () => {
        this.unreadNotificationCount = 0;
      }
    });
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  logout(): void {
    // Drafts are keyed by user id, so they must be cleared while that id is
    // still resolvable — after logout there is nothing to key on, and the
    // entries would linger on what may be a shared machine.
    this.draftStorage.discardAllForCurrentUser();
    this.authService.logout().subscribe({
      next: () => this.router.navigate(['/login']),
      error: () => this.router.navigate(['/login'])
    });
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  toggleMenu(menu: 'org' | 'admin' | 'perf' | 'user', event: Event): void {
    event.stopPropagation();
    this.orgMenuOpen = menu === 'org' ? !this.orgMenuOpen : false;
    this.adminMenuOpen = menu === 'admin' ? !this.adminMenuOpen : false;
    this.perfMenuOpen = menu === 'perf' ? !this.perfMenuOpen : false;
    this.userMenuOpen = menu === 'user' ? !this.userMenuOpen : false;
    this.notifDropdownOpen = false;
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  toggleNotifDropdown(event: Event): void {
    event.stopPropagation();
    const opening = !this.notifDropdownOpen;
    this.closeMenus();
    this.notifDropdownOpen = opening;
    if (opening) {
      this.loadRecentNotifications();
    }
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  markDropdownNotifRead(n: AppNotification, event: Event): void {
    event.stopPropagation();
    if (n.read) return;
    this.notificationService.markRead(n.id).subscribe({
      next: () => {
        n.read = true;
        n.readAt = new Date().toISOString();
        if (this.unreadNotificationCount > 0) this.unreadNotificationCount--;
      },
      error: () => this.notifDropdownError = 'Failed to mark the notification as read.'
    });
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  markAllNotifRead(event: Event): void {
    event.stopPropagation();
    this.notificationService.markAllRead().subscribe({
      next: () => {
        this.recentNotifications = this.recentNotifications.map(n => ({ ...n, read: true, readAt: n.readAt ?? new Date().toISOString() }));
        this.unreadNotificationCount = 0;
      },
      error: () => this.notifDropdownError = 'Failed to mark all notifications as read.'
    });
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  navigateToNotification(n: AppNotification): void {
    this.closeMenus();
    if (!n.read) {
      this.notificationService.markRead(n.id).subscribe({
        next: () => { n.read = true; if (this.unreadNotificationCount > 0) this.unreadNotificationCount--; },
        error: () => { /* Navigation remains available if marking read fails. */ }
      });
    }
    if (n.entityType === 'KPI' && n.entityId) {
      this.router.navigate([n.type === 'KPI_SUBMITTED' && this.canReviewTeamKpis ? '/kpi/review' : '/kpi/my-kpis'], { queryParams: { openKpiId: n.entityId } });
    } else {
      this.router.navigate(['/notifications']);
    }
  }

  private loadRecentNotifications(): void {
    this.notifDropdownLoading = true;
    this.notifDropdownError = '';
    this.notificationService.getNotifications({ sortBy: 'newest', size: 6 }).subscribe({
      next: (result) => {
        this.recentNotifications = result?.items || [];
        this.notifDropdownLoading = false;
      },
      error: () => {
        this.notifDropdownError = 'Failed to load notifications. Try opening the panel again.';
        this.recentNotifications = [];
        this.notifDropdownLoading = false;
      }
    });
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  toggleMobileMenu(event: Event): void {
    event.stopPropagation();
    this.mobileMenuOpen = !this.mobileMenuOpen;
    if (this.mobileMenuOpen) {
      this.syncMobileSectionsForCurrentRoute();
    } else {
      this.mobilePerfOpen = true;
      this.mobileOrgOpen = false;
      this.mobileAdminOpen = false;
      this.mobileAccountOpen = false;
    }
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  toggleMobileSection(section: 'perf' | 'org' | 'admin' | 'account'): void {
    this.mobilePerfOpen = section === 'perf' ? !this.mobilePerfOpen : this.mobilePerfOpen;
    this.mobileOrgOpen = section === 'org' ? !this.mobileOrgOpen : this.mobileOrgOpen;
    this.mobileAdminOpen = section === 'admin' ? !this.mobileAdminOpen : this.mobileAdminOpen;
    this.mobileAccountOpen = section === 'account' ? !this.mobileAccountOpen : this.mobileAccountOpen;
  }

  closeMenus(): void {
    this.mobileMenuOpen = false;
    this.mobilePerfOpen = true;
    this.mobileOrgOpen = false;
    this.mobileAdminOpen = false;
    this.mobileAccountOpen = false;
    this.orgMenuOpen = false;
    this.adminMenuOpen = false;
    this.perfMenuOpen = false;
    this.userMenuOpen = false;
    this.notifDropdownOpen = false;
  }

  private isAnyRouteActive(prefixes: string[]): boolean {
    const url = this.router.url || '';
    return prefixes.some(prefix => url.startsWith(prefix));
  }

  private syncMobileSectionsForCurrentRoute(): void {
    if (this.isPerformanceSectionActive) {
      this.mobilePerfOpen = true;
      this.mobileOrgOpen = false;
      this.mobileAdminOpen = false;
      this.mobileAccountOpen = false;
      return;
    }
    if (this.isOrgSectionActive) {
      this.mobilePerfOpen = false;
      this.mobileOrgOpen = true;
      this.mobileAdminOpen = false;
      this.mobileAccountOpen = false;
      return;
    }
    if (this.isAdminSectionActive) {
      this.mobilePerfOpen = false;
      this.mobileOrgOpen = false;
      this.mobileAdminOpen = true;
      this.mobileAccountOpen = false;
      return;
    }
    if (this.router.url.startsWith('/profile')) {
      this.mobilePerfOpen = false;
      this.mobileOrgOpen = false;
      this.mobileAdminOpen = false;
      this.mobileAccountOpen = true;
      return;
    }
    this.mobilePerfOpen = true;
    this.mobileOrgOpen = false;
    this.mobileAdminOpen = false;
    this.mobileAccountOpen = false;
  }

  /**
   * Close menus only for clicks that land outside every menu.
   *
   * Relying on `stopPropagation()` in each toggle meant any handler that forgot
   * it — or any click on non-interactive markup inside an open panel — reached
   * this listener and collapsed the menu before the toggle's state change was
   * rendered, so the dropdown appeared not to open at all. Marking the menu
   * containers with `data-menu-root` keeps that decision here instead of
   * spreading it across every handler in the template.
   */
  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement | null;
    if (target?.closest?.('[data-menu-root]')) {
      return;
    }
    this.closeMenus();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closeMenus();
  }
}
