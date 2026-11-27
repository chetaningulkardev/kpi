import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { AuthService } from './services/auth.service';
import { UiGuardService } from './services/ui-guard.service';
import { ConfigurationService } from './services/configuration.service';
import { NotificationService } from './services/notification.service';
import { PermissionsService } from './services/permissions.service';
import { DraftStorageService } from './services/draft-storage.service';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render app shell', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    // NOTE: no `whenStable()` here — App starts a repeating notification poll
    // (`timer(0, 30_000)`), so the zone never becomes stable.
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('router-outlet')).toBeTruthy();
  });

  describe('outside-click handling', () => {
    // A menu that closes on the very click that opened it looks broken rather
    // than closed, so these pin the boundary between "inside" and "outside".
    function clickOn(element: Element): MouseEvent {
      const event = new MouseEvent('click', { bubbles: true });
      element.dispatchEvent(event);
      return event;
    }

    it('keeps a menu open when the click came from inside a menu container', () => {
      const fixture = TestBed.createComponent(App);
      const app = fixture.componentInstance;

      const container = document.createElement('div');
      container.setAttribute('data-menu-root', '');
      const button = document.createElement('button');
      container.appendChild(button);
      document.body.appendChild(container);

      app.perfMenuOpen = true;
      app.onDocumentClick(clickOn(button));

      expect(app.perfMenuOpen).toBe(true);
      container.remove();
    });

    it('closes menus when the click landed outside every menu', () => {
      const fixture = TestBed.createComponent(App);
      const app = fixture.componentInstance;

      const outside = document.createElement('div');
      document.body.appendChild(outside);

      app.perfMenuOpen = true;
      app.userMenuOpen = true;
      app.onDocumentClick(clickOn(outside));

      expect(app.perfMenuOpen).toBe(false);
      expect(app.userMenuOpen).toBe(false);
      outside.remove();
    });

    it('leaves the mobile panel open while expanding one of its sections', () => {
      const fixture = TestBed.createComponent(App);
      const app = fixture.componentInstance;
      fixture.detectChanges();

      app.mobileMenuOpen = true;
      app.mobileOrgOpen = false;
      fixture.detectChanges();

      const sectionToggle = (fixture.nativeElement as HTMLElement)
        .querySelector('[data-menu-root] button');

      app.toggleMobileSection('org');
      if (sectionToggle) {
        app.onDocumentClick(clickOn(sectionToggle));
      }

      expect(app.mobileMenuOpen).toBe(true);
      expect(app.mobileOrgOpen).toBe(true);
    });
  });

  describe('dropdown panels are not clipped by an ancestor', () => {
    // An `overflow` value other than `visible` on any ancestor turns that
    // ancestor into a clipping/scroll container, and the absolutely positioned
    // panels hang below the 4rem header — so they open but are never painted.
    // The component state looks correct in that case, which is exactly why this
    // is asserted against the DOM rather than against the boolean flags.
    function renderLoggedIn() {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        imports: [App],
        providers: [
          // The header only renders when logged in, and it is full of
          // `routerLink`s, so a router must be available here.
          provideRouter([]),
          {
            provide: AuthService,
            useValue: {
              currentUser$: of(null),
              currentUserValue: null,
              isAuthenticated: () => true,
              logout: () => of(null),
            },
          },
          { provide: UiGuardService, useValue: { isBlocking$: of(false) } },
          { provide: ConfigurationService, useValue: { getPendingReviews: () => of([]) } },
          {
            provide: NotificationService,
            useValue: {
              getUnreadCount: () => of(0),
              getNotifications: () => of({ items: [] }),
              markRead: () => of(null),
              markAllRead: () => of(null),
            },
          },
          {
            provide: PermissionsService,
            useValue: { permissions: () => null, load: () => of(null), clear: () => {} },
          },
          { provide: DraftStorageService, useValue: { discardAllForCurrentUser: () => {} } },
        ],
      });

      const fixture = TestBed.createComponent(App);
      fixture.detectChanges();
      return fixture;
    }

    it('renders the Performance panel with no clipping ancestor inside the header', () => {
      const fixture = renderLoggedIn();
      const app = fixture.componentInstance;

      app.perfMenuOpen = true;
      fixture.detectChanges();

      const host = fixture.nativeElement as HTMLElement;
      const panel = host.querySelector('nav .absolute');
      expect(panel).toBeTruthy();

      const clipping: string[] = [];
      for (let el = panel!.parentElement; el && el.tagName !== 'HEADER'; el = el.parentElement) {
        const offending = Array.from(el.classList).filter(c => c.startsWith('overflow-'));
        if (offending.length > 0) {
          clipping.push(`${el.tagName.toLowerCase()}: ${offending.join(' ')}`);
        }
      }

      expect(clipping).toEqual([]);
    });
  });
});
