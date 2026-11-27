import {
  Directive,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnDestroy,
  OnInit,
  Output,
  inject
} from '@angular/core';

/**
 * Turns a plain styled `div` into a dialog that a keyboard or screen-reader
 * user can actually operate.
 *
 * Applied to the modal *panel* — the white card — rather than the backdrop.
 * The backdrop is a click target with no meaning of its own; announcing it as
 * a dialog would put the name and the content in different places.
 *
 * Every modal in this application is mounted with `*ngIf`, so the directive's
 * own lifecycle already tracks open and close: construction is the dialog
 * opening and destruction is it closing. That avoids a second piece of state
 * that could disagree with the one the template is already using.
 *
 * What it provides:
 *  - `role="dialog"` and `aria-modal="true"`, so assistive technology treats
 *    the rest of the page as inert while it is open
 *  - an accessible name taken from the value bound to the selector
 *  - focus moved into the dialog on open, and returned to whatever opened it
 *    on close
 *  - Tab and Shift+Tab cycled within the dialog
 *  - Escape reported through `dismiss`
 */
@Directive({
  selector: '[appAccessibleModal]',
  standalone: true,
  host: {
    'role': 'dialog',
    'aria-modal': 'true',
    // Lets the panel itself hold focus. Without this it is not a focus target
    // and the browser would leave focus behind on the page underneath.
    'tabindex': '-1'
  }
})
export class AccessibleModalDirective implements OnInit, OnDestroy {

  private host = inject(ElementRef<HTMLElement>);

  /** The dialog's accessible name, bound through the selector. */
  @Input('appAccessibleModal') label = '';

  /**
   * Raised on Escape. The directive does not close anything itself — the
   * component owns that decision, and one modal here deliberately asks for
   * confirmation before discarding an edit.
   */
  @Output() dismiss = new EventEmitter<void>();

  /**
   * Where focus was before the dialog opened. Returning focus here on close is
   * what stops a keyboard user from being dropped back at the top of the
   * document after every interaction.
   */
  private previouslyFocused: HTMLElement | null = null;

  private static readonly FOCUSABLE = [
    'a[href]',
    'button:not([disabled])',
    'input:not([disabled]):not([type="hidden"])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    '[tabindex]:not([tabindex="-1"])'
  ].join(',');

  ngOnInit(): void {
    const element = this.element;
    if (this.label) {
      element.setAttribute('aria-label', this.label);
    }

    this.previouslyFocused = document.activeElement as HTMLElement | null;

    // Focus the panel rather than its first control. The first control is
    // sometimes a destructive button — "Delete" on a confirmation dialog — and
    // focusing it invites someone to confirm a thing they have not read yet.
    // Landing on the container announces the dialog's name and leaves the next
    // Tab to the user.
    element.focus({ preventScroll: true });
  }

  ngOnDestroy(): void {
    const target = this.previouslyFocused;
    this.previouslyFocused = null;

    // The trigger may have been removed by the very action that closed the
    // dialog — deleting a row unmounts the button that opened it. Focusing a
    // detached node silently sends focus to <body>, so check first.
    if (target && target.isConnected && typeof target.focus === 'function') {
      target.focus({ preventScroll: true });
    }
  }

  @HostListener('keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      // Stopped here so a dialog opened from within another dialog does not
      // close both at once.
      event.stopPropagation();
      event.preventDefault();
      this.dismiss.emit();
      return;
    }

    if (event.key === 'Tab') {
      this.trapTab(event);
    }
  }

  /**
   * Keeps Tab inside the dialog. Without this, tabbing past the last control
   * walks into the page behind — which is still rendered, still clickable, and
   * from the user's point of view no longer the thing they are in.
   */
  private trapTab(event: KeyboardEvent): void {
    const focusable = this.focusableChildren();

    // Nothing to move between: hold focus on the panel so Tab cannot escape.
    if (focusable.length === 0) {
      event.preventDefault();
      this.element.focus({ preventScroll: true });
      return;
    }

    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const active = document.activeElement;

    if (event.shiftKey) {
      // Shift+Tab from the first control, or from the panel itself, wraps to
      // the end rather than leaving.
      if (active === first || active === this.element) {
        event.preventDefault();
        last.focus();
      }
      return;
    }

    if (active === last) {
      event.preventDefault();
      first.focus();
    }
  }

  /**
   * Focusable descendants in document order.
   *
   * Filtered on explicit intent — `hidden` and `aria-hidden` — rather than on
   * measured layout. Templates here hide content with `*ngIf`, which removes
   * the nodes altogether, so there is nothing present-but-invisible for a
   * geometry check to catch. Asking for geometry would only make the trap
   * depend on the browser having laid the dialog out first, which is not
   * guaranteed at the moment a key is pressed.
   */
  private focusableChildren(): HTMLElement[] {
    const nodes = this.element.querySelectorAll<HTMLElement>(AccessibleModalDirective.FOCUSABLE);
    return Array.from(nodes).filter(el => !el.hidden && !el.closest('[hidden],[aria-hidden="true"]'));
  }

  private get element(): HTMLElement {
    return this.host.nativeElement;
  }
}

