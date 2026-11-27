import { Component, OnInit, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { ConfigurationService, KpiScorecard } from '../../../services/configuration.service';
import { extractHttpError } from '../../../services/http-error.util';

/** One plotted cycle, in SVG user units. */
export interface TrendPoint {
  cycleCode: string;
  cycleName: string;
  percentage: number;
  score: number | null;
  maxScore: number | null;
  x: number;
  y: number;
  /** Change in percentage points against the previous cycle; null for the first. */
  delta: number | null;
}

export interface CategoryRow {
  name: string;
  score: number;
  previous: number | null;
  delta: number | null;
}

@Component({
  selector: 'app-my-performance',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './my-performance.component.html'
})
export class MyPerformanceComponent implements OnInit {

  private configService = inject(ConfigurationService);
  private cdr = inject(ChangeDetectorRef);

  // The chart is drawn in a fixed user-space viewBox and scaled by CSS, so the
  // maths below never has to know the rendered pixel size.
  readonly chartWidth = 720;
  readonly chartHeight = 260;
  readonly padding = { top: 20, right: 24, bottom: 40, left: 48 };

  loading = true;
  errorMessage = '';
  history: KpiScorecard[] = [];
  points: TrendPoint[] = [];
  categoryRows: CategoryRow[] = [];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.errorMessage = '';
    this.configService.getMyScorecardHistory()
      .pipe(finalize(() => { this.loading = false; this.cdr.detectChanges(); }))
      .subscribe({
        next: (cards) => {
          this.history = cards ?? [];
          this.points = this.buildPoints(this.history);
          this.categoryRows = this.buildCategoryRows(this.history);
        },
        error: (err) => {
          this.errorMessage = extractHttpError(err, 'Could not load your performance history.');
          this.history = [];
          this.points = [];
          this.categoryRows = [];
        }
      });
  }

  // ── Derived state ───────────────────────────────────────────────────────

  get latest(): KpiScorecard | null {
    return this.history.length ? this.history[this.history.length - 1] : null;
  }

  get previous(): KpiScorecard | null {
    return this.history.length > 1 ? this.history[this.history.length - 2] : null;
  }

  /**
   * Change against the immediately preceding cycle, in percentage points.
   * Percentage points, not percent: a move from 40% to 50% is +10 here, and
   * describing it as "+25%" would be a different and much louder claim.
   */
  get latestDelta(): number | null {
    return this.latest ? this.deltaFor(this.latest) : null;
  }

  /** Only meaningful once there are two cycles to compare. */
  get hasTrend(): boolean {
    return this.points.length >= 2;
  }

  get averagePercentage(): number | null {
    if (!this.points.length) {
      return null;
    }
    const total = this.points.reduce((sum, p) => sum + p.percentage, 0);
    return Math.round(total / this.points.length * 10) / 10;
  }

  get bestPoint(): TrendPoint | null {
    if (!this.points.length) {
      return null;
    }
    return this.points.reduce((best, p) => (p.percentage > best.percentage ? p : best));
  }

  // ── Chart geometry ──────────────────────────────────────────────────────

  /**
   * The y-axis is pinned to 0–100 rather than fitted to the data. An
   * auto-fitted axis makes a drift from 82% to 79% look like a cliff, which in
   * a performance review is a misrepresentation rather than a styling choice.
   */
  private yFor(percentage: number): number {
    const plotHeight = this.chartHeight - this.padding.top - this.padding.bottom;
    const clamped = Math.max(0, Math.min(100, percentage));
    return this.padding.top + plotHeight * (1 - clamped / 100);
  }

  private xFor(index: number, count: number): number {
    const plotWidth = this.chartWidth - this.padding.left - this.padding.right;
    if (count <= 1) {
      // A single cycle sits in the middle instead of hugging the left edge.
      return this.padding.left + plotWidth / 2;
    }
    return this.padding.left + plotWidth * (index / (count - 1));
  }

  private buildPoints(cards: KpiScorecard[]): TrendPoint[] {
    // A cycle with no computable percentage is dropped rather than plotted at
    // zero. It has no position on this axis, and inventing one would draw a
    // dip that never happened.
    const scored = cards.filter(c => c.scorePercentage !== null && c.scorePercentage !== undefined);

    return scored.map((card, index) => {
      const percentage = card.scorePercentage as number;
      const prior = index > 0 ? scored[index - 1].scorePercentage as number : null;
      return {
        cycleCode: card.cycleCode ?? '—',
        cycleName: card.cycleName ?? card.cycleCode ?? 'Unnamed cycle',
        percentage,
        score: card.overallScore,
        maxScore: card.maxScore,
        x: this.xFor(index, scored.length),
        y: this.yFor(percentage),
        delta: prior === null ? null : Math.round((percentage - prior) * 10) / 10
      };
    });
  }

  /** Points joined into an SVG polyline. Empty when there is nothing to join. */
  get linePath(): string {
    return this.points.map(p => `${p.x},${p.y}`).join(' ');
  }

  /** Horizontal gridlines every 25%. */
  readonly gridLines = [0, 25, 50, 75, 100];

  gridY(percentage: number): number {
    return this.yFor(percentage);
  }

  get axisLeft(): number { return this.padding.left; }
  get axisRight(): number { return this.chartWidth - this.padding.right; }
  get axisBottom(): number { return this.chartHeight - this.padding.bottom; }

  // ── Category breakdown ──────────────────────────────────────────────────

  private buildCategoryRows(cards: KpiScorecard[]): CategoryRow[] {
    const latest = cards.length ? cards[cards.length - 1] : null;
    if (!latest?.categoryScores) {
      return [];
    }
    const prior = cards.length > 1 ? cards[cards.length - 2]?.categoryScores ?? {} : {};

    return Object.keys(latest.categoryScores).sort().map(name => {
      const score = latest.categoryScores[name];
      // A category absent from the previous cycle has no comparison — it is
      // new, not unchanged, so the delta stays null rather than becoming the
      // full score.
      const previous = Object.prototype.hasOwnProperty.call(prior, name) ? prior[name] : null;
      return {
        name,
        score,
        previous,
        delta: previous === null ? null : Math.round((score - previous) * 100) / 100
      };
    });
  }

  // ── Presentation ────────────────────────────────────────────────────────

  /**
   * The change recorded for a card in the table.
   *
   * <p>Looked up from the plotted points rather than recomputed against the
   * adjacent table row, so the table and the chart can never disagree: an
   * unscored cycle sits between two scored ones in the table but is skipped by
   * the line, and the comparison has to skip it too.
   */
  deltaFor(card: KpiScorecard): number | null {
    const point = this.points.find(p => p.cycleCode === card.cycleCode);
    return point ? point.delta : null;
  }

  deltaLabel(delta: number | null, unit: string): string {
    if (delta === null) {
      return 'No previous cycle to compare';
    }
    if (delta === 0) {
      return `Unchanged from the previous cycle`;
    }
    const direction = delta > 0 ? 'up' : 'down';
    return `${direction} ${Math.abs(delta)}${unit} from the previous cycle`;
  }

  deltaClass(delta: number | null): string {
    if (delta === null || delta === 0) {
      return 'text-slate-500';
    }
    return delta > 0 ? 'text-emerald-600' : 'text-amber-600';
  }

  deltaSymbol(delta: number | null): string {
    if (delta === null) {
      return '';
    }
    if (delta === 0) {
      return '±0';
    }
    return delta > 0 ? `+${delta}` : `${delta}`;
  }

  scoreClass(percentage: number | null): string {
    if (percentage === null) {
      return 'text-slate-400';
    }
    if (percentage >= 80) { return 'text-emerald-600'; }
    if (percentage >= 60) { return 'text-sky-600'; }
    if (percentage >= 40) { return 'text-amber-600'; }
    return 'text-rose-600';
  }

  trackByCycle = (_: number, point: TrendPoint): string => point.cycleCode;
  trackByCategory = (_: number, row: CategoryRow): string => row.name;
  trackByScorecard = (_: number, card: KpiScorecard): number => card.id;
}
