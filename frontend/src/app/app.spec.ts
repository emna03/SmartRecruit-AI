import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { storeSession } from '../testing/auth-fixtures';
import { App } from './app';

describe('App', () => {
  function create() {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideRouter([])],
    });
    return TestBed.createComponent(App);
  }

  beforeEach(() => localStorage.clear());

  it('shows login and register links to anonymous visitors', async () => {
    const fixture = create();
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('SmartRecruit');
    expect(text).toContain('Connexion');
    expect(text).toContain('Inscription');
  });

  it('shows the user name and logout button when logged in', async () => {
    storeSession('CANDIDAT');
    const fixture = create();
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('[data-testid=navbar-user]')?.textContent).toContain('Alice Martin');
    expect(element.textContent).toContain('Déconnexion');
    expect(element.textContent).not.toContain('Inscription');
  });
});
