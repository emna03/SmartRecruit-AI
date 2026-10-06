import { APIRequestContext, expect, Locator, Page, test } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * ÉTAPE 12 sur la vraie pile : données créées par l'API (CV fictif analysé par le CV Parser, scores de matching
 * calculés par FastAPI) -> MySQL -> endpoints de statistiques -> dashboards Angular. Chaque chiffre affiché est
 * comparé à une requête SQL exécutée dans le conteneur MySQL au même moment.
 * Prérequis : MySQL (conteneur smartrecruit-mysql), backend et service IA démarrés.
 * Comptes et entreprises de test supprimés à la fin via l'API admin (sauf E2E_KEEP_DATA=1).
 */
const suffix = Date.now().toString(36);
const password = 'E2eTest123';
const emails = {
  recA: `e2e12-recruteur-a-${suffix}@test.local`,
  recB: `e2e12-recruteur-b-${suffix}@test.local`,
  fr: `e2e12-camille-${suffix}@test.local`,
  nocv: `e2e12-nora-${suffix}@test.local`,
};
const companies = { a: `E2E12 Alpha ${suffix}`, b: `E2E12 Beta ${suffix}` };
const titles = { java: `Full Stack Java ${suffix}`, data: `Data Engineer ${suffix}`, beta: `DevOps Beta ${suffix}` };
const fixtures = join(__dirname, '..', '..', 'ai-service', 'tests', 'fixtures', 'cv');
const screenshotDir = join(__dirname, 'screenshots');
const statuses = ['EN_ATTENTE', 'PRESELECTIONNE', 'ACCEPTE', 'REFUSE'];
const offers: Record<string, number> = {};
const tokens: Record<string, string> = {};

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

function count(query: string): string {
  return mysql(query)[0][0];
}

/** Comptes par statut dans l'ordre affiché par les graphiques (0 quand MySQL n'a aucune ligne). */
function byStatus(query: string, keys = statuses): string[] {
  const rows = new Map(mysql(query).map(([key, value]) => [key, value]));
  return keys.map((k) => rows.get(k) ?? '0');
}

/** Score au format affiché par Angular en fr-FR (« 61,8 / 100 »). */
function score(value: string): string {
  return `${Number(value).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} / 100`;
}

function candidateSql(email: string): string {
  return `(SELECT p.id FROM candidate_profiles p JOIN users u ON u.id = p.user_id WHERE u.email = '${email}')`;
}

function companySql(name: string): string {
  return `(SELECT id FROM companies WHERE name = '${name}')`;
}

function kpi(page: Page, id: string): Locator {
  return page.getByTestId(id).getByTestId('kpi-value');
}

/** Valeurs du tableau accessible d'un graphique (une cellule par catégorie et par série). */
async function chartValues(page: Page, id: string): Promise<string[]> {
  return page.getByTestId(id).getByTestId('chart-data').locator('tbody td').allTextContents();
}

async function register(request: APIRequestContext, key: string, email: string, firstName: string, role: string): Promise<void> {
  const response = await request.post('/api/auth/register', { data: { email, password, firstName, lastName: 'E2E', role } });
  expect(response.status()).toBe(201);
  tokens[key] = (await response.json()).accessToken;
}

function auth(key: string) {
  return { Authorization: `Bearer ${tokens[key]}` };
}

async function createOffer(request: APIRequestContext, key: string, title: string, contractType: string, skills: string[], status = 'PUBLIEE') {
  const response = await request.post('/api/recruiter/jobs', {
    headers: auth(key),
    data: { title, description: 'Offre de test E2E étape 12 (dashboards).', location: 'Tunis', contractType, minExperienceYears: 0, requiredSkills: skills, status },
  });
  expect(response.status()).toBe(201);
  return (await response.json()).id as number;
}

async function apply(request: APIRequestContext, key: string, offer: number): Promise<number> {
  const response = await request.post('/api/candidate/applications', { headers: auth(key), data: { jobOfferId: offer } });
  expect(response.status()).toBe(201);
  return (await response.json()).id;
}

