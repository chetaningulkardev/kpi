import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { MyPerformanceComponent } from './my-performance.component';
import { KpiScorecard } from '../../../services/configuration.service';

function card(partial: Partial<KpiScorecard>): KpiScorecard {
  return {
    id: 1,
    userId: 9,
    employeeName: 'Dev One',
    role: 'Developer',
    employeeLevel: 'L3',
    overallScore: 4,
    maxScore: 5,
    scorePercentage: 80,
    categoryScores: {},
    status: 'PUBLISHED',
    calibrationNotes: null,
    calibratedByName: null,
    cycleCode: 'Q1-2026',
    cycleName: 'Q1 Review',
    cycleStartDate: '2026-01-01',
    cycleEndDate: '2026-03-31',
    ...partial
  };
}

describe('MyPerformanceComponent', () => {
  let fixture: ComponentFixture<MyPerformanceComponent>;
  let component: MyPerformanceComponent;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MyPerformanceComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();

    fixture = TestBed.createComponent(MyPerformanceComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function respond(cards: KpiScorecard[]): void {
    fixture.detectChanges();
    httpMock.expectOne('/api/kpi/scorecards/me/history').flush(cards);
    fixture.detectChanges();
  }

  it('should load history on init', () => {
    respond([card({ cycleCode: 'Q1-2026' })]);
    expect(component.history.length).toBe(1);
    expect(component.loading).toBe(false);
  });

  it('should show an empty state rather than an error when nothing is published', () => {
    respond([]);
    expect(component.points).toEqual([]);
    expect(component.errorMessage).toBe('');
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('No published scorecards yet');
  });

  it('should surface a load failure', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/kpi/scorecards/me/history')
      .flush({ error: 'boom' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(component.errorMessage).toBeTruthy();
    expect(component.history).toEqual([]);
  });

  // ── The maths that decides what someone's record looks like ─────────────

  it('should compute the change between cycles in percentage points', () => {
    respond([
      card({ id: 1, cycleCode: 'Q1-2026', scorePercentage: 40 }),
      card({ id: 2, cycleCode: 'Q2-2026', scorePercentage: 50 })
    ]);
    // 40 -> 50 is +10 points, not +25 percent.
    expect(component.latestDelta).toBe(10);
  });

  it('should report no change for the first cycle rather than growth from zero', () => {
    respond([card({ id: 1, cycleCode: 'Q1-2026', scorePercentage: 62 })]);
    expect(component.latestDelta).toBeNull();
    expect(component.points[0].delta).toBeNull();
  });

  it('should omit unscored cycles from the trend instead of plotting them at zero', () => {
    respond([
      card({ id: 1, cycleCode: 'Q1-2026', scorePercentage: 70 }),
      card({ id: 2, cycleCode: 'Q2-2026', scorePercentage: null, overallScore: null }),
      card({ id: 3, cycleCode: 'Q3-2026', scorePercentage: 74 })
    ]);

    expect(component.points.map(p => p.cycleCode)).toEqual(['Q1-2026', 'Q3-2026']);
    // The comparison skips the gap: 70 -> 74, not 0 -> 74.
    expect(component.points[1].delta).toBe(4);
  });

  it('should keep the y-axis anchored to 0-100 so a small drift is not drawn as a collapse', () => {
    respond([
      card({ id: 1, cycleCode: 'Q1-2026', scorePercentage: 82 }),
      card({ id: 2, cycleCode: 'Q2-2026', scorePercentage: 79 })
    ]);

    const [first, second] = component.points;
    const plotHeight = component.chartHeight - component.padding.top - component.padding.bottom;
    // Three points of difference must occupy three percent of the plot, not
    // the whole of it as an auto-fitted axis would give.
    expect(Math.abs(second.y - first.y)).toBeCloseTo(plotHeight * 0.03, 5);
  });

  it('should place a lone point mid-chart rather than against the axis', () => {
    respond([card({ id: 1, cycleCode: 'Q1-2026', scorePercentage: 50 })]);
    const midpoint = component.padding.left
      + (component.chartWidth - component.padding.left - component.padding.right) / 2;
    expect(component.points[0].x).toBeCloseTo(midpoint, 5);
  });

  it('should require two scored cycles before drawing a trend line', () => {
    respond([card({ id: 1, cycleCode: 'Q1-2026', scorePercentage: 50 })]);
    expect(component.hasTrend).toBe(false);
    expect((fixture.nativeElement as HTMLElement).querySelector('svg')).toBeNull();
  });

  it('should average only the scored cycles', () => {
    respond([
      card({ id: 1, cycleCode: 'Q1-2026', scorePercentage: 60 }),
      card({ id: 2, cycleCode: 'Q2-2026', scorePercentage: null }),
      card({ id: 3, cycleCode: 'Q3-2026', scorePercentage: 80 })
    ]);
    // An unscored cycle must not drag the average toward zero.
    expect(component.averagePercentage).toBe(70);
  });

  it('should identify the best cycle', () => {
    respond([
      card({ id: 1, cycleCode: 'Q1-2026', scorePercentage: 60 }),
      card({ id: 2, cycleCode: 'Q2-2026', scorePercentage: 91 }),
      card({ id: 3, cycleCode: 'Q3-2026', scorePercentage: 80 })
    ]);
    expect(component.bestPoint?.cycleCode).toBe('Q2-2026');
  });

  // ── Category breakdown ──────────────────────────────────────────────────

  it('should compare categories against the previous cycle', () => {
    respond([
      card({ id: 1, cycleCode: 'Q1-2026', categoryScores: { Delivery: 3, Quality: 4 } }),
      card({ id: 2, cycleCode: 'Q2-2026', categoryScores: { Delivery: 4, Quality: 3.5 } })
    ]);

    const rows = component.categoryRows;
    expect(rows.map(r => r.name)).toEqual(['Delivery', 'Quality']);
    expect(rows[0].delta).toBe(1);
    expect(rows[1].delta).toBe(-0.5);
  });

  it('should mark a category that did not exist last cycle as new, not as a full gain', () => {
    respond([
      card({ id: 1, cycleCode: 'Q1-2026', categoryScores: { Delivery: 3 } }),
      card({ id: 2, cycleCode: 'Q2-2026', categoryScores: { Delivery: 3, Mentoring: 4 } })
    ]);

    const mentoring = component.categoryRows.find(r => r.name === 'Mentoring');
    expect(mentoring?.previous).toBeNull();
    expect(mentoring?.delta).toBeNull();
  });

  // ── Labelling ───────────────────────────────────────────────────────────

  it('should spell out the change for assistive technology', () => {
    expect(component.deltaLabel(8, ' percentage points'))
      .toBe('up 8 percentage points from the previous cycle');
    expect(component.deltaLabel(-3, ' percentage points'))
      .toBe('down 3 percentage points from the previous cycle');
    expect(component.deltaLabel(null, ' percentage points'))
      .toBe('No previous cycle to compare');
  });

  it('should render the chart with a text alternative', () => {
    respond([
      card({ id: 1, cycleCode: 'Q1-2026', scorePercentage: 60 }),
      card({ id: 2, cycleCode: 'Q2-2026', scorePercentage: 75 })
    ]);

    const svg = (fixture.nativeElement as HTMLElement).querySelector('svg');
    expect(svg?.getAttribute('role')).toBe('img');
    expect(svg?.getAttribute('aria-label')).toContain('60');
    expect(svg?.getAttribute('aria-label')).toContain('75');
  });

  it('should give the table the same numbers as the chart', () => {
    respond([
      card({ id: 1, cycleCode: 'Q1-2026', scorePercentage: 60 }),
      card({ id: 2, cycleCode: 'Q2-2026', scorePercentage: 75 })
    ]);

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('60%');
    expect(text).toContain('75%');
    expect(component.deltaFor(component.history[1])).toBe(15);
  });
});

