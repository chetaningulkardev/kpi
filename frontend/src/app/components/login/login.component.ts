import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { LoginCredentials, Role } from '../../models/auth.model';
import { isAdminUser, isManagerUser } from '../../services/role-access.util';

interface DemoCredentials {
  enabled: boolean;
  email: string;
  password: string;
}

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {
  loginForm!: FormGroup;
  loading = false;
  submitted = false;
  passwordVisible = false;
  error: string | null = null;
  demoCredentials: DemoCredentials | null = null;
  returnUrl: string = '';
  private hasExplicitReturnUrl = false;

  constructor(
    private formBuilder: FormBuilder,
    private router: Router,
    private activatedRoute: ActivatedRoute,
    private authService: AuthService,
    private http: HttpClient
  ) { }

  ngOnInit(): void {
    // Get return url from:
    // 1. Query parameter (returnUrl)
    // 2. Session storage (redirectUrl from auth guard)
    // 3. Default to dashboard
    const queryReturnUrl = this.activatedRoute.snapshot.queryParams['returnUrl'];
    const sessionRedirectUrl = sessionStorage.getItem('redirectUrl');

    this.hasExplicitReturnUrl = !!(queryReturnUrl || sessionRedirectUrl);
    this.returnUrl = queryReturnUrl || sessionRedirectUrl || this.getDefaultHomeRoute(this.authService.currentUserValue?.roles);

    // Clear the session redirect URL once we've used it
    sessionStorage.removeItem('redirectUrl');

    // Initialize form
    this.loginForm = this.formBuilder.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]]
    });

    this.http.get<DemoCredentials>('/api/public/demo-credentials').subscribe({
      next: (credentials) => this.demoCredentials = credentials.enabled ? credentials : null,
      error: () => this.demoCredentials = null
    });

    // If already logged in then redirect to return url
    if (this.authService.isAuthenticated()) {
      this.router.navigateByUrl(this.returnUrl);
    }
  }

  get f() {
    return this.loginForm.controls;
  }

  onSubmit(): void {
    if (this.loading) return;
    this.submitted = true;
    this.error = null;

    // Return if form is invalid
    if (this.loginForm.invalid) {
      return;
    }

    this.loading = true;
    const credentials: LoginCredentials = {
      email: this.f['email'].value,
      password: this.f['password'].value
    };

    this.authService.login(credentials).subscribe({
      next: (response) => {
        const targetRoute = this.hasExplicitReturnUrl
          ? this.returnUrl
          : this.getDefaultHomeRoute(response.user?.roles);
        sessionStorage.setItem('previousUrl', targetRoute);
        this.router.navigateByUrl(targetRoute);
      },
      error: (error) => {
        this.error = error.error?.error || 'Login failed. Please check your credentials.';
        this.loading = false;
      }
    });
   }

   // noinspection JSUnusedGlobalSymbols - used in template
   // noinspection JSUnusedGlobalSymbols - used in template
   togglePasswordVisibility(): void {
     this.passwordVisible = !this.passwordVisible;
   }

  private getDefaultHomeRoute(roles: Role[] | null | undefined): string {
    if (isAdminUser(roles)) {
      return '/dashboard';
    }
    if (isManagerUser(roles)) {
      return '/kpi/review';
    }
    return '/kpi/my-kpis';
  }
}
