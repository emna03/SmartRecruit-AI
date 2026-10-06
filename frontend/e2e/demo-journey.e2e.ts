import { expect, Page, test } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * ÉTAPE 13 : parcours complet de démonstration, entièrement par l'interface Angular, dans l'ordre de
 * docs/demo-guide.md. Après chaque action, l'état est vérifié directement dans MySQL.
 *   recruteur (inscription, entreprise, offre en brouillon puis publiée)
 *   → candidat (inscription, profil, CV PDF analysé, score de compatibilité, candidature, Assistant RH)
 *   → recruteur (classement, détail du score, changement de statut)
 *   → admin (dashboard, utilisateurs, entreprises, offres)
 *   → recruteur (risque de turnover : vrai modèle ML, résultat comparé au rapport d'entraînement).
 * Prérequis : MySQL (conteneur smartrecruit-mysql), backend et service IA démarrés, modèle ML entraîné.
 * Comptes et entreprise supprimés à la fin via l'API admin (sauf E2E_KEEP_DATA=1).
 */
const suffix = Date.now().toString(36);
const password = 'E2eTest123';
const recruiter = { email: `e2e13-recruteur-${suffix}@test.local`, firstName: 'Rania', lastName: 'Demo' };
const candidate = { email: `e2e13-camille-${suffix}@test.local`, firstName: 'Camille', lastName: 'Demo' };
const companyName = `E2E13 Demo ${suffix}`;
const jobTitle = `Développeur Full Stack Java ${suffix}`;
const skills = ['Java', 'Spring Boot', 'Angular', 'MySQL', 'Docker', 'Kubernetes'];
const cvFile = join(__dirname, '..', '..', 'ai-service', 'tests', 'fixtures', 'cv', 'cv_dev_fullstack_fr.pdf');
const report = JSON.parse(readFileSync(join(__dirname, '..', '..', 'ai-service', 'ml', 'reports', 'turnover_evaluation.json'), 'utf8'));
const screenshotDir = join(__dirname, 'screenshots');

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

function value(query: string): string {
  return mysql(query)[0]?.[0] ?? '';
}

const offerSql = `(SELECT id FROM job_offers WHERE title = '${jobTitle}')`;
const candidateSql = `(SELECT p.id FROM candidate_profiles p JOIN users u ON u.id = p.user_id WHERE u.email = '${candidate.email}')`;

async function login(page: Page, email: string, pass: string, home: RegExp): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Mot de passe').fill(pass);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).toHaveURL(home);
}

async function register(page: Page, user: typeof recruiter, path: string): Promise<void> {
  await page.goto(path);
  await page.getByLabel('Prénom').fill(user.firstName);
  await page.getByLabel('Nom', { exact: true }).fill(user.lastName);
  await page.getByLabel('Email').fill(user.email);
  await page.getByLabel('Mot de passe').fill(password);
  await page.getByLabel('Confirmation').fill(password);
  await page.getByRole('button', { name: 'Créer mon compte' }).click();
}

function toast(page: Page, text: string) {
  return page.locator('.toast').filter({ hasText: text });
}

async function shot(page: Page, name: string): Promise<void> {
  await page.screenshot({ path: join(screenshotDir, `demo-${name}.png`), fullPage: true });
}

test.afterAll(async ({ request }) => {
  if (process.env['E2E_KEEP_DATA'] === '1') {
    return;
  }
  const session = await request.post('/api/auth/login', { data: { email: envValue('ADMIN_EMAIL'), password: envValue('ADMIN_PASSWORD') } });
  const headers = { Authorization: `Bearer ${(await session.json()).accessToken}` };
  const users = await (await request.get('/api/admin/users', { headers })).json();
  for (const user of users.filter((u: { email: string }) => u.email.startsWith('e2e13-'))) {
    await request.delete(`/api/admin/users/${user.id}`, { headers });
  }
  const companies = await (await request.get('/api/admin/companies', { headers })).json();
  for (const company of companies.filter((c: { name: string }) => c.name === companyName)) {
    await request.delete(`/api/admin/companies/${company.id}`, { headers });
  }
});

