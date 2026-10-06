import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { map, Observable, tap } from 'rxjs';
import { API_URL } from '../api.config';
import { AuthResponse, LoginRequest, RegisterRequest, Role, User } from '../models';

interface Session {
  token: string;
  user: User;
  /** Expiration du jeton (ms depuis l'epoch). */
  expiresAt: number;
}

export const SESSION_STORAGE_KEY = 'smartrecruit.session';

const HOME_BY_ROLE: Record<Role, string> = {
  ADMIN: '/admin/dashboard',
  RECRUTEUR: '/recruiter/dashboard',
  CANDIDAT: '/candidate/dashboard',
};

/** Lit le champ exp (secondes) d'un JWT, sans vérifier la signature (le backend s'en charge). */
export function jwtExpiration(token: string): number | null {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const exp = JSON.parse(atob(payload)).exp;
    return typeof exp === 'number' ? exp * 1000 : null;
  } catch {
    return null;
  }
}

/**
 * Session de l'utilisateur connecté : jeton JWT + utilisateur, conservés dans le localStorage
 * pour survivre au rechargement de la page. Le mot de passe n'est jamais stocké.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(this.restore());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly role = computed(() => this.user()?.role ?? null);
  readonly isAuthenticated = computed(() => this.session() !== null);

  /** Jeton valide, ou null (une session expirée est effacée). */
  get token(): string | null {
    const session = this.session();
    if (!session) {
      return null;
    }
    if (Date.now() >= session.expiresAt) {
      this.clearSession();
      return null;
    }
    return session.token;
  }

  isLoggedIn(): boolean {
    return this.token !== null;
  }

  login(request: LoginRequest): Observable<User> {
    return this.http.post<AuthResponse>(`${API_URL}/auth/login`, request).pipe(map((r) => this.store(r)));
  }

  register(request: RegisterRequest): Observable<User> {
    return this.http.post<AuthResponse>(`${API_URL}/auth/register`, request).pipe(map((r) => this.store(r)));
  }

  /** Recharge l'utilisateur depuis le backend (ex. après modification du nom dans le profil). */
  refreshUser(): Observable<User> {
    return this.http.get<User>(`${API_URL}/auth/me`).pipe(
      tap((user) => this.session.update((s) => (s ? { ...s, user } : s))),
      tap(() => this.persist()),
    );
  }

  logout(): void {
    this.clearSession();
    void this.router.navigateByUrl('/');
  }

  clearSession(): void {
    this.session.set(null);
    localStorage.removeItem(SESSION_STORAGE_KEY);
  }

  homeFor(role: Role | null = this.role()): string {
    return role ? HOME_BY_ROLE[role] : '/';
  }

  private store(response: AuthResponse): User {
    const expiresAt = jwtExpiration(response.accessToken) ?? Date.now() + response.expiresInMs;
    this.session.set({ token: response.accessToken, user: response.user, expiresAt });
    this.persist();
    return response.user;
  }

  private persist(): void {
    const session = this.session();
    if (session) {
      localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
    }
  }

  private restore(): Session | null {
    try {
      const raw = localStorage.getItem(SESSION_STORAGE_KEY);
      if (!raw) {
        return null;
      }
      const session = JSON.parse(raw) as Session;
      if (!session.token || !session.user?.role || !(session.expiresAt > Date.now())) {
        localStorage.removeItem(SESSION_STORAGE_KEY);
        return null;
      }
      return session;
    } catch {
      localStorage.removeItem(SESSION_STORAGE_KEY);
      return null;
    }
  }
}
