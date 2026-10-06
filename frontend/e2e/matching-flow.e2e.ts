import { APIRequestContext, expect, Page, test } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * ÉTAPE 9 sur la vraie pile : candidat -> CV -> offre -> FastAPI (matching) -> score -> MySQL -> Angular,
 * puis classement côté recruteur.
 * Prérequis : MySQL (conteneur smartrecruit-mysql), backend et service IA démarrés.
 * CV fictifs de ai-service/tests/fixtures/cv ; comptes supprimés à la fin via l'API admin (sauf E2E_KEEP_DATA=1).
 */
const suffix = Date.now().toString(36);
const password = 'E2eTest123';
const emails = {
  recruiter: `e2e9-recruteur-${suffix}@test.local`,
  fr: `e2e9-camille-${suffix}@test.local`,
  en: `e2e9-alex-${suffix}@test.local`,
  min: `e2e9-sami-${suffix}@test.local`,
  nocv: `e2e9-nora-${suffix}@test.local`,
};
const fixtures = join(__dirname, '..', '..', 'ai-service', 'tests', 'fixtures', 'cv');
const screenshotDir = join(__dirname, 'screenshots');
let offerId = 0;

test.describe.configure({ mode: 'serial' });

function envValue(key: string): string {
  const envFile = join(__dirname, '..', '..', '.env');
  const line = existsSync(envFile) ? readFileSync(envFile, 'utf8').split(/\r?\n/).find((l) => l.startsWith(`${key}=`)) : undefined;
  if (!line) {
    throw new Error(`${key} absent du fichier .env`);
  }
  return line.slice(key.length + 1).trim();
}

/** Requête SQL dans le conteneur MySQL (mot de passe transmis par variable d'environnement, jamais affiché). */
function mysql(query: string): string[][] {
  const output = execFileSync(
    'docker',
    ['exec', '-e', 'MYSQL_PWD', 'smartrecruit-mysql', 'mysql', '-u', envValue('DB_USER'), '-D', envValue('DB_NAME'),
      '--batch', '--skip-column-names', '--default-character-set=utf8mb4', '-e', query],
    { encoding: 'utf8', env: { ...process.env, MYSQL_PWD: envValue('DB_PASSWORD') } },
  );
  return output.split(/\r?\n/).filter((line) => line.length > 0).map((line) => line.split('\t'));
}

async function register(request: APIRequestContext, email: string, firstName: string, role: string): Promise<string> {
  const response = await request.post('/api/auth/register', { data: { email, password, firstName, lastName: 'E2E', role } });
  expect(response.status()).toBe(201);
  return (await response.json()).accessToken;
}

async function login(page: Page, email: string, home: RegExp): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Mot de passe').fill(password);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).toHaveURL(home);
}

/** Candidat préparé par l'API : CV fictif analysé par le CV Parser, puis candidature à l'offre. */
async function candidateWithCvApplies(request: APIRequestContext, email: string, firstName: string, cv: string): Promise<void> {
  const headers = { Authorization: `Bearer ${await register(request, email, firstName, 'CANDIDAT')}` };
  const upload = await request.post('/api/candidate/cv', {
    headers,
    multipart: { file: { name: cv, mimeType: 'application/pdf', buffer: readFileSync(join(fixtures, cv)) } },
  });
  expect((await upload.json()).parsingStatus).toBe('TERMINE');
  expect((await request.post('/api/candidate/applications', { headers, data: { jobOfferId: offerId } })).status()).toBe(201);
}

function scoreSql(email: string, columns: string): string {
  return `SELECT ${columns} FROM matching_results m JOIN candidate_profiles p ON p.id = m.candidate_id
    JOIN users u ON u.id = p.user_id WHERE u.email = '${email}' AND m.job_offer_id = ${offerId}`;
}