test('1-2 recruteur : inscription, entreprise, offre en brouillon puis publiée', async ({ page }) => {
  await register(page, recruiter, '/register?role=RECRUTEUR');
  await expect(page).toHaveURL(/\/recruiter\/company$/);
  expect(mysql(`SELECT r.name, u.enabled = 1, LENGTH(u.password_hash) > 50 FROM users u JOIN roles r ON r.id = u.role_id
    WHERE u.email = '${recruiter.email}'`)).toEqual([['RECRUTEUR', '1', '1']]);

  await page.locator('#c-name').fill(companyName);
  await page.getByLabel('Secteur').fill('Logiciel');
  await page.getByLabel('Ville').fill('Tunis');
  await page.getByTestId('save-company').click();
  await expect(toast(page, 'Entreprise créée')).toBeVisible();
  expect(value(`SELECT COUNT(*) FROM recruiter_profiles rp JOIN users u ON u.id = rp.user_id JOIN companies c ON c.id = rp.company_id
    WHERE u.email = '${recruiter.email}' AND c.name = '${companyName}'`)).toBe('1');

  await page.getByRole('link', { name: 'Nouvelle offre' }).first().click();
  await page.getByLabel('Intitulé du poste').fill(jobTitle);
  await page.getByLabel('Type de contrat').selectOption('CDI');
  await page.getByLabel('Lieu').fill('Tunis');
  await page.getByLabel('Expérience minimale (années)').fill('3');
  await page.getByLabel("Niveau d'étude").selectOption('BAC_PLUS_5');
  const input = page.locator('#job-skills');
  for (const skill of skills) {
    await input.fill(skill);
    await input.press('Enter');
  }
  await page.getByLabel('Description du poste').fill('Développement d’applications Java / Spring Boot et Angular, déploiement Docker.');
  await page.getByTestId('save-draft').click();
  await expect(page).toHaveURL(/\/recruiter\/jobs$/);
  const row = page.getByRole('row', { name: new RegExp(jobTitle) });
  await expect(row).toContainText('Brouillon');
  expect(mysql(`SELECT status, contract_type, min_experience_years, education_level FROM job_offers WHERE title = '${jobTitle}'`))
    .toEqual([['BROUILLON', 'CDI', '3', 'BAC_PLUS_5']]);
  expect(value(`SELECT COUNT(*) FROM job_offer_skills WHERE job_offer_id = ${offerSql}`)).toBe(String(skills.length));

  await row.getByTestId('publish-job').click();
  await expect(row).toContainText('Publiée');
  expect(value(`SELECT status FROM job_offers WHERE title = '${jobTitle}'`)).toBe('PUBLIEE');
  await shot(page, '02-offre-publiee');
});

