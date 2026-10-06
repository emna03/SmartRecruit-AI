import { Component, inject, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { errorMessage } from '../../../core/http-errors';
import { CandidateProfile } from '../../../core/models';
import { CandidateProfileService } from '../../../core/services/candidate-profile.service';
import { ToastService } from '../../../core/services/toast.service';
import { LoadingSpinner } from '../../../shared/loading-spinner';
import { SkillsInput } from '../../../shared/skills-input';
import { EducationSection } from './education-section';
import { ExperienceSection } from './experience-section';
import { ProfileInfoForm } from './profile-info-form';

@Component({
  selector: 'app-candidate-profile-page',
  imports: [ReactiveFormsModule, LoadingSpinner, SkillsInput, ProfileInfoForm, EducationSection, ExperienceSection],
  template: `
    <h1 class="page-title">Mon profil</h1>
    <p class="page-subtitle">Un profil complet est visible par les recruteurs auxquels vous postulez.</p>

    @if (loading()) {
      <app-loading-spinner />
    } @else if (error()) {
      <div class="alert alert-danger">{{ error() }}</div>
    } @else if (profile(); as p) {
      <app-profile-info-form [profile]="p" (saved)="profile.set($event)" />

      <div class="card mb-4">
        <div class="card-header bg-white section-title"><i aria-hidden="true" class="bi bi-lightning-charge me-2 text-primary"></i>Compétences</div>
        <div class="card-body">
          <app-skills-input [formControl]="skills" [max]="50" inputId="candidate-skills" />
          <div class="text-end mt-3">
            <button class="btn btn-primary" type="button" (click)="saveSkills()" [disabled]="savingSkills()" data-testid="save-skills">
              @if (savingSkills()) {
                <span class="spinner-border spinner-border-sm me-1"></span>
              }
              Enregistrer les compétences
            </button>
          </div>
        </div>
      </div>

      <app-education-section [profile]="p" (changed)="profile.set($event)" />
      <app-experience-section [profile]="p" (changed)="profile.set($event)" />
    }
  `,
})
export class CandidateProfilePage implements OnInit {
  private readonly profileService = inject(CandidateProfileService);
  private readonly toast = inject(ToastService);

  readonly profile = signal<CandidateProfile | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly savingSkills = signal(false);
  readonly skills = new FormControl<string[]>([], { nonNullable: true });

  ngOnInit(): void {
    this.profileService.get().subscribe({
      next: (profile) => {
        this.profile.set(profile);
        this.skills.setValue(profile.skills);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'Impossible de charger votre profil.'));
        this.loading.set(false);
      },
    });
  }

  saveSkills(): void {
    this.savingSkills.set(true);
    this.profileService.replaceSkills(this.skills.value).subscribe({
      next: (profile) => {
        this.profile.set(profile);
        this.skills.setValue(profile.skills);
        this.savingSkills.set(false);
        this.toast.success('Compétences enregistrées.');
      },
      error: (err) => {
        this.savingSkills.set(false);
        this.toast.error(errorMessage(err, "Les compétences n'ont pas pu être enregistrées."));
      },
    });
  }
}
