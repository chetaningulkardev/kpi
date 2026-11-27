import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { NotificationService, AppNotification, NotificationPage } from '../../services/notification.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
  <section class="max-w-5xl mx-auto px-4 py-8 space-y-4">
    <div class="flex items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-slate-900">Notifications</h1>
        <p class="text-sm text-slate-500">Review KPI workflow updates and mark them as read.</p>
      </div>
      <button (click)="markAllRead()" [disabled]="markingAll || unreadCount === 0"
        class="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50">
        {{ markingAll ? 'Updating...' : 'Mark all as read' }}
      </button>
    </div>

    <div class="rounded-xl border bg-white px-4 py-3 text-sm text-slate-600 flex items-center justify-between">
      <span>Total {{ totalItems }} notifications</span>
      <span class="font-semibold text-dt-700">Unread {{ unreadCount }}</span>
    </div>

    <div class="rounded-xl border bg-white p-4 grid gap-3 md:grid-cols-6">
      <div>
        <label class="block text-xs font-semibold text-slate-600 mb-1">Read State</label>
        <select [(ngModel)]="readFilter" (ngModelChange)="onFilterChange()"
          class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
          <option value="all">All</option>
          <option value="unread">Unread</option>
          <option value="read">Read</option>
        </select>
      </div>
      <div>
        <label class="block text-xs font-semibold text-slate-600 mb-1">Type</label>
        <select [(ngModel)]="typeFilter" (ngModelChange)="onFilterChange()"
          class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
          <option value="">All Types</option>
          <option *ngFor="let t of notificationTypes" [value]="t">{{ t }}</option>
        </select>
      </div>
      <div>
        <label class="block text-xs font-semibold text-slate-600 mb-1">Sort</label>
        <select [(ngModel)]="sortBy" (ngModelChange)="onFilterChange()"
          class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
          <option value="newest">Newest first</option>
          <option value="oldest">Oldest first</option>
          <option value="type">Type (A-Z)</option>
        </select>
      </div>
      <div>
        <label class="block text-xs font-semibold text-slate-600 mb-1">From</label>
        <input type="date" [(ngModel)]="fromDate" (ngModelChange)="onFilterChange()"
          class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
      </div>
      <div>
        <label class="block text-xs font-semibold text-slate-600 mb-1">To</label>
        <input type="date" [(ngModel)]="toDate" (ngModelChange)="onFilterChange()"
          class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
      </div>
      <div class="flex items-end">
        <button (click)="clearFilters()" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50">
          Clear filters
        </button>
      </div>
    </div>

    <div *ngIf="loading" class="rounded-xl border bg-white p-8 text-center text-slate-400">Loading notifications...</div>

    <p *ngIf="error" role="alert" class="rounded-lg bg-red-50 p-4 text-red-700">{{ error }}
      <button (click)="load()" class="ml-2 underline">Retry</button>
    </p>
    <div *ngIf="!loading && !error && notifications.length === 0" class="rounded-xl border bg-white p-10 text-center text-slate-400">
      No notifications match your filters.
    </div>

    <div *ngIf="!loading && notifications.length > 0" class="space-y-3">
      <article *ngFor="let n of notifications; trackBy: trackByNotificationId"
        class="rounded-xl border bg-white p-4"
        [class.border-dt-300]="!n.read"
        [class.bg-dt-50]="!n.read">
        <div class="flex items-start justify-between gap-3">
          <div class="space-y-1">
            <p class="text-sm font-semibold" [class.text-slate-900]="!n.read" [class.text-slate-700]="n.read">{{ n.title }}</p>
            <p class="text-sm text-slate-600">{{ n.message }}</p>
            <p class="text-xs text-slate-400">{{ n.createdAt | date: 'd MMM y, h:mm a' }}</p>
          </div>
          <div class="flex items-center gap-2 shrink-0">
            <button *ngIf="n.entityType === 'KPI' && n.entityId" (click)="openReviewFromNotification(n)"
              class="text-xs font-semibold text-dt-700 hover:text-dt-900 underline">Open</button>
            <button *ngIf="!n.read" (click)="markRead(n)" class="text-xs font-semibold text-slate-600 hover:text-slate-900 underline">
              Mark read
            </button>
          </div>
        </div>
      </article>
    </div>

    <div *ngIf="!loading && totalPages > 1" class="rounded-xl border bg-white px-4 py-3 flex items-center justify-between text-sm">
      <button (click)="goPrevPage()" [disabled]="page <= 0"
        class="rounded-lg border border-slate-300 px-3 py-1.5 font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50">
        Previous
      </button>
      <span class="text-slate-600">Page {{ page + 1 }} of {{ totalPages }}</span>
      <button (click)="goNextPage()" [disabled]="page >= totalPages - 1"
        class="rounded-lg border border-slate-300 px-3 py-1.5 font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50">
        Next
      </button>
    </div>
  </section>
  `
})
export class NotificationsComponent implements OnInit, OnDestroy {
  notifications: AppNotification[] = [];
  loading = false;
  markingAll = false;
  notificationTypes: string[] = [];
  page = 0;
  size = 20;
  totalItems = 0;
  totalPages = 0;
  readFilter: 'all' | 'read' | 'unread' = 'all';
  typeFilter = '';
  sortBy: 'newest' | 'oldest' | 'type' = 'newest';
  fromDate = '';
  toDate = '';
  error = '';
  unreadCount = 0;
  private listRequest?: Subscription;

  constructor(private notificationService: NotificationService, private router: Router) {}

  ngOnInit(): void {
    this.loadNotificationTypes();
    this.load();
  }

  ngOnDestroy(): void {
    this.listRequest?.unsubscribe();
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  load(): void {
    this.loadWithQuery();
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  onFilterChange(): void {
    this.page = 0;
    this.loadWithQuery();
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  clearFilters(): void {
    this.readFilter = 'all';
    this.typeFilter = '';
    this.sortBy = 'newest';
    this.fromDate = '';
    this.toDate = '';
    this.page = 0;
    this.loadWithQuery();
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  goPrevPage(): void {
    if (this.page <= 0) return;
    this.page -= 1;
    this.loadWithQuery();
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  goNextPage(): void {
    if (this.page >= this.totalPages - 1) return;
    this.page += 1;
    this.loadWithQuery();
  }

  private loadWithQuery(): void {
    this.listRequest?.unsubscribe();
    this.error = '';
    if (this.fromDate && this.toDate && this.fromDate > this.toDate) {
      this.error = 'From date must be on or before To date.';
      this.loading = false;
      return;
    }
    this.loading = true;
    this.notificationService.getUnreadCount().subscribe({
      next: count => this.unreadCount = count,
      error: () => this.error = 'Failed to load the unread notification count.'
    });
    this.listRequest = this.notificationService.getNotifications({
      read: this.readFilter,
      type: this.typeFilter,
      sortBy: this.sortBy,
      fromDate: this.fromDate || undefined,
      toDate: this.toDate || undefined,
      page: this.page,
      size: this.size
    }).subscribe({
      next: (result: NotificationPage) => {
        this.notifications = result?.items || [];
        this.page = Number(result?.page ?? 0);
        this.size = Number(result?.size ?? 20);
        this.totalItems = Number(result?.totalItems ?? 0);
        this.totalPages = Number(result?.totalPages ?? 0);
        this.loading = false;
      },
      error: () => {
        this.error = 'Failed to load notifications.';
        this.notifications = [];
        this.totalItems = 0;
        this.totalPages = 0;
        this.loading = false;
      }
    });
  }

  private loadNotificationTypes(): void {
    this.notificationService.getNotificationTypes().subscribe({
      next: (types) => {
        this.notificationTypes = (types || []).filter(t => !!String(t || '').trim());
      },
      error: () => {
        this.notificationTypes = [];
      }
    });
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  // noinspection JSUnusedGlobalSymbols - used in template
  markRead(n: AppNotification): void {
    this.notificationService.markRead(n.id).subscribe({
      next: () => {
        n.read = true;
        n.readAt = new Date().toISOString();
        this.load();
      },
      error: () => this.error = 'Failed to mark the notification as read.'
    });
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  // noinspection JSUnusedGlobalSymbols - used in template
  openReviewFromNotification(n: AppNotification): void {
    const navigateToReview = () => {
      const route = n.type === 'KPI_SUBMITTED' ? '/kpi/review' : '/kpi/my-kpis';
      this.router.navigate([route], {
        queryParams: { openKpiId: n.entityId }
      });
    };

    if (n.read) {
      navigateToReview();
      return;
    }

    this.notificationService.markRead(n.id).subscribe({
      next: () => {
        n.read = true;
        n.readAt = new Date().toISOString();
        navigateToReview();
      },
      error: () => {
        navigateToReview();
      }
    });
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  // noinspection JSUnusedGlobalSymbols - used in template
  markAllRead(): void {
    if (this.unreadCount === 0 || this.markingAll) {
      return;
    }
    this.markingAll = true;
    this.notificationService.markAllRead().subscribe({
      next: () => {
        this.notifications = this.notifications.map(n => ({ ...n, read: true, readAt: n.readAt ?? new Date().toISOString() }));
        this.unreadCount = 0;
        this.load();
      },
      error: () => this.error = 'Failed to mark all notifications as read.'
    }).add(() => {
      this.markingAll = false;
    });
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  trackByNotificationId = (_: number, n: AppNotification): number => n.id;
}
