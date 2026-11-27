import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';

export interface AppNotification {
  id: number;
  title: string;
  message: string;
  type: string;
  entityType?: string;
  entityId?: number;
  read: boolean;
  readAt?: string;
  createdAt: string;
}

export interface NotificationQuery {
  read?: 'all' | 'read' | 'unread';
  type?: string;
  sortBy?: 'newest' | 'oldest' | 'type';
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export interface NotificationPage {
  items: AppNotification[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  constructor(private http: HttpClient) {}

  // noinspection JSUnusedGlobalSymbols - used across app shell and notifications page
  getNotifications(query: NotificationQuery = {}): Observable<NotificationPage> {
    let params = new HttpParams()
      .set('page', String(query.page ?? 0))
      .set('size', String(query.size ?? 20))
      .set('sortBy', String(query.sortBy ?? 'newest'));

    if (query.read && query.read !== 'all') {
      params = params.set('read', String(query.read === 'read'));
    }
    if (query.type && query.type.trim()) {
      params = params.set('type', query.type.trim());
    }
    if (query.fromDate) {
      params = params.set('fromDate', query.fromDate);
    }
    if (query.toDate) {
      params = params.set('toDate', query.toDate);
    }

    return this.http.get<NotificationPage>('/api/notifications', { params });
  }

  // noinspection JSUnusedGlobalSymbols - used by notifications filter dropdown
  getNotificationTypes(): Observable<string[]> {
    return this.http.get<string[]>('/api/notifications/types');
  }

  // noinspection JSUnusedGlobalSymbols - used in app shell badge
  getUnreadCount(): Observable<number> {
    return this.http.get<{ unreadCount: number }>('/api/notifications/unread-count').pipe(
      map((resp) => Number(resp?.unreadCount ?? 0))
    );
  }

  // noinspection JSUnusedGlobalSymbols - used in notifications page
  markRead(id: number): Observable<void> {
    return this.http.post<void>(`/api/notifications/${id}/read`, {});
  }

  // noinspection JSUnusedGlobalSymbols - used in notifications page
  markAllRead(): Observable<{ updated: number }> {
    return this.http.post<{ updated: number }>('/api/notifications/read-all', {});
  }
}

