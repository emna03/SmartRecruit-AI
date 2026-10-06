import { APIRequestContext, expect, Page, test } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * ÉTAPE 10 sur la vraie pile : candidat -> Assistant RH (Angular) -> Spring Boot -> FastAPI (chatbot)
 * -> réponse basée sur les offres publiées de MySQL -> Angular.
 * Prérequis : MySQL (conteneur smartrecruit-mysql), backend et service IA démarrés.
 * Compétence inventée pour ce test : seule l'offre créée ici la demande. Données supprimées à la fin (sauf E2E_KEEP_DATA=1).
 */
const suffix = Date.now().toString(36);
const password = 'E2eTest123';
const skill = `Zorg${suffix.replace(/\d/g, '')}`;
const title = `Ingénieur Plateforme ${suffix}`;
const company = `E2E10 Corp ${suffix}`;
const emails = { recruiter: `e2e10-recruteur-${suffix}@test.local`, candidate: `e2e10-camille-${suffix}@test.local` };
const screenshotDir = join(__dirname, 'screenshots');
const offerIds: number[] = [];

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

async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(emails.candidate);
  await page.getByLabel('Mot de passe').fill(password);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).toHaveURL(/\/candidate\/dashboard$/);
}

/** Envoie une question et attend la réponse réelle de l'API (Spring Boot -> FastAPI). */
async function ask(page: Page, question: string): Promise<void> {
  const response = page.waitForResponse((r) => r.url().endsWith('/api/candidate/chatbot/messages'));
  await page.getByTestId('chat-input').fill(question);
  await page.getByTestId('chat-send').click();
  expect((await response).status()).toBe(200);
  await expect(page.getByTestId('chat-typing')).toHaveCount(0);
}

test.beforeAll(async ({ request }) => {
  const headers = { Authorization: `Bearer ${await register(request, emails.recruiter, 'Rania', 'RECRUTEUR')}` };
  expect((await request.post('/api/recruiter/company', { headers, data: { name: company, sector: 'IT', location: 'Tunis' } })).status()).toBe(201);
  for (const [offerTitle, status] of [[title, 'PUBLIEE'], [`Brouillon ${suffix}`, 'BROUILLON']]) {
    const offer = await request.post('/api/recruiter/jobs', {
      headers,
      data: {
        title: offerTitle, description: 'Offre de test E2E étape 10 (chatbot).', location: 'Tunis', contractType: 'CDI',
        minExperienceYears: 2, educationLevel: 'BAC_PLUS_5', status, requiredSkills: ['Java', 'Spring Boot', skill],
      },
    });
    expect(offer.status()).toBe(201);
    offerIds.push((await offer.json()).id);
  }
  await register(request, emails.candidate, 'Camille', 'CANDIDAT');
});

test.afterAll(async ({ request }) => {
  if (process.env['E2E_KEEP_DATA'] === '1') {
    return;
  }
  const login = await request.post('/api/auth/login', { data: { email: envValue('ADMIN_EMAIL'), password: envValue('ADMIN_PASSWORD') } });
  const headers = { Authorization: `Bearer ${(await login.json()).accessToken}` };
  const companies = await (await request.get('/api/admin/companies', { headers })).json();
  for (const found of companies.filter((c: { name: string }) => c.name === company)) {
    await request.delete(`/api/admin/companies/${found.id}`, { headers });
  }
  const users = await (await request.get('/api/admin/users', { headers })).json();
  for (const user of users.filter((u: { email: string }) => u.email.startsWith('e2e10-'))) {
    await request.delete(`/api/admin/users/${user.id}`, { headers });
  }
  // Aucun endpoint ne supprime une compétence du référentiel : la compétence inventée est retirée directement.
  mysql(`DELETE FROM skills WHERE name = '${skill}' AND NOT EXISTS (SELECT 1 FROM job_offer_skills js WHERE js.skill_id = skills.id)
    AND NOT EXISTS (SELECT 1 FROM cv_skills cs WHERE cs.skill_id = skills.id) AND NOT EXISTS (SELECT 1 FROM candidate_skills c WHERE c.skill_id = skills.id)`);
});

