import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ToastService } from '../services/toast.service';
import { ErrorInterceptor } from './error.interceptor';
import { HANDLED_BY_SCREEN, HANDLED_STATUSES } from './error-handling';

describe('ErrorInterceptor', () => {
  const url = '/api/v1/a';
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let show: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([ErrorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    show = vi.spyOn(TestBed.inject(ToastService), 'show');
  });

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  // Envoie une requête, répond avec l'erreur et renvoie les erreurs reçues par l'appelant
  function failWith(
    status: number,
    options: { body?: unknown; headers?: Record<string, string>; context?: HttpContext } = {},
  ): any[] {
    const errors: any[] = [];
    http.get(url, { context: options.context }).subscribe({ error: (e) => errors.push(e) });
    httpMock
      .expectOne(url)
      .flush(options.body ?? null, { status, statusText: 'Error', headers: options.headers });
    return errors;
  }

  // Tous les 5xx donnent le même message
  it('shows the internal server error toast on a 500', () => {
    failWith(500);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Erreur serveur interne');
  });

  it.each([502, 503, 504])('shows the internal server error toast on a %i', (status) => {
    failWith(status);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Erreur serveur interne');
  });

  it('shows the access denied toast on a 403', () => {
    failWith(403);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Accès refusé');
  });

  it('shows the not found toast on a 404', () => {
    failWith(404);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Ressource introuvable');
  });

  it('shows the server message on a 422 that has one', () => {
    failWith(422, { body: { message: 'Plaque déjà utilisée' } });

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Plaque déjà utilisée');
  });

  it('shows the default forbidden operation toast on a 422 without message', () => {
    failWith(422);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Opération non autorisée');
  });

  it.each([400, 409, 418])('shows the generic toast on a %i', (status) => {
    failWith(status);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Une erreur est survenue');
  });

  // Les 401 sont gérés par AuthInterceptor et les écrans
  it('shows no toast on a 401', () => {
    failWith(401);

    expect(show).not.toHaveBeenCalled();
  });

  it.each([500, 401])('rethrows the original error to the caller on a %i', (status) => {
    const errors = failWith(status);

    expect(errors).toHaveLength(1);
    expect(errors[0].status).toBe(status);
  });

  // Statut 0 : le serveur n'a pas répondu
  it('shows the unreachable server toast on a network error', () => {
    const errors: any[] = [];
    http.get(url).subscribe({ error: (e) => errors.push(e) });
    httpMock.expectOne(url).error(new ProgressEvent('error'));

    expect(errors[0].status).toBe(0);
    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Serveur injoignable');
  });

  // 429 sans jeton : toast avec le décompte
  it('shows the countdown toast on a 429 without the screen token', () => {
    failWith(429, { headers: { 'Retry-After': '30' } });

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Trop de tentatives, réessayez dans 30 secondes');
  });

  it('uses the singular for a Retry-After of one second', () => {
    failWith(429, { headers: { 'Retry-After': '1' } });

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Trop de tentatives, réessayez dans 1 seconde');
  });

  it.each([
    ['absent', undefined],
    ['non numeric', 'abc'],
    ['zero', '0'],
    ['negative', '-5'],
    ['an HTTP date', 'Wed, 21 Oct 2026 07:28:00 GMT'],
  ])('falls back to the generic delay when Retry-After is %s', (_label, value) => {
    failWith(429, { headers: value === undefined ? undefined : { 'Retry-After': value } });

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Trop de tentatives, réessayez plus tard');
  });

  // 429 avec jeton : l'écran affiche son message, pas de toast
  it('stays silent on a 429 flagged as handled by the screen and rethrows it', () => {
    const context = new HttpContext().set(HANDLED_BY_SCREEN, true);

    const errors = failWith(429, { headers: { 'Retry-After': '30' }, context });

    expect(show).not.toHaveBeenCalled();
    expect(errors).toHaveLength(1);
    expect(errors[0].status).toBe(429);
  });

  // Statuts listés : l'écran affiche lui-même l'erreur, pas de toast
  it.each([400, 410])('stays silent on a %i listed in HANDLED_STATUSES and rethrows it', (status) => {
    const context = new HttpContext().set(HANDLED_STATUSES, [400, 410]);

    const errors = failWith(status, { context });

    expect(show).not.toHaveBeenCalled();
    expect(errors).toHaveLength(1);
    expect(errors[0].status).toBe(status);
  });

  // Un statut absent de la liste garde son toast habituel
  it.each([
    [500, 'Erreur serveur interne'],
    [404, 'Ressource introuvable'],
  ])('still toasts a status not listed in HANDLED_STATUSES (%i)', (status, message) => {
    const context = new HttpContext().set(HANDLED_STATUSES, [400, 410]);

    failWith(status, { context });

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith(message);
  });

  // HANDLED_BY_SCREEN ne silence que le 429
  it('still toasts a 400 flagged only with HANDLED_BY_SCREEN', () => {
    const context = new HttpContext().set(HANDLED_BY_SCREEN, true);

    failWith(400, { context });

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Une erreur est survenue');
  });
});
