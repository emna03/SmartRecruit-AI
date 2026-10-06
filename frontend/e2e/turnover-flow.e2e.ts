import { expect, Page, test } from '@playwright/test';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * ÉTAPE 11 sur la vraie pile : recruteur / admin -> formulaire « Risque de turnover » (Angular) -> Spring Boot
 * -> FastAPI -> modèle ML réellement entraîné sur le dataset IBM -> prédiction affichée.
 * Prérequis : MySQL, backend et service IA démarrés, modèle entraîné (python -m ml.train).
 * Les valeurs attendues viennent du rapport produit par l'entraînement, jamais d'un score écrit dans ce test.
 */
type Features = Record<string, string | number | boolean>;
interface Example { probability: number; riskLevel: string; features: Features }

const report = JSON.parse(readFileSync(join(__dirname, '..', '..', 'ai-service', 'ml', 'reports', 'turnover_evaluation.json'), 'utf8'));
const examples = report.examples as { lowRisk: Example; highRisk: Example };
const suffix = Date.now().toString(36);
const password = 'E2eTest123';
const recruiter = `e2e11-recruteur-${suffix}@test.local`;
const candidate = `e2e11-candidat-${suffix}@test.local`;
const screenshotDir = join(__dirname, 'screenshots');
const disclaimer =
  'Cette prédiction est une estimation produite par un modèle entraîné sur un dataset de démonstration et ne doit pas être utilisée seule pour prendre une décision RH.';

test.describe.configure({ mode: 'serial' });

function envValue(key: string): string {
  const envFile = join(__dirname, '..', '..', '.env');
  const line = existsSync(envFile) ? readFileSync(envFile, 'utf8').split(/\r?\n/).find((l) => l.startsWith(`${key}=`)) : undefined;
  if (!line) {
    throw new Error(`${key} absent du fichier .env`);
  }
  return line.slice(key.length + 1).trim();
}

function percent(value: number): string {
  return `${(value * 100).toFixed(1).replace('.', ',')} %`;
}

async function login(page: Page, email: string, pass: string, space: RegExp): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Mot de passe').fill(pass);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).toHaveURL(space);
}

/** Saisie champ par champ, comme un utilisateur (département d'abord : il filtre les postes). */
async function fill(page: Page, features: Features): Promise<void> {
  const keys = ['department', ...Object.keys(features).filter((k) => k !== 'department')];
  for (const key of keys) {
    const field = page.getByTestId(`t-${key}`);
    const value = features[key];
    if ((await field.evaluate((e) => e.tagName)) === 'SELECT') {
      const values = await field.locator('option').evaluateAll((options) => options.map((o) => (o as HTMLOptionElement).value));
      const match = values.find((v) => v.endsWith(`: ${value}`));
      expect(match, `option ${value} pour ${key}`).toBeDefined();
      await field.selectOption(match!);
    } else {
      await field.fill(String(value));
    }
  }
}

/** Lance l'estimation et attend la vraie réponse de l'API (Spring Boot -> FastAPI). */
async function estimate(page: Page, expectedBody: Features): Promise<void> {
  const request = page.waitForRequest((r) => r.url().endsWith('/api/hr/turnover/predictions'));
  const response = page.waitForResponse((r) => r.url().endsWith('/api/hr/turnover/predictions'));
  await page.getByTestId('turnover-submit').click();
  expect((await request).postDataJSON()).toEqual(expectedBody);
  expect((await response).status()).toBe(200);
}

test.beforeAll(async ({ request }) => {
  for (const [email, role] of [[recruiter, 'RECRUTEUR'], [candidate, 'CANDIDAT']]) {
    const response = await request.post('/api/auth/register', { data: { email, password, firstName: 'Test', lastName: 'E2E', role } });
    expect(response.status()).toBe(201);
  }
});

test.afterAll(async ({ request }) => {
  const login = await request.post('/api/auth/login', { data: { email: envValue('ADMIN_EMAIL'), password: envValue('ADMIN_PASSWORD') } });
  const headers = { Authorization: `Bearer ${(await login.json()).accessToken}` };
  const users = await (await request.get('/api/admin/users', { headers })).json();
  for (const user of users.filter((u: { email: string }) => u.email.startsWith('e2e11-'))) {
    await request.delete(`/api/admin/users/${user.id}`, { headers });
  }
});

