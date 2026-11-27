import { vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { KpiCacheService } from './kpi-cache.service';
import { ConfigurationService, KpiInstance, ReviewCycle } from './configuration.service';
import { createSpyObj } from '../../testing/spy-utils';

const cycle: ReviewCycle = {
  id: 1, code: 'Q4-2026', name: 'Q4 Review', startDate: '2026-10-01',
  endDate: '2026-12-31', statusCode: 'OPEN'
};
const kpi: KpiInstance = {
  id: 1, kpiName: 'Code Quality', kpiCode: 'CQ001', categoryName: 'Engineering',
  weight: 30, target: '100', frequency: 'QUARTERLY', status: 'PENDING',
  actualValue: null, comments: null, justification: null, rating: null, reviewerComments: null
};

function setup() {
  const configSpy = createSpyObj<ConfigurationService>([
    'getReviewCycles', 'getMyKpis'
  ]);
  configSpy.getReviewCycles.mockReturnValue(of([cycle]));
  configSpy.getMyKpis.mockReturnValue(of([kpi]));

  TestBed.configureTestingModule({
    providers: [
      KpiCacheService,
      { provide: ConfigurationService, useValue: configSpy }
    ]
  });

  return {
    cache: TestBed.inject(KpiCacheService),
    configSpy
  };
}

describe('KpiCacheService', () => {

  // ── Caching behavior ───────────────────────────────────────────────────────
  describe('Caching', () => {
    it('should fetch cycles from API on first call', () => {
      const { cache, configSpy } = setup();
      cache.getReviewCycles().subscribe();
      expect(configSpy.getReviewCycles).toHaveBeenCalledTimes(1);
    });

    it('should return cached cycles on second call', () => {
      const { cache, configSpy } = setup();
      cache.getReviewCycles().subscribe();
      cache.getReviewCycles().subscribe();
      expect(configSpy.getReviewCycles).toHaveBeenCalledTimes(1); // still just once
    });

    it('should bypass cache when force=true', () => {
      const { cache, configSpy } = setup();
      cache.getReviewCycles().subscribe();
      cache.clearAll();
      cache.getReviewCycles(true).subscribe();
      expect(configSpy.getReviewCycles).toHaveBeenCalledTimes(2);
    });

    it('should cache KPIs per cycle code', () => {
      const { cache, configSpy } = setup();
      cache.getMyKpis('Q4-2026').subscribe();
      cache.getMyKpis('Q4-2026').subscribe();
      expect(configSpy.getMyKpis).toHaveBeenCalledTimes(1);
    });

    it('should fetch separately for different cycles', () => {
      const { cache, configSpy } = setup();
      cache.getMyKpis('Q3-2026').subscribe();
      cache.getMyKpis('Q4-2026').subscribe();
      expect(configSpy.getMyKpis).toHaveBeenCalledTimes(2);
    });
  });

  // ── Error handling ─────────────────────────────────────────────────────────
  // The cache is resilient (serves stale data on failure) but must NOT hide a
  // failure when it has nothing to fall back on, otherwise the UI silently
  // renders an empty list instead of an error state.
  describe('Error Handling', () => {
    it('should propagate getMyKpis error when nothing is cached', () => {
      const { cache, configSpy } = setup();
      configSpy.getMyKpis.mockReturnValue(throwError(() => new Error('Server error')));

      const onNext = vi.fn();
      const onError = vi.fn();
      cache.getMyKpis('Q4-2026').subscribe({ next: onNext, error: onError });

      expect(onError).toHaveBeenCalledTimes(1);
      expect(onNext).not.toHaveBeenCalled();
    });

    it('should serve stale KPIs when a refresh fails', () => {
      const { cache, configSpy } = setup();
      cache.getMyKpis('Q4-2026').subscribe(); // warm the cache

      configSpy.getMyKpis.mockReturnValue(throwError(() => new Error('Server error')));

      const onNext = vi.fn();
      const onError = vi.fn();
      cache.getMyKpis('Q4-2026', true).subscribe({ next: onNext, error: onError });

      expect(onError).not.toHaveBeenCalled();
      expect(onNext).toHaveBeenCalledWith([kpi]);
    });

    it('should propagate getReviewCycles error when nothing is cached', () => {
      const { cache, configSpy } = setup();
      configSpy.getReviewCycles.mockReturnValue(throwError(() => new Error('Server error')));

      const onNext = vi.fn();
      const onError = vi.fn();
      cache.getReviewCycles().subscribe({ next: onNext, error: onError });

      expect(onError).toHaveBeenCalledTimes(1);
      expect(onNext).not.toHaveBeenCalled();
    });

    it('should serve stale cycles when a refresh fails', () => {
      const { cache, configSpy } = setup();
      cache.getReviewCycles().subscribe(); // warm the cache

      configSpy.getReviewCycles.mockReturnValue(throwError(() => new Error('Server error')));

      const onNext = vi.fn();
      const onError = vi.fn();
      cache.getReviewCycles(true).subscribe({ next: onNext, error: onError });

      expect(onError).not.toHaveBeenCalled();
      expect(onNext).toHaveBeenCalledWith([cycle]);
    });
  });

  // ── Cache invalidation ─────────────────────────────────────────────────────
  describe('Cache Invalidation', () => {
    it('invalidateKpiCache() should remove specific cycle cache', () => {
      const { cache, configSpy } = setup();
      cache.getMyKpis('Q4-2026').subscribe();
      cache.invalidateKpiCache('Q4-2026');
      cache.getMyKpis('Q4-2026').subscribe();
      expect(configSpy.getMyKpis).toHaveBeenCalledTimes(2);
    });

    it('clearAll() should clear all caches', () => {
      const { cache, configSpy } = setup();
      cache.getReviewCycles().subscribe();
      cache.getMyKpis('Q4-2026').subscribe();
      cache.clearAll();
      cache.getReviewCycles().subscribe();
      cache.getMyKpis('Q4-2026').subscribe();
      expect(configSpy.getReviewCycles).toHaveBeenCalledTimes(2);
      expect(configSpy.getMyKpis).toHaveBeenCalledTimes(2);
    });
  });

  // ── Optimistic updates ─────────────────────────────────────────────────────
  describe('Optimistic Updates', () => {
    it('should update KPI in cache optimistically', () => {
      const { cache } = setup();
      cache.getMyKpis('Q4-2026').subscribe();

      const updated = { ...kpi, status: 'SUBMITTED', actualValue: 85 };
      cache.updateKpiInCache('Q4-2026', updated);

      let result: KpiInstance[] = [];
      cache.getMyKpis('Q4-2026').subscribe(d => result = d);

      expect(result[0].status).toBe('SUBMITTED');
      expect(result[0].actualValue).toBe(85);
    });

    it('should do nothing for unknown cycle in optimistic update', () => {
      const { cache } = setup();
      expect(() => cache.updateKpiInCache('UNKNOWN', { ...kpi })).not.toThrow();
    });
  });

  // ── Computations ───────────────────────────────────────────────────────────
  describe('computeTotalWeight()', () => {
    it('should sum all KPI weights', () => {
      const { cache } = setup();
      const kpis = [
        { ...kpi, weight: 30 },
        { ...kpi, id: 2, weight: 40 },
        { ...kpi, id: 3, weight: 30 }
      ];
      expect(cache.computeTotalWeight(kpis)).toBe(100);
    });

    it('should return 0 for empty array', () => {
      const { cache } = setup();
      expect(cache.computeTotalWeight([])).toBe(0);
    });

    it('should handle undefined weights', () => {
      const { cache } = setup();
      const kpis = [{ ...kpi, weight: undefined as any }, { ...kpi, id: 2, weight: 50 }];
      expect(cache.computeTotalWeight(kpis)).toBe(50);
    });
  });

  describe('computeWeightedScoreTotal()', () => {
    it('should calculate correctly', () => {
      const { cache } = setup();
      const kpis = [
        { ...kpi, rating: 4, weight: 50 },
        { ...kpi, id: 2, rating: 5, weight: 50 }
      ];
      expect(cache.computeWeightedScoreTotal(kpis)).toBeCloseTo(4.5);
    });

    it('should skip KPIs without rating', () => {
      const { cache } = setup();
      const kpis = [
        { ...kpi, rating: null, weight: 50 },
        { ...kpi, id: 2, rating: 4, weight: 50 }
      ];
      expect(cache.computeWeightedScoreTotal(kpis)).toBeCloseTo(2.0);
    });
  });

  describe('extractCategories()', () => {
    it('should return sorted unique categories', () => {
      const { cache } = setup();
      const kpis = [
        { ...kpi, categoryName: 'Delivery' },
        { ...kpi, id: 2, categoryName: 'Quality' },
        { ...kpi, id: 3, categoryName: 'Delivery' }
      ];
      const cats = cache.extractCategories(kpis);
      expect(cats).toEqual(['Delivery', 'Quality']);
    });

    it('should use "Other" for null categories', () => {
      const { cache } = setup();
      const kpis = [{ ...kpi, categoryName: null as any }];
      expect(cache.extractCategories(kpis)).toContain('Other');
    });
  });

  describe('getCycleDaysRemaining()', () => {
    it('should return null for undefined cycle', () => {
      const { cache } = setup();
      expect(cache.getCycleDaysRemaining(undefined)).toBeNull();
    });

    it('should return positive days for future end date', () => {
      const { cache } = setup();
      const futureCycle = { ...cycle, endDate: '2099-12-31' };
      const days = cache.getCycleDaysRemaining(futureCycle);
      expect(days).not.toBeNull();
      if (days) expect(days).toBeGreaterThan(0);
    });

    it('should return negative days for past end date', () => {
      const { cache } = setup();
      const pastCycle = { ...cycle, endDate: '2020-01-01' };
      const days = cache.getCycleDaysRemaining(pastCycle);
      if (days) expect(days).toBeLessThan(0);
    });
  });

  describe('filterKpis()', () => {
    const kpis: KpiInstance[] = [
      { ...kpi, id: 1, kpiName: 'Code Quality', categoryName: 'Engineering', status: 'PENDING' },
      { ...kpi, id: 2, kpiName: 'Team Output', categoryName: 'Management', status: 'SUBMITTED' },
      { ...kpi, id: 3, kpiName: 'Code Coverage', categoryName: 'Engineering', status: 'APPROVED' }
    ];

    it('should return all when no filters', () => {
      const { cache } = setup();
      expect(cache.filterKpis(kpis, {})).toHaveLength(3);
    });

    it('should filter by status', () => {
      const { cache } = setup();
      expect(cache.filterKpis(kpis, { status: 'PENDING' })).toHaveLength(1);
    });

    it('should filter by category', () => {
      const { cache } = setup();
      expect(cache.filterKpis(kpis, { category: 'Engineering' })).toHaveLength(2);
    });

    it('should filter by search term (case-insensitive)', () => {
      const { cache } = setup();
      expect(cache.filterKpis(kpis, { search: 'code' })).toHaveLength(2);
    });

    it('should combine multiple filters', () => {
      const { cache } = setup();
      const result = cache.filterKpis(kpis, { search: 'code', status: 'PENDING' });
      expect(result).toHaveLength(1);
      expect(result[0].id).toBe(1);
    });

    it('should handle empty kpis array', () => {
      const { cache } = setup();
      expect(cache.filterKpis([], { search: 'anything' })).toHaveLength(0);
    });
  });
});

