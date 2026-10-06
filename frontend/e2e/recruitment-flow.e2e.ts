import { APIRequestContext, expect, Page, test } from '@playwright/test';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * Parcours complet sur la vraie pile (Angular -> Spring Boot -> MySQL) :
 * recruteur (entreprise + offre publiée), visiteur (recherche), candidat (profil + candidature),
 * recruteur (présélection), candidat (nouveau statut). Les données créées sont supprimées à la fin
 * via l'API admin, sauf si E2E_KEEP_DATA=1.
 */
const suffix = Date.now().toString(36);
const password = 'E2eTest123';
const recruiter = { email: `e2e6-recruteur-${suffix}@test.local`, firstName: 'Bob', lastName: 'Durand' };
const candidate = { email: `e2e6-candidat-${suffix}@test.local`, firstName: 'Élodie', lastName: 'Martin' };
const companyName = `E2E6 Acme ${suffix}`;
const jobTitle = `Développeur Angular E2E ${suffix}`;
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

async function login(page: Page, email: string): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Mot de passe').fill(password);
  await page.getByRole('button', { name: 'Se connecter' }).click();
}

async function addSkills(page: Page, inputSelector: string, skills: string[]): Promise<void> {
  const input = page.locator(inputSelector);
  for (const skill of skills) {
    await input.fill(skill);
    await input.press('Enter');
  }
}

function toast(page: Page, text: string | RegExp) {
  return page.locator('.toast').filter({ hasText: text });
}

async function cleanup(request: APIRequestContext): Promise<void> {
  const loginResponse = await request.post('/api/auth/login', {
    data: { email: envValue('ADMIN_EMAIL'), password: envValue('ADMIN_PASSWORD') },
  });
  expect(loginResponse.ok()).toBe(true);
  const headers = { Authorization: `Bearer ${(await loginResponse.json()).accessToken}` };
  const companies = await (await request.get('/api/admin/companies', { headers })).json();
  for (const company of companies.filter((c: { name: string }) => c.name.startsWith('E2E6 '))) {
    await request.delete(`/api/admin/companies/${company.id}`, { headers });
  }
  const users = await (await request.get('/api/admin/users', { headers })).json();
  for (const user of users.filter((u: { email: string }) => u.email.startsWith('e2e6-'))) {
    await request.delete(`/api/admin/users/${user.id}`, { headers });
  }
}

test.afterAll(async ({ request }) => {
  if (process.env['E2E_KEEP_DATA'] !== '1') {
    await cleanup(request);
  }
});

test('E1 les espaces protégés renvoient un visiteur vers la connexion', async ({ page }) => {
  await page.goto('/candidate/profile');
  await expect(page).toHaveURL(/\/login\?returnUrl=%2Fcandidate%2Fprofile/);
  await page.goto('/recruiter/jobs');
  await expect(page).toHaveURL(/\/login\?returnUrl=%2Frecruiter%2Fjobs/);
  await page.goto('/admin/users');
  await expect(page).toHaveURL(/\/login/);
});

test("E2 la page d'accueil charge les offres depuis l'API", async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: /Trouvez le bon poste/ })).toBeVisible();
  await expect(page.getByText(/offre\(s\) publiée\(s\) actuellement/)).toBeVisible();
  await page.screenshot({ path: join(screenshotDir, '01-accueil.png'), fullPage: true });
});