test('3-8 candidat : profil, CV analysé, score, candidature, Assistant RH', async ({ page }) => {
  await register(page, candidate, '/register');
  await expect(page).toHaveURL(/\/candidate\/profile$/);
  await page.getByLabel('Titre du profil').fill('Développeuse Full Stack');
  await page.getByLabel('Ville').fill('Tunis');
  await page.getByTestId('save-profile').click();
  await expect(toast(page, 'Profil enregistré')).toBeVisible();
  expect(mysql(`SELECT p.headline, p.location FROM candidate_profiles p WHERE p.id = ${candidateSql}`)).toEqual([['Développeuse Full Stack', 'Tunis']]);

  // 4-5 : import du CV PDF et analyse NLP (Spring Boot -> FastAPI -> MySQL).
  await page.getByRole('link', { name: 'Mon CV', exact: true }).click();
  await page.getByTestId('cv-file-input').setInputFiles(cvFile);
  await expect(page.getByTestId('cv-status')).toHaveText(/CV analysé/);
  for (const skill of ['Java', 'Spring Boot', 'Angular', 'Docker']) {
    await expect(page.getByTestId('cv-skills').getByText(skill, { exact: true })).toBeVisible();
  }
  const [cv] = mysql(`SELECT c.parsing_status, c.content_type, (SELECT COUNT(*) FROM cv_skills s WHERE s.cv_id = c.id),
    JSON_UNQUOTE(JSON_EXTRACT(c.extracted_data, '$.highestEducationLevel')) FROM cvs c WHERE c.candidate_id = ${candidateSql}`);
  expect(cv.slice(0, 2)).toEqual(['TERMINE', 'application/pdf']);
  expect(Number(cv[2])).toBeGreaterThan(0);
  expect(cv[3]).toBe('BAC_PLUS_5');
  await shot(page, '05-cv-analyse');

  // 6 : consultation de l'offre et score de compatibilité.
  await page.getByRole('link', { name: 'Offres', exact: true }).click();
  await page.getByTestId('filter-keyword').fill(suffix);
  await page.getByRole('button', { name: 'Rechercher' }).click();
  await page.getByRole('link', { name: new RegExp(jobTitle) }).click();
  await expect(page).toHaveURL(/\/candidate\/jobs\/\d+$/);
  await expect(page.getByTestId('matching-score')).toHaveText(/\d+(,\d)?\s*%/);
  const [score] = mysql(`SELECT m.score, m.algorithm_version FROM matching_results m WHERE m.candidate_id = ${candidateSql} AND m.job_offer_id = ${offerSql}`);
  const displayed = Number(score[0]).toLocaleString('fr-FR', { maximumFractionDigits: 1 });
  await expect(page.getByTestId('matching-score')).toHaveText(new RegExp(`^\\s*${displayed}\\s*%`));
  expect(score[1]).toBe('1.0.0');
  await expect(page.getByTestId('missing-skills')).toContainText('Kubernetes');
  await expect(page.getByTestId('matching-disclaimer')).toContainText('ne constitue pas une décision de recrutement');
  await shot(page, '06-score-compatibilite');

  // 7 : candidature.
  await page.getByTestId('apply-open').click();
  await page.getByTestId('apply-submit').click();
  await expect(toast(page, 'Candidature envoyée')).toBeVisible();
  expect(value(`SELECT status FROM applications WHERE candidate_id = ${candidateSql} AND job_offer_id = ${offerSql}`)).toBe('EN_ATTENTE');

  // 8 : Assistant RH, réponse fondée sur les offres publiées de MySQL.
  await page.getByRole('link', { name: 'Assistant RH', exact: true }).first().click();
  await expect(page).toHaveURL(/\/candidate\/assistant$/);
  const response = page.waitForResponse((r) => r.url().endsWith('/api/candidate/chatbot/messages'));
  await page.getByTestId('chat-input').fill('Quelles offres demandent Kubernetes ?');
  await page.getByTestId('chat-send').click();
  expect((await response).status()).toBe(200);
  const published = mysql(`SELECT o.title FROM job_offers o JOIN job_offer_skills js ON js.job_offer_id = o.id JOIN skills s ON s.id = js.skill_id
    WHERE o.status = 'PUBLIEE' AND s.name = 'Kubernetes'`).map(([title]) => title);
  const answer = page.getByTestId('chat-bot-message').last();
  await expect(answer).toHaveAttribute('data-intent', 'SEARCH_OFFERS');
  await expect(answer.getByTestId('chat-offer-title')).toHaveText(published);
  await shot(page, '08-assistant-rh');
});

test('9 recruteur : classement par score, détail du score, changement de statut', async ({ page }) => {
  await login(page, recruiter.email, password, /\/recruiter\/dashboard$/);
  await page.getByRole('link', { name: 'Mes offres', exact: true }).click();
  await page.getByRole('row', { name: new RegExp(jobTitle) }).getByRole('link', { name: 'Candidats', exact: true }).click();
  const card = page.getByTestId('candidate-card');
  await expect(card).toHaveCount(1);
  await expect(card).toContainText('Camille Demo');
  await expect(card.getByTestId('candidate-rank')).toHaveText('#1');
  const score = Number(value(`SELECT score FROM matching_results WHERE candidate_id = ${candidateSql} AND job_offer_id = ${offerSql}`));
  await expect(card.getByTestId('match-score')).toHaveText(new RegExp(`^\\s*${score.toLocaleString('fr-FR', { maximumFractionDigits: 1 })}\\s*%`));
  await expect(page.getByTestId('ranking-disclaimer')).toContainText('aide à la décision');

  await card.getByTestId('application-status').selectOption('PRESELECTIONNE');
  await expect(toast(page, 'présélectionné')).toBeVisible();
  expect(value(`SELECT status FROM applications WHERE candidate_id = ${candidateSql} AND job_offer_id = ${offerSql}`)).toBe('PRESELECTIONNE');
  await shot(page, '09-classement-recruteur');

  await card.getByRole('link', { name: 'Profil et détail du score' }).click();
  await expect(page.getByRole('heading', { name: 'Camille Demo' })).toBeVisible();
  await expect(page.getByText('Statut actuel')).toContainText('Présélectionné');
  await expect(page.getByTestId('application-matching').getByTestId('matching-explanation')).toContainText('Score global');
});

