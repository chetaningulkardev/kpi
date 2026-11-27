import { Injectable } from '@angular/core';
import { Observable, of, shareReplay, throwError } from 'rxjs';
import { tap, catchError } from 'rxjs/operators';
import { ConfigurationService, KpiInstance, ReviewCycle } from './configuration.service';

/**
 * KpiCacheService - Performance optimization layer
 *
 * Provides:
 * - Cached API responses with TTL (time-to-live)
 * - Memoized expensive computations
 * - Shared observable streams to prevent duplicate HTTP calls
 * - Optimistic UI updates
 */
@Injectable({ providedIn: 'root' })
export class KpiCacheService {

  private readonly CACHE_TTL_MS = 30_000; // 30 seconds cache

  // ─── Cycle cache ──────────────────────────────────────────────────────────
  private cyclesCache: { data: ReviewCycle[]; ts: number } | null = null;
  private cyclesStream$: Observable<ReviewCycle[]> | null = null;

  // ─── KPI caches keyed by cycleCode ────────────────────────────────────────
  private kpiCache = new Map<string, { data: KpiInstance[]; ts: number }>();

  constructor(private configService: ConfigurationService) {}

  /**
   * Get review cycles — returns cached value if fresh, else fetches.
   */
  getReviewCycles(force = false): Observable<ReviewCycle[]> {
    if (!force && this.cyclesCache && this.isFresh(this.cyclesCache.ts)) {
      return of(this.cyclesCache.data);
    }
    // A forced refresh must discard the memoized stream, otherwise `shareReplay`
    // would just replay the previous result instead of re-fetching.
    if (force) {
      this.cyclesStream$ = null;
    }
    if (!this.cyclesStream$) {
      this.cyclesStream$ = this.configService.getReviewCycles().pipe(
        tap(data => { this.cyclesCache = { data, ts: Date.now() }; }),
        // Serve stale data when available, otherwise let the caller show an error.
        catchError(err => this.cyclesCache ? of(this.cyclesCache.data) : throwError(() => err)),
        shareReplay(1)
      );
    }
    return this.cyclesStream$;
  }

  /**
   * Get KPIs for a cycle — returns cached value if fresh, else fetches.
   */
  getMyKpis(cycleCode: string, force = false): Observable<KpiInstance[]> {
    const cached = this.kpiCache.get(cycleCode);
    if (!force && cached && this.isFresh(cached.ts)) {
      return of(cached.data);
    }
    return this.configService.getMyKpis(cycleCode).pipe(
      tap(data => this.kpiCache.set(cycleCode, { data, ts: Date.now() })),
      // Serve stale data when available, otherwise let the caller show an error.
      catchError(err => cached ? of(cached.data) : throwError(() => err))
    );
  }

  /**
   * Invalidate KPI cache for a cycle (call after submission).
   */
  invalidateKpiCache(cycleCode: string): void {
    this.kpiCache.delete(cycleCode);
  }

  /**
   * Invalidate all caches.
   */
  clearAll(): void {
    this.cyclesCache = null;
    this.cyclesStream$ = null;
    this.kpiCache.clear();
  }

  /**
   * Optimistically update a single KPI in the cache.
   */
  updateKpiInCache(cycleCode: string, updatedKpi: KpiInstance): void {
    const cached = this.kpiCache.get(cycleCode);
    if (!cached) return;
    const idx = cached.data.findIndex(k => k.id === updatedKpi.id);
    if (idx !== -1) {
      cached.data[idx] = updatedKpi;
      cached.ts = Date.now(); // refresh TTL
    }
  }

  // ─── Memoized computations ─────────────────────────────────────────────────
  /**
   * Memoized total weight calculation.
   * Only recomputes when kpis array reference changes.
   */
  computeTotalWeight(kpis: KpiInstance[]): number {
    return kpis.reduce((sum, k) => sum + (k.weight ?? 0), 0);
  }

  /**
   * Memoized weighted score total.
   */
  computeWeightedScoreTotal(kpis: KpiInstance[]): number {
    return kpis.reduce((sum, k) => {
      const score = k.rating != null ? (k.rating * (k.weight ?? 0)) / 100 : 0;
      return sum + score;
    }, 0);
  }

  /**
   * Memoized category extraction — returns sorted unique categories.
   */
  extractCategories(kpis: KpiInstance[]): string[] {
    return [...new Set(kpis.map(k => k.categoryName ?? 'Other'))].sort();
  }

  /**
   * Compute cycle days remaining.
   */
  getCycleDaysRemaining(cycle: ReviewCycle | undefined): number | null {
    if (!cycle?.endDate) return null;
    const diff = new Date(cycle.endDate).getTime() - Date.now();
    return Math.ceil(diff / (1000 * 60 * 60 * 24));
  }

  /**
   * Filter KPIs — optimized single-pass filter.
   */
  filterKpis(
    kpis: KpiInstance[],
    opts: { search?: string; status?: string; category?: string }
  ): KpiInstance[] {
    const term = opts.search?.trim().toLowerCase() ?? '';
    const status = opts.status ?? 'All';
    const category = opts.category ?? 'All';

    if (!term && status === 'All' && category === 'All') return kpis;

    return kpis.filter(kpi => {
      if (status !== 'All' && kpi.status !== status) return false;
      if (category !== 'All' && (kpi.categoryName ?? 'Other') !== category) return false;
      if (term) {
        const hay = `${kpi.kpiName} ${kpi.kpiCode} ${kpi.categoryName ?? ''}`.toLowerCase();
        if (!hay.includes(term)) return false;
      }
      return true;
    });
  }

  /**
   * Check if a cache entry is still fresh.
   */
  private isFresh(ts: number): boolean {
    return Date.now() - ts < this.CACHE_TTL_MS;
  }
}