test.beforeAll(async ({ request }) => {
  const headers = { Authorization: `Bearer ${await register(request, emails.recruiter, 'Rania', 'RECRUTEUR')}` };
  expect((await request.post('/api/recruiter/company', { headers, data: { name: `E2E9 Corp ${suffix}`, sector: 'IT', location: 'Tunis' } })).status()).toBe(201);
  const offer = await request.post('/api/recruiter/jobs', {
    headers,
    data: {
      title: `Développeur Full Stack Java ${suffix}`, description: 'Offre de test E2E étape 9 (matching).', location: 'Tunis',
      contractType: 'CDI', minExperienceYears: 3, educationLevel: 'BAC_PLUS_5', status: 'PUBLIEE',
      requiredSkills: ['Java', 'Spring Boot', 'Angular', 'MySQL', 'Docker', 'Kubernetes'],
    },
  });
  expect(offer.status()).toBe(201);
  offerId = (await offer.json()).id;
  await register(request, emails.fr, 'Camille', 'CANDIDAT');
  await register(request, emails.nocv, 'Nora', 'CANDIDAT');
});

test.afterAll(async ({ request }) => {
  if (process.env['E2E_KEEP_DATA'] === '1') {
    return;
  }
  const login = await request.post('/api/auth/login', { data: { email: envValue('ADMIN_EMAIL'), password: envValue('ADMIN_PASSWORD') } });
  const headers = { Authorization: `Bearer ${(await login.json()).accessToken}` };
  if (offerId) {
    await request.delete(`/api/admin/jobs/${offerId}`, { headers });
  }
  const users = await (await request.get('/api/admin/users', { headers })).json();
  for (const user of users.filter((u: { email: string }) => u.email.startsWith('e2e9-'))) {
    await request.delete(`/api/admin/users/${user.id}`, { headers });
  }
});

test('M1 sans CV, le candidat est invité à importer son CV', async ({ page }) => {
  await login(page, emails.nocv, /\/candidate\/dashboard$/);
  await page.goto(`/candidate/jobs/${offerId}`);
  await expect(page.getByTestId('matching-status')).toContainText('Importez votre CV');
  await expect(page.getByTestId('matching-status').getByRole('link', { name: 'Importer mon CV' })).toHaveAttribute('href', '/candidate/cv');
  await expect(page.getByTestId('matching-result')).toHaveCount(0);
});

test('M2 le candidat importe son CV puis voit son score de compatibilité expliqué', async ({ page }) => {
  await login(page, emails.fr, /\/candidate\/dashboard$/);
  await page.goto('/candidate/cv');
  await page.getByTestId('cv-file-input').setInputFiles(join(fixtures, 'cv_dev_fullstack_fr.pdf'));
  await expect(page.getByTestId('cv-status')).toHaveText(/CV analysé/);

  const matching = page.waitForResponse((r) => r.url().endsWith(`/api/candidate/jobs/${offerId}/matching`));
  await page.goto(`/candidate/jobs/${offerId}`);
  expect((await matching).status()).toBe(200);

  await expect(page.getByTestId('matching-score')).toHaveText(/90\s*%/);
  await expect(page.getByTestId('matching-level')).toHaveText('Compatibilité élevée');
  await expect(page.getByTestId('subscore-skills')).toContainText('83,3 %');
  await expect(page.getByTestId('subscore-experience')).toContainText('100 %');
  await expect(page.getByTestId('subscore-education')).toContainText('100 %');
  for (const skill of ['Java', 'Spring Boot', 'Angular', 'MySQL', 'Docker']) {
    await expect(page.getByTestId('matched-skills').getByText(skill, { exact: true })).toBeVisible();
  }
  await expect(page.getByTestId('missing-skills')).toHaveText(/^\s*Kubernetes\s*$/);
  await expect(page.getByTestId('matching-explanation')).toContainText('Score global : 90 % — compatibilité élevée.');
  await expect(page.getByTestId('matching-explanation')).toContainText("4 ans et 2 mois d'expérience détectée(s) dans le CV pour 3 ans demandé(s)");
  await expect(page.getByTestId('matching-disclaimer')).toContainText('ne constitue pas une décision de recrutement');
  await expect(page.getByTestId('matching-meta')).toContainText('Algorithme v1.0.0');
  await page.getByTestId('matching-card').screenshot({ path: join(screenshotDir, '14-candidat-score-compatibilite.png') });

  await page.getByTestId('apply-open').click();
  await page.getByTestId('apply-submit').click();
  await expect(page.getByTestId('already-applied')).toBeVisible();
});

