import { Component, input } from '@angular/core';
import { AbstractControl } from '@angular/forms';

/** Message d'erreur sous un champ, affiché dès que le champ a été touché. */
@Component({
  selector: 'app-field-error',
  template: `
    @if (message(); as text) {
      <div class="invalid-feedback d-block">{{ text }}</div>
    }
  `,
})
export class FieldError {
  readonly control = input.required<AbstractControl | null>();
  /** Messages personnalisés par clé d'erreur (ex. { pattern: 'Format invalide' }). */
  readonly messages = input<Record<string, string>>({});

  message(): string | null {
    const control = this.control();
    if (!control || !control.errors || !(control.touched || control.dirty)) {
      return null;
    }
    const errors = control.errors;
    const key = Object.keys(errors)[0];
    const custom = this.messages()[key];
    if (custom) {
      return custom;
    }
    switch (key) {
      case 'required':
        return 'Ce champ est obligatoire.';
      case 'email':
        return 'Adresse email invalide.';
      case 'minlength':
        return `Au moins ${errors['minlength'].requiredLength} caractères.`;
      case 'maxlength':
        return `${errors['maxlength'].requiredLength} caractères maximum.`;
      case 'min':
        return `La valeur minimale est ${errors['min'].min}.`;
      case 'max':
        return `La valeur maximale est ${errors['max'].max}.`;
      case 'server':
        return errors['server'];
      default:
        return 'Valeur invalide.';
    }
  }
}
