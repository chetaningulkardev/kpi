import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { AccessibleModalDirective } from './accessible-modal.directive';

@Component({
  standalone: true,
  imports: [CommonModule, AccessibleModalDirective],
  template: `
    <button id="trigger" (click)="open = true">Open</button>

    <div *ngIf="open" class="backdrop">
      <div appAccessibleModal="Edit KPI" (dismiss)="open = false" id="panel">
        <button id="first">First</button>
        <input id="middle" />
        <button id="last">Last</button>
        <button id="hidden" *ngIf="false">Never rendered</button>
      </div>
    </div>
  `
})
class HostComponent {
  open = false;
}

@Component({
  standalone: true,
  imports: [CommonModule, AccessibleModalDirective],
  template: `
    <div *ngIf="open">
      <div appAccessibleModal="Empty dialog" (dismiss)="open = false" id="panel"></div>
    </div>
  `
})
class EmptyHostComponent {
  open = true;
}

describe('AccessibleModalDirective', () => {

  let fixture: ComponentFixture<HostComponent>;
  let component: HostComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    component = fixture.componentInstance;
    // Attached to the live document so focus actually moves; a detached
    // fixture silently keeps focus on <body> and every assertion here would
    // pass for the wrong reason.
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.nativeElement.remove();
  });

  function el(id: string): HTMLElement {
    return fixture.nativeElement.querySelector(`#${id}`) as HTMLElement;
  }

  function openModal(): void {
    el('trigger').focus();
    component.open = true;
    fixture.detectChanges();
  }

  function press(key: string, shiftKey = false): KeyboardEvent {
    const event = new KeyboardEvent('keydown', { key, shiftKey, bubbles: true, cancelable: true });
    (document.activeElement ?? el('panel')).dispatchEvent(event);
    fixture.detectChanges();
    return event;
  }

  // ── Semantics ───────────────────────────────────────────────────────────

  it('should announce the panel as a modal dialog', () => {
    openModal();
    const panel = el('panel');
    expect(panel.getAttribute('role')).toBe('dialog');
    expect(panel.getAttribute('aria-modal')).toBe('true');
  });

  it('should name the dialog from the bound value', () => {
    openModal();
    expect(el('panel').getAttribute('aria-label')).toBe('Edit KPI');
  });

  // ── Focus ───────────────────────────────────────────────────────────────

  it('should move focus into the dialog when it opens', () => {
    openModal();
    expect(document.activeElement).toBe(el('panel'));
  });

  it('should focus the panel rather than the first button, which may be destructive', () => {
    openModal();
    expect(document.activeElement).not.toBe(el('first'));
  });

  it('should return focus to whatever opened it', () => {
    openModal();
    component.open = false;
    fixture.detectChanges();
    expect(document.activeElement).toBe(el('trigger'));
  });

  it('should not throw when the trigger no longer exists', () => {
    const detached = document.createElement('button');
    document.body.appendChild(detached);
    detached.focus();

    component.open = true;
    fixture.detectChanges();

    detached.remove();

    expect(() => {
      component.open = false;
      fixture.detectChanges();
    }).not.toThrow();
  });

  // ── Keyboard ────────────────────────────────────────────────────────────

  it('should report Escape as a dismissal', () => {
    openModal();
    press('Escape');
    expect(component.open).toBe(false);
  });

  it('should stop Escape from also reaching a dialog underneath', () => {
    openModal();
    const event = press('Escape');
    expect(event.defaultPrevented).toBe(true);
  });

  it('should wrap Tab from the last control back to the first', () => {
    openModal();
    el('last').focus();
    press('Tab');
    expect(document.activeElement).toBe(el('first'));
  });

  it('should wrap Shift+Tab from the first control to the last', () => {
    openModal();
    el('first').focus();
    press('Tab', true);
    expect(document.activeElement).toBe(el('last'));
  });

  it('should send Shift+Tab from the panel itself to the last control', () => {
    openModal();
    expect(document.activeElement).toBe(el('panel'));
    press('Tab', true);
    expect(document.activeElement).toBe(el('last'));
  });

  it('should leave Tab alone in the middle of the dialog', () => {
    openModal();
    el('first').focus();
    const event = press('Tab');
    // The browser's own ordering is correct here; intervening would be worse.
    expect(event.defaultPrevented).toBe(false);
  });

  it('should ignore keys it does not handle', () => {
    openModal();
    const event = press('a');
    expect(event.defaultPrevented).toBe(false);
    expect(component.open).toBe(true);
  });

  it('should hold focus in a dialog that has no controls', async () => {
    await TestBed.resetTestingModule()
      .configureTestingModule({ imports: [EmptyHostComponent] })
      .compileComponents();

    const empty = TestBed.createComponent(EmptyHostComponent);
    document.body.appendChild(empty.nativeElement);
    empty.detectChanges();

    const panel = empty.nativeElement.querySelector('#panel') as HTMLElement;
    expect(document.activeElement).toBe(panel);

    const event = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
    panel.dispatchEvent(event);
    empty.detectChanges();

    expect(event.defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(panel);

    empty.nativeElement.remove();
  });
});

