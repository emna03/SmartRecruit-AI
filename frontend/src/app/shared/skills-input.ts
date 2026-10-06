import { Component, DestroyRef, forwardRef, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { catchError, debounceTime, distinctUntilChanged, of, Subject, switchMap } from 'rxjs';
import { SkillService } from '../core/services/skill.service';

/**
 * Saisie de compétences sous forme d'étiquettes, avec suggestions issues du référentiel du backend.
 * Utilisable avec formControlName (valeur : string[]) ou seul via writeValue / la sortie du formulaire parent.
 */
@Component({
  selector: 'app-skills-input',
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => SkillsInput), multi: true }],
  template: `
    <div class="form-control d-flex flex-wrap align-items-center gap-1 position-relative"
         [class.is-invalid]="invalid()" [class.bg-body-secondary]="disabled()">
      @for (skill of skills(); track skill) {
        <span class="skill-badge mb-0">
          {{ skill }}
          @if (!disabled()) {
            <button type="button" class="btn-close btn-close-sm ms-1 align-middle" style="font-size: .55rem"
                    [attr.aria-label]="'Retirer ' + skill" (click)="remove(skill)"></button>
          }
        </span>
      }
      <input class="border-0 flex-grow-1 py-1" style="outline: none; min-width: 10rem; background: transparent"
             [id]="inputId()" [placeholder]="placeholder()" [disabled]="disabled()" [value]="query()"
             autocomplete="off" (input)="onInput($any($event.target).value)" (keydown)="onKeydown($event)"
             (blur)="onBlur()" />
      @if (suggestions().length) {
        <ul class="list-group position-absolute start-0 top-100 w-100 shadow-sm mt-1" style="z-index: 20">
          @for (s of suggestions(); track s) {
            <li class="list-group-item list-group-item-action py-2" role="button" (mousedown)="add(s)">{{ s }}</li>
          }
        </ul>
      }
    </div>
    <div class="form-text">Appuyez sur Entrée ou virgule pour ajouter une compétence ({{ skills().length }}/{{ max() }}).</div>
  `,
})
export class SkillsInput implements ControlValueAccessor {
  private readonly skillService = inject(SkillService);

  readonly placeholder = input('Ex. Java, Angular, SQL...');
  readonly max = input(30);
  readonly invalid = input(false);
  readonly inputId = input('skills-input');

  readonly skills = signal<string[]>([]);
  readonly query = signal('');
  readonly suggestions = signal<string[]>([]);
  readonly disabled = signal(false);

  private readonly search$ = new Subject<string>();
  private onChange: (value: string[]) => void = () => {};
  private onTouched: () => void = () => {};

  constructor() {
    this.search$
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((q) => (q.length < 1 ? of([]) : this.skillService.search(q).pipe(catchError(() => of([]))))),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((found) => {
        const current = new Set(this.skills().map((s) => s.toLowerCase()));
        this.suggestions.set(found.map((s) => s.name).filter((name) => !current.has(name.toLowerCase())));
      });
  }

  onInput(value: string): void {
    if (value.includes(',')) {
      value.split(',').slice(0, -1).forEach((part) => this.add(part));
      value = value.split(',').pop() ?? '';
    }
    this.query.set(value);
    this.search$.next(value.trim());
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter') {
      event.preventDefault();
      this.add(this.query());
    } else if (event.key === 'Backspace' && !this.query() && this.skills().length) {
      this.remove(this.skills()[this.skills().length - 1]);
    }
  }

  onBlur(): void {
    if (this.query().trim()) {
      this.add(this.query());
    }
    this.suggestions.set([]);
    this.onTouched();
  }

  add(raw: string): void {
    const skill = raw.trim().replace(/\s+/g, ' ');
    this.query.set('');
    this.suggestions.set([]);
    if (!skill || skill.length > 100 || this.skills().length >= this.max()) {
      return;
    }
    if (this.skills().some((s) => s.toLowerCase() === skill.toLowerCase())) {
      return;
    }
    this.update([...this.skills(), skill]);
  }

  remove(skill: string): void {
    this.update(this.skills().filter((s) => s !== skill));
  }

  writeValue(value: string[] | null): void {
    this.skills.set(value ? [...value] : []);
  }

  registerOnChange(fn: (value: string[]) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  private update(skills: string[]): void {
    this.skills.set(skills);
    this.onChange(skills);
    this.onTouched();
  }
}
