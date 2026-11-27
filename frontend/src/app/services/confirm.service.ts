import { Injectable, signal } from '@angular/core';
import { Observable, Subject, firstValueFrom } from 'rxjs';

/** Optional free-text field collected as part of the confirmation. */
export interface ConfirmReasonField {
  label: string;
  placeholder?: string;
  /** When true the confirm button stays disabled until text is entered. */
  required?: boolean;
  maxLength?: number;
}

export interface ConfirmOptions {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Styles the confirm button as destructive and warns assistive technology. */
  dangerous?: boolean;
  reason?: ConfirmReasonField;
}

export interface ConfirmOutcome {
  confirmed: boolean;
  /** Present only when the request asked for a reason and the user confirmed. */
  reason?: string;
}

interface ActiveRequest extends ConfirmOptions {
  result: Subject<ConfirmOutcome>;
}

/**
 * Application-owned replacement for {@code window.confirm} and
 * {@code window.prompt}.
 *
 * <p>The native dialogs were doing real work here — gating every delete, and
 * collecting the rework reason that becomes permanent text on someone's
 * performance record. They are a poor fit for that:
 *
 * <ul>
 *   <li>they block the JavaScript thread and cannot be styled or translated;</li>
 *   <li>{@code prompt} accepts unbounded input with no validation surface;</li>
 *   <li>they are suppressed outright in some browser contexts, in which case
 *       {@code confirm} returns false and the action silently does not happen;</li>
 *   <li>jsdom does not implement them, so every path behind one was untestable —
 *       which is why the delete actions in this application had no tests at all.</li>
 * </ul>
 */
@Injectable({ providedIn: 'root' })
export class ConfirmService {

  private readonly request = signal<ActiveRequest | null>(null);

  /** Read by the dialog component. Null when nothing is being asked. */
  readonly active = this.request.asReadonly();

  /**
   * Ask the user to confirm, optionally collecting a reason.
   *
   * <p>Emits exactly once and completes, so callers can subscribe without
   * unsubscribing.
   */
  ask(options: ConfirmOptions): Observable<ConfirmOutcome> {
    const result = new Subject<ConfirmOutcome>();

    if (this.request()) {
      // A second question while one is already open would either stack two
      // dialogs or replace the first without answering it. Both end with
      // someone confirming something other than what they were shown, so the
      // newcomer is declined instead. Declining is the safe direction: every
      // caller treats "not confirmed" as "do nothing".
      queueMicrotask(() => {
        result.next({ confirmed: false });
        result.complete();
      });
      return result.asObservable();
    }

    this.request.set({ ...options, result });
    return result.asObservable();
  }

  /** Convenience for the common yes/no case. */
  confirm(options: ConfirmOptions): Observable<boolean> {
    return new Observable<boolean>(subscriber => {
      this.ask(options).subscribe(outcome => {
        subscriber.next(outcome.confirmed);
        subscriber.complete();
      });
    });
  }

  /**
   * Promise form, for the many call sites shaped like the native API:
   *
   * <pre>if (!await confirm.confirmed({...})) return;</pre>
   *
   * <p>Offered deliberately. Rewriting every guarded delete into a callback
   * would move the whole method body one level deeper for no behavioural gain,
   * and each of those rewrites is a chance to change what happens after the
   * user says yes. Awaiting keeps the diff to the condition itself.
   */
  confirmed(options: ConfirmOptions): Promise<boolean> {
    return firstValueFrom(this.confirm(options));
  }

  /** Promise form of {@link ask}, for the cases that also collect a reason. */
  askFor(options: ConfirmOptions): Promise<ConfirmOutcome> {
    return firstValueFrom(this.ask(options));
  }

  /** Called by the dialog when the user answers. */
  resolve(outcome: ConfirmOutcome): void {
    const current = this.request();
    if (!current) {
      return;
    }
    this.request.set(null);
    current.result.next(outcome);
    current.result.complete();
  }

  /** Answering "no" is the default for any dismissal — Escape, backdrop, cancel. */
  dismiss(): void {
    this.resolve({ confirmed: false });
  }
}

