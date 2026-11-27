import { TestBed } from '@angular/core/testing';
import { DraftStorageService } from './draft-storage.service';
import { AuthService } from './auth.service';
import { FakeStorage, installFakeLocalStorage } from '../testing/fake-storage';

interface Draft {
  actualValue: number | null;
  comments: string;
  justification: string;
}

const DRAFT: Draft = { actualValue: 42, comments: 'shipped the migration', justification: '' };

describe('DraftStorageService', () => {
  let service: DraftStorageService;
  let currentUser: { id: number } | null;
  let storage: FakeStorage;
  let restoreStorage: () => void;

  beforeEach(() => {
    ({ storage, restore: restoreStorage } = installFakeLocalStorage());
    currentUser = { id: 7 };

    TestBed.configureTestingModule({
      providers: [
        DraftStorageService,
        { provide: AuthService, useValue: { get currentUserValue() { return currentUser; } } }
      ]
    });
    service = TestBed.inject(DraftStorageService);
  });

  afterEach(() => {
    restoreStorage();
    TestBed.resetTestingModule();
  });

  it('should round-trip a draft', () => {
    service.save('self-assessment', 1, DRAFT);

    const restored = service.restore<Draft>('self-assessment', 1);
    expect(restored?.payload).toEqual(DRAFT);
    expect(restored?.savedAt).toBeInstanceOf(Date);
  });

  it('should return null when nothing was ever saved', () => {
    expect(service.restore<Draft>('self-assessment', 999)).toBeNull();
  });

  it('should keep drafts for different KPIs apart', () => {
    service.save('self-assessment', 1, DRAFT);
    service.save('self-assessment', 2, { ...DRAFT, comments: 'a different KPI' });

    expect(service.restore<Draft>('self-assessment', 1)?.payload.comments).toBe('shipped the migration');
    expect(service.restore<Draft>('self-assessment', 2)?.payload.comments).toBe('a different KPI');
  });

  // A shared or kiosk machine must not show one person's half-written
  // self-assessment to whoever signs in next.
  it('should not expose one user\'s draft to another', () => {
    service.save('self-assessment', 1, DRAFT);

    currentUser = { id: 8 };
    expect(service.restore<Draft>('self-assessment', 1)).toBeNull();

    currentUser = { id: 7 };
    expect(service.restore<Draft>('self-assessment', 1)).not.toBeNull();
  });

  it('should suppress a draft identical to the server record', () => {
    service.save('self-assessment', 1, DRAFT);

    // Nothing diverges, so offering to "restore" would imply unsaved work
    // that does not exist.
    expect(service.restore<Draft>('self-assessment', 1, { ...DRAFT })).toBeNull();
  });

  it('should offer a draft that differs from the server record', () => {
    service.save('self-assessment', 1, DRAFT);

    const server: Draft = { actualValue: null, comments: '', justification: '' };
    expect(service.restore<Draft>('self-assessment', 1, server)?.payload).toEqual(DRAFT);
  });

  it('should discard a draft on request', () => {
    service.save('self-assessment', 1, DRAFT);
    service.discard('self-assessment', 1);

    expect(service.restore<Draft>('self-assessment', 1)).toBeNull();
  });

  it('should ignore and remove a draft past its TTL', () => {
    const key = 'kpi.draft.7.self-assessment.1';
    const thirtyOneDaysAgo = Date.now() - 31 * 24 * 60 * 60 * 1000;
    storage.setItem(key, JSON.stringify({ savedAt: thirtyOneDaysAgo, payload: DRAFT }));

    expect(service.restore<Draft>('self-assessment', 1)).toBeNull();
    // Stale text from a previous cycle must not merely be hidden, but dropped.
    expect(storage.getItem(key)).toBeNull();
  });

  it('should drop an unparseable entry rather than throw', () => {
    const key = 'kpi.draft.7.self-assessment.1';
    storage.setItem(key, 'not json at all');

    expect(() => service.restore<Draft>('self-assessment', 1)).not.toThrow();
    expect(storage.getItem(key)).toBeNull();
  });

  it('should clear every draft belonging to the current user on logout', () => {
    service.save('self-assessment', 1, DRAFT);
    service.save('self-assessment', 2, DRAFT);
    currentUser = { id: 8 };
    service.save('self-assessment', 3, DRAFT);

    currentUser = { id: 7 };
    service.discardAllForCurrentUser();

    expect(service.restore<Draft>('self-assessment', 1)).toBeNull();
    expect(service.restore<Draft>('self-assessment', 2)).toBeNull();

    // Another user's drafts are not ours to delete.
    currentUser = { id: 8 };
    expect(service.restore<Draft>('self-assessment', 3)).not.toBeNull();
  });

  it('sweepExpired should remove only expired entries', () => {
    service.save('self-assessment', 1, DRAFT);
    storage.setItem(
      'kpi.draft.7.self-assessment.2',
      JSON.stringify({ savedAt: Date.now() - 31 * 24 * 60 * 60 * 1000, payload: DRAFT })
    );
    storage.setItem('unrelated.key', 'keep me');

    service.sweepExpired();

    expect(service.restore<Draft>('self-assessment', 1)).not.toBeNull();
    expect(storage.getItem('kpi.draft.7.self-assessment.2')).toBeNull();
    // Sweeping our own namespace must not touch anything else on the origin.
    expect(storage.getItem('unrelated.key')).toBe('keep me');
  });

  it('should not throw when storage is unavailable', () => {
    // Private browsing and an exhausted quota both throw here. Losing the
    // safety net is acceptable; breaking the form being typed into is not.
    storage.setItem = () => { throw new Error('QuotaExceededError'); };

    expect(() => service.save('self-assessment', 1, DRAFT)).not.toThrow();
  });

  it('should fall back to an anonymous namespace when no user is resolved', () => {
    currentUser = null;

    expect(() => service.save('self-assessment', 1, DRAFT)).not.toThrow();
    expect(service.restore<Draft>('self-assessment', 1)?.payload).toEqual(DRAFT);
  });
});

