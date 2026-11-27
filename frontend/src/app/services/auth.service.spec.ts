import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

describe('AuthService token lifecycle', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('stores the replacement refresh token', () => {
    vi.spyOn(service, 'getRefreshTokenFromLocalStorage').mockReturnValue('old-refresh');
    const saveRefresh = vi.spyOn(service, 'setRefreshToken').mockImplementation(() => {});
    vi.spyOn(service, 'setToken').mockImplementation(() => {});
    vi.spyOn(service, 'setCurrentUser').mockImplementation(() => {});
    service.refreshToken().subscribe();
    const request = http.expectOne('/api/auth/refresh-token');
    expect(request.request.headers.get('Authorization')).toBe('Bearer old-refresh');
    request.flush({ data: { access_token: 'new-access', refresh_token: 'new-refresh', user: {} } });
    expect(saveRefresh).toHaveBeenCalledWith('new-refresh');
  });

  it('sends both tokens for revocation on logout', () => {
    vi.spyOn(service, 'getTokenFromLocalStorage').mockReturnValue('access');
    vi.spyOn(service, 'getRefreshTokenFromLocalStorage').mockReturnValue('refresh');
    const clear = vi.spyOn(service, 'clearTokens').mockImplementation(() => {});
    service.logout().subscribe();
    const requests = http.match('/api/auth/logout');
    expect(requests.map(request => request.request.headers.get('Authorization')))
      .toEqual(['Bearer access', 'Bearer refresh']);
    requests.forEach(request => request.flush({ data: 'ok' }));
    expect(clear).toHaveBeenCalled();
  });
});