test('C1 le candidat cherche des offres par compétence et obtient les vraies offres publiées', async ({ page }) => {
  await login(page);
  await page.getByRole('link', { name: 'Assistant RH' }).first().click();
  await expect(page).toHaveURL(/\/candidate\/assistant$/);
  await expect(page.getByTestId('chat-disclaimer')).toContainText('offres publiées');

  await ask(page, `Quelles offres demandent ${skill} ?`);
  await expect(page.getByTestId('chat-user-message')).toHaveText(`Quelles offres demandent ${skill} ?`);
  const answer = page.getByTestId('chat-bot-message').last();
  await expect(answer).toHaveAttribute('data-intent', 'SEARCH_OFFERS');
  await expect(answer.getByTestId('chat-answer')).toContainText(`J'ai trouvé 1 offre publiée qui demande ${skill}.`);
  await expect(answer.getByTestId('chat-understood')).toHaveText(`Compris : ${skill}`);
  await expect(answer.getByTestId('chat-offer')).toHaveCount(1);
  await expect(answer.getByTestId('chat-offer-title')).toHaveText(title);
  await expect(answer.getByTestId('chat-offer')).toContainText(company);
  await expect(answer).not.toContainText(`Brouillon ${suffix}`);

  const rows = mysql(`SELECT o.id, o.title FROM job_offers o JOIN job_offer_skills js ON js.job_offer_id = o.id
    JOIN skills s ON s.id = js.skill_id WHERE o.status = 'PUBLIEE' AND s.name = '${skill}'`);
  expect(rows).toEqual([[String(offerIds[0]), title]]);
  await expect(answer.getByTestId('chat-offer-link')).toHaveAttribute('href', `/candidate/jobs/${offerIds[0]}`);

  await expect(page.getByTestId('chat-context')).toContainText(title);
  await ask(page, "Combien d'années d'expérience sont nécessaires ?");
  const experience = page.getByTestId('chat-bot-message').last();
  await expect(experience).toHaveAttribute('data-intent', 'OFFER_EXPERIENCE');
  await expect(experience.getByTestId('chat-answer')).toContainText(`L'offre « ${title} » (${company}) demande au moins 2 ans d'expérience.`);
  await page.screenshot({ path: join(screenshotDir, '17-candidat-assistant-rh.png'), fullPage: true });

  await answer.getByTestId('chat-offer-link').click();
  await expect(page).toHaveURL(new RegExp(`/candidate/jobs/${offerIds[0]}$`));
  await expect(page.getByRole('heading', { name: title })).toBeVisible();
});

test("C2 depuis une offre, le candidat interroge l'assistant sur cette offre ; questions inconnues et offre inexistante", async ({ page }) => {
  await login(page);
  await page.goto(`/candidate/jobs/${offerIds[0]}`);
  await page.getByTestId('ask-assistant').click();
  await expect(page).toHaveURL(new RegExp(`/candidate/assistant\\?offre=${offerIds[0]}$`));
  await expect(page.getByTestId('chat-context')).toContainText(title);

  await ask(page, "Quel niveau d'étude est demandé ?");
  await expect(page.getByTestId('chat-answer').last()).toContainText('demande un niveau Bac+5');

  await ask(page, 'Comment fonctionne le score de compatibilité ?');
  const faq = page.getByTestId('chat-bot-message').last();
  await expect(faq).toHaveAttribute('data-intent', 'FAQ_MATCHING');
  await expect(faq.getByTestId('chat-answer')).toContainText('jamais une décision automatique');

  await ask(page, 'Quelles sont les compétences demandées pour l\'offre « Astronaute lunaire » ?');
  const missing = page.getByTestId('chat-bot-message').last();
  await expect(missing.getByTestId('chat-answer')).toContainText('Je ne trouve aucune offre publiée intitulée');
  await expect(missing.getByTestId('chat-offer')).toHaveCount(0);

  await ask(page, 'Quelle est la recette de la pâte à crêpes ?');
  const unknown = page.getByTestId('chat-bot-message').last();
  await expect(unknown).toHaveAttribute('data-intent', 'UNKNOWN');
  await expect(unknown.getByTestId('chat-answer')).toContainText('Je ne sais pas répondre à cette question');
  await page.screenshot({ path: join(screenshotDir, '18-candidat-assistant-offre.png'), fullPage: true });

  await page.getByTestId('chat-reset').click();
  await expect(page.getByTestId('chat-user-message')).toHaveCount(0);
});