async function login(page: Page, email: string, pass: string, home: RegExp): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Mot de passe').fill(pass);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).toHaveURL(home);
}

test.beforeAll(async ({ request }) => {
  await register(request, 'recA', emails.recA, 'Rania', 'RECRUTEUR');
  await register(request, 'recB', emails.recB, 'Bilel', 'RECRUTEUR');
  await register(request, 'fr', emails.fr, 'Camille', 'CANDIDAT');
  await register(request, 'nocv', emails.nocv, 'Nora', 'CANDIDAT');
  for (const [key, name] of [['recA', companies.a], ['recB', companies.b]]) {
    expect((await request.post('/api/recruiter/company', { headers: auth(key), data: { name, sector: 'IT', location: 'Tunis' } })).status()).toBe(201);
  }
  offers['java'] = await createOffer(request, 'recA', titles.java, 'CDI', ['Java', 'Spring Boot', 'Angular', 'MySQL', 'Docker']);
  offers['data'] = await createOffer(request, 'recA', titles.data, 'CDD', ['Python', 'SQL', 'Docker']);
  offers['draft'] = await createOffer(request, 'recA', `Brouillon ${suffix}`, 'STAGE', ['Excel'], 'BROUILLON');
  offers['closed'] = await createOffer(request, 'recA', `Close ${suffix}`, 'CDI', ['Java']);
  expect((await request.patch(`/api/recruiter/jobs/${offers['closed']}/status`, { headers: auth('recA'), data: { status: 'FERMEE' } })).status()).toBe(200);
  offers['beta'] = await createOffer(request, 'recB', titles.beta, 'FREELANCE', ['Kubernetes', 'Docker', 'Terraform']);

  const upload = await request.post('/api/candidate/cv', {
    headers: auth('fr'),
    multipart: { file: { name: 'cv.pdf', mimeType: 'application/pdf', buffer: readFileSync(join(fixtures, 'cv_dev_fullstack_fr.pdf')) } },
  });
  expect((await upload.json()).parsingStatus).toBe('TERMINE');
  for (const offer of [offers['java'], offers['data'], offers['beta']]) {
    const matching = await request.get(`/api/candidate/jobs/${offer}/matching`, { headers: auth('fr') });
    expect((await matching.json()).status).toBe('CALCULE');
  }
  await apply(request, 'fr', offers['java']);
  await apply(request, 'fr', offers['beta']);
  await apply(request, 'nocv', offers['java']);
});

test.afterAll(async ({ request }) => {
  if (process.env['E2E_KEEP_DATA'] === '1') {
    return;
  }
  const login = await request.post('/api/auth/login', { data: { email: envValue('ADMIN_EMAIL'), password: envValue('ADMIN_PASSWORD') } });
  const headers = { Authorization: `Bearer ${(await login.json()).accessToken}` };
  const users = await (await request.get('/api/admin/users', { headers })).json();
  for (const user of users.filter((u: { email: string }) => u.email.startsWith('e2e12-'))) {
    await request.delete(`/api/admin/users/${user.id}`, { headers });
  }
  const list = await (await request.get('/api/admin/companies', { headers })).json();
  for (const company of list.filter((c: { name: string }) => c.name.endsWith(suffix))) {
    await request.delete(`/api/admin/companies/${company.id}`, { headers });
  }
});

