import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, Observable, of, throwError } from 'rxjs';
import { API_URL } from '../api.config';
import { CandidateCv, CvImportRequest, CvImportResult, ProfessionalSummary } from '../models';

/** Taille maximale d'un CV (identique à MAX_CV_SIZE_MB côté serveur, qui reste seul juge). */
export const MAX_CV_SIZE_MB = 5;
const PDF_SIGNATURE = '%PDF-';

/** CV du candidat connecté : import, remplacement, analyse par le CV Parser, ajout au profil. */
@Injectable({ providedIn: 'root' })
export class CvService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_URL}/candidate/cv`;

  /** CV actuel, ou null si aucun CV n'a encore été importé. */
  get(): Observable<CandidateCv | null> {
    return this.http.get<CandidateCv>(this.base).pipe(
      catchError((err: unknown) =>
        err instanceof HttpErrorResponse && err.status === 404 ? of(null) : throwError(() => err),
      ),
    );
  }

  upload(file: File): Observable<CandidateCv> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.post<CandidateCv>(this.base, body);
  }

  analyze(): Observable<CandidateCv> {
    return this.http.post<CandidateCv>(`${this.base}/analyze`, null);
  }

  importIntoProfile(request: CvImportRequest): Observable<CvImportResult> {
    return this.http.post<CvImportResult>(`${this.base}/import`, request);
  }

  /** Résumé professionnel généré par Gemini à partir des données déjà extraites du CV. */
  generateProfessionalSummary(): Observable<ProfessionalSummary> {
    return this.http.post<ProfessionalSummary>(`${this.base}/professional-summary`, null);
  }

  delete(): Observable<void> {
    return this.http.delete<void>(this.base);
  }
}

/**
 * Contrôle rapide avant envoi (confort uniquement : le serveur revérifie tout).
 * Renvoie un message d'erreur, ou null si le fichier peut être envoyé.
 */
export async function checkCvFile(file: File): Promise<string | null> {
  if (file.size === 0) {
    return 'Le fichier est vide.';
  }
  if (file.size > MAX_CV_SIZE_MB * 1024 * 1024) {
    return `Le fichier dépasse la taille maximale de ${MAX_CV_SIZE_MB} Mo.`;
  }
  const head = new Uint8Array(await file.slice(0, 1024).arrayBuffer());
  const text = String.fromCharCode(...head);
  if (!text.includes(PDF_SIGNATURE)) {
    return "Le fichier n'est pas un PDF valide. Seuls les CV au format PDF sont acceptés.";
  }
  return null;
}
