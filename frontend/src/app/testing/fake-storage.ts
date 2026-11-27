/**
 * A complete in-memory `Storage` for specs.
 *
 * The unit-test environment provides only a partial `localStorage` — it has
 * `getItem`/`setItem` but no `clear`, `length` or `key(i)`. `DraftStorageService`
 * iterates the store to sweep expired entries, so testing it against the
 * default shim would exercise something no browser actually presents.
 */
export class FakeStorage implements Storage {
  private store = new Map<string, string>();

  get length(): number {
    return this.store.size;
  }

  key(index: number): string | null {
    return Array.from(this.store.keys())[index] ?? null;
  }

  getItem(key: string): string | null {
    return this.store.has(key) ? this.store.get(key)! : null;
  }

  setItem(key: string, value: string): void {
    this.store.set(key, String(value));
  }

  removeItem(key: string): void {
    this.store.delete(key);
  }

  clear(): void {
    this.store.clear();
  }

  [name: string]: any;
}

/**
 * Swaps `globalThis.localStorage` for a fresh fake and returns a function that
 * puts the original back.
 */
export function installFakeLocalStorage(): { storage: FakeStorage; restore: () => void } {
  const original = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
  const storage = new FakeStorage();

  Object.defineProperty(globalThis, 'localStorage', {
    value: storage,
    configurable: true,
    writable: true
  });

  return {
    storage,
    restore: () => {
      if (original) {
        Object.defineProperty(globalThis, 'localStorage', original);
      } else {
        delete (globalThis as any).localStorage;
      }
    }
  };
}

