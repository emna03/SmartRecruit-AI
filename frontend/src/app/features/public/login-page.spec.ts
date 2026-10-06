import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { fakeAuthResponse } from '../../../testing/auth-fixtures';
import { LoginPage } from './login-page';

describe('LoginPage', () => {
  let http: HttpTestingController;

  function create(returnUrl?: string) {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(LoginPage);
    if (returnUrl) {
      fixture.componentRef.setInput('returnUrl', returnUrl);
    }
    return { fixture, page: fixture.componentInstance };
  }

  afterEach(() => http.verify());

  it('redirects to the space of the logged-in role', () => {
    const { page } = create();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    page.form.setValue({ email: 'bob@acme.com', password: 'Passw0rd' });
    page.submit();

    http.expectOne('/api/auth/login').flush(fakeAuthResponse('RECRUTEUR'));
    expect(navigate).toHaveBeenCalledWith('/recruiter/dashboard');
  });

  it('goes back to the returnUrl after login', () => {
    const { page } = create('/jobs/42');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    page.form.setValue({ email: 'alice@test.com', password: 'Passw0rd' });
    page.submit();

    http.expectOne('/api/auth/login').flush(fakeAuthResponse('CANDIDAT'));
    expect(navigate).toHaveBeenCalledWith('/jobs/42');
  });

  it('displays the error message on bad credentials', async () => {
    const { page, fixture } = create();
    page.form.setValue({ email: 'alice@test.com', password: 'mauvais' });
    page.submit();
    expect(page.loading()).toBe(true);

    http.expectOne('/api/auth/login').flush(
      { status: 401, message: 'Email ou mot de passe incorrect' },
      { status: 401, statusText: 'Unauthorized' },
    );
    await fixture.whenStable();

    expect(page.loading()).toBe(false);
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid=login-error]')?.textContent).toContain(
      'Email ou mot de passe incorrect',
    );
  });

  it('validates the email before calling the API', () => {
    const { page } = create();
    page.form.setValue({ email: 'pas-un-email', password: 'x' });
    page.submit();

    http.expectNone('/api/auth/login');
    expect(page.form.controls.email.hasError('email')).toBe(true);
  });
});
