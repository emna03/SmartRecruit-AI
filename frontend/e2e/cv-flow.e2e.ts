import { APIRequestContext, expect, Page, test } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * ÉTAPE 8 sur la vraie pile : Angular -> Spring Boot -> FastAPI (CV Parser) -> MySQL -> Angular.
 * Prérequis : MySQL (conteneur smartrecruit-mysql), backend et service IA démarrés.
 * Les CV utilisés sont les CV fictifs de ai-service/tests/fixtures/cv (aucune donnée réelle).
 * Le compte créé est supprimé à la fin via l'API admin, sauf si E2E_KEEP_DATA=1.
 */
const suffix = Date.now().toString(36);
const password = 'E2eTest123';
const email = `e2e8-candidat-${suffix}@test.local`;
const fixtures = join(__dirname, '..', '..', 'ai-service', 'tests', 'fixtures', 'cv');
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

function cvRowSql(columns: string): string {
  return `SELECT ${columns} FROM cvs c JOIN candidate_profiles p ON p.id = c.candidate_id JOIN users u ON u.id = p.user_id WHERE u.email = '${email}'`;
}

async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Mot de passe').fill(password);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).toHaveURL(/\/candidate\/dashboard$/);
}

async function adminHeaders(request: APIRequestContext): Promise<Record<string, string>> {
  const response = await request.post('/api/auth/login', {
    data: { email: envValue('ADMIN_EMAIL'), password: envValue('ADMIN_PASSWORD') },
  });
  expect(response.ok()).toBe(true);
  return { Authorization: `Bearer ${(await response.json()).accessToken}` };
}

test.beforeAll(async ({ request }) => {
  const response = await request.post('/api/auth/register', {
    data: { email, password, firstName: 'Camille', lastName: 'Exemple', role: 'CANDIDAT' },
  });
  expect(response.status()).toBe(201);
});

test.afterAll(async ({ request }) => {
  if (process.env['E2E_KEEP_DATA'] === '1') {
    return;
  }
  const headers = await adminHeaders(request);
  const users = await (await request.get('/api/admin/users', { headers })).json();
  for (const user of users.filter((u: { email: string }) => u.email.startsWith('e2e8-'))) {
    await request.delete(`/api/admin/users/${user.id}`, { headers });
  }
});

test('C1 le candidat importe son CV PDF et voit les informations extraites', async ({ page }) => {
  await login(page);
  await page.getByRole('link', { name: 'Mon CV', exact: true }).click();
  await expect(page).toHaveURL(/\/candidate\/cv$/);
  await expect(page.getByTestId('cv-dropzone')).toBeVisible();

  const responsePromise = page.waitForResponse((r) => r.url().endsWith('/api/candidate/cv') && r.request().method() === 'POST');
  await page.getByTestId('cv-file-input').setInputFiles(join(fixtures, 'cv_dev_fullstack_fr.pdf'));
  await expect(page.getByTestId('cv-analyzing')).toBeVisible();
  expect((await responsePromise).status()).toBe(200);

  await expect(page.getByTestId('cv-status')).toHaveText(/CV analysé/);
  const skills = page.getByTestId('cv-skills');
  for (const name of ['Java', 'Spring Boot', 'Angular', 'MySQL', 'TypeScript', 'Docker', 'API REST', 'Scrum']) {
    await expect(skills.getByText(name, { exact: true })).toBeVisible();
  }
  await expect(page.getByTestId('cv-educations')).toContainText("Diplôme d'ingénieur en Génie Logiciel");
  await expect(page.getByTestId('cv-educations')).toContainText('École Supérieure Fictive d\'Ingénieurs');
  await expect(page.getByTestId('cv-experiences')).toContainText('Développeuse Full Stack');
  await expect(page.getByTestId('cv-experiences')).toContainText('Société Exemple · Tunis');
  await expect(page.getByTestId('cv-experiences')).toContainText('mars 2023 – aujourd’hui');
  await expect(page.getByTestId('cv-languages')).toContainText('Langue maternelle');
  await expect(page.getByTestId('cv-languages')).toContainText('TOEIC 850');
  await expect(page.getByTestId('cv-summary')).toContainText('Bac+5');
  await expect(page.getByTestId('cv-method')).toContainText('sans LLM');
  await page.screenshot({ path: join(screenshotDir, '11-candidat-cv-analyse.png'), fullPage: true });
});