test('T1 le recruteur saisit un profil et obtient la prédiction réelle du modèle (risque élevé)', async ({ page }) => {
  await login(page, recruiter, password, /\/recruiter\/dashboard$/);
  await page.getByRole('link', { name: 'Risque de turnover' }).first().click();
  await expect(page).toHaveURL(/\/recruiter\/turnover$/);
  await expect(page.getByTestId('turnover-disclaimer')).toHaveText(disclaimer);

  await page.getByTestId('turnover-submit').click();
  await expect(page.getByTestId('turnover-error')).toContainText('Complétez ou corrigez');

  const high = examples.highRisk;
  await fill(page, high.features);
  await estimate(page, high.features);

  const result = page.getByTestId('turnover-result');
  await expect(result).toHaveAttribute('data-level', 'ELEVE');
  await expect(page.getByTestId('turnover-level')).toHaveText('Risque élevé');
  await expect(page.getByTestId('turnover-probability')).toHaveText(percent(high.probability));
  await expect(page.getByTestId('turnover-score-explanation')).toContainText('pas une certitude');
  const factors = page.getByTestId('turnover-factor');
  expect(await factors.count()).toBeGreaterThanOrEqual(1);
  expect(await factors.count()).toBeLessThanOrEqual(5);
  const chosen = report.selection.chosenModel as string;
  const metrics = report.test.results[chosen];
  await expect(page.getByTestId('turnover-model')).toContainText(`ROC-AUC ${metrics.rocAuc.toFixed(2).replace('.', ',')}`);
  await expect(page.getByTestId('turnover-model')).toContainText('IBM HR Analytics Employee Attrition & Performance');
  await expect(page.getByTestId('turnover-result-disclaimer')).toHaveText(disclaimer);
  await page.screenshot({ path: join(screenshotDir, '19-recruteur-risque-turnover-eleve.png'), fullPage: true });

  // Saisie incohérente : bloquée dans Angular, aucun appel.
  await page.getByTestId('t-totalWorkingYears').fill('0');
  await page.getByTestId('t-yearsAtCompany').fill('5');
  let called = false;
  page.on('request', (r) => { if (r.url().endsWith('/api/hr/turnover/predictions')) called = true; });
  await page.getByTestId('turnover-submit').click();
  await expect(page.getByTestId('turnover-consistency')).toContainText("ne peut pas dépasser l'expérience totale");
  expect(called).toBe(false);
});

test("T2 l'administrateur utilise un exemple fictif du jeu de test (risque faible)", async ({ page }) => {
  await login(page, envValue('ADMIN_EMAIL'), envValue('ADMIN_PASSWORD'), /\/admin\/dashboard$/);
  await page.getByRole('link', { name: 'Risque de turnover' }).first().click();
  await expect(page).toHaveURL(/\/admin\/turnover$/);

  await page.getByTestId('turnover-example-1').click();
  const low = examples.lowRisk;
  await estimate(page, low.features);

  await expect(page.getByTestId('turnover-result')).toHaveAttribute('data-level', 'FAIBLE');
  await expect(page.getByTestId('turnover-level')).toHaveText('Risque faible');
  await expect(page.getByTestId('turnover-probability')).toHaveText(percent(low.probability));
  await page.screenshot({ path: join(screenshotDir, '20-admin-risque-turnover-faible.png'), fullPage: true });
});

test("T3 un candidat n'a pas accès à l'écran ni à l'API", async ({ page, request }) => {
  await login(page, candidate, password, /\/candidate\/dashboard$/);
  await expect(page.getByRole('link', { name: 'Risque de turnover' })).toHaveCount(0);
  await page.goto('/recruiter/turnover');
  await expect(page).not.toHaveURL(/\/recruiter\/turnover$/);

  const token = (await (await request.post('/api/auth/login', { data: { email: candidate, password } })).json()).accessToken;
  const response = await request.post('/api/hr/turnover/predictions', { headers: { Authorization: `Bearer ${token}` }, data: examples.lowRisk.features });
  expect(response.status()).toBe(403);
});
