import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CandidateCv } from '../models';
import { checkCvFile, CvService } from './cv.service';

const PDF_BYTES = '%PDF-1.4\n% CV fictif\n%%EOF';

describe('CvService', () => {
  let service: CvService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CvService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('returns null when the candidate has no CV yet', () => {
    let result: CandidateCv | null | undefined;
    service.get().subscribe((cv) => (result = cv));

    http.expectOne('/api/candidate/cv').flush({ status: 404, message: 'Aucun CV importé' }, { status: 404, statusText: 'Not Found' });

    expect(result).toBeNull();
  });

  it('propagates other errors', () => {
    let status = 0;
    service.get().subscribe({ error: (err) => (status = err.status) });

    http.expectOne('/api/candidate/cv').flush({}, { status: 503, statusText: 'Service Unavailable' });

    expect(status).toBe(503);
  });

  it('uploads the file as multipart form data in the "file" field', () => {
    const file = new File([PDF_BYTES], 'Mon CV.pdf', { type: 'application/pdf' });
    service.upload(file).subscribe();

    const req = http.expectOne('/api/candidate/cv');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeInstanceOf(FormData);
    expect((req.request.body as FormData).get('file')).toBeInstanceOf(File);
    req.flush({});
  });

  it('calls the analyze, import and delete endpoints', () => {
    service.analyze().subscribe();
    service.importIntoProfile({ skills: true, educations: false, experiences: true }).subscribe();
    service.delete().subscribe();

    expect(http.expectOne('/api/candidate/cv/analyze').request.method).toBe('POST');
    const importReq = http.expectOne('/api/candidate/cv/import');
    expect(importReq.request.body).toEqual({ skills: true, educations: false, experiences: true });
    expect(http.expectOne({ method: 'DELETE', url: '/api/candidate/cv' })).toBeTruthy();
  });
});

describe('checkCvFile', () => {
  it('accepts a PDF whatever its declared type', async () => {
    expect(await checkCvFile(new File([PDF_BYTES], 'cv.bin', { type: 'application/octet-stream' }))).toBeNull();
  });

  it('rejects empty, oversized and non-PDF files', async () => {
    expect(await checkCvFile(new File([], 'cv.pdf'))).toBe('Le fichier est vide.');
    expect(await checkCvFile(new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'cv.pdf'))).toBe(
      'Le fichier dépasse la taille maximale de 5 Mo.',
    );
    expect(await checkCvFile(new File(['<html>cv</html>'], 'cv.pdf', { type: 'application/pdf' }))).toContain(
      "n'est pas un PDF valide",
    );
  });
});
