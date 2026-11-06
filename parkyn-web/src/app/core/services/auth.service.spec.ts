import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService, CurrentUser, LoginResponse } from './auth.service';

describe('AuthService', () => {
  const API = `${environment.apiUrl}/auth`;
  const ACCESS_KEY = 'parkyn_access_token';
  const REFRESH_KEY = 'parkyn_refresh_token';
  const USER_KEY = 'parkyn_user';

  const driver: CurrentUser = {
    id: '1',
    firstname: 'Jean',
    lastname: 'Dupont',
    email: 'jean@parkyn.app',
    role: 'DRIVER',
  };
  const loginResponse = {
    access_token: 'access-1',
    expired_at: '2026-11-06T10:15:00Z',
    refresh_expired_at: '2026-11-13T10:00:00Z',
    token_type: 'Bearer',
  } as LoginResponse;

  let httpMock: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  // Le service lit le stockage à sa construction : on l'instancie après avoir préparé l'état voulu
  function createService(): AuthService {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    httpMock = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    return TestBed.inject(AuthService);
  }

  // Ouvre une session par un vrai login
  function signIn(auth: AuthService, accessToken = 'access-1'): void {
    auth.login('jean@parkyn.app', 'secret').subscribe();
    httpMock.expectOne(`${API}/login`).flush({ ...loginResponse, access_token: accessToken });
  }

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  describe('login', () => {
    // Envoie l'email et le mot de passe et garde l'access token en mémoire, jamais dans localStorage
    it('posts the credentials and keeps the access token in memory only', () => {
      const auth = createService();
      let result: LoginResponse | undefined;

      auth.login('jean@parkyn.app', 'secret').subscribe((r) => (result = r));

      const request = httpMock.expectOne(`${API}/login`);
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({ email: 'jean@parkyn.app', password: 'secret' });
      request.flush(loginResponse);

      expect(result).toEqual(loginResponse);
      expect(auth.getAccessToken()).toBe('access-1');
      expect(auth.isAuthenticated()).toBe(true);
      expect(localStorage.getItem(ACCESS_KEY)).toBeNull();
      expect(localStorage.getItem(REFRESH_KEY)).toBeNull();
    });

    // Un échec de connexion ne laisse aucune session
    it('keeps no session when the login fails', () => {
      const auth = createService();
      let status: number | undefined;

      auth.login('jean@parkyn.app', 'mauvais').subscribe({ error: (e) => (status = e.status) });
      httpMock.expectOne(`${API}/login`).flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(status).toBe(401);
      expect(auth.getAccessToken()).toBeNull();
      expect(auth.isAuthenticated()).toBe(false);
    });
  });

  describe('startup', () => {
    // Un ancien token laissé dans localStorage n'ouvre pas de session
    it('ignores an access token left in localStorage', () => {
      localStorage.setItem(ACCESS_KEY, 'stale-token');
      const auth = createService();

      expect(auth.getAccessToken()).toBeNull();
      expect(auth.isAuthenticated()).toBe(false);
    });
  });

  describe('register', () => {
    // Envoie les quatre champs de l'inscription
    it('posts the registration fields', () => {
      const auth = createService();

      auth.register('Jean', 'Dupont', 'jean@parkyn.app', 'un-mot-de-passe').subscribe();

      const request = httpMock.expectOne(`${API}/register`);
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({
        firstname: 'Jean',
        lastname: 'Dupont',
        email: 'jean@parkyn.app',
        password: 'un-mot-de-passe',
      });
      request.flush(null, { status: 202, statusText: 'Accepted' });
    });
  });

  describe('refresh', () => {
    // Le refresh token voyage dans le cookie : le corps est vide
    it('sends an empty body', () => {
      const auth = createService();

      auth.refresh().subscribe();

      const request = httpMock.expectOne(`${API}/refresh`);
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({});
      request.flush(loginResponse);
    });

    // Remplace l'access token en mémoire, sans rien écrire dans localStorage
    it('replaces the access token in memory', () => {
      const auth = createService();
      signIn(auth, 'old-token');

      auth.refresh().subscribe();
      httpMock.expectOne(`${API}/refresh`).flush({ ...loginResponse, access_token: 'new-token' });

      expect(auth.getAccessToken()).toBe('new-token');
      expect(localStorage.getItem(ACCESS_KEY)).toBeNull();
      expect(localStorage.getItem(REFRESH_KEY)).toBeNull();
    });

    // Plusieurs appels simultanés partagent une seule requête
    it('shares one request between concurrent calls', () => {
      const auth = createService();
      const tokens: string[] = [];

      auth.refresh().subscribe((r) => tokens.push(r.access_token));
      auth.refresh().subscribe((r) => tokens.push(r.access_token));
      auth.refresh().subscribe((r) => tokens.push(r.access_token));

      httpMock.expectOne(`${API}/refresh`).flush({ ...loginResponse, access_token: 'shared' });

      expect(tokens).toEqual(['shared', 'shared', 'shared']);
    });

    // Une fois terminé, un nouveau refresh repart sur une nouvelle requête
    it('sends a new request once the previous refresh is done', () => {
      const auth = createService();

      auth.refresh().subscribe();
      httpMock.expectOne(`${API}/refresh`).flush({ ...loginResponse, access_token: 'a' });

      auth.refresh().subscribe();
      httpMock.expectOne(`${API}/refresh`).flush({ ...loginResponse, access_token: 'b' });

      expect(auth.getAccessToken()).toBe('b');
    });

    // Un refresh refusé déconnecte et transmet l'erreur d'origine
    it('logs out and forwards the error when the refresh is refused', () => {
      const auth = createService();
      signIn(auth);
      const statuses: number[] = [];

      auth.refresh().subscribe({ error: (e) => statuses.push(e.status) });
      httpMock.expectOne(`${API}/refresh`).flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(statuses).toEqual([401]);
      const logout = httpMock.expectOne(`${API}/logout`);
      logout.flush(null, { status: 204, statusText: 'No Content' });
      expect(auth.getAccessToken()).toBeNull();
      expect(navigate).toHaveBeenCalledWith(['/login']);
    });

    // Après un échec, un nouvel appel peut réessayer
    it('allows a new attempt after a failure', () => {
      const auth = createService();

      auth.refresh().subscribe({ error: () => {} });
      httpMock.expectOne(`${API}/refresh`).flush(null, { status: 401, statusText: 'x' });
      httpMock.expectOne(`${API}/logout`).flush(null, { status: 204, statusText: 'No Content' });

      auth.refresh().subscribe({ error: () => {} });
      httpMock.expectOne(`${API}/refresh`).flush(null, { status: 401, statusText: 'x' });
      httpMock.expectOne(`${API}/logout`).flush(null, { status: 204, statusText: 'No Content' });
    });
  });

  describe('logout', () => {
    // La requête part avant l'effacement : la session locale n'est effacée qu'à la réponse
    it('keeps the session until the server answers, then clears it', () => {
      localStorage.setItem(USER_KEY, JSON.stringify(driver));
      const auth = createService();
      signIn(auth, 'token');

      auth.logout();

      const request = httpMock.expectOne(`${API}/logout`);
      expect(request.request.method).toBe('POST');
      expect(auth.getAccessToken()).toBe('token');
      expect(navigate).not.toHaveBeenCalled();

      request.flush(null, { status: 204, statusText: 'No Content' });

      expect(auth.getAccessToken()).toBeNull();
      expect(localStorage.getItem(USER_KEY)).toBeNull();
      expect(auth.currentUser).toBeNull();
      expect(navigate).toHaveBeenCalledWith(['/login']);
    });

    // Même si le serveur refuse, l'utilisateur est déconnecté localement
    it('clears the session even when the request fails', () => {
      const auth = createService();
      signIn(auth);

      auth.logout();
      httpMock.expectOne(`${API}/logout`).flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(auth.isAuthenticated()).toBe(false);
      expect(navigate).toHaveBeenCalledWith(['/login']);
    });
  });

  describe('current user', () => {
    // Recharge l'utilisateur mémorisé au démarrage
    it('restores the stored user on startup', () => {
      localStorage.setItem(USER_KEY, JSON.stringify(driver));
      const auth = createService();

      expect(auth.currentUser).toEqual(driver);
      expect(auth.getEmail()).toBe('jean@parkyn.app');
      expect(auth.getRole()).toBe('DRIVER');
    });

    // Sans utilisateur, aucun rôle ni email
    it('has no role and no email when nobody is signed in', () => {
      const auth = createService();

      expect(auth.currentUser).toBeNull();
      expect(auth.getRole()).toBeNull();
      expect(auth.getEmail()).toBeNull();
    });

    // Charge le profil, le mémorise et le publie
    it('loads, stores and publishes the user', () => {
      const auth = createService();
      const emitted: (CurrentUser | null)[] = [];
      auth.currentUser$.subscribe((u) => emitted.push(u));

      auth.currentUserDetails().subscribe();
      const request = httpMock.expectOne(`${API}/me`);
      expect(request.request.method).toBe('GET');
      request.flush(driver);

      expect(emitted).toEqual([null, driver]);
      expect(JSON.parse(localStorage.getItem(USER_KEY)!)).toEqual(driver);
      expect(auth.getRole()).toBe('DRIVER');
    });
  });

  describe('hasRole', () => {
    // Faux sans utilisateur
    it('is false when nobody is signed in', () => {
      const auth = createService();

      expect(auth.hasRole('ADMIN', 'AGENT', 'DRIVER')).toBe(false);
    });

    // Vrai si le rôle figure parmi ceux demandés, faux sinon
    it('matches only the roles asked for', () => {
      localStorage.setItem(USER_KEY, JSON.stringify(driver));
      const auth = createService();

      expect(auth.hasRole('DRIVER')).toBe(true);
      expect(auth.hasRole('ADMIN', 'DRIVER')).toBe(true);
      expect(auth.hasRole('ADMIN', 'AGENT')).toBe(false);
    });
  });

  describe('isAuthenticated', () => {
    // Suit la présence d'un access token en mémoire
    it('follows the presence of an access token', () => {
      const auth = createService();
      expect(auth.isAuthenticated()).toBe(false);

      signIn(auth);
      expect(auth.isAuthenticated()).toBe(true);
    });
  });
});
