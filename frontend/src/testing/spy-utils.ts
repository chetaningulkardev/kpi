import { vi } from 'vitest';

/**
 * Vitest replacement for Jasmine's `jasmine.createSpyObj`.
 *
 * Creates an object whose listed methods are all `vi.fn()` spies, so specs can
 * stub return values (`spy.method.mockReturnValue(...)`) and assert calls
 * (`expect(spy.method).toHaveBeenCalledWith(...)`).
 *
 * @example
 * const configSpy = createSpyObj<ConfigurationService>(['getReviewCycles', 'getMyKpis']);
 * configSpy.getReviewCycles.mockReturnValue(of([cycle]));
 */
export function createSpyObj<T>(methodNames: readonly (keyof T | string)[]): any {
  const spyObj: Record<string, unknown> = {};
  for (const name of methodNames) {
    spyObj[String(name)] = vi.fn();
  }
  return spyObj;
}

