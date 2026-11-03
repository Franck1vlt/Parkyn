import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthInterceptor } from './auth.interceptor';
import { environment } from '../../../environments/environment';

describe('AuthInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    localStorage.setItem('parkyn_access_token', 'old-token');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([AuthInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  // --- Tests ---

  // Partage une seule mise à jour du token entre plusieurs requêtes 401 simultanées
  it('shares a single refresh between concurrent 401s', () => {
    const responses: any[] = [];
    const urls = ['/api/v1/a', '/api/v1/b', '/api/v1/c'];

    const requests = urls.map((url) => getRequest(url, responses));

    requests.forEach((request) => {
      request.flush(null, { status: 401, statusText: 'Unauthorized' });
    });

    httpMock.expectOne(`${environment.apiUrl}/auth/refresh`).flush({ access_token: 'new-token' });

    urls.forEach((url) => {
      const request = httpMock.expectOne(url);
      expect(request.request.headers.get('Authorization')).toBe('Bearer new-token');
      request.flush({});
    });

    expect(responses).toHaveLength(3);
  });

  // Rejoue la requête sans rafraîchir le token si celui-ci a déjà changé
  it('replays without refreshing when the token already changed', () => {
    const responses: any[] = [];
    const url = '/api/v1/a';
    const refreshUrl = `${environment.apiUrl}/auth/refresh`;

    const initialRequest = getRequest(url, responses);
    expect(initialRequest.request.headers.get('Authorization')).toBe('Bearer old-token');
    localStorage.setItem('parkyn_access_token', 'new-token');
    initialRequest.flush(null, { status: 401, statusText: 'Unauthorized' });

    httpMock.expectNone(refreshUrl);
    const request = httpMock.expectOne(url);
    expect(request.request.headers.get('Authorization')).toBe('Bearer new-token');
    request.flush({});

    expect(responses).toHaveLength(1);
  });

  // Déconnecte l'utilisateur si le rafraîchissement échoue
  it('logs out once when the shared refresh fails', () => {
    const errors: any[] = [];
    const urls = ['/api/v1/a', '/api/v1/b', '/api/v1/c'];
    const refreshUrl = `${environment.apiUrl}/auth/refresh`;
    const logoutUrl = `${environment.apiUrl}/auth/logout`;

    const requests = urls.map((url) => getRequest(url, undefined, (error) => errors.push(error)));

    requests.forEach((request) => {
      expect(request.request.headers.get('Authorization')).toBe('Bearer old-token');
      request.flush(null, { status: 401, statusText: 'Unauthorized' });
    });

    const refreshRequest = httpMock.expectOne(refreshUrl);
    refreshRequest.flush(null, { status: 401, statusText: 'Unauthorized' });

    const logoutRequests = httpMock.match(logoutUrl);
    expect(logoutRequests).toHaveLength(1);
    expect(errors).toHaveLength(3);

    logoutRequests[0].flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(localStorage.getItem('parkyn_access_token')).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  // Transmet l'erreur de la requête rejouée, pas le 401 d'origine
  it('forwards the replayed request error', () => {
    const errors: any[] = [];
    const url = '/api/v1/a';

    getRequest(url, undefined, (error) => errors.push(error)).flush(null, {
      status: 401,
      statusText: 'Unauthorized',
    });
    httpMock.expectOne(`${environment.apiUrl}/auth/refresh`).flush({ access_token: 'new-token' });
    httpMock.expectOne(url).flush(null, { status: 409, statusText: 'Conflict' });

    expect(errors).toHaveLength(1);
    expect(errors[0].status).toBe(409);
  });

  // --- Helpers ---
  function getRequest(url: string, responses?: any[], onError?: (error: any) => void): TestRequest {
    http.get(url).subscribe({
      next: (response) => responses?.push(response),
      error: onError,
    });
    return httpMock.expectOne(url);
  }
});
