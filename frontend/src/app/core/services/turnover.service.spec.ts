import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EXAMPLE_PROFILES } from '../../features/hr/turnover-fields';
import { TurnoverService } from './turnover.service';

describe('TurnoverService', () => {
  it('posts the 24 model variables to the HR endpoint', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);

    TestBed.inject(TurnoverService).predict(EXAMPLE_PROFILES[0].values).subscribe();

    const request = http.expectOne('/api/hr/turnover/predictions');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(EXAMPLE_PROFILES[0].values);
    expect(Object.keys(request.request.body)).toHaveLength(24);
    request.flush({});
    http.verify();
  });
});
