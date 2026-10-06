import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { fakeAuthResponse } from '../../../testing/auth-fixtures';
import { RegisterPage } from './register-page';

describe('RegisterPage', () => {
  let http: HttpTestingController;

  function create() {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [RegisterPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(RegisterPage);
    return { fixture, page: fixture.componentInstance };
  }

  function fill(page: RegisterPage, overrides: Record<string, string> = {}) {
    page.form.patchValue({
      firstName: 'Alice',
      lastName: 'Martin',
      email: 'alice@test.com',
      password: 'Passw0rd',
      confirmPassword: 'Passw0rd',
      ...overrides,
    });
  }

  afterEach(() => http.verify());

  it('applies the same password rules as the backend', () => {
    const { page } = create();
    fill(page, { password: 'court1', confirmPassword: 'court1' });
    expect(page.form.controls.password.hasError('minlength')).toBe(true);

    fill(page, { password: 'sanschiffre', confirmPassword: 'sanschiffre' });
    expect(page.form.controls.password.hasError('pattern')).toBe(true);

    fill(page);
    expect(page.form.valid).toBe(true);
  });

  it('requires matching passwords', () => {
    const { page } = create();
    fill(page, { confirmPassword: 'Different1' });

    expect(page.form.controls.confirmPassword.hasError('mismatch')).toBe(true);
    expect(page.form.valid).toBe(false);
  });

  it('does not call the API when the form is invalid and shows the errors', async () => {
    const { page, fixture } = create();
    await fixture.whenStable();
    page.submit();
    await fixture.whenStable();

    http.expectNone('/api/auth/register');
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.invalid-feedback').length).toBeGreaterThan(0);
  });

  it('registers a recruiter and redirects to the company page', () => {
    const { page } = create();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fill(page);
    page.form.controls.role.setValue('RECRUTEUR');
    page.submit();

    const req = http.expectOne('/api/auth/register');
    expect(req.request.body).toEqual({
      role: 'RECRUTEUR',
      firstName: 'Alice',
      lastName: 'Martin',
      email: 'alice@test.com',
      password: 'Passw0rd',
    });
    req.flush(fakeAuthResponse('RECRUTEUR'));
    expect(navigate).toHaveBeenCalledWith('/recruiter/company');
  });

  it('shows the backend error (email already used)', async () => {
    const { page, fixture } = create();
    fill(page);
    page.submit();
    http.expectOne('/api/auth/register').flush(
      { status: 409, message: 'Un compte existe déjà avec cet email' },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();

    expect(page.loading()).toBe(false);
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid=register-error]')?.textContent).toContain(
      'Un compte existe déjà',
    );
  });
});
