import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TURNOVER_DISCLAIMER, TurnoverPrediction } from '../../core/models';
import { displayValue, EXAMPLE_PROFILES, parseReference } from './turnover-fields';
import { TurnoverPredictionPage } from './turnover-prediction-page';

const URL = '/api/hr/turnover/predictions';

const MODEL = {
  name: 'RANDOM_FOREST',
  version: '1.0.0',
  dataset: 'IBM HR Analytics Employee Attrition & Performance',
  explanationMethod: 'OCCLUSION',
  testAccuracy: 0.8197,
  testPrecision: 0.4483,
  testRecall: 0.5532,
  testF1: 0.4952,
  testRocAuc: 0.7844,
} as const;

/** Réponses réellement produites par le modèle (FastAPI) pour les deux exemples fictifs du jeu de test. */
const HIGH: TurnoverPrediction = {
  probability: 0.9166,
  riskLevel: 'ELEVE',
  mediumThreshold: 0.3,
  highThreshold: 0.5,
  factors: [
    { feature: 'totalWorkingYears', label: "Années d'expérience totales", impact: 0.104, direction: 'INCREASES_RISK', referenceValue: '10' },
    { feature: 'jobLevel', label: 'Niveau hiérarchique', impact: 0.0932, direction: 'INCREASES_RISK', referenceValue: '2' },
    { feature: 'monthlyIncome', label: 'Salaire mensuel', impact: 0.0924, direction: 'INCREASES_RISK', referenceValue: '5004' },
    { feature: 'overTime', label: 'Heures supplémentaires', impact: 0.0764, direction: 'INCREASES_RISK', referenceValue: 'false' },
    { feature: 'yearsAtCompany', label: "Ancienneté dans l'entreprise", impact: 0.0749, direction: 'INCREASES_RISK', referenceValue: '5' },
  ],
  model: MODEL,
};

const LOW: TurnoverPrediction = {
  probability: 0.0786,
  riskLevel: 'FAIBLE',
  mediumThreshold: 0.3,
  highThreshold: 0.5,
  factors: [
    { feature: 'jobRole', label: 'Poste', impact: -0.0234, direction: 'DECREASES_RISK', referenceValue: 'SALES_EXECUTIVE' },
    { feature: 'educationField', label: "Domaine d'études", impact: 0.0118, direction: 'INCREASES_RISK', referenceValue: 'LIFE_SCIENCES' },
  ],
  model: MODEL,
};

