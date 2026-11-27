import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LoginComponent } from './login.component';
import { AuthService } from '../../services/auth.service';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { isAuthenticated: () => false, currentUserValue: null } }
      ]
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
  });

  afterEach(() => httpTesting.verify());

  it('shows the seeded credentials only when the server enables demo data', () => {
    httpTesting.expectOne('/api/public/demo-credentials').flush({
      enabled: true,
      email: 'john.doe@example.com',
      password: 'Welcome2026!'
    });
    fixture.detectChanges();

    const page = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(page).toContain('john.doe@example.com');
    expect(page).toContain('Welcome2026!');
  });

  it('does not expose demo credentials when demo data is disabled', () => {
    httpTesting.expectOne('/api/public/demo-credentials').flush({ enabled: false });
    fixture.detectChanges();

    const page = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(page).not.toContain('Development demo account');
    expect(page).not.toContain('Welcome2026!');
  });
});