test('E3 un recruteur s’inscrit, crée son entreprise et publie une offre', async ({ page }) => {
  await page.goto('/register?role=RECRUTEUR');
  await page.getByRole('button', { name: 'Créer mon compte' }).click();
  await expect(page.getByText('Ce champ est obligatoire.').first()).toBeVisible();

  await page.getByLabel('Prénom').fill(recruiter.firstName);
  await page.getByLabel('Nom', { exact: true }).fill(recruiter.lastName);
  await page.getByLabel('Email').fill(recruiter.email);
  await page.getByLabel('Mot de passe').fill('court');
  await page.getByLabel('Confirmation').fill('autre');
  await page.getByRole('button', { name: 'Créer mon compte' }).click();
  await expect(page.getByText('Au moins 8 caractères.')).toBeVisible();
  await expect(page.getByText('Les mots de passe ne correspondent pas.')).toBeVisible();

  await page.getByLabel('Mot de passe').fill(password);
  await page.getByLabel('Confirmation').fill(password);
  await page.getByRole('button', { name: 'Créer mon compte' }).click();
  await expect(page).toHaveURL(/\/recruiter\/company$/);
  await expect(page.getByTestId('navbar-user')).toHaveText('Bob Durand');

  await page.goto('/recruiter/jobs');
  await expect(page.getByTestId('no-company')).toBeVisible();
  await page.getByRole('link', { name: 'Créer mon entreprise' }).click();

  await page.locator('#c-name').fill(companyName);
  await page.getByLabel('Secteur').fill('Logiciel');
  await page.getByLabel('Ville').fill('Tunis');
  await page.getByLabel('Site web').fill('acme.example');
  await page.getByTestId('save-company').click();
  await expect(page.getByText('Le site doit commencer par http:// ou https://')).toBeVisible();
  await page.getByLabel('Site web').fill('https://acme.example');
  await page.getByTestId('save-company').click();
  await expect(toast(page, 'Entreprise créée')).toBeVisible();

  await page.getByRole('link', { name: 'Nouvelle offre' }).first().click();
  await expect(page).toHaveURL(/\/recruiter\/jobs\/create$/);
  await page.getByTestId('publish-new-job').click();
  await expect(page.getByText('Indiquez au moins une compétence.')).toBeVisible();

  await page.getByLabel('Intitulé du poste').fill(jobTitle);
  await page.getByLabel('Type de contrat').selectOption('CDI');
  await page.getByLabel('Lieu').fill('Tunis');
  await page.getByLabel('Expérience minimale (années)').fill('2');
  await page.getByLabel("Niveau d'étude").selectOption('BAC_PLUS_5');
  await page.getByLabel('Salaire min.').fill('4000');
  await page.getByLabel('Salaire max.').fill('2500');
  await expect(page.getByText('Le salaire max. doit être supérieur ou égal au salaire min.')).toBeVisible();
  await page.getByLabel('Salaire max.').fill('5000');
  await addSkills(page, '#job-skills', ['Angular', 'TypeScript', 'Spring Boot']);
  await page.getByLabel('Description du poste').fill('Développement de l’interface Angular et intégration avec l’API Spring Boot.');
  await page.screenshot({ path: join(screenshotDir, '02-recruteur-nouvelle-offre.png'), fullPage: true });
  await page.getByTestId('publish-new-job').click();

  await expect(page).toHaveURL(/\/recruiter\/jobs$/);
  await expect(toast(page, 'Offre publiée')).toBeVisible();
  const row = page.getByRole('row', { name: new RegExp(jobTitle) });
  await expect(row).toContainText('Publiée');
  await page.screenshot({ path: join(screenshotDir, '03-recruteur-mes-offres.png'), fullPage: true });
});

test('E4 un visiteur trouve l’offre publiée via la recherche', async ({ page }) => {
  await page.goto('/jobs');
  await page.getByTestId('filter-keyword').fill(suffix);
  await page.getByRole('button', { name: 'Rechercher' }).click();
  await expect(page).toHaveURL(new RegExp(`keyword=${suffix}`));
  await expect(page.getByTestId('jobs-count')).toHaveText('1 offre(s) trouvée(s)');

  await page.getByRole('link', { name: new RegExp(jobTitle) }).click();
  await expect(page.getByTestId('job-title')).toHaveText(jobTitle);
  await expect(page.getByText('TypeScript', { exact: true })).toBeVisible();
  await expect(page.getByText(companyName)).toBeVisible();
  await expect(page.getByTestId('login-to-apply')).toBeVisible();
  await page.screenshot({ path: join(screenshotDir, '04-detail-offre-public.png'), fullPage: true });
});

