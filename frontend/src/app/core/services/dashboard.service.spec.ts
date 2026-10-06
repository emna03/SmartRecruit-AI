import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { DashboardService } from './dashboard.service';

describe('DashboardService', () => {
  it('reads each role dashboard from its own endpoint, without any identifier', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const service = TestBed.inject(DashboardService);

    service.candidate().subscribe();
    service.recruiter().subscribe();
    service.admin().subscribe();

    for (const url of ['/api/candidate/dashboard', '/api/recruiter/dashboard', '/api/admin/dashboard']) {
      const request = http.expectOne(url);
      expect(request.request.method).toBe('GET');
      expect(request.request.params.keys()).toEqual([]);
      request.flush({});
    }
    http.verify();
  });
});