test('D1 dashboard candidat : chiffres identiques à MySQL, puis mis à jour après une nouvelle candidature', async ({ page, request }) => {
  const cid = candidateSql(emails.fr);
  const visible = `FROM matching_results m JOIN job_offers o ON o.id = m.job_offer_id WHERE m.candidate_id = ${cid}
    AND (o.status = 'PUBLIEE' OR EXISTS (SELECT 1 FROM applications a WHERE a.candidate_id = ${cid} AND a.job_offer_id = o.id))`;
  await login(page, emails.fr, password, /\/candidate\/dashboard$/);

  await expect(kpi(page, 'kpi-applications')).toHaveText(count(`SELECT COUNT(*) FROM applications WHERE candidate_id = ${cid}`));
  await expect(kpi(page, 'kpi-offers')).toHaveText(count(`SELECT COUNT(*) FROM job_offers WHERE status = 'PUBLIEE'`));
  await expect(page.getByTestId('kpi-offers').getByTestId('kpi-hint')).toContainText(count(
    `SELECT COUNT(*) FROM job_offers o WHERE o.status = 'PUBLIEE' AND NOT EXISTS (SELECT 1 FROM applications a WHERE a.job_offer_id = o.id AND a.candidate_id = ${cid})`,
  ));
  expect(count(`SELECT parsing_status FROM cvs WHERE candidate_id = ${cid}`)).toBe('TERMINE');
  await expect(kpi(page, 'kpi-cv')).toHaveText('Analysé');
  await expect(kpi(page, 'kpi-best-score')).toHaveText(score(count(`SELECT MAX(m.score) ${visible}`)));
  await expect(page.getByTestId('kpi-best-score').getByTestId('kpi-hint')).toHaveText(`${count(`SELECT COUNT(*) ${visible}`)} offre(s) avec un score`);
  expect(await chartValues(page, 'chart-applications-status')).toEqual(
    byStatus(`SELECT status, COUNT(*) FROM applications WHERE candidate_id = ${cid} GROUP BY status`),
  );
  const expectedScores = mysql(`SELECT m.score ${visible} ORDER BY m.score DESC, m.computed_at DESC, o.id DESC LIMIT 5`).map(([s]) => score(s).replace(' / 100', ''));
  expect(await chartValues(page, 'chart-top-matches')).toEqual(expectedScores);
  await expect(page.getByTestId('top-match')).toHaveCount(expectedScores.length);
  await expect(page.getByTestId('chart-top-matches')).toContainText('ne constituent pas une décision de recrutement');
  for (const id of ['quick-cv', 'quick-jobs', 'quick-applications', 'quick-assistant']) {
    await expect(page.getByTestId(id)).toBeVisible();
  }
  await page.screenshot({ path: join(screenshotDir, '21-candidat-dashboard-statistiques.png'), fullPage: true });

  // Nouvelle candidature (offre Data) puis rechargement : le dashboard suit MySQL.
  await apply(request, 'fr', offers['data']);
  await page.reload();
  await expect(kpi(page, 'kpi-applications')).toHaveText(count(`SELECT COUNT(*) FROM applications WHERE candidate_id = ${cid}`));
  await expect(kpi(page, 'kpi-applications')).toHaveText('3');
  expect(await chartValues(page, 'chart-applications-status')).toEqual(['3', '0', '0', '0']);
});

