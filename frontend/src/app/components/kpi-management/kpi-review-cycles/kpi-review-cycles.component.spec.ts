import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { KpiReviewCyclesComponent } from './kpi-review-cycles.component';
import { ConfigurationService } from '../../../services/configuration.service';
import { vi } from 'vitest';

describe('KpiReviewCyclesComponent', () => {
  let fixture: ComponentFixture<KpiReviewCyclesComponent>;
  let component: KpiReviewCyclesComponent;

  const configServiceMock = {
    getReviewCycles: vi.fn().mockReturnValue(of([])),
    deleteReviewCycle: vi.fn().mockReturnValue(of(void 0)),
    openReviewCycle: vi.fn().mockReturnValue(of({})),
    closeReviewCycle: vi.fn().mockReturnValue(of({})),
    generateCycleKpis: vi.fn().mockReturnValue(of('ok')),
    createReviewCycle: vi.fn().mockReturnValue(of({})),
    updateReviewCycle: vi.fn().mockReturnValue(of({}))
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [KpiReviewCyclesComponent],
      providers: [{ provide: ConfigurationService, useValue: configServiceMock }]
    }).compileComponents();

    fixture = TestBed.createComponent(KpiReviewCyclesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('opens confirm popup for delete action', () => {
    component.delete({ id: 11, code: 'Q1_2027', name: 'Q1', startDate: '', endDate: '', statusCode: 'DRAFT' });

    expect(component.confirmPopup.visible).toBe(true);
    expect(component.confirmPopup.actionLabel).toBe('Delete');
  });

  it('executes confirm popup action for generate KPI', () => {
    const cycle = { id: 22, code: 'Q2_2027', name: 'Q2', startDate: '', endDate: '', statusCode: 'OPEN', kpisGenerated: false };
    component.generateKpis(cycle);

    expect(component.confirmPopup.visible).toBe(true);
    component.confirmPopupAction();

    expect(configServiceMock.generateCycleKpis).toHaveBeenCalledWith(22);
  });
});

