import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { storeSession } from '../../../testing/auth-fixtures';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;

  function setup(): void {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  }

  beforeEach(() => localStorage.clear());
  afterEach(() => controller.verify());

  it('adds the Bearer token to backend calls', () => {
    const token = storeSession('CANDIDAT');
    setup();
    http.get('/api/candidate/profile').subscribe();

    const req = controller.expectOne('/api/candidate/profile');
    expect(req.request.headers.get('Authorization')).toBe(`Bearer ${token}`);
    req.flush({});
  });

  it('sends no token when logged out', () => {
    setup();
    http.get('/api/jobs').subscribe();

    const req = controller.expectOne('/api/jobs');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('never sends the token to another origin', () => {
    storeSession('CANDIDAT');
    setup();
    http.get('https://example.com/data').subscribe();

    const req = controller.expectOne('https://example.com/data');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('clears the session and redirects to /login on 401', () => {
    storeSession('CANDIDAT');
    setup();
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    let status = 0;
    http.get('/api/candidate/profile').subscribe({ error: (e) => (status = e.status) });

    controller.expectOne('/api/candidate/profile').flush({ message: 'Expiré' }, { status: 401, statusText: 'Unauthorized' });

    expect(status).toBe(401);
    expect(TestBed.inject(AuthService).isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login'], expect.objectContaining({ queryParams: expect.objectContaining({ expired: 1 }) }));
  });

  it('does not log out on a 401 from login (bad credentials)', () => {
    setup();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');
    http.post('/api/auth/login', {}).subscribe({ error: () => undefined });

    controller.expectOne('/api/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(navigate).not.toHaveBeenCalled();
  });
});