test('10 admin : dashboard, utilisateurs, entreprises, offres = MySQL', async ({ page }) => {
  await login(page, envValue('ADMIN_EMAIL'), envValue('ADMIN_PASSWORD'), /\/admin\/dashboard$/);
  await expect(page.getByTestId('kpi-users').getByTestId('kpi-value')).toHaveText(value('SELECT COUNT(*) FROM users'));
  await expect(page.getByTestId('kpi-companies').getByTestId('kpi-value')).toHaveText(value('SELECT COUNT(*) FROM companies'));
  await expect(page.getByTestId('kpi-offers').getByTestId('kpi-value')).toHaveText(value('SELECT COUNT(*) FROM job_offers'));
  await expect(page.getByTestId('kpi-applications').getByTestId('kpi-value')).toHaveText(value('SELECT COUNT(*) FROM applications'));
  await shot(page, '10-dashboard-admin');

  await page.getByRole('link', { name: 'Utilisateurs', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Utilisateurs' })).toBeVisible();
  await expect(page.locator('tbody tr')).toHaveCount(Number(value('SELECT COUNT(*) FROM users')));
  await expect(page.getByRole('row', { name: new RegExp(recruiter.email) })).toContainText('Recruteur');
  await expect(page.getByRole('row', { name: new RegExp(candidate.email) })).toContainText('Candidat');

  await page.getByRole('link', { name: 'Entreprises', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Entreprises' })).toBeVisible();
  await expect(page.getByRole('row', { name: new RegExp(companyName) })).toBeVisible();

  await page.getByRole('link', { name: 'Offres', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Offres' })).toBeVisible();
  await expect(page.getByRole('row', { name: new RegExp(jobTitle) })).toContainText('Publiée');
});

test('11 recruteur : risque de turnover avec le vrai modèle ML, rien enregistré dans MySQL', async ({ page }) => {
  const tables = value('SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()');
  await login(page, recruiter.email, password, /\/recruiter\/dashboard$/);
  await page.getByRole('link', { name: 'Risque de turnover' }).first().click();
  await page.getByTestId('turnover-example-2').click();
  const request = page.waitForRequest((r) => r.url().endsWith('/api/hr/turnover/predictions'));
  const response = page.waitForResponse((r) => r.url().endsWith('/api/hr/turnover/predictions'));
  await page.getByTestId('turnover-submit').click();
  expect((await response).status()).toBe(200);
  const sent = (await request).postDataJSON();
  const sorted = (o: Record<string, unknown>) => JSON.stringify(Object.keys(o).sort().map((k) => [k, o[k]]));
  const example = [report.examples.lowRisk, report.examples.highRisk].find((e) => sorted(e.features) === sorted(sent));
  expect(example, "l'exemple envoyé fait partie du rapport d'entraînement").toBeDefined();
  await expect(page.getByTestId('turnover-result')).toHaveAttribute('data-level', example.riskLevel);
  await expect(page.getByTestId('turnover-probability')).toHaveText(`${(example.probability * 100).toFixed(1).replace('.', ',')} %`);
  await expect(page.getByTestId('turnover-result-disclaimer')).toContainText('ne doit pas être utilisée seule');
  await shot(page, '11-turnover');
  expect(value('SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()')).toBe(tables);
});
