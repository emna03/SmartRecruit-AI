import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Téléphone : même règle que le backend (vide autorisé). */
export const PHONE_PATTERN = /^[+0-9 ().-]{6,30}$/;
/** Site web : http:// ou https:// (vide autorisé). */
export const WEBSITE_PATTERN = /^https?:\/\/\S+$/;

function isEmpty(value: unknown): boolean {
  return value === null || value === undefined || value === '';
}

/**
 * Validateur de groupe : la valeur de endKey doit être >= à celle de startKey
 * (dates ISO « yyyy-MM-dd » ou nombres). Erreur posée sur le groupe : { [errorKey]: true }.
 */
export function orderedFields(startKey: string, endKey: string, errorKey: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const start = group.get(startKey)?.value;
    const end = group.get(endKey)?.value;
    if (isEmpty(start) || isEmpty(end)) {
      return null;
    }
    const inOrder = typeof start === 'number' || typeof end === 'number' ? Number(end) >= Number(start) : end >= start;
    return inOrder ? null : { [errorKey]: true };
  };
}

/** Date du jour au format ISO local « yyyy-MM-dd ». */
export function todayIso(): string {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 10);
}

/** La date ne doit pas être dans le futur. */
export const notInFuture: ValidatorFn = (control) =>
  !isEmpty(control.value) && control.value > todayIso() ? { future: true } : null;