test('E5 un candidat s’inscrit depuis l’offre, complète son profil et postule', async ({ page }) => {
  await page.goto(`/jobs?keyword=${suffix}`);
  await page.getByRole('link', { name: new RegExp(jobTitle) }).click();
  const jobUrl = new URL(page.url()).pathname;
  await page.getByTestId('login-to-apply').click();
  await expect(page).toHaveURL(/\/login\?returnUrl=/);
  await page.getByRole('link', { name: 'Créer un compte' }).click();

  await page.getByLabel('Prénom').fill(candidate.firstName);
  await page.getByLabel('Nom', { exact: true }).fill(candidate.lastName);
  await page.getByLabel('Email').fill(candidate.email);
  await page.getByLabel('Mot de passe').fill(password);
  await page.getByLabel('Confirmation').fill(password);
  await page.getByRole('button', { name: 'Créer mon compte' }).click();
  await expect(page).toHaveURL(new RegExp(`${jobUrl}$`));
  await expect(page.getByTestId('apply-open')).toBeVisible();

  await page.goto('/recruiter/dashboard');
  await expect(page).toHaveURL(/\/candidate\/dashboard$/);

  await page.getByRole('link', { name: 'Mon profil', exact: true }).click();
  await page.getByLabel('Titre du profil').fill('Développeuse Angular / Spring Boot');
  await page.getByLabel('Téléphone').fill('abc');
  await page.getByTestId('save-profile').click();
  await expect(page.getByText('Numéro de téléphone invalide')).toBeVisible();
  await page.getByLabel('Téléphone').fill('+216 20 123 456');
  await page.getByLabel('Ville').fill('Tunis');
  await page.getByLabel("Années d'expérience").fill('3');
  await page.getByLabel("Niveau d'étude").selectOption('BAC_PLUS_5');
  await page.getByTestId('save-profile').click();
  await expect(toast(page, 'Profil enregistré')).toBeVisible();

  await addSkills(page, '#candidate-skills', ['Angular', 'typescript', 'Docker']);
  await page.getByTestId('save-skills').click();
  await expect(toast(page, 'Compétences enregistrées')).toBeVisible();

  await page.getByTestId('add-education').click();
  await page.getByLabel('Diplôme').fill("Diplôme d'ingénieur");
  await page.getByLabel('Spécialité').fill('Génie logiciel');
  await page.getByLabel('Établissement').fill('ESPRIT');
  await page.locator('#edu-start').fill('2021-09-01');
  await page.locator('#edu-end').fill('2026-06-30');
  await page.getByTestId('save-education').click();
  await expect(toast(page, 'Formation enregistrée')).toBeVisible();

  await page.getByTestId('add-experience').click();
  await page.getByLabel('Poste', { exact: true }).fill('Stagiaire développeuse');
  await page.getByLabel('Entreprise', { exact: true }).fill('Globex');
  await page.locator('#exp-start').fill('2025-06-01');
  await page.locator('#exp-end').fill('2025-01-01');
  await expect(page.getByText('La date de fin doit être postérieure à la date de début.')).toBeVisible();
  await page.locator('#exp-end').fill('2025-08-31');
  await page.getByTestId('save-experience').click();
  await expect(toast(page, 'Expérience enregistrée')).toBeVisible();
  await expect(page.getByText('Stagiaire développeuse')).toBeVisible();
  await page.screenshot({ path: join(screenshotDir, '05-candidat-profil.png'), fullPage: true });

  await page.getByRole('link', { name: 'Offres', exact: true }).click();
  await page.getByTestId('filter-keyword').fill(suffix);
  await page.getByRole('button', { name: 'Rechercher' }).click();
  await page.getByRole('link', { name: new RegExp(jobTitle) }).click();
  await expect(page).toHaveURL(/\/candidate\/jobs\/\d+$/);
  await page.getByTestId('apply-open').click();
  await page.getByLabel('Lettre de motivation (facultative)').fill('Très motivée par ce poste Angular.');
  await page.getByTestId('apply-submit').click();
  await expect(toast(page, 'Candidature envoyée')).toBeVisible();
  await expect(page.getByTestId('already-applied')).toContainText('En attente');

  await page.getByRole('link', { name: 'Mes candidatures' }).first().click();
  const row = page.getByTestId('applications-table').getByRole('row', { name: new RegExp(jobTitle) });
  await expect(row).toContainText('En attente');
  await expect(row).toContainText(companyName);
});

