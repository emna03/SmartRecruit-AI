import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { fakeAuthResponse, fakeJwt, fakeUser, storeSession } from '../../../testing/auth-fixtures';
import { AuthService, jwtExpiration, SESSION_STORAGE_KEY } from './auth.service';

describe('AuthService', () => {
  let http: HttpTestingController;

  function create(): AuthService {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(AuthService);
  }

  beforeEach(() => localStorage.clear());
  afterEach(() => http.verify());

  it('stores the session after login and exposes user and role', () => {
    const service = create();
    let loggedRole: string | undefined;
    service.login({ email: 'alice@test.com', password: 'Passw0rd!' }).subscribe((u) => (loggedRole = u.role));

    const req = http.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'alice@test.com', password: 'Passw0rd!' });
    req.flush(fakeAuthResponse('RECRUTEUR'));

    expect(loggedRole).toBe('RECRUTEUR');
    expect(service.isAuthenticated()).toBe(true);
    expect(service.role()).toBe('RECRUTEUR');
    expect(service.token).toBeTruthy();
    expect(JSON.parse(localStorage.getItem(SESSION_STORAGE_KEY)!).user.email).toBe('alice@test.com');
  });

  it('never stores the password', () => {
    const service = create();
    service.register({ email: 'a@b.com', password: 'Secret123', firstName: 'A', lastName: 'B', role: 'CANDIDAT' }).subscribe();
    http.expectOne('/api/auth/register').flush(fakeAuthResponse('CANDIDAT'));

    expect(localStorage.getItem(SESSION_STORAGE_KEY)).not.toContain('Secret123');
  });

  it('restores a valid session from localStorage', () => {
    storeSession('CANDIDAT');
    const service = create();

    expect(service.isLoggedIn()).toBe(true);
    expect(service.user()?.firstName).toBe('Alice');
  });

  it('ignores and removes an expired session', () => {
    storeSession('CANDIDAT', -60);
    const service = create();

    expect(service.isLoggedIn()).toBe(false);
    expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('ignores a corrupted session', () => {
    localStorage.setItem(SESSION_STORAGE_KEY, '{not json');
    const service = create();

    expect(service.isAuthenticated()).toBe(false);
  });

  it('clears the session on logout', () => {
    storeSession('ADMIN');
    const service = create();
    service.logout();

    expect(service.isAuthenticated()).toBe(false);
    expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('refreshes the user (e.g. after a name change)', () => {
    storeSession('CANDIDAT');
    const service = create();
    service.refreshUser().subscribe();
    http.expectOne('/api/auth/me').flush(fakeUser('CANDIDAT', { firstName: 'Alicia' }));

    expect(service.user()?.firstName).toBe('Alicia');
    expect(JSON.parse(localStorage.getItem(SESSION_STORAGE_KEY)!).user.firstName).toBe('Alicia');
  });

  it('returns the home page of each role', () => {
    const service = create();
    expect(service.homeFor('CANDIDAT')).toBe('/candidate/dashboard');
    expect(service.homeFor('RECRUTEUR')).toBe('/recruiter/dashboard');
    expect(service.homeFor('ADMIN')).toBe('/admin/dashboard');
    expect(service.homeFor(null)).toBe('/');
  });

  it('reads the JWT expiration', () => {
    create();
    const exp = jwtExpiration(fakeJwt(120))!;
    expect(exp).toBeGreaterThan(Date.now() + 100_000);
    expect(jwtExpiration('not-a-jwt')).toBeNull();
  });
});