test('M3 le score est enregistré dans MySQL avec ses sous-scores et son explication', async () => {
  const [row] = mysql(scoreSql(emails.fr,
    "m.score, m.skills_score, m.experience_score, m.education_score, m.algorithm_version, JSON_LENGTH(m.matched_skills), " +
    "m.missing_skills, LEFT(m.explanation, 42), m.computed_at IS NOT NULL"));
  expect(row).toEqual(['90.00', '83.30', '100.00', '100.00', '1.0.0', '5', '["Kubernetes"]',
    'Score global : 90 % — compatibilité élevée', '1']);
});

test('M4 le recruteur consulte les candidats classés du meilleur score au moins bon', async ({ page, request }) => {
  await candidateWithCvApplies(request, emails.min, 'Sami', 'cv_minimal_fr.pdf');
  await candidateWithCvApplies(request, emails.en, 'Alex', 'cv_data_scientist_en.pdf');
  expect(mysql(`SELECT m.score FROM matching_results m WHERE m.job_offer_id = ${offerId} ORDER BY m.score DESC`))
    .toEqual([['90.00'], ['55.00'], ['19.00']]);

  await login(page, emails.recruiter, /\/recruiter\/dashboard$/);
  await page.goto(`/recruiter/jobs/${offerId}/candidates`);
  const cards = page.getByTestId('candidate-card');
  await expect(cards).toHaveCount(3);
  await expect(cards.nth(0)).toContainText('Camille');
  await expect(cards.nth(0).getByTestId('candidate-rank')).toHaveText('#1');
  await expect(cards.nth(0).getByTestId('match-score')).toHaveText(/90\s*%/);
  await expect(cards.nth(0).getByTestId('candidate-missing')).toContainText('Kubernetes');
  await expect(cards.nth(1)).toContainText('Alex');
  await expect(cards.nth(1).getByTestId('match-score')).toHaveText(/55\s*%/);
  await expect(cards.nth(2)).toContainText('Sami');
  await expect(cards.nth(2).getByTestId('match-score')).toHaveText(/19\s*%/);
  await expect(page.getByTestId('ranking-disclaimer')).toContainText('aide à la décision');
  await page.screenshot({ path: join(screenshotDir, '15-recruteur-classement-scores.png'), fullPage: true });

  const refresh = page.waitForResponse((r) => r.url().endsWith(`/api/recruiter/jobs/${offerId}/matching`));
  await page.getByTestId('refresh-scores').click();
  expect((await refresh).status()).toBe(200);
  await expect(page.getByText('3 score(s) recalculé(s).')).toBeVisible();
  await expect(cards.nth(0)).toContainText('Camille');

  await cards.nth(1).getByRole('link', { name: 'Profil et détail du score' }).click();
  const detail = page.getByTestId('application-matching');
  await expect(detail.getByTestId('matching-score')).toHaveText(/55\s*%/);
  await expect(detail.getByTestId('related-skills')).toContainText('MySQL (CV : PostgreSQL)');
  await expect(detail.getByTestId('missing-skills')).toContainText('Spring Boot');
  await expect(detail.getByTestId('matching-disclaimer')).toContainText('Aide à la décision uniquement');
  await detail.screenshot({ path: join(screenshotDir, '16-recruteur-detail-score.png') });
});
