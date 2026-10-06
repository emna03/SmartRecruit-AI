import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, RouterStateSnapshot, UrlTree } from '@angular/router';
import { storeSession } from '../../../testing/auth-fixtures';
import { guestGuard, roleGuard } from './auth.guards';

describe('auth guards', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = (url: string) => ({ url }) as RouterStateSnapshot;

  function setup(): void {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideRouter([])] });
  }

  function run(guard: ReturnType<typeof roleGuard>, url: string) {
    return TestBed.runInInjectionContext(() => guard(route, state(url)));
  }

  beforeEach(() => localStorage.clear());

  it('redirects an anonymous user to /login with returnUrl', () => {
    setup();
    const result = run(roleGuard('CANDIDAT'), '/candidate/profile') as UrlTree;

    expect(result.toString()).toBe('/login?returnUrl=%2Fcandidate%2Fprofile');
  });

  it('lets a user with the right role through', () => {
    storeSession('CANDIDAT');
    setup();

    expect(run(roleGuard('CANDIDAT'), '/candidate/profile')).toBe(true);
  });

  it('sends a user with the wrong role to their own space', () => {
    storeSession('CANDIDAT');
    setup();
    const result = run(roleGuard('RECRUTEUR'), '/recruiter/jobs') as UrlTree;

    expect(result.toString()).toBe('/candidate/dashboard');
  });

  it('treats an expired session as anonymous', () => {
    storeSession('RECRUTEUR', -10);
    setup();
    const result = run(roleGuard('RECRUTEUR'), '/recruiter/jobs') as UrlTree;

    expect(result.toString()).toContain('/login');
  });

  it('keeps logged-in users away from login and register', () => {
    storeSession('RECRUTEUR');
    setup();
    const result = TestBed.runInInjectionContext(() => guestGuard(route, state('/login'))) as UrlTree;

    expect(result.toString()).toBe('/recruiter/dashboard');
  });

  it('allows anonymous users on login and register', () => {
    setup();
    expect(TestBed.runInInjectionContext(() => guestGuard(route, state('/login')))).toBe(true);
  });
});
