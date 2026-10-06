import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../../core/http-errors';
import {
  CandidateCv,
  CV_SECTION_LABELS,
  CvImportResult,
  DetectedSkill,
  DOCUMENT_LANGUAGE_LABELS,
  EDUCATION_LEVEL_LABELS,
  LANGUAGE_LEVEL_LABELS,
} from '../../../core/models';
import { checkCvFile, CvService, MAX_CV_SIZE_MB } from '../../../core/services/cv.service';
import { ToastService } from '../../../core/services/toast.service';
import { LoadingSpinner } from '../../../shared/loading-spinner';

interface SkillGroup {
  category: string;
  items: DetectedSkill[];
}

const MONTHS = ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'];

/** « 2023-03 » -> « mars 2023 », « 2023 » -> « 2023 ». */
export function formatPartialDate(value: string | null): string {
  if (!value) {
    return '';
  }
  const [year, month] = value.split('-');
  return month ? `${MONTHS[Number(month) - 1]} ${year}` : year;
}

export function formatDuration(months: number | null): string {
  if (months === null || months === undefined) {
    return '';
  }
  const years = Math.floor(months / 12);
  const rest = months % 12;
  const parts = [];
  if (years) {
    parts.push(`${years} an${years > 1 ? 's' : ''}`);
  }
  if (rest || !years) {
    parts.push(`${rest} mois`);
  }
  return parts.join(' ');
}

function groupByCategory(skills: DetectedSkill[]): SkillGroup[] {
  const groups = new Map<string, DetectedSkill[]>();
  for (const skill of skills) {
    groups.set(skill.category, [...(groups.get(skill.category) ?? []), skill]);
  }
  return [...groups.entries()].map(([category, items]) => ({ category, items }));
}

/** Page « Mon CV » : import du PDF, analyse automatique (CV Parser) et affichage des informations extraites. */
@Component({
  selector: 'app-candidate-cv-page',
  imports: [DatePipe, DecimalPipe, RouterLink, LoadingSpinner],
  templateUrl: './candidate-cv-page.html',
  styles: `
    .dropzone {
      border: 2px dashed var(--sr-border);
      border-radius: var(--sr-radius);
      background: #fff;
      padding: 2rem 1rem;
      text-align: center;
      transition: border-color 0.15s ease, background 0.15s ease;
      cursor: pointer;
    }
    .dropzone.over,
    .dropzone:hover {
      border-color: var(--sr-primary);
      background: var(--sr-primary-soft);
    }
    .dropzone .bi {
      font-size: 2.25rem;
      color: var(--sr-primary);
    }
    .analyzing {
      border: 1px solid var(--sr-primary);
      background: var(--sr-primary-soft);
      border-radius: var(--sr-radius);
    }
    .category-label {
      font-size: 0.75rem;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: #6b7280;
      font-weight: 600;
    }
    .skill-badge.tech {
      background: #e0f2fe;
      color: #075985;
    }
    .timeline-item + .timeline-item {
      border-top: 1px solid var(--sr-border);
    }
  `,
})
export class CandidateCvPage implements OnInit {
  private readonly cvService = inject(CvService);
  private readonly toast = inject(ToastService);

  readonly maxSizeMb = MAX_CV_SIZE_MB;
  readonly educationLabels = EDUCATION_LEVEL_LABELS;
  readonly languageLabels = LANGUAGE_LEVEL_LABELS;
  readonly sectionLabels = CV_SECTION_LABELS;
  readonly documentLanguageLabels = DOCUMENT_LANGUAGE_LABELS;
  readonly formatDate = formatPartialDate;
  readonly formatDuration = formatDuration;

  readonly cv = signal<CandidateCv | null>(null);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  /** Envoi + analyse en cours (peut prendre quelques secondes). */
  readonly analyzing = signal(false);
  readonly uploadError = signal<string | null>(null);
  readonly dragOver = signal(false);
  readonly deleting = signal(false);
  readonly confirmDelete = signal(false);
  readonly importing = signal(false);
  readonly importResult = signal<CvImportResult | null>(null);
  readonly importSkills = signal(true);
  readonly importEducations = signal(true);
  readonly importExperiences = signal(true);

  readonly analysis = computed(() => this.cv()?.analysis ?? null);
  readonly technologyGroups = computed(() => groupByCategory(this.analysis()?.technologies ?? []));
  readonly skillGroups = computed(() => groupByCategory(this.analysis()?.skills ?? []));
  readonly busy = computed(() => this.analyzing() || this.deleting() || this.importing());

  ngOnInit(): void {
    this.cvService.get().subscribe({
      next: (cv) => {
        this.cv.set(cv);
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(errorMessage(err, 'Impossible de charger votre CV.'));
        this.loading.set(false);
      },
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (file) {
      void this.send(file);
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    if (!this.busy()) {
      this.dragOver.set(true);
    }
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
    const file = event.dataTransfer?.files?.[0];
    if (file && !this.busy()) {
      void this.send(file);
    }
  }

  async send(file: File): Promise<void> {
    this.uploadError.set(null);
    this.importResult.set(null);
    const problem = await checkCvFile(file);
    if (problem) {
      this.uploadError.set(problem);
      return;
    }
    this.analyzing.set(true);
    this.cvService.upload(file).subscribe({
      next: (cv) => this.analysisDone(cv),
      error: (err) => {
        this.analyzing.set(false);
        this.uploadError.set(errorMessage(err, "Le CV n'a pas pu être importé."));
      },
    });
  }

  reanalyze(): void {
    this.uploadError.set(null);
    this.importResult.set(null);
    this.analyzing.set(true);
    this.cvService.analyze().subscribe({
      next: (cv) => this.analysisDone(cv),
      error: (err) => {
        this.analyzing.set(false);
        this.uploadError.set(errorMessage(err, "L'analyse n'a pas pu être relancée."));
      },
    });
  }

  remove(): void {
    this.deleting.set(true);
    this.cvService.delete().subscribe({
      next: () => {
        this.cv.set(null);
        this.deleting.set(false);
        this.confirmDelete.set(false);
        this.importResult.set(null);
        this.toast.success('CV supprimé.');
      },
      error: (err) => {
        this.deleting.set(false);
        this.toast.error(errorMessage(err, "Le CV n'a pas pu être supprimé."));
      },
    });
  }

  importIntoProfile(): void {
    this.importing.set(true);
    this.cvService
      .importIntoProfile({
        skills: this.importSkills(),
        educations: this.importEducations(),
        experiences: this.importExperiences(),
      })
      .subscribe({
        next: (result) => {
          this.importing.set(false);
          this.importResult.set(result);
          const added = result.skillsAdded + result.educationsAdded + result.experiencesAdded;
          this.toast.success(added ? 'Profil mis à jour à partir du CV.' : 'Votre profil contenait déjà ces informations.');
        },
        error: (err) => {
          this.importing.set(false);
          this.toast.error(errorMessage(err, "L'import dans le profil a échoué."));
        },
      });
  }

  private analysisDone(cv: CandidateCv): void {
    this.cv.set(cv);
    this.analyzing.set(false);
    if (cv.parsingStatus === 'TERMINE') {
      this.toast.success('CV analysé avec succès.');
    }
  }
}