test('C2 les données extraites sont enregistrées dans MySQL', async () => {
  const [row] = mysql(cvRowSql(
    "c.parsing_status, c.content_type, c.original_file_name, c.stored_file_name, c.file_size, " +
    "JSON_LENGTH(c.extracted_data, '$.technologies'), JSON_LENGTH(c.extracted_data, '$.educations'), " +
    "JSON_LENGTH(c.extracted_data, '$.experiences'), JSON_LENGTH(c.extracted_data, '$.languages'), " +
    "JSON_UNQUOTE(JSON_EXTRACT(c.extracted_data, '$.highestEducationLevel')), " +
    "JSON_EXTRACT(c.extracted_data, '$.totalExperienceMonths') > 0, JSON_CONTAINS_PATH(c.extracted_data, 'one', '$.text'), " +
    "INSTR(c.extracted_text, 'camille.exemple@example.com'), INSTR(c.extracted_text, '[email]'), c.parsed_at IS NOT NULL"));
  expect(row).toEqual(['TERMINE', 'application/pdf', 'cv_dev_fullstack_fr.pdf', expect.stringMatching(/^[0-9a-f]{32}\.pdf$/),
    '2753', '24', '2', '2', '3', 'BAC_PLUS_5', '1', '0', '0', expect.not.stringMatching(/^0$/), '1']);

  const skills = mysql(cvRowSql('s.name, s.category') .replace('FROM cvs c', 'FROM cvs c JOIN cv_skills cs ON cs.cv_id = c.id JOIN skills s ON s.id = cs.skill_id'));
  const byName = new Map(skills.map(([name, category]) => [name, category]));
  expect(byName.size).toBe(33);
  expect(byName.get('Java')).toBe('Langage');
  expect(byName.get('Spring Boot')).toBe('Framework back-end');
  expect(byName.get('Angular')).toBe('Framework front-end');
  expect(byName.get('MySQL')).toBe('Base de données');
  expect(byName.get('Scrum')).toBe('Méthodologie');

  const storedFile = row[3];
  expect(existsSync(join(__dirname, '..', '..', 'backend', 'uploads', 'cvs', storedFile))).toBe(true);
});

test('C3 les fichiers invalides sont refusés avec un message compréhensible', async ({ page }) => {
  await login(page);
  await page.goto('/candidate/cv');
  await expect(page.getByTestId('cv-status')).toHaveText(/CV analysé/);
  const [before] = mysql(cvRowSql('c.stored_file_name'));

  await page.getByTestId('cv-file-input').setInputFiles(join(fixtures, 'not_a_pdf.pdf'));
  await expect(page.getByTestId('cv-error')).toContainText("n'est pas un PDF valide");

  await page.getByTestId('cv-file-input').setInputFiles(join(fixtures, 'cv_image_only.pdf'));
  await expect(page.getByTestId('cv-error')).toContainText('ne contient pas de texte exploitable');
  await page.screenshot({ path: join(screenshotDir, '12-candidat-cv-erreur.png'), fullPage: true });

  await page.getByTestId('cv-file-input').setInputFiles(join(fixtures, 'cv_encrypted.pdf'));
  await expect(page.getByTestId('cv-error')).toContainText('protégé par un mot de passe');

  await page.getByTestId('cv-file-input').setInputFiles({
    name: 'trop-gros.pdf', mimeType: 'application/pdf', buffer: Buffer.concat([Buffer.from('%PDF-1.4\n'), Buffer.alloc(6 * 1024 * 1024)]),
  });
  await expect(page.getByTestId('cv-error')).toContainText('taille maximale de 5 Mo');

  // Le CV analysé précédemment est conservé.
  await expect(page.getByTestId('cv-status')).toHaveText(/CV analysé/);
  expect(mysql(cvRowSql('c.stored_file_name, c.parsing_status'))).toEqual([[before[0], 'TERMINE']]);
});

