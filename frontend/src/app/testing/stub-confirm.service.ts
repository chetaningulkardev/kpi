import { Observable, of } from 'rxjs';
import { ConfirmOptions, ConfirmOutcome } from '../services/confirm.service';

/**
 * Stand-in for {@link ConfirmService} in component tests.
 *
 * <p>Answers immediately with whatever {@link answer} is set to, so a spec can
 * exercise both the "user agreed" and "user declined" paths without rendering
 * the dialog. Every request is recorded, which lets a test assert on what the
 * user was actually asked — the wording of a destructive confirmation is part
 * of the behaviour, not decoration.
 */
export class StubConfirmService {

  answer: ConfirmOutcome = { confirmed: true };

  /** Every question asked, oldest first. */
  readonly asked: ConfirmOptions[] = [];

  get lastAsked(): ConfirmOptions | undefined {
    return this.asked[this.asked.length - 1];
  }

  active(): null {
    return null;
  }

  ask(options: ConfirmOptions): Observable<ConfirmOutcome> {
    this.asked.push(options);
    return of(this.answer);
  }

  confirm(options: ConfirmOptions): Observable<boolean> {
    this.asked.push(options);
    return of(this.answer.confirmed);
  }

  confirmed(options: ConfirmOptions): Promise<boolean> {
    this.asked.push(options);
    return Promise.resolve(this.answer.confirmed);
  }

  askFor(options: ConfirmOptions): Promise<ConfirmOutcome> {
    this.asked.push(options);
    return Promise.resolve(this.answer);
  }

  resolve(): void { /* nothing to settle — answers are immediate */ }

  dismiss(): void { /* nothing to settle — answers are immediate */ }
}

