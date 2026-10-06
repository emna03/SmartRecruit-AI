import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { applyFieldErrors, errorMessage } from '../../core/http-errors';
import {
  RiskLevel,
  TURNOVER_DISCLAIMER,
  TurnoverDepartment,
  TurnoverFactor,
  TurnoverFeature,
  TurnoverModelInfo,
  TurnoverPrediction,
  TurnoverPredictionRequest,
} from '../../core/models';
import { TurnoverService } from '../../core/services/turnover.service';
import { FieldError } from '../../shared/field-error';
import {
  displayValue,
  EXAMPLE_PROFILES,
  FieldOption,
  FieldValue,
  JOB_ROLES_BY_DEPARTMENT,
  parseReference,
  TURNOVER_FIELDS,
  TURNOVER_SECTIONS,
  TurnoverField,
} from './turnover-fields';

export const RISK_LEVELS: Record<RiskLevel, { label: string; css: string; icon: string }> = {
  FAIBLE: { label: 'Risque faible', css: 'success', icon: 'bi-shield-check' },
  MOYEN: { label: 'Risque moyen', css: 'warning', icon: 'bi-exclamation-circle' },
  ELEVE: { label: 'Risque élevé', css: 'danger', icon: 'bi-exclamation-triangle' },
};

export const MODEL_NAMES: Record<TurnoverModelInfo['name'], string> = {
  LOGISTIC_REGRESSION: 'Régression logistique',
  RANDOM_FOREST: 'Forêt aléatoire (Random Forest)',
};

/** Erreurs de cohérence entre champs (mêmes règles que Spring Boot et FastAPI). */
export const CONSISTENCY_MESSAGES: Record<string, string> = {
  yearsAtCompanyWithinCareer: "L'ancienneté dans l'entreprise ne peut pas dépasser l'expérience totale.",
  yearsWithinCompany:
    "Les années dans le poste, avec le manager et depuis la dernière promotion ne peuvent pas dépasser l'ancienneté dans l'entreprise.",
  jobRoleInDepartment: "Ce poste n'existe pas dans le département choisi.",
};

const integer: ValidatorFn = (control) =>
  control.value === null || control.value === '' || Number.isInteger(control.value) ? null : { integer: true };

function filled(value: unknown): value is number {
  return typeof value === 'number' && !Number.isNaN(value);
}

const consistency: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const v = group.value as Partial<Record<TurnoverFeature, FieldValue | null>>;
  const errors: ValidationErrors = {};
  if (filled(v.yearsAtCompany) && filled(v.totalWorkingYears) && v.yearsAtCompany > v.totalWorkingYears) {
    errors['yearsAtCompanyWithinCareer'] = true;
  }
  if (filled(v.yearsAtCompany)) {
    const company = v.yearsAtCompany;
    if ([v.yearsInCurrentRole, v.yearsWithCurrManager, v.yearsSinceLastPromotion].some((y) => filled(y) && y > company)) {
      errors['yearsWithinCompany'] = true;
    }
  }
  const department = v.department as TurnoverDepartment | null | undefined;
  if (department && v.jobRole && !JOB_ROLES_BY_DEPARTMENT[department].includes(v.jobRole as never)) {
    errors['jobRoleInDepartment'] = true;
  }
  return Object.keys(errors).length ? errors : null;
};

function buildForm(): FormGroup<Record<string, FormControl<FieldValue | null>>> {
  const controls: Record<string, FormControl<FieldValue | null>> = {};
  for (const field of TURNOVER_FIELDS) {
    const validators = [Validators.required];
    if (!field.options) {
      validators.push(integer, Validators.min(field.min!), Validators.max(field.max!));
    }
    controls[field.key] = new FormControl<FieldValue | null>(null, validators);
  }
  return new FormGroup(controls, { validators: consistency });
}

/**
 * Estimation du risque de turnover d'un employé (espaces recruteur et administrateur) : démonstration académique
 * d'aide à la décision. La saisie n'est ni enregistrée ni stockée dans le navigateur.
 */
@Component({
  selector: 'app-turnover-prediction-page',
  imports: [ReactiveFormsModule, FieldError],
  templateUrl: './turnover-prediction-page.html',
})
export class TurnoverPredictionPage {
  private readonly turnover = inject(TurnoverService);

