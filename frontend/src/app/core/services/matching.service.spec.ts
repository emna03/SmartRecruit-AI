import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatchingService } from './matching.service';

describe('MatchingService', () => {
  let service: MatchingService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(MatchingService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads the candidate score of an offer', () => {
    let status = '';
    service.getForOffer(5).subscribe((m) => (status = m.status));

    const req = http.expectOne('/api/candidate/jobs/5/matching');
    expect(req.request.method).toBe('GET');
    req.flush({ status: 'CV_ABSENT', message: 'Importez votre CV', result: null });
    expect(status).toBe('CV_ABSENT');
  });

  it('asks the server to recompute the scores of an offer', () => {
    service.refreshForOffer(5).subscribe();

    const req = http.expectOne('/api/recruiter/jobs/5/matching');
    expect(req.request.method).toBe('POST');
    req.flush({ computed: 0, withoutAnalyzedCv: 0, applications: [] });
  });
});
