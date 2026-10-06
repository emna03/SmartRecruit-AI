import { defineConfig } from '@playwright/test';

/**
 * Tests de bout en bout Angular -> Spring Boot -> MySQL.
 * Prérequis : MySQL et le backend démarrés (le serveur Angular est lancé automatiquement s'il ne tourne pas).
 * Utilise Microsoft Edge installé sur le poste (aucun navigateur à télécharger).
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  outputDir: 'e2e/test-results',
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    channel: process.env['E2E_BROWSER_CHANNEL'] ?? 'msedge',
    headless: true,
    locale: 'fr-FR',
    viewport: { width: 1366, height: 860 },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'npx ng serve --port 4200',
    url: 'http://localhost:4200',
    reuseExistingServer: true,
    timeout: 180_000,
  },
});
