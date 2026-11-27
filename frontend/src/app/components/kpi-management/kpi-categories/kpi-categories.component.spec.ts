import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { KpiCategoriesComponent } from './kpi-categories.component';
import { ConfigurationService, KpiCategory } from '../../../services/configuration.service';
import { ConfirmService } from '../../../services/confirm.service';
import { StubConfirmService } from '../../../testing/stub-confirm.service';

/**
 * Deletion used to sit behind {@code window.confirm}, which jsdom does not
 * implement — so none of this could be tested at all. These are the assertions
 * that gap was hiding.
 */
function makeCategory(overrides: Partial<KpiCategory> = {}): KpiCategory {
  return {
    id: 7,
    categoryCode: 'DELIVERY',
    categoryName: 'Delivery',
    description: '',
    displayOrder: 1,
    active: true,
    ...overrides
  } as KpiCategory;
}

function setup(overrides: Record<string, unknown> = {}) {
  const svc = {
    getKpiCategories: vi.fn().mockReturnValue(of([makeCategory()])),
    deleteKpiCategory: vi.fn().mockReturnValue(of(void 0)),
    createKpiCategory: vi.fn().mockReturnValue(of(makeCategory())),
    updateKpiCategory: vi.fn().mockReturnValue(of(makeCategory())),
    ...overrides
  };
  const confirmStub = new StubConfirmService();

  TestBed.configureTestingModule({
    imports: [KpiCategoriesComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: ConfigurationService, useValue: svc },
      { provide: ConfirmService, useValue: confirmStub }
    ]
  });

  const fixture = TestBed.createComponent(KpiCategoriesComponent);
  return { fixture, comp: fixture.componentInstance, svc, confirmStub };
}

describe('KpiCategoriesComponent deletion', () => {

  afterEach(() => TestBed.resetTestingModule());

  it('should ask before deleting', async () => {
    const { comp, confirmStub } = setup();
    await comp.delete(makeCategory());

    expect(confirmStub.asked.length).toBe(1);
  });

  it('should name the category in the question', async () => {
    const { comp, confirmStub } = setup();
    await comp.delete(makeCategory({ categoryCode: 'QUALITY' }));

    // "Delete this item?" gives no way to notice the wrong row was clicked.
    expect(confirmStub.lastAsked?.message).toContain('QUALITY');
  });

  it('should mark the action as destructive', async () => {
    const { comp, confirmStub } = setup();
    await comp.delete(makeCategory());

    expect(confirmStub.lastAsked?.dangerous).toBe(true);
    expect(confirmStub.lastAsked?.confirmLabel).toBe('Delete');
  });

  it('should delete once confirmed', async () => {
    const { comp, svc } = setup();
    await comp.delete(makeCategory({ id: 7 }));

    expect(svc.deleteKpiCategory).toHaveBeenCalledWith(7);
  });

  it('should not delete when declined', async () => {
    const { comp, svc, confirmStub } = setup();
    confirmStub.answer = { confirmed: false };

    await comp.delete(makeCategory());

    expect(svc.deleteKpiCategory).not.toHaveBeenCalled();
  });

  it('should not ask about a category that was never saved', async () => {
    const { comp, svc, confirmStub } = setup();
    await comp.delete(makeCategory({ id: undefined }));

    // Nothing to delete, so nothing to ask — a dialog here would be a dead end.
    expect(confirmStub.asked.length).toBe(0);
    expect(svc.deleteKpiCategory).not.toHaveBeenCalled();
  });

  it('should report a refusal from the server', async () => {
    const { comp } = setup({
      deleteKpiCategory: vi.fn().mockReturnValue(
        throwError(() => ({ error: { error: 'Category is in use' } }))
      )
    });

    await comp.delete(makeCategory());

    expect(comp.pageMsgIsError).toBe(true);
    expect(comp.pageMsg).toBeTruthy();
  });

  it('should clear the in-progress marker whether it succeeds or fails', async () => {
    const { comp } = setup({
      deleteKpiCategory: vi.fn().mockReturnValue(throwError(() => new Error('nope')))
    });

    await comp.delete(makeCategory());

    // Left set, the row's spinner never stops and the button stays disabled.
    expect(comp.deletingId).toBeNull();
  });
});

