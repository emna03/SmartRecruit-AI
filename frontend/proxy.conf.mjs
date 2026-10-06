// Proxy du serveur de développement : /api -> backend Spring Boot (évite toute configuration CORS en local).
// Cible : variable BACKEND_URL, sinon SERVER_PORT lu dans ../.env, sinon http://localhost:8080.
import { existsSync, readFileSync } from 'node:fs';

function serverPortFromEnvFile() {
  const envFile = new URL('../.env', import.meta.url);
  if (!existsSync(envFile)) {
    return null;
  }
  const line = readFileSync(envFile, 'utf8')
    .split(/\r?\n/)
    .find((l) => l.startsWith('SERVER_PORT='));
  return line ? line.split('=')[1].trim() : null;
}

const target = process.env.BACKEND_URL ?? `http://localhost:${serverPortFromEnvFile() ?? '8080'}`;
console.log(`[proxy] /api -> ${target}`);

export default {
  '/api': { target, secure: false, changeOrigin: true },
};
