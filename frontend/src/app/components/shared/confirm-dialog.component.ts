import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ConfirmService } from '../../services/confirm.service';
import { AccessibleModalDirective } from '../../directives/accessible-modal.directive';

/**
 * Renders whatever {@link ConfirmService} is currently asking.
 *
 * <p>Mounted once in the application shell rather than per page, so every
 * confirmation in the product looks and behaves the same — and so the reason
 * captured for a rejected KPI is validated in one place instead of six.
 *
 * <p>The reason field is bound with a template reference rather than
 * {@code ngModel}. This component sits in the eagerly-loaded shell, so
 * importing {@code FormsModule} here would pull the whole forms package into
 * the initial bundle for every visitor — including the ones who never open a
 * dialog. One textarea does not need it.
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CommonModule, AccessibleModalDirective],
  template: `
    <div *ngIf="confirmService.active() as request"
      class="fixed inset-0 z-[90] flex items-center justify-center bg-black/40 p-4"
      (click)="cancel()">

      <div class="w-full max-w-md rounded-xl bg-white shadow-2xl"
        [appAccessibleModal]="request.title"
        (dismiss)="cancel()"
        (click)="$event.stopPropagation()">

        <div class="px-5 py-4 border-b border-slate-200">
          <h2 class="text-base font-semibold text-slate-900">{{ request.title }}</h2>
          <p class="mt-1 text-sm text-slate-600">{{ request.message }}</p>
        </div>

        <div *ngIf="request.reason" class="px-5 py-4">
          <label [attr.for]="'confirm-reason'" class="block text-sm font-medium text-slate-700">
            {{ request.reason.label }}
            <span *ngIf="request.reason.required" class="text-rose-600" aria-hidden="true">*</span>
          </label>
          <textarea id="confirm-reason"
            #reasonBox
            [value]="reason"
            (input)="reason = reasonBox.value"
            [attr.maxlength]="request.reason.maxLength || 2000"
            [attr.placeholder]="request.reason.placeholder || null"
            [attr.aria-required]="request.reason.required ? 'true' : null"
            [attr.aria-describedby]="showReasonError() ? 'confirm-reason-error' : null"
            rows="4"
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-dt-500 focus:outline-none focus:ring-1 focus:ring-dt-500"></textarea>

          <p *ngIf="showReasonError()" id="confirm-reason-error" class="mt-1 text-sm text-rose-600" role="alert">
            This is required — the person receiving it needs to know what to change.
          </p>
        </div>

        <div class="flex justify-end gap-2 px-5 py-4 border-t border-slate-200">
          <button type="button" (click)="cancel()"
            class="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">
            {{ request.cancelLabel || 'Cancel' }}
          </button>
          <button type="button" (click)="accept()"
            [ngClass]="request.dangerous
              ? 'bg-rose-600 hover:bg-rose-700 focus:ring-rose-500'
              : 'bg-dt-600 hover:bg-dt-700 focus:ring-dt-500'"
            class="rounded-lg px-4 py-2 text-sm font-semibold text-white focus:outline-none focus:ring-2 focus:ring-offset-1">
            {{ request.confirmLabel || 'Confirm' }}
          </button>
        </div>
      </div>
    </div>
  `
})
export class ConfirmDialogComponent {

  readonly confirmService = inject(ConfirmService);

  reason = '';

  /**
   * Shown only after an attempt to confirm. Marking a field invalid before the
   * user has tried to submit reads as being told off for not having finished
   * typing yet.
   */
  private readonly attempted = signal(false);

  showReasonError(): boolean {
    const request = this.confirmService.active();
    return !!request?.reason?.required && this.attempted() && !this.reason.trim();
  }

  accept(): void {
    const request = this.confirmService.active();
    if (!request) {
      return;
    }

    if (request.reason?.required && !this.reason.trim()) {
      this.attempted.set(true);
      return;
    }

    const reason = request.reason ? this.reason.trim() : undefined;
    this.reset();
    this.confirmService.resolve({ confirmed: true, reason });
  }

  cancel(): void {
    this.reset();
    this.confirmService.dismiss();
  }

  private reset(): void {
    this.reason = '';
    this.attempted.set(false);
  }
}