test('D2 dashboard recruteur : KPI, graphiques et meilleurs candidats = MySQL, puis changement de statut', async ({ page, request }) => {
  const company = companySql(companies.a);
  const apps = `FROM applications a JOIN job_offers o ON o.id = a.job_offer_id WHERE o.company_id = ${company}`;
  const scored = `FROM applications a JOIN job_offers o ON o.id = a.job_offer_id JOIN matching_results m
    ON m.candidate_id = a.candidate_id AND m.job_offer_id = a.job_offer_id WHERE o.company_id = ${company}`;
  await login(page, emails.recA, password, /\/recruiter\/dashboard$/);

  const offerStatus = byStatus(`SELECT status, COUNT(*) FROM job_offers WHERE company_id = ${company} GROUP BY status`, ['PUBLIEE', 'BROUILLON', 'FERMEE']);
  await expect(kpi(page, 'kpi-offers')).toHaveText(count(`SELECT COUNT(*) FROM job_offers WHERE company_id = ${company}`));
  await expect(kpi(page, 'kpi-published')).toHaveText(offerStatus[0]);
  await expect(kpi(page, 'kpi-drafts')).toHaveText(offerStatus[1]);
  await expect(kpi(page, 'kpi-closed')).toHaveText(offerStatus[2]);
  await expect(kpi(page, 'kpi-applications')).toHaveText(count(`SELECT COUNT(*) ${apps}`));
  const [scoredCount, average] = mysql(`SELECT COUNT(*), ROUND(AVG(m.score), 1) ${scored}`)[0];
  await expect(kpi(page, 'kpi-average-score')).toHaveText(score(average));
  await expect(page.getByTestId('kpi-average-score').getByTestId('kpi-hint')).toHaveText(`${scoredCount} candidature(s) avec score`);
  expect(await chartValues(page, 'chart-applications-status')).toEqual(byStatus(`SELECT a.status, COUNT(*) ${apps} GROUP BY a.status`));

  // Tableau des offres : uniquement les offres de l'entreprise A, avec leur nombre de candidats réel.
  const perOffer = mysql(`SELECT o.title, COUNT(a.id) FROM job_offers o LEFT JOIN applications a ON a.job_offer_id = o.id
    WHERE o.company_id = ${company} GROUP BY o.id, o.title`);
  await expect(page.getByTestId('offer-row')).toHaveCount(perOffer.length);
  for (const [title, applications] of perOffer) {
    await expect(page.getByTestId('offer-row').filter({ hasText: title }).getByTestId('offer-applications')).toHaveText(applications);
  }
  await expect(page.getByTestId('offers-table')).not.toContainText(titles.beta);

  // Meilleurs candidats : seuls les candidats ayant postulé et ayant un score, dans l'ordre de MySQL.
  const top = mysql(`SELECT m.score ${scored} ORDER BY m.score DESC, a.created_at DESC, a.id DESC LIMIT 5`).map(([s]) => score(s));
  await expect(page.getByTestId('top-candidate-score')).toHaveText(top);
  await expect(page.getByTestId('top-candidates')).not.toContainText('Nora');
  await expect(page.locator('main')).toContainText('jamais une décision automatique');
  await page.screenshot({ path: join(screenshotDir, '22-recruteur-dashboard-statistiques.png'), fullPage: true });

  // Le recruteur accepte Camille sur l'offre Java : le graphique par statut suit MySQL.
  const application = count(`SELECT a.id ${apps} AND a.job_offer_id = ${offers['java']} AND a.candidate_id = ${candidateSql(emails.fr)}`);
  expect((await request.patch(`/api/recruiter/applications/${application}/status`, { headers: auth('recA'), data: { status: 'ACCEPTE' } })).status()).toBe(200);
  await page.reload();
  await expect(kpi(page, 'kpi-applications')).toHaveText(count(`SELECT COUNT(*) ${apps}`));
  await expect.poll(() => chartValues(page, 'chart-applications-status')).toEqual(byStatus(`SELECT a.status, COUNT(*) ${apps} GROUP BY a.status`));
  expect(await chartValues(page, 'chart-applications-status')).toEqual(['2', '0', '1', '0']);

  // Affichage mobile : pas de défilement horizontal.
  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload();
  await expect(kpi(page, 'kpi-offers')).toBeVisible();
  const overflowing = await page.evaluate(() =>
    document.documentElement.scrollWidth <= window.innerWidth
      ? []
      : [...document.querySelectorAll('body *')]
          .filter((e) => e.getBoundingClientRect().right > window.innerWidth + 1)
          .map((e) => `${e.tagName.toLowerCase()}.${e.className} [${e.getAttribute('data-testid') ?? ''}]`)
          .slice(0, 8),
  );
  expect(overflowing, 'éléments plus larges que l’écran mobile').toEqual([]);
  await page.screenshot({ path: join(screenshotDir, '24-mobile-recruteur-dashboard.png'), fullPage: true });
});

test("D3 isolation : le recruteur B ne voit que son entreprise", async ({ page }) => {
  const company = companySql(companies.b);
  await login(page, emails.recB, password, /\/recruiter\/dashboard$/);
  await expect(kpi(page, 'kpi-offers')).toHaveText(count(`SELECT COUNT(*) FROM job_offers WHERE company_id = ${company}`));
  await expect(kpi(page, 'kpi-applications')).toHaveText(count(
    `SELECT COUNT(*) FROM applications a JOIN job_offers o ON o.id = a.job_offer_id WHERE o.company_id = ${company}`,
  ));
  await expect(page.getByTestId('offer-row')).toHaveCount(1);
  await expect(page.locator('main')).not.toContainText(titles.java);
  await expect(page.locator('main')).not.toContainText(titles.data);
  await expect(page.locator('main')).not.toContainText('Nora');
});

