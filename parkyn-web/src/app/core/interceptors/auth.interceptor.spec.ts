import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { AuthInterceptor } from './auth.interceptor';
import { environment } from '../../../environments/environment';

describe('AuthInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;
  let auth: AuthService;

  beforeEach(() => {
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
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  // --- Tests ---

  // Partage une seule mise à jour du token entre plusieurs requêtes 401 simultanées
  it('shares a single refresh between concurrent 401s', () => {
    signIn('old-token');
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
    signIn('old-token');
    const responses: any[] = [];
    const url = '/api/v1/a';
    const refreshUrl = `${environment.apiUrl}/auth/refresh`;

    const initialRequest = getRequest(url, responses);
    expect(initialRequest.request.headers.get('Authorization')).toBe('Bearer old-token');
    signIn('new-token');
    initialRequest.flush(null, { status: 401, statusText: 'Unauthorized' });

    httpMock.expectNone(refreshUrl);
    const request = httpMock.expectOne(url);
    expect(request.request.headers.get('Authorization')).toBe('Bearer new-token');
    request.flush({});

    expect(responses).toHaveLength(1);
  });

  // Refresh refusé : session effacée une seule fois, sans logout inutile
  it('clears the session once when the shared refresh fails', () => {
    signIn('old-token');
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

    expect(errors.map((e) => e.status)).toEqual([401, 401, 401]);
    httpMock.expectNone(logoutUrl);
    httpMock.expectNone(refreshUrl);
    expect(auth.getAccessToken()).toBeNull();
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  // Transmet l'erreur de la requête rejouée, pas le 401 d'origine
  it('forwards the replayed request error', () => {
    signIn('old-token');
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

  // Sans token : pas d'en-tête Authorization et le 401 remonte sans refresh
  // (comportement actuel ; D1 le changera pour retrouver la session après un rechargement)
  it('sends no Authorization header and does not refresh without a token', () => {
    const errors: any[] = [];
    const url = '/api/v1/a';

    const request = getRequest(url, undefined, (error) => errors.push(error));
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(null, { status: 401, statusText: 'Unauthorized' });

    httpMock.expectNone(`${environment.apiUrl}/auth/refresh`);
    expect(errors.map((e) => e.status)).toEqual([401]);
  });

  // Un 401 au login (mauvais identifiants) ne déclenche jamais de refresh, même avec un ancien token
  it('never refreshes on a 401 from the login', () => {
    signIn('old-token');
    const errors: any[] = [];
    const loginUrl = `${environment.apiUrl}/auth/login`;

    http.post(loginUrl, {}).subscribe({ error: (error) => errors.push(error) });
    httpMock.expectOne(loginUrl).flush(null, { status: 401, statusText: 'Unauthorized' });

    httpMock.expectNone(`${environment.apiUrl}/auth/refresh`);
    expect(errors.map((e) => e.status)).toEqual([401]);
  });

  // Logout avec token expiré : refresh puis rejeu du logout avec le nouveau token
  it('replays the logout with a refreshed token after a 401', () => {
    signIn('old-token');
    loadProfile();
    expect(auth.currentUser).not.toBeNull();
    const logoutUrl = `${environment.apiUrl}/auth/logout`;
    const refreshUrl = `${environment.apiUrl}/auth/refresh`;

    auth.logout();
    const firstLogout = httpMock.expectOne(logoutUrl);
    expect(firstLogout.request.headers.get('Authorization')).toBe('Bearer old-token');
    firstLogout.flush(null, { status: 401, statusText: 'Unauthorized' });

    httpMock.expectOne(refreshUrl).flush({ access_token: 'new-token' });
    expect(navigate).not.toHaveBeenCalled();

    const replayedLogout = httpMock.expectOne(logoutUrl);
    expect(replayedLogout.request.headers.get('Authorization')).toBe('Bearer new-token');
    replayedLogout.flush(null, { status: 204, statusText: 'No Content' });

    expect(auth.getAccessToken()).toBeNull();
    expect(auth.currentUser).toBeNull();
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  // Refresh refusé pendant le logout : un seul logout, un seul refresh, session effacée une fois
  it('clears the session once without looping when the refresh fails during logout', () => {
    signIn('old-token');
    loadProfile();
    expect(auth.currentUser).not.toBeNull();
    const logoutUrl = `${environment.apiUrl}/auth/logout`;
    const refreshUrl = `${environment.apiUrl}/auth/refresh`;

    auth.logout();
    httpMock.expectOne(logoutUrl).flush(null, { status: 401, statusText: 'Unauthorized' });
    httpMock.expectOne(refreshUrl).flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(httpMock.match(logoutUrl)).toHaveLength(0);
    httpMock.expectNone(refreshUrl);
    expect(auth.getAccessToken()).toBeNull();
    expect(auth.currentUser).toBeNull();
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  // Token déjà renouvelé pendant le logout : rejeu sans refresh
  it('replays the logout without refreshing when the token already changed', () => {
    signIn('old-token');
    const logoutUrl = `${environment.apiUrl}/auth/logout`;

    auth.logout();
    const firstLogout = httpMock.expectOne(logoutUrl);
    expect(firstLogout.request.headers.get('Authorization')).toBe('Bearer old-token');
    signIn('new-token');
    firstLogout.flush(null, { status: 401, statusText: 'Unauthorized' });

    httpMock.expectNone(`${environment.apiUrl}/auth/refresh`);
    const replayedLogout = httpMock.expectOne(logoutUrl);
    expect(replayedLogout.request.headers.get('Authorization')).toBe('Bearer new-token');
    replayedLogout.flush(null, { status: 204, statusText: 'No Content' });

    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  // --- Helpers ---
  // Ouvre une session par un vrai login (token en mémoire)
  function signIn(token: string): void {
    auth.login('jean@parkyn.app', 'secret').subscribe();
    httpMock.expectOne(`${environment.apiUrl}/auth/login`).flush({ access_token: token });
  }

  // Charge le profil pour que l'effacement de currentUser soit observable
  function loadProfile(): void {
    auth.currentUserDetails().subscribe();
    httpMock.expectOne(`${environment.apiUrl}/auth/me`).flush({
      id: '1',
      firstname: 'Jean',
      lastname: 'Dupont',
      email: 'jean@parkyn.app',
      role: 'DRIVER',
    });
  }

  function getRequest(url: string, responses?: any[], onError?: (error: any) => void): TestRequest {
    http.get(url).subscribe({
      next: (response) => responses?.push(response),
      error: onError,
    });
    return httpMock.expectOne(url);
  }
});
