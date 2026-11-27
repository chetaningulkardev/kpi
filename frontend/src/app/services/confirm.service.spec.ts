import { TestBed } from '@angular/core/testing';
import { ConfirmService } from './confirm.service';

describe('ConfirmService', () => {

  let service: ConfirmService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ConfirmService);
  });

  it('should expose nothing until asked', () => {
    expect(service.active()).toBeNull();
  });

  it('should publish the request for the dialog to render', () => {
    service.ask({ title: 'Delete team', message: 'Delete team Alpha?' }).subscribe();
    expect(service.active()?.title).toBe('Delete team');
    expect(service.active()?.message).toBe('Delete team Alpha?');
  });

  it('should resolve true when confirmed', async () => {
    const answer = service.confirmed({ title: 't', message: 'm' });
    service.resolve({ confirmed: true });
    await expect(answer).resolves.toBe(true);
  });

  it('should resolve false when dismissed', async () => {
    const answer = service.confirmed({ title: 't', message: 'm' });
    service.dismiss();
    await expect(answer).resolves.toBe(false);
  });

  it('should clear the request once answered', () => {
    service.confirm({ title: 't', message: 'm' }).subscribe();
    expect(service.active()).not.toBeNull();
    service.resolve({ confirmed: true });
    expect(service.active()).toBeNull();
  });

  it('should carry the reason back to the caller', async () => {
    const outcome = service.askFor({
      title: 'Reject',
      message: 'Reject 3 KPIs?',
      reason: { label: 'Why?', required: true }
    });
    service.resolve({ confirmed: true, reason: 'Targets were not evidenced' });
    await expect(outcome).resolves.toEqual({
      confirmed: true,
      reason: 'Targets were not evidenced'
    });
  });

  it('should complete, so callers need not unsubscribe', () => {
    let completed = false;
    service.confirm({ title: 't', message: 'm' }).subscribe({ complete: () => (completed = true) });
    service.resolve({ confirmed: true });
    expect(completed).toBe(true);
  });

  it('should ignore a resolve when nothing was asked', () => {
    expect(() => service.resolve({ confirmed: true })).not.toThrow();
    expect(service.active()).toBeNull();
  });

  // ── Concurrency ─────────────────────────────────────────────────────────

  it('should decline a second question while one is open, rather than replacing it', async () => {
    const first = service.confirmed({ title: 'First', message: 'first?' });
    const second = service.confirmed({ title: 'Second', message: 'second?' });

    // The dialog on screen must still be the one the user is reading.
    expect(service.active()?.title).toBe('First');

    // A declined question answers "no", so its caller does nothing at all.
    await expect(second).resolves.toBe(false);

    service.resolve({ confirmed: true });
    await expect(first).resolves.toBe(true);
  });

  it('should accept a new question once the previous one is answered', async () => {
    const first = service.confirmed({ title: 'First', message: 'first?' });
    service.resolve({ confirmed: false });
    await expect(first).resolves.toBe(false);

    const second = service.confirmed({ title: 'Second', message: 'second?' });
    expect(service.active()?.title).toBe('Second');
    service.resolve({ confirmed: true });
    await expect(second).resolves.toBe(true);
  });
});