describe('TurnoverPredictionPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<TurnoverPredictionPage>;
  let page: TurnoverPredictionPage;
  let el: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [TurnoverPredictionPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TurnoverPredictionPage);
    page = fixture.componentInstance;
    el = fixture.nativeElement as HTMLElement;
    await render();
  });

  afterEach(() => http.verify());

  async function render(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function text(id: string): string {
    return el.querySelector(`[data-testid=${id}]`)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  function all(id: string): HTMLElement[] {
    return Array.from(el.querySelectorAll<HTMLElement>(`[data-testid=${id}]`));
  }

  async function click(id: string): Promise<void> {
    el.querySelector<HTMLButtonElement>(`[data-testid=${id}]`)!.click();
    await render();
  }

  async function submit(): Promise<void> {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await render();
  }

  it('always shows the required disclaimer and the 24 fields', () => {
    expect(text('turnover-disclaimer')).toBe(TURNOVER_DISCLAIMER);
    expect(TURNOVER_DISCLAIMER).toBe(
      'Cette prédiction est une estimation produite par un modèle entraîné sur un dataset de démonstration et ne doit pas être utilisée seule pour prendre une décision RH.',
    );
    expect(el.querySelectorAll('select, input[type=number]')).toHaveLength(24);
    expect(el.textContent).not.toMatch(/Âge|Genre|Situation familiale/);
  });

  it('never sends an incomplete form', async () => {
    await submit();

    http.expectNone(URL);
    expect(text('turnover-error')).toContain('Complétez ou corrigez');
    expect(el.querySelectorAll('.is-invalid').length).toBe(24);
  });

  it('sends exactly the entered variables and shows a high risk with its factors and the real model metrics', async () => {
    await click('turnover-example-2');
    await submit();

    const request = http.expectOne(URL);
    expect(request.request.body).toEqual(EXAMPLE_PROFILES[1].values);
    expect(Object.keys(request.request.body)).toHaveLength(24);
    request.flush(HIGH);
    await render();

    expect(all('turnover-result')[0].getAttribute('data-level')).toBe('ELEVE');
    expect(text('turnover-level')).toBe('Risque élevé');
    expect(text('turnover-probability')).toBe('91,7 %');
    expect(text('turnover-score-explanation')).toContain('pas une certitude');
    expect(all('turnover-factor')).toHaveLength(5);
    const overTime = el.querySelector<HTMLElement>('[data-feature=overTime]')!;
    expect(overTime.textContent).toContain('Heures supplémentaires : Oui');
    expect(overTime.textContent).toContain('augmente le risque');
    expect(overTime.textContent).toContain('+7,6 point(s) de score par rapport à la valeur de référence « Non »');
    expect(text('turnover-model')).toContain('Forêt aléatoire (Random Forest) v1.0.0');
    expect(text('turnover-model')).toContain('ROC-AUC 0,78 · rappel 55,3 % · précision 44,8 % · F1 0,50 · accuracy 82,0 %');
    expect(text('turnover-result-disclaimer')).toBe(TURNOVER_DISCLAIMER);
  });

  it('shows a low risk and factors that decrease the risk', async () => {
    await click('turnover-example-1');
    await submit();
    http.expectOne(URL).flush(LOW);
    await render();

    expect(text('turnover-level')).toBe('Risque faible');
    expect(text('turnover-probability')).toBe('7,9 %');
    const jobRole = el.querySelector<HTMLElement>('[data-feature=jobRole]')!;
    expect(jobRole.getAttribute('data-direction')).toBe('DECREASES_RISK');
    expect(jobRole.textContent).toContain('Poste : Directeur de production');
    expect(jobRole.textContent).toContain('« Commercial senior (Sales Executive) »');
  });

  it('only proposes the job roles of the chosen department and clears an incompatible one', async () => {
    page.form.controls['department'].setValue('HUMAN_RESOURCES');
    await render();
    const roles = Array.from(el.querySelectorAll<HTMLOptionElement>('[data-testid=t-jobRole] option')).map((o) => o.textContent!.trim());
    expect(roles).toEqual(['Choisir...', 'Manager', 'Chargé RH']);

    page.form.controls['jobRole'].setValue('MANAGER');
    page.form.controls['department'].setValue('SALES');
    expect(page.form.controls['jobRole'].value).toBe('MANAGER');
    page.form.controls['jobRole'].setValue('SALES_EXECUTIVE');
    page.form.controls['department'].setValue('RESEARCH_DEVELOPMENT');
    expect(page.form.controls['jobRole'].value).toBeNull();
  });

  it('refuses inconsistent years without calling the API', async () => {
    await click('turnover-example-1');
    page.form.controls['totalWorkingYears'].setValue(3);
    page.form.controls['yearsSinceLastPromotion'].setValue(9);
    page.form.markAllAsTouched();
    await submit();

    http.expectNone(URL);
    expect(text('turnover-consistency')).toContain("ne peut pas dépasser l'expérience totale");
    expect(text('turnover-consistency')).toContain("ne peuvent pas dépasser l'ancienneté");
  });

  it('refuses out-of-range and decimal values typed in the form', async () => {
    await click('turnover-example-1');
    const income = el.querySelector<HTMLInputElement>('[data-testid=t-monthlyIncome]')!;
    income.value = '500';
    income.dispatchEvent(new Event('input'));
    const distance = el.querySelector<HTMLInputElement>('[data-testid=t-distanceFromHome]')!;
    distance.value = '2.5';
    distance.dispatchEvent(new Event('input'));
    await submit();

    http.expectNone(URL);
    expect(el.textContent).toContain('La valeur minimale est 1009.');
    expect(el.textContent).toContain('Saisissez un nombre entier.');
  });

  it('displays the error when the AI service is unavailable and keeps the entered values', async () => {
    await click('turnover-example-2');
    await submit();
    http.expectOne(URL).flush(
      { message: 'Le service IA est momentanément indisponible. Réessayez plus tard.' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    await render();

    expect(text('turnover-error')).toContain('momentanément indisponible');
    expect(all('turnover-result')).toHaveLength(0);
    expect(page.form.controls['monthlyIncome'].value).toBe(2121);
  });

  it('shows the backend validation errors on the fields', async () => {
    await click('turnover-example-2');
    await submit();
    http.expectOne(URL).flush(
      { message: 'Données invalides', fieldErrors: { monthlyIncome: 'doit être supérieur ou égal à 1009' } },
      { status: 400, statusText: 'Bad Request' },
    );
    await render();

    expect(text('turnover-error')).toBe('Données invalides');
    expect(el.textContent).toContain('doit être supérieur ou égal à 1009');
  });
});

describe('turnover field helpers', () => {
  it('formats entered values and reference values', () => {
    expect(displayValue('overTime', true)).toBe('Oui');
    expect(displayValue('businessTravel', 'TRAVEL_RARELY')).toBe('Rares');
    expect(displayValue('jobSatisfaction', 4)).toBe('4 — Très élevée');
    expect(displayValue('monthlyIncome', 5004)).toBe((5004).toLocaleString('fr-FR'));
    expect(displayValue('monthlyIncome', null)).toBe('—');
    expect(parseReference('overTime', 'false')).toBe(false);
    expect(parseReference('jobLevel', '2')).toBe(2);
    expect(parseReference('jobRole', 'SALES_EXECUTIVE')).toBe('SALES_EXECUTIVE');
  });
});
