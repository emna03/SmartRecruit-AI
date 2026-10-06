import { SESSION_STORAGE_KEY } from '../app/core/auth/auth.service';
import { AuthResponse, Role, User } from '../app/core/models';

/** Faux JWT (signature factice) : seul le champ exp est lu côté frontend. */
export function fakeJwt(expiresInSeconds = 3600): string {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const exp = Math.floor(Date.now() / 1000) + expiresInSeconds;
  return `${encode({ alg: 'HS256' })}.${encode({ sub: '1', exp })}.signature`;
}

export function fakeUser(role: Role = 'CANDIDAT', overrides: Partial<User> = {}): User {
  return {
    id: 1,
    email: 'alice@test.com',
    firstName: 'Alice',
    lastName: 'Martin',
    role,
    enabled: true,
    createdAt: '2026-10-06T10:00:00',
    ...overrides,
  };
}

export function fakeAuthResponse(role: Role = 'CANDIDAT', expiresInSeconds = 3600): AuthResponse {
  return { accessToken: fakeJwt(expiresInSeconds), tokenType: 'Bearer', expiresInMs: expiresInSeconds * 1000, user: fakeUser(role) };
}

/** Enregistre une session dans le localStorage, comme après une connexion. */
export function storeSession(role: Role = 'CANDIDAT', expiresInSeconds = 3600): string {
  const token = fakeJwt(expiresInSeconds);
  localStorage.setItem(
    SESSION_STORAGE_KEY,
    JSON.stringify({ token, user: fakeUser(role), expiresAt: Date.now() + expiresInSeconds * 1000 }),
  );
  return token;
}
