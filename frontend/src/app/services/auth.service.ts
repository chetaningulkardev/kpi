import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, BehaviorSubject, forkJoin, of } from 'rxjs';
import { map, tap, catchError } from 'rxjs/operators';
import { jwtDecode } from 'jwt-decode';
import { LoginCredentials, AuthResponse, User, ChangePasswordRequest, ApiResponse } from '../models/auth.model';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = '/api/auth';
  private currentUserSubject: BehaviorSubject<User | null>;
  public currentUser$: Observable<User | null>;
  private tokenSubject: BehaviorSubject<string | null>;
  public token$: Observable<string | null>;

  constructor(private http: HttpClient) {
    this.currentUserSubject = new BehaviorSubject<User | null>(this.getCurrentUserFromLocalStorage());
    this.currentUser$ = this.currentUserSubject.asObservable();
    this.tokenSubject = new BehaviorSubject<string | null>(this.getTokenFromLocalStorage());
    this.token$ = this.tokenSubject.asObservable();
  }

  private hasStorage(): boolean {
    return typeof globalThis !== 'undefined'
      && !!(globalThis as any).localStorage
      && typeof (globalThis as any).localStorage.getItem === 'function';
  }

  /**
   * Get current user value
   */
  public get currentUserValue(): User | null {
    return this.currentUserSubject.value;
  }

  /**
   * Get current token value
   */
  public get currentTokenValue(): string | null {
    return this.tokenSubject.value;
  }

  /**
   * Login user with email and password
   */
  login(credentials: LoginCredentials): Observable<AuthResponse> {
    return this.http.post<ApiResponse<AuthResponse>>(`${this.apiUrl}/login`, credentials)
      .pipe(
        map(response => response.data),
        tap(response => {
          this.setToken(response.access_token);
          this.setCurrentUser(response.user);
          this.setRefreshToken(response.refresh_token);
        }),
        catchError(error => {
          console.error('Login failed:', error);
          throw error;
        })
      );
  }

  /**
   * Logout user
   */
  logout(): Observable<any> {
    const tokens = [this.getTokenFromLocalStorage(), this.getRefreshTokenFromLocalStorage()]
      .filter((token): token is string => !!token);
    const requests = tokens.length
      ? tokens.map(token => this.http.post<ApiResponse<string>>(`${this.apiUrl}/logout`, {}, {
          headers: new HttpHeaders({ Authorization: `Bearer ${token}` })
        }))
      : [this.http.post<ApiResponse<string>>(`${this.apiUrl}/logout`, {})];
    return forkJoin(requests)
      .pipe(
        tap(() => {
          this.clearTokens();
          this.currentUserSubject.next(null);
        }),
        catchError(() => {
          // Even if the logout request fails, clear local storage
          this.clearTokens();
          this.currentUserSubject.next(null);
          return of(null);
        })
      );
  }

  /**
   * Get current user profile
   */
  getCurrentUserProfile(): Observable<User> {
    return this.http.get<ApiResponse<User>>(`${this.apiUrl}/me`)
      .pipe(
        map(response => response.data),
        tap(user => this.setCurrentUser(user))
      );
  }

  /**
   * Change password
   */
  changePassword(request: ChangePasswordRequest): Observable<any> {
    return this.http.post<ApiResponse<string>>(`${this.apiUrl}/change-password`, request)
      .pipe(
        map(response => response.data)
      );
  }

  /**
   * Refresh access token
   */
  refreshToken(): Observable<AuthResponse> {
    const refreshToken = this.getRefreshTokenFromLocalStorage();
    if (!refreshToken) {
      throw new Error('No refresh token available');
    }

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${refreshToken}`
    });

    return this.http.post<ApiResponse<AuthResponse>>(`${this.apiUrl}/refresh-token`, {}, { headers })
      .pipe(
        map(response => response.data),
        tap(response => {
          this.setToken(response.access_token);
          this.setCurrentUser(response.user);
          this.setRefreshToken(response.refresh_token);
        })
      );
  }

  /**
   * Validate token
   */
  validateToken(token?: string): Observable<boolean> {
    const tokenToValidate = token || this.getTokenFromLocalStorage();
    if (!tokenToValidate) {
      return of(false);
    }

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${tokenToValidate}`
    });

    return this.http.get<ApiResponse<boolean>>(`${this.apiUrl}/validate`, { headers })
      .pipe(
        map(response => response.data),
        catchError(() => of(false))
      );
  }

  /**
   * Check if user is authenticated
   */
  isAuthenticated(): boolean {
    const token = this.getTokenFromLocalStorage();
    if (!token) {
      return false;
    }

    try {
      const decoded: any = jwtDecode(token);
      const currentTime = Date.now() / 1000;
      return decoded.exp > currentTime;
    } catch (error) {
      return false;
    }
  }

  /**
   * Get token from local storage
   */
  getTokenFromLocalStorage(): string | null {
    if (!this.hasStorage()) return null;
    return localStorage.getItem('access_token');
  }

  /**
   * Set token in local storage
   */
  setToken(token: string): void {
    if (this.hasStorage()) {
      localStorage.setItem('access_token', token);
    }
    this.tokenSubject.next(token);
  }

  /**
   * Get refresh token from local storage
   */
  getRefreshTokenFromLocalStorage(): string | null {
    if (!this.hasStorage()) return null;
    return localStorage.getItem('refresh_token');
  }

  /**
   * Set refresh token in local storage
   */
  setRefreshToken(refreshToken: string): void {
    if (this.hasStorage()) {
      localStorage.setItem('refresh_token', refreshToken);
    }
  }

  /**
   * Get current user from local storage
   */
  getCurrentUserFromLocalStorage(): User | null {
    if (!this.hasStorage()) return null;
    const user = localStorage.getItem('current_user');
    if (!user) return null;
    try {
      return JSON.parse(user);
    } catch {
      return null;
    }
  }

  /**
   * Set current user in local storage
   */
  setCurrentUser(user: User): void {
    if (this.hasStorage()) {
      localStorage.setItem('current_user', JSON.stringify(user));
    }
    this.currentUserSubject.next(user);
  }

  /**
   * Clear all tokens and user data
   */
  clearTokens(): void {
    if (this.hasStorage()) {
      localStorage.removeItem('access_token');
      localStorage.removeItem('refresh_token');
      localStorage.removeItem('current_user');
    }
    this.tokenSubject.next(null);
  }

  /**
   * Get token expiration time
   */
  getTokenExpirationTime(): number | null {
    const token = this.getTokenFromLocalStorage();
    if (!token) {
      return null;
    }

    try {
      const decoded: any = jwtDecode(token);
      return decoded.exp * 1000; // Convert to milliseconds
    } catch (error) {
      return null;
    }
  }
}
