import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { VerifyEmailComponent } from './verify-email.component';

@Component({ template: '' })
class LoginStubComponent {}

describe('VerifyEmailComponent', () => {
  const INVALID = 'Le lien de vérification est invalide ou a déjà été utilisé.';
  const EXPIRED = 'Le lien de vérification a expiré.';
  const SUCCESS = 'Votre adresse email est vérifiée. Vous pouvez vous connecter.';
  const LOADING = 'Vérification de votre adresse email en cours…';
  const GENERIC = 'Une erreur est survenue, réessayez.';

  const auth = { verifyEmail: vi.fn() };
  let harness: RouterTestingHarness;
  let host: HTMLElement;

  beforeEach(async () => {
    vi.resetAllMocks();
    await TestBed.configureTestingModule({
      imports: [VerifyEmailComponent],
      providers: [
        provideRouter([
          { path: 'verify-email', component: VerifyEmailComponent },
          { path: 'login', component: LoginStubComponent },
          { path: 'register', component: LoginStubComponent },
        ]),
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
  });

  // Ouvre la page à l'URL donnée et rend le premier état
  async function open(url: string): Promise<void> {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, VerifyEmailComponent);
    harness.detectChanges();
    host = harness.routeNativeElement as HTMLElement;
  }

  function byId(id: string): HTMLElement | null {
    return host.querySelector<HTMLElement>(`[data-testid="${id}"]`);
  }

  function text(id: string): string {
    return (byId(id)?.textContent ?? '').replace(/\s+/g, ' ').trim();
  }

  // Un seul bloc d'état à la fois, du rôle attendu
  function expectSingleState(role: 'status' | 'alert', id: string): void {
    const blocks = host.querySelectorAll('[role="status"], [role="alert"]');
    expect(blocks).toHaveLength(1);
    expect(byId(id)).not.toBeNull();
    expect(byId(id)?.getAttribute('role')).toBe(role);
  }

  function httpError(status: number, headers: Record<string, string> = {}) {
    return throwError(() => new HttpErrorResponse({ status, headers: new HttpHeaders(headers) }));
  }

  // Jeton inutilisable : message d'invalidité sans appel au serveur
  it.each([
    ['absent', '/verify-email'],
    ['empty', '/verify-email?token='],
    ['blank', '/verify-email?token=%20%20%20'],
  ])('shows the invalid link message without any call when the token is %s', async (_l, url) => {
    await open(url);

    expect(auth.verifyEmail).not.toHaveBeenCalled();
    expectSingleState('alert', 'verify-invalid');
    expect(text('verify-invalid')).toContain(INVALID);
    expect(byId('login-link')).not.toBeNull();
  });

  // Un seul appel, avec le jeton de l'URL tel quel
  it('calls verifyEmail once with the token from the URL', async () => {
    auth.verifyEmail.mockReturnValue(new Subject<void>());

    await open('/verify-email?token=abc');

    expect(auth.verifyEmail).toHaveBeenCalledTimes(1);
    expect(auth.verifyEmail).toHaveBeenCalledWith('abc');
  });

  // Pendant la requête : statut de chargement, ni alerte ni lien
  it('shows the loading status while the request is pending', async () => {
    auth.verifyEmail.mockReturnValue(new Subject<void>());

    await open('/verify-email?token=abc');

    expectSingleState('status', 'verify-loading');
    expect(text('verify-loading')).toContain(LOADING);
    expect(host.querySelector('[role="alert"]')).toBeNull();
    expect(byId('login-link')).toBeNull();
    expect(byId('register-link')).toBeNull();
    expect(byId('retry-button')).toBeNull();
  });

  it('shows the success status and the login link', async () => {
    auth.verifyEmail.mockReturnValue(of(undefined));

    await open('/verify-email?token=abc');

    expectSingleState('status', 'verify-success');
    expect(text('verify-success')).toContain(SUCCESS);
    expect(host.querySelector('[role="alert"]')).toBeNull();
    expect(byId('login-link')?.getAttribute('href')).toBe('/login');
  });

  // Même message que le jeton absent : pas d'énumération
  it('shows the invalid link alert on a 400', async () => {
    auth.verifyEmail.mockReturnValue(httpError(400));

    await open('/verify-email?token=abc');

    expectSingleState('alert', 'verify-invalid');
    expect(text('verify-invalid')).toContain(INVALID);
    expect(byId('login-link')?.getAttribute('href')).toBe('/login');
    expect(byId('retry-button')).toBeNull();
  });

  it('shows the expired link alert on a 410', async () => {
    auth.verifyEmail.mockReturnValue(httpError(410));

    await open('/verify-email?token=abc');

    expectSingleState('alert', 'verify-expired');
    expect(text('verify-expired')).toContain(EXPIRED);
    expect(byId('register-link')?.getAttribute('href')).toBe('/register');
    expect(byId('retry-button')).toBeNull();
    expect(byId('login-link')).toBeNull();
  });

  // Erreurs techniques : message neutre et possibilité de réessayer
  it.each([
    [0, {}],
    [429, { 'Retry-After': '30' }],
    [500, {}],
  ])('shows the generic alert and a retry button on a %s', async (status, headers) => {
    auth.verifyEmail.mockReturnValue(httpError(status, headers));

    await open('/verify-email?token=abc');

    expectSingleState('alert', 'verify-error');
    expect(text('verify-error')).toContain(GENERIC);
    expect(byId('retry-button')).not.toBeNull();
    expect(byId('login-link')).toBeNull();
    expect(byId('register-link')).toBeNull();
  });

  // Réessayer renvoie le même jeton, même si l'URL n'en porte plus
  it('retries with the same token and shows the success', async () => {
    const second = new Subject<void>();
    auth.verifyEmail.mockReturnValueOnce(httpError(500)).mockReturnValueOnce(second);
    await open('/verify-email?token=abc');
    const retry = byId('retry-button');
    expect(retry).not.toBeNull();

    retry!.click();
    harness.detectChanges();

    expect(auth.verifyEmail).toHaveBeenCalledTimes(2);
    expect(auth.verifyEmail).toHaveBeenNthCalledWith(2, 'abc');
    expectSingleState('status', 'verify-loading');

    second.next();
    second.complete();
    harness.detectChanges();

    expectSingleState('status', 'verify-success');
    expect(text('verify-success')).toContain(SUCCESS);
  });
});