  protected readonly sections = TURNOVER_SECTIONS;
  protected readonly examples = EXAMPLE_PROFILES;
  protected readonly disclaimer = TURNOVER_DISCLAIMER;
  protected readonly riskLevels = RISK_LEVELS;
  protected readonly modelNames = MODEL_NAMES;
  protected readonly consistencyMessages = CONSISTENCY_MESSAGES;

  readonly form = buildForm();
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);
  readonly result = signal<TurnoverPrediction | null>(null);
  /** Valeurs envoyées pour la prédiction affichée (pour expliquer les facteurs). */
  readonly submitted = signal<TurnoverPredictionRequest | null>(null);
  private readonly department = toSignal(this.form.controls['department'].valueChanges, { initialValue: null });

  constructor() {
    this.form.controls['department'].valueChanges.subscribe((department) => {
      const jobRole = this.form.controls['jobRole'];
      if (department && jobRole.value && !JOB_ROLES_BY_DEPARTMENT[department as TurnoverDepartment].includes(jobRole.value as never)) {
        jobRole.setValue(null);
      }
    });
  }

  /** Postes proposés : uniquement ceux du département choisi. */
  optionsFor(field: TurnoverField): FieldOption[] {
    const department = this.department() as TurnoverDepartment | null;
    if (field.key === 'jobRole' && department) {
      return field.options!.filter((o) => JOB_ROLES_BY_DEPARTMENT[department].includes(o.value as never));
    }
    return field.options ?? [];
  }

  control(key: TurnoverFeature): FormControl<FieldValue | null> {
    return this.form.controls[key];
  }

  invalid(key: TurnoverFeature): boolean {
    const control = this.control(key);
    return control.invalid && (control.touched || control.dirty);
  }

  consistencyErrors(): string[] {
    const errors = this.form.errors ?? {};
    return Object.keys(errors).map((key) => CONSISTENCY_MESSAGES[key]).filter(Boolean);
  }

  fillExample(index: number): void {
    this.form.reset(this.examples[index].values as unknown as Record<string, FieldValue>);
    this.result.set(null);
    this.error.set(null);
  }

  clear(): void {
    this.form.reset();
    this.result.set(null);
    this.error.set(null);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set('Complétez ou corrigez les champs signalés avant de lancer l\u2019estimation.');
      return;
    }
    const request = this.form.getRawValue() as unknown as TurnoverPredictionRequest;
    this.sending.set(true);
    this.error.set(null);
    this.turnover.predict(request).subscribe({
      next: (prediction) => {
        this.submitted.set(request);
        this.result.set(prediction);
        this.sending.set(false);
      },
      error: (err: unknown) => {
        this.result.set(null);
        applyFieldErrors(this.form, err);
        this.error.set(errorMessage(err, "L'estimation n'a pas pu être calculée. Réessayez plus tard."));
        this.sending.set(false);
      },
    });
  }

  percent(value: number): string {
    return `${(value * 100).toLocaleString('fr-FR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} %`;
  }

  metric(value: number): string {
    return value.toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  enteredValue(factor: TurnoverFactor): string {
    return displayValue(factor.feature, this.submitted()?.[factor.feature]);
  }

  /** Effet du facteur, exprimé dans l'unité de la méthode d'explication du modèle. */
  impactText(factor: TurnoverFactor, method: TurnoverModelInfo['explanationMethod']): string {
    const sign = factor.impact > 0 ? '+' : '−';
    if (method === 'OCCLUSION') {
      const points = Math.abs(factor.impact * 100).toLocaleString('fr-FR', { maximumFractionDigits: 1 });
      const reference = factor.referenceValue === null ? '' : ` « ${displayValue(factor.feature, parseReference(factor.feature, factor.referenceValue))} »`;
      return `${sign}${points} point(s) de score par rapport à la valeur de référence${reference}`;
    }
    const value = Math.abs(factor.impact).toLocaleString('fr-FR', { maximumFractionDigits: 2 });
    return `${sign}${value} (log-odds) par rapport à un employé moyen du jeu d'entraînement`;
  }

  /** Largeur relative de la barre d'un facteur (le plus fort = 100 %). */
  barWidth(factor: TurnoverFactor, factors: TurnoverFactor[]): number {
    const max = Math.max(...factors.map((f) => Math.abs(f.impact)));
    return max > 0 ? Math.round((Math.abs(factor.impact) / max) * 100) : 0;
  }
}