test('E6 le recruteur consulte le candidat et le présélectionne', async ({ page }) => {
  await login(page, recruiter.email);
  await expect(page).toHaveURL(/\/recruiter\/dashboard$/);
  await expect(page.locator('.stat-card').filter({ hasText: 'Candidatures reçues' })).toContainText('1');
  await page.screenshot({ path: join(screenshotDir, '06-recruteur-dashboard.png'), fullPage: true });

  await page.getByRole('link', { name: 'Mes offres', exact: true }).click();
  await page.getByRole('row', { name: new RegExp(jobTitle) }).getByRole('link', { name: 'Candidats', exact: true }).click();
  const card = page.getByTestId('candidate-card');
  await expect(card).toContainText('Élodie Martin');
  await expect(card).toContainText('Développeuse Angular / Spring Boot');
  await expect(card.locator('.skill-badge.matched')).toHaveCount(2);

  await card.getByTestId('application-status').selectOption('PRESELECTIONNE');
  await expect(toast(page, 'présélectionné')).toBeVisible();
  await page.screenshot({ path: join(screenshotDir, '07-recruteur-candidats.png'), fullPage: true });

  await card.getByRole('link', { name: 'Voir le profil complet' }).click();
  await expect(page.getByRole('heading', { name: 'Élodie Martin' })).toBeVisible();
  await expect(page.getByText('Stagiaire développeuse — Globex')).toBeVisible();
  await expect(page.getByText('Très motivée par ce poste Angular.')).toBeVisible();
  await expect(page.getByText('Statut actuel')).toContainText('Présélectionné');
});

test('E7 le candidat voit le nouveau statut et ses statistiques', async ({ page }) => {
  await login(page, candidate.email);
  await expect(page).toHaveURL(/\/candidate\/dashboard$/);
  await expect(page.locator('.stat-card').filter({ hasText: 'Présélectionné' })).toContainText('1');
  await page.screenshot({ path: join(screenshotDir, '08-candidat-dashboard.png'), fullPage: true });

  await page.getByRole('link', { name: 'Mes candidatures' }).first().click();
  await expect(page.getByTestId('applications-table').getByRole('row', { name: new RegExp(jobTitle) })).toContainText('Présélectionné');
  await page.screenshot({ path: join(screenshotDir, '09-candidat-candidatures.png'), fullPage: true });

  await page.getByRole('button', { name: 'Déconnexion' }).click();
  await expect(page).toHaveURL(/\/$/);
  await page.goto('/candidate/applications');
  await expect(page).toHaveURL(/\/login/);
});

test('E8 les erreurs du backend sont affichées', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill(candidate.email);
  await page.getByLabel('Mot de passe').fill('Mauvais123');
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page.getByTestId('login-error')).toHaveText('Email ou mot de passe incorrect');

  await page.goto('/register');
  await page.getByLabel('Prénom').fill('X');
  await page.getByLabel('Nom', { exact: true }).fill('Y');
  await page.getByLabel('Email').fill(candidate.email.toUpperCase());
  await page.getByLabel('Mot de passe').fill(password);
  await page.getByLabel('Confirmation').fill(password);
  await page.getByRole('button', { name: 'Créer mon compte' }).click();
  await expect(page.getByTestId('register-error')).toContainText('existe déjà');

  await page.goto('/jobs/999999999');
  await expect(page.getByText("Cette offre n'existe pas ou n'est plus disponible.")).toBeVisible();
});

test('E9 affichage mobile : menu repliable', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  await expect(page.getByRole('link', { name: 'Connexion' })).toBeHidden();
  await page.getByRole('button', { name: 'Menu' }).click();
  await expect(page.getByRole('link', { name: 'Connexion' })).toBeVisible();
  await page.screenshot({ path: join(screenshotDir, '10-mobile-accueil.png'), fullPage: false });
});
