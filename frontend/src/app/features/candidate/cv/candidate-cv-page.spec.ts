import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CandidateCv } from '../../../core/models';
import { CandidateCvPage, formatDuration, formatPartialDate } from './candidate-cv-page';

const URL = '/api/candidate/cv';
const PDF = new File(['%PDF-1.4\n% CV fictif\n%%EOF'], 'cv.pdf', { type: 'application/pdf' });

function analyzedCv(overrides: Partial<CandidateCv> = {}): CandidateCv {
  return {
    id: 7,
    originalFileName: 'cv-camille.pdf',
    fileSize: 2753,
    parsingStatus: 'TERMINE',
    parsingError: null,
    parsedAt: '2026-10-06T10:00:00',
    updatedAt: '2026-10-06T10:00:00',
    analysis: {
      parserVersion: '1.0.0',
      method: 'Extraction pdfplumber ; lexique ; règles. Déterministe, sans LLM.',
      documentLanguage: 'fr',
      pageCount: 1,
      textLength: 1415,
      sectionsDetected: ['experience', 'formation', 'langues'],
      skills: [{ name: 'API REST', category: 'Développement', occurrences: 3 }],
      technologies: [
        { name: 'Java', category: 'Langage', occurrences: 3 },
        { name: 'Spring Boot', category: 'Framework back-end', occurrences: 3 },
        { name: 'Angular', category: 'Framework front-end', occurrences: 2 },
        { name: 'MySQL', category: 'Base de données', occurrences: 1 },
      ],
      educations: [
        {
          degree: "Diplôme d'ingénieur en Génie Logiciel",
          fieldOfStudy: 'Génie Logiciel',
          institution: 'École Supérieure Fictive',
          startDate: '2019',
          endDate: '2022',
          level: 'BAC_PLUS_5',
        },
      ],
      experiences: [
        {
          jobTitle: 'Développeuse Full Stack',
          company: 'Société Exemple',
          location: 'Tunis',
          startDate: '2023-03',
          endDate: null,
          current: true,
          durationMonths: 44,
          description: 'API REST avec Spring Boot.',
        },
      ],
      languages: [{ name: 'Anglais', level: 'COURANT', cefr: 'B2', certification: 'TOEIC 850' }],
      totalExperienceMonths: 50,
      highestEducationLevel: 'BAC_PLUS_5',
      warnings: [],
      processingTimeMs: 25,
    },
    ...overrides,
  };
}

describe('CandidateCvPage', () => {
  let http: HttpTestingController;

  async function create(initial: CandidateCv | null) {
    TestBed.configureTestingModule({
      imports: [CandidateCvPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(CandidateCvPage);
    fixture.detectChanges();
    const req = http.expectOne(URL);
    if (initial) {
      req.flush(initial);
    } else {
      req.flush({ status: 404, message: 'Aucun CV importé' }, { status: 404, statusText: 'Not Found' });
    }
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    return { fixture, page: fixture.componentInstance, el, text: (id: string) => el.querySelector(`[data-testid=${id}]`)?.textContent ?? '' };
  }

  afterEach(() => http.verify());

  it('offers the drop zone when no CV has been imported', async () => {
    const { el } = await create(null);

    expect(el.querySelector('[data-testid=cv-dropzone]')).not.toBeNull();
    expect(el.querySelector('[data-testid=cv-summary]')).toBeNull();
  });

  it('displays the information extracted from the CV', async () => {
    const { text } = await create(analyzedCv());

    expect(text('cv-status')).toContain('CV analysé');
    expect(text('cv-skills')).toContain('Java');
    expect(text('cv-skills')).toContain('Spring Boot');
    expect(text('cv-skills')).toContain('Framework front-end');
    expect(text('cv-skills')).toContain('API REST');
    expect(text('cv-educations')).toContain("Diplôme d'ingénieur en Génie Logiciel");
    expect(text('cv-educations')).toContain('2019 – 2022');
    expect(text('cv-experiences')).toContain('Développeuse Full Stack');
    expect(text('cv-experiences')).toContain('mars 2023 – aujourd’hui');
    expect(text('cv-languages')).toContain('Courant');
    expect(text('cv-languages')).toContain('TOEIC 850');
    expect(text('cv-summary')).toContain('4 ans 2 mois');
    expect(text('cv-method')).toContain('Aucun modèle de langage');
  });

  it('shows a loader during the analysis then the result', async () => {
    const { page, fixture, el, text } = await create(null);

    await page.send(PDF);
    fixture.detectChanges();
    expect(el.querySelector('[data-testid=cv-analyzing]')).not.toBeNull();

    const req = http.expectOne({ method: 'POST', url: URL });
    expect((req.request.body as FormData).get('file')).toBeInstanceOf(File);
    req.flush(analyzedCv());
    await fixture.whenStable();

    expect(el.querySelector('[data-testid=cv-analyzing]')).toBeNull();
    expect(text('cv-skills')).toContain('MySQL');
  });

  it('refuses a non-PDF file before sending it', async () => {
    const { page, fixture, text } = await create(null);

    await page.send(new File(['bonjour'], 'cv.pdf', { type: 'application/pdf' }));
    fixture.detectChanges();

    http.expectNone({ method: 'POST', url: URL });
    expect(text('cv-error')).toContain("n'est pas un PDF valide");
  });

  it('displays the message returned by the server', async () => {
    const { page, fixture, text } = await create(null);

    await page.send(PDF);
    http.expectOne({ method: 'POST', url: URL }).flush(
      { status: 422, message: 'Le PDF ne contient pas de texte exploitable (document scanné ou image).' },
      { status: 422, statusText: 'Unprocessable Content' },
    );
    await fixture.whenStable();

    expect(page.analyzing()).toBe(false);
    expect(text('cv-error')).toContain('texte exploitable');
  });

  it('explains a failed analysis and allows a retry', async () => {
    const failed = analyzedCv({
      parsingStatus: 'ECHEC',
      parsingError: 'Le service IA est momentanément indisponible. Réessayez plus tard.',
      analysis: null,
    });
    const { page, fixture, text } = await create(failed);

    expect(text('cv-parsing-error')).toContain('momentanément indisponible');
    page.reanalyze();
    http.expectOne({ method: 'POST', url: `${URL}/analyze` }).flush(analyzedCv());
    await fixture.whenStable();

    expect(text('cv-status')).toContain('CV analysé');
  });

  it('imports the selected elements into the profile', async () => {
    const { page, fixture, text } = await create(analyzedCv());
    page.importExperiences.set(false);

    page.importIntoProfile();
    const req = http.expectOne(`${URL}/import`);
    expect(req.request.body).toEqual({ skills: true, educations: true, experiences: false });
    req.flush({
      skillsAdded: 5,
      educationsAdded: 1,
      experiencesAdded: 0,
      skipped: 0,
      educationLevelSet: true,
      yearsOfExperienceSet: false,
      profile: {},
    });
    await fixture.whenStable();

    expect(text('cv-import-result')).toContain('5 compétence(s), 1 formation(s)');
  });
});

describe('CV formatting helpers', () => {
  it('formats partial dates and durations', () => {
    expect(formatPartialDate('2023-03')).toBe('mars 2023');
    expect(formatPartialDate('2023')).toBe('2023');
    expect(formatPartialDate(null)).toBe('');
    expect(formatDuration(50)).toBe('4 ans 2 mois');
    expect(formatDuration(12)).toBe('1 an');
    expect(formatDuration(6)).toBe('6 mois');
  });
});
