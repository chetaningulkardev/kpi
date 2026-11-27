import { Injectable, inject } from '@angular/core';
import { AuthService } from './auth.service';

/**
 * A draft as stored, with the metadata needed to decide whether it is still
 * worth offering back to the user.
 */
interface StoredDraft<T> {
  savedAt: number;
  payload: T;
}

/** What a caller gets back when a draft is found. */
export interface RestoredDraft<T> {
  payload: T;
  savedAt: Date;
}

/**
 * Local persistence for in-progress form input.
 *
 * <p>Self-assessments are the longest free text anyone types into this
 * application, and nothing held them: navigating away, closing the tab, or
 * simply letting the 60-minute token lapse discarded the lot with no warning.
 * A performance review is exactly the wrong place to lose someone's writing.</p>
 *
 * <p>Drafts are kept in {@code localStorage} rather than on the server on
 * purpose. A draft is not a submission — persisting half-finished
 * self-assessments server-side would make them visible to reviewers and to the
 * audit trail, which changes what the record means. This is a local safety net,
 * and submitting remains the only act that publishes anything.</p>
 */
@Injectable({ providedIn: 'root' })
export class DraftStorageService {

  private readonly auth = inject(AuthService);

  private readonly PREFIX = 'kpi.draft';

  /**
   * Drafts older than this are ignored and swept. A month is long enough to
   * cover a review cycle that someone comes back to, short enough that stale
   * text from a previous cycle is never resurrected.
   */
  private readonly MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000;

  /**
   * Namespaced by user so a shared or kiosk machine cannot show one person's
   * half-written review to the next person who signs in.
   */
  private key(kind: string, entityId: number | string): string {
    const userId = this.auth.currentUserValue?.id ?? 'anonymous';
    return `${this.PREFIX}.${userId}.${kind}.${entityId}`;
  }

  save<T>(kind: string, entityId: number | string, payload: T): void {
    const record: StoredDraft<T> = { savedAt: Date.now(), payload };
    try {
      localStorage.setItem(this.key(kind, entityId), JSON.stringify(record));
    } catch {
      // Private browsing and a full quota both throw here. Losing the safety
      // net is bad; breaking the form the user is typing into is worse, so a
      // failure to persist is deliberately silent.
      this.sweepExpired();
    }
  }

  /**
   * Returns a draft only when it is recent and actually differs from what the
   * server already holds.
   *
   * @param serverState the values loaded from the API; a draft identical to
   *                    these is not worth telling the user about
   */
  restore<T>(kind: string, entityId: number | string, serverState?: T): RestoredDraft<T> | null {
    let raw: string | null;
    try {
      raw = localStorage.getItem(this.key(kind, entityId));
    } catch {
      return null;
    }
    if (!raw) {
      return null;
    }

    let record: StoredDraft<T>;
    try {
      record = JSON.parse(raw) as StoredDraft<T>;
    } catch {
      // Corrupt or from an older shape — drop it rather than fail the page.
      this.discard(kind, entityId);
      return null;
    }

    if (!record?.savedAt || Date.now() - record.savedAt > this.MAX_AGE_MS) {
      this.discard(kind, entityId);
      return null;
    }

    // Offering a draft that matches the saved record would be noise, and worse,
    // would imply there is unsaved work when there is none.
    if (serverState !== undefined && JSON.stringify(record.payload) === JSON.stringify(serverState)) {
      this.discard(kind, entityId);
      return null;
    }

    return { payload: record.payload, savedAt: new Date(record.savedAt) };
  }

  discard(kind: string, entityId: number | string): void {
    try {
      localStorage.removeItem(this.key(kind, entityId));
    } catch {
      // Nothing useful to do; the TTL sweep will catch it.
    }
  }

  /**
   * Removes every draft belonging to the signed-in user. Called on logout so a
   * shared machine is left clean.
   */
  discardAllForCurrentUser(): void {
    const userId = this.auth.currentUserValue?.id ?? 'anonymous';
    this.removeMatching(key => key.startsWith(`${this.PREFIX}.${userId}.`));
  }

  /**
   * Drops drafts past their TTL. Without this, every abandoned draft would sit
   * in localStorage indefinitely and eventually exhaust the origin's quota.
   */
  sweepExpired(): void {
    const cutoff = Date.now() - this.MAX_AGE_MS;
    this.removeMatching(key => {
      if (!key.startsWith(this.PREFIX)) {
        return false;
      }
      try {
        const record = JSON.parse(localStorage.getItem(key) ?? '{}') as StoredDraft<unknown>;
        return !record?.savedAt || record.savedAt < cutoff;
      } catch {
        // Unparseable entries are ours and useless — clear them out.
        return true;
      }
    });
  }

  private removeMatching(predicate: (key: string) => boolean): void {
    try {
      // Collected first because removing during iteration reindexes the store
      // and would skip entries.
      const doomed: string[] = [];
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        if (key && predicate(key)) {
          doomed.push(key);
        }
      }
      doomed.forEach(key => localStorage.removeItem(key));
    } catch {
      // Storage unavailable; nothing to clean.
    }
  }
}

