import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let navigate: ReturnType<typeof vi.spyOn>;
  const auth = { login: vi.fn(), currentUserDetails: vi.fn() };

  beforeEach(async () => {
    vi.resetAllMocks();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();

    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
  });

  function fill(email: string, password: string) {
    const host = fixture.nativeElement as HTMLElement;
    const emailInput = host.querySelector<HTMLInputElement>('#email')!;
    const passwordInput = host.querySelector<HTMLInputElement>('#password')!;
    emailInput.value = email;
    emailInput.dispatchEvent(new Event('input'));
    passwordInput.value = password;
    passwordInput.dispatchEvent(new Event('input'));
  }

  function submit() {
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
  }

  // Affiche les champs email et mot de passe
  it('renders the email and password fields', () => {
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('#email')).not.toBeNull();
    expect(host.querySelector('#password')).not.toBeNull();
  });

  // Envoie ce qui est saisi, charge le profil puis redirige vers l'accueil
  it('logs in with the typed values, loads the profile, then goes home', () => {
    auth.login.mockReturnValue(of({}));
    auth.currentUserDetails.mockReturnValue(of({}));

    fill('jean@parkyn.app', 'secret');
    submit();

    expect(auth.login).toHaveBeenCalledWith('jean@parkyn.app', 'secret');
    expect(auth.currentUserDetails).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith(['/']);
  });

  // Un échec de connexion ne redirige pas et ne charge pas le profil
  it('stays on the page when the login fails', () => {
    auth.login.mockReturnValue(throwError(() => ({ status: 401 })));
    vi.spyOn(console, 'log').mockImplementation(() => {});

    fill('jean@parkyn.app', 'mauvais');
    submit();

    expect(auth.currentUserDetails).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  // Le bouton œil bascule la visibilité du mot de passe
  it('toggles the password visibility', () => {
    const input = fixture.nativeElement.querySelector('#password') as HTMLInputElement;
    expect(input.type).toBe('password');

    fixture.nativeElement.querySelector('button[type="button"]').click();
    fixture.detectChanges();

    expect(input.type).toBe('text');
  });

  // --- Messages d'erreur en ligne ---

  function httpError(status: number, headers: Record<string, string> = {}) {
    return throwError(() => new HttpErrorResponse({ status, headers: new HttpHeaders(headers) }));
  }

  function alerts(): HTMLElement[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('[role="alert"]'),
    );
  }

  function submitAndRender() {
    fill('jean@parkyn.app', 'mauvais');
    submit();
    fixture.detectChanges();
  }

  // Un 401 affiche un message unique, sans désigner de champ
  it('shows an inline alert on a 401', () => {
    auth.login.mockReturnValue(httpError(401));

    submitAndRender();

    const alert = (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]');
    expect(alert?.textContent?.trim()).toBe('Email ou mot de passe incorrect');
  });

  it.each([
    ['30', 'Trop de tentatives, réessayez dans 30 secondes'],
    ['2', 'Trop de tentatives, réessayez dans 2 secondes'],
    ['1', 'Trop de tentatives, réessayez dans 1 seconde'],
  ])('shows the countdown on a 429 with Retry-After %s', (retryAfter, expected) => {
    auth.login.mockReturnValue(httpError(429, { 'Retry-After': retryAfter }));

    submitAndRender();

    const alert = (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]');
    expect(alert?.textContent?.trim()).toBe(expected);
  });

  it.each([
    ['absent', {}],
    ['non numeric', { 'Retry-After': 'abc' }],
    ['zero', { 'Retry-After': '0' }],
    ['negative', { 'Retry-After': '-5' }],
  ])('shows the generic delay on a 429 when Retry-After is %s', (_label, headers) => {
    auth.login.mockReturnValue(httpError(429, headers));

    submitAndRender();

    const alert = (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]');
    expect(alert?.textContent?.trim()).toBe('Trop de tentatives, réessayez plus tard');
  });

  // Une nouvelle erreur remplace la précédente
  it('replaces the 401 alert by the 429 alert', () => {
    auth.login.mockReturnValueOnce(httpError(401));
    submitAndRender();
    auth.login.mockReturnValueOnce(httpError(429, { 'Retry-After': '30' }));
    submitAndRender();

    expect(alerts()).toHaveLength(1);
    expect(alerts()[0].textContent?.trim()).toBe('Trop de tentatives, réessayez dans 30 secondes');
  });

  // Une connexion réussie efface l'alerte
  it('clears the alert and redirects after a successful retry', () => {
    auth.login.mockReturnValueOnce(httpError(401));
    submitAndRender();
    expect(alerts()).toHaveLength(1);

    auth.login.mockReturnValueOnce(of({}));
    auth.currentUserDetails.mockReturnValue(of({}));
    submitAndRender();

    expect(alerts()).toHaveLength(0);
    expect(navigate).toHaveBeenCalledWith(['/']);
  });

  // Garde-fou : un 500 est géré par le toast global, pas par l'écran
  it('shows no inline alert on a 500', () => {
    auth.login.mockReturnValue(httpError(500));

    submitAndRender();

    expect(alerts()).toHaveLength(0);
  });
});