test('C4 le candidat remplace son CV', async ({ page }) => {
  await login(page);
  await page.goto('/candidate/cv');
  const [before] = mysql(cvRowSql('c.id, c.stored_file_name'));

  await page.getByTestId('cv-file-input').setInputFiles(join(fixtures, 'cv_data_scientist_en.pdf'));
  await expect(page.getByTestId('cv-file')).toContainText('cv_data_scientist_en.pdf');
  await expect(page.getByTestId('cv-skills').getByText('scikit-learn', { exact: true })).toBeVisible();
  await expect(page.getByTestId('cv-skills').getByText('Spring Boot', { exact: true })).toHaveCount(0);
  await expect(page.getByTestId('cv-languages')).toContainText('Espagnol');

  const [after] = mysql(cvRowSql('c.id, c.stored_file_name, c.original_file_name'));
  expect(after[0]).toBe(before[0]);
  expect(after[1]).not.toBe(before[1]);
  expect(after[2]).toBe('cv_data_scientist_en.pdf');
  const uploads = join(__dirname, '..', '..', 'backend', 'uploads', 'cvs');
  expect(existsSync(join(uploads, before[1]))).toBe(false);
  expect(existsSync(join(uploads, after[1]))).toBe(true);
});

test('C5 les informations extraites sont ajoutées au profil', async ({ page }) => {
  await login(page);
  await page.goto('/candidate/cv');
  await page.getByTestId('cv-import-button').click();
  await expect(page.getByTestId('cv-import-result')).toContainText('22 compétence(s), 2 formation(s) et 2 expérience(s)');

  expect(mysql(`SELECT e.degree, e.institution, e.start_date FROM educations e JOIN candidate_profiles p ON p.id = e.candidate_id
    JOIN users u ON u.id = p.user_id WHERE u.email = '${email}' ORDER BY e.start_date DESC`)).toEqual([
    ['Master of Science in Data Science', 'Example University', '2018-01-01'],
    ['Bachelor of Science in Computer Science', 'Sample Institute of Technology', '2015-01-01'],
  ]);
  expect(mysql(`SELECT x.job_title, x.company_name, x.start_date, COALESCE(x.end_date, 'NULL') FROM experiences x
    JOIN candidate_profiles p ON p.id = x.candidate_id JOIN users u ON u.id = p.user_id WHERE u.email = '${email}'
    ORDER BY x.start_date DESC`)).toEqual([
    ['Data Scientist', 'Sample Analytics Ltd', '2021-01-01', 'NULL'],
    ['Data Analyst Intern', 'Demo Corp', '2020-06-01', '2020-12-01'],
  ]);
  expect(mysql(`SELECT p.education_level, p.years_of_experience FROM candidate_profiles p JOIN users u ON u.id = p.user_id
    WHERE u.email = '${email}'`)).toEqual([['BAC_PLUS_5', '6']]);

  await page.getByTestId('cv-import').getByRole('link', { name: 'Mon profil', exact: true }).click();
  await expect(page.getByText('Master of Science in Data Science')).toBeVisible();
  await expect(page.getByText('Data Analyst Intern')).toBeVisible();
  await page.screenshot({ path: join(screenshotDir, '13-candidat-profil-depuis-cv.png'), fullPage: true });

  await page.getByRole('link', { name: 'Tableau de bord', exact: true }).click();
  await expect(page.locator('li', { hasText: 'CV importé' }).locator('.bi-check-circle-fill')).toBeVisible();
});

test('C6 le candidat supprime son CV', async ({ page }) => {
  await login(page);
  await page.goto('/candidate/cv');
  const [before] = mysql(cvRowSql('c.stored_file_name'));

  await page.getByTestId('cv-delete').click();
  await page.getByTestId('cv-delete-confirm').click();
  await expect(page.getByTestId('cv-dropzone')).toBeVisible();

  expect(mysql(cvRowSql('c.id'))).toEqual([]);
  expect(existsSync(join(__dirname, '..', '..', 'backend', 'uploads', 'cvs', before[0]))).toBe(false);
});