test('D4 dashboard admin : statistiques globales = MySQL, aucune statistique de prédiction inventée', async ({ page, request }) => {
  await login(page, envValue('ADMIN_EMAIL'), envValue('ADMIN_PASSWORD'), /\/admin\/dashboard$/);
  const roles = byStatus('SELECT r.name, COUNT(*) FROM users u JOIN roles r ON r.id = u.role_id GROUP BY r.name', ['ADMIN', 'RECRUTEUR', 'CANDIDAT']);
  await expect(kpi(page, 'kpi-users')).toHaveText(count('SELECT COUNT(*) FROM users'));
  await expect(kpi(page, 'kpi-candidates')).toHaveText(roles[2]);
  await expect(kpi(page, 'kpi-recruiters')).toHaveText(roles[1]);
  await expect(kpi(page, 'kpi-companies')).toHaveText(count('SELECT COUNT(*) FROM companies'));
  await expect(kpi(page, 'kpi-offers')).toHaveText(count('SELECT COUNT(*) FROM job_offers'));
  await expect(kpi(page, 'kpi-applications')).toHaveText(count('SELECT COUNT(*) FROM applications'));
  expect(await chartValues(page, 'chart-users-role')).toEqual(roles);
  expect(await chartValues(page, 'chart-offers-status')).toEqual(
    byStatus('SELECT status, COUNT(*) FROM job_offers GROUP BY status', ['BROUILLON', 'PUBLIEE', 'FERMEE']),
  );
  expect(await chartValues(page, 'chart-applications-status')).toEqual(byStatus('SELECT status, COUNT(*) FROM applications GROUP BY status'));
  await expect(page.getByTestId('cv-total')).toHaveText(count('SELECT COUNT(*) FROM cvs'));
  await expect(page.getByTestId('cv-analyzed')).toHaveText(count(`SELECT COUNT(*) FROM cvs WHERE parsing_status = 'TERMINE'`));
  const [stored, average] = mysql('SELECT COUNT(*), ROUND(AVG(score), 1) FROM matching_results')[0];
  await expect(page.getByTestId('scores-total')).toHaveText(stored);
  await expect(page.getByTestId('scores-average')).toHaveText(score(average));

  // État du module ML = réponse réelle du service IA ; aucune statistique de prédiction (non enregistrées).
  const session = await request.post('/api/auth/login', { data: { email: envValue('ADMIN_EMAIL'), password: envValue('ADMIN_PASSWORD') } });
  const status = await request.get('/api/admin/ai/status', { headers: { Authorization: `Bearer ${(await session.json()).accessToken}` } });
  const expected = status.ok() ? ((await status.json()).modules['ml'] ? 'available' : 'unavailable') : 'unreachable';
  await expect(page.getByTestId('ml-state')).toHaveAttribute('data-state', expected);
  await expect(page.getByTestId('turnover-card')).toContainText('aucune statistique');
  await page.screenshot({ path: join(screenshotDir, '23-admin-dashboard-statistiques.png'), fullPage: true });
});

test("D5 accès interdit : un candidat ne peut ouvrir ni les dashboards ni les statistiques des autres rôles", async ({ page, request }) => {
  await login(page, emails.nocv, password, /\/candidate\/dashboard$/);
  for (const path of ['/recruiter/dashboard', '/admin/dashboard']) {
    await page.goto(path);
    await expect(page).not.toHaveURL(new RegExp(`${path}$`));
  }
  for (const path of ['/api/recruiter/dashboard', '/api/admin/dashboard']) {
    expect((await request.get(path, { headers: auth('nocv') })).status()).toBe(403);
  }
  expect((await request.get('/api/candidate/dashboard')).status()).toBe(401);
});
