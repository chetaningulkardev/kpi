import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmDialogComponent } from './confirm-dialog.component';
import { ConfirmService } from '../../services/confirm.service';

describe('ConfirmDialogComponent', () => {

  let fixture: ComponentFixture<ConfirmDialogComponent>;
  let service: ConfirmService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ConfirmDialogComponent] }).compileComponents();
    fixture = TestBed.createComponent(ConfirmDialogComponent);
    service = TestBed.inject(ConfirmService);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
  });

  afterEach(() => fixture.nativeElement.remove());

  function host(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function buttonLabelled(text: string): HTMLButtonElement | undefined {
    return Array.from(host().querySelectorAll('button'))
      .find(b => (b.textContent ?? '').trim() === text) as HTMLButtonElement | undefined;
  }

  it('should render nothing until something is asked', () => {
    expect(host().querySelector('[role="dialog"]')).toBeNull();
  });

  it('should render the question', () => {
    service.confirm({ title: 'Confirm deletion', message: 'Delete team Alpha?' }).subscribe();
    fixture.detectChanges();

    const text = host().textContent ?? '';
    expect(text).toContain('Confirm deletion');
    expect(text).toContain('Delete team Alpha?');
  });

  it('should be a dialog for assistive technology', () => {
    service.confirm({ title: 'Confirm deletion', message: 'Delete?' }).subscribe();
    fixture.detectChanges();

    const panel = host().querySelector('[role="dialog"]');
    expect(panel?.getAttribute('aria-modal')).toBe('true');
    expect(panel?.getAttribute('aria-label')).toBe('Confirm deletion');
  });

  it('should confirm when the action is taken', async () => {
    const answer = service.confirmed({ title: 't', message: 'm', confirmLabel: 'Delete' });
    fixture.detectChanges();

    buttonLabelled('Delete')?.click();
    fixture.detectChanges();

    await expect(answer).resolves.toBe(true);
  });

  it('should decline when cancelled', async () => {
    const answer = service.confirmed({ title: 't', message: 'm' });
    fixture.detectChanges();

    buttonLabelled('Cancel')?.click();
    fixture.detectChanges();

    await expect(answer).resolves.toBe(false);
  });

  it('should decline on Escape', async () => {
    const answer = service.confirmed({ title: 't', message: 'm' });
    fixture.detectChanges();

    host().querySelector('[role="dialog"]')!.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    fixture.detectChanges();

    await expect(answer).resolves.toBe(false);
  });

  // ── The reason field ────────────────────────────────────────────────────

  it('should not show a reason field unless one was requested', () => {
    service.confirm({ title: 't', message: 'm' }).subscribe();
    fixture.detectChanges();
    expect(host().querySelector('textarea')).toBeNull();
  });

  it('should collect a reason and hand it back', async () => {
    const outcome = service.askFor({
      title: 'Reject',
      message: 'Reject 3 KPIs?',
      confirmLabel: 'Reject',
      reason: { label: 'Why are these rejected?', required: true }
    });
    fixture.detectChanges();

    const textarea = host().querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = '  Targets were not evidenced  ';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    buttonLabelled('Reject')?.click();
    fixture.detectChanges();

    // Trimmed: the reason becomes permanent text on a performance record.
    await expect(outcome).resolves.toEqual({
      confirmed: true,
      reason: 'Targets were not evidenced'
    });
  });

  it('should refuse to confirm while a required reason is blank', () => {
    let settled = false;
    service.askFor({
      title: 'Reject',
      message: 'Reject?',
      confirmLabel: 'Reject',
      reason: { label: 'Why?', required: true }
    }).then(() => (settled = true));
    fixture.detectChanges();

    buttonLabelled('Reject')?.click();
    fixture.detectChanges();

    expect(settled).toBe(false);
    expect(service.active()).not.toBeNull();
    expect(host().textContent).toContain('This is required');
  });

  it('should not scold before the user has tried to submit', () => {
    service.askFor({
      title: 'Reject',
      message: 'Reject?',
      reason: { label: 'Why?', required: true }
    });
    fixture.detectChanges();

    expect(host().textContent).not.toContain('This is required');
  });

  it('should treat whitespace as no reason at all', () => {
    let settled = false;
    service.askFor({
      title: 'Reject',
      message: 'Reject?',
      confirmLabel: 'Reject',
      reason: { label: 'Why?', required: true }
    }).then(() => (settled = true));
    fixture.detectChanges();

    const textarea = host().querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = '    ';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    buttonLabelled('Reject')?.click();
    fixture.detectChanges();

    expect(settled).toBe(false);
  });

  it('should not carry a reason over into the next question', async () => {
    const first = service.askFor({
      title: 'Reject',
      message: 'Reject?',
      confirmLabel: 'Reject',
      reason: { label: 'Why?', required: true }
    });
    fixture.detectChanges();

    const textarea = host().querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = 'first reason';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    buttonLabelled('Reject')?.click();
    fixture.detectChanges();
    await first;

    const second = service.askFor({
      title: 'Rework',
      message: 'Rework?',
      confirmLabel: 'Rework',
      reason: { label: 'Why?', required: true }
    });
    fixture.detectChanges();

    // A stale reason would be attributed to the wrong decision entirely.
    expect((host().querySelector('textarea') as HTMLTextAreaElement).value).toBe('');

    service.dismiss();
    await second;
  });
});

