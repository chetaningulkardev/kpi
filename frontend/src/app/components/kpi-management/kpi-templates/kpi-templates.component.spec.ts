import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { KpiTemplatesComponent } from './kpi-templates.component';
import { ConfigurationService } from '../../../services/configuration.service';
import { vi } from 'vitest';

describe('KpiTemplatesComponent', () => {
  let fixture: ComponentFixture<KpiTemplatesComponent>;
  let component: KpiTemplatesComponent;

  const configServiceMock = {
    getTemplates: vi.fn().mockReturnValue(of([])),
    getKpiMasters: vi.fn().mockReturnValue(of([])),
    getRoles: vi.fn().mockReturnValue(of([])),
    deleteTemplate: vi.fn().mockReturnValue(of(void 0)),
    activateTemplate: vi.fn().mockReturnValue(of(void 0)),
    cloneTemplate: vi.fn().mockReturnValue(of({})),
    archiveTemplate: vi.fn().mockReturnValue(of(void 0)),
    getTemplateById: vi.fn().mockReturnValue(of({ id: 1, code: 'TMP', name: 'T', roleCode: 'R', items: [] })),
    addTemplateItem: vi.fn().mockReturnValue(of({})),
    removeTemplateItem: vi.fn().mockReturnValue(of(void 0)),
    updateTemplate: vi.fn().mockReturnValue(of({})),
    createTemplate: vi.fn().mockReturnValue(of({}))
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [KpiTemplatesComponent],
      providers: [{ provide: ConfigurationService, useValue: configServiceMock }]
    }).compileComponents();

    fixture = TestBed.createComponent(KpiTemplatesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('opens confirm popup for archive action', () => {
    component.archiveTemplate({ id: 9, code: 'TMP9', name: 'Temp 9', roleCode: 'ADMIN', items: [] });

    expect(component.confirmPopup.visible).toBe(true);
    expect(component.confirmPopup.actionLabel).toBe('Archive');
  });

  it('executes confirm popup action for activate action', () => {
    component.activateTemplate({ id: 5, code: 'TMP5', name: 'Temp 5', roleCode: 'ADMIN', items: [] });

    expect(component.confirmPopup.visible).toBe(true);
    component.confirmPopupAction();

    expect(configServiceMock.activateTemplate).toHaveBeenCalledWith(5);
  });
});

