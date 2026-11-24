import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { ResendVerificationComponent } from './resend-verification.component';

const GENERIC_400 = "Les informations saisies n'ont pas pu être validées. Veuillez réessayer.";

// Adresse valide pour Angular et Hibernate : local part <= 64, labels de domaine <= 63
function emailOfLength(total: number): string {
  const local = 'a'.repeat(64);
  let remaining = total - local.length - 1;
  const labels: string[] = [];
  while (remaining > 0) {
    const size = remaining <= 63 ? remaining : remaining - 64 >= 1 ? 63 : 62;
    labels.push('b'.repeat(size));
    remaining -= size + 1;
  }
  return `${local}@${labels.join('.')}`;
}

describe('ResendVerificationComponent with a mocked AuthService', () => {
  let fixture: ComponentFixture<ResendVerificationComponent>;
  const auth = { resendVerification: vi.fn() };

  beforeEach(async () => {
    vi.resetAllMocks();
    await TestBed.configureTestingModule({
      imports: [ResendVerificationComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();

    fixture = TestBed.createComponent(ResendVerificationComponent);
    fixture.detectChanges();
  });

  // --- Helpers ---

  function host(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function input(): HTMLInputElement {
    const el = host().querySelector<HTMLInputElement>('#email');
    expect(el).not.toBeNull();
    return el as HTMLInputElement;
  }

  function fill(value: string) {
    const el = input();
    el.value = value;
    el.dispatchEvent(new Event('input'));
  }

  function blur() {
    input().dispatchEvent(new Event('blur'));
  }

  function submit() {
    const form = host().querySelector('form');
    expect(form).not.toBeNull();
    form!.dispatchEvent(new Event('submit'));
  }

  function submitAndRender() {
    submit();
    fixture.detectChanges();
  }

  function fillAndSubmit(email = 'jean@parkyn.app') {
    fill(email);
    submitAndRender();
  }

  // Présence attendue : le rouge est une assertion et non un TypeError
  function byTestId(id: string): HTMLElement {
    const el = host().querySelector<HTMLElement>(`[data-testid="${id}"]`);
    expect(el).not.toBeNull();
    return el as HTMLElement;
  }

  // Absence attendue
  function maybeTestId(id: string): HTMLElement | null {
    return host().querySelector<HTMLElement>(`[data-testid="${id}"]`);
  }

  function submitButton(): HTMLButtonElement {
    return byTestId('submit-button') as HTMLButtonElement;
  }

  function emailErrors(): string[] {
    return Array.from(host().querySelectorAll('#email-error p')).map(
      (p) => p.textContent?.trim() ?? '',
    );
  }

  function alerts(): HTMLElement[] {
    return Array.from(host().querySelectorAll<HTMLElement>('[role="alert"]'));
  }

  function httpError(status: number, error: object | string | null = null, headers = {}) {
    return throwError(
      () => new HttpErrorResponse({ status, error, headers: new HttpHeaders(headers) }),
    );
  }

  // --- Structure ---

  // Cas 4 : titre et champ email étiqueté
  it('renders the title and a labelled email field', () => {
    const title = host().querySelector('h2');
    expect(title?.textContent?.trim()).toBe('Renvoyer le lien de vérification');

    const label = host().querySelector('label[for="email"]');
    expect(label?.textContent?.trim()).toBe('Email');
    expect(input().type).toBe('email');
    expect(input().getAttribute('autocomplete')).toBe('email');
  });

  // Cas 5
  it('disables the submit button while the form is empty', () => {
    expect(submitButton().disabled).toBe(true);
  });

  // Cas 6
  it('enables the submit button with a valid email', () => {
    fill('jean@parkyn.app');
    fixture.detectChanges();

    expect(submitButton().disabled).toBe(false);
  });

  // --- Validation locale ---

  // Cas 7
  it.each([
    ['an empty email', '', "L'adresse email est obligatoire."],
    ['an invalid email', 'pas-un-email', "L'adresse email n'est pas valide."],
    ['an email of 255 characters', emailOfLength(255), '254 caractères maximum.'],
  ])('rejects %s with its message and never calls the service', (_label, value, message) => {
    fill(value);
    fixture.detectChanges();
    expect(submitButton().disabled).toBe(true);

    submitAndRender();

    expect(auth.resendVerification).not.toHaveBeenCalled();
    expect(emailErrors()).toEqual([message]);
    expect(input().getAttribute('aria-invalid')).toBe('true');
    expect(input().getAttribute('aria-describedby')).toBe('email-error');
  });

  // Cas 8
  it('shows the required message only after blur', () => {
    expect(emailErrors()).toEqual([]);

    fill('x');
    fill('');
    fixture.detectChanges();
    expect(emailErrors()).toEqual([]);

    blur();
    fixture.detectChanges();

    expect(emailErrors()).toEqual(["L'adresse email est obligatoire."]);
  });

  // Cas 9
  it('accepts an email of 254 characters', () => {
    auth.resendVerification.mockReturnValue(of(undefined));
    const email = emailOfLength(254);
    expect(email).toHaveLength(254);

    fillAndSubmit(email);

    expect(emailErrors()).toEqual([]);
    expect(auth.resendVerification).toHaveBeenCalledTimes(1);
    expect(auth.resendVerification).toHaveBeenCalledWith(email);
  });

  // --- Soumission ---

  // Cas 10 : pas de trim ni de minuscules côté front
  it('calls resendVerification once with the email as typed', () => {
    auth.resendVerification.mockReturnValue(of(undefined));

    fillAndSubmit('Jean@Parkyn.app');

    expect(auth.resendVerification).toHaveBeenCalledTimes(1);
    expect(auth.resendVerification).toHaveBeenCalledWith('Jean@Parkyn.app');
  });

  // Cas 11
  it('calls the service only once when submitted twice while pending', () => {
    auth.resendVerification.mockReturnValue(new Subject<void>());
    fill('jean@parkyn.app');

    submit();
    submit();

    expect(auth.resendVerification).toHaveBeenCalledTimes(1);
  });

  // Cas 12
  it('disables the button and shows the pending label while sending', () => {
    auth.resendVerification.mockReturnValue(new Subject<void>());
    expect(submitButton().textContent?.trim()).toBe('Renvoyer le lien');

    fillAndSubmit();

    expect(submitButton().disabled).toBe(true);
    expect(submitButton().textContent?.trim()).toBe('Envoi…');
  });

  // --- Succès ---

  // Cas 13
  it('shows the neutral success status with the typed address', () => {
    auth.resendVerification.mockReturnValue(of(undefined));

    fillAndSubmit();

    const success = byTestId('resend-success');
    expect(success.getAttribute('role')).toBe('status');
    const text = (success.textContent ?? '').replace(/\s+/g, ' ');
    expect(text).toContain('Vérifiez votre boîte mail');
    expect(text).toContain(
      "Si un compte non vérifié existe avec l'adresse jean@parkyn.app, un nouvel email vient d'être envoyé.",
    );
    expect(byTestId('success-email').textContent?.trim()).toBe('jean@parkyn.app');
    expect(alerts()).toHaveLength(0);
  });

  // Cas 14
  it('replaces the form by the status and offers only the login link', () => {
    auth.resendVerification.mockReturnValue(of(undefined));

    fillAndSubmit();

    expect(byTestId('resend-success')).not.toBeNull();
    expect(host().querySelector('form')).toBeNull();
    expect(host().querySelector('#email')).toBeNull();
    expect(maybeTestId('submit-button')).toBeNull();
    expect(maybeTestId('resend-button')).toBeNull();
    expect(byTestId('login-link').getAttribute('href')).toBe('/login');
  });

  // Cas 15
  it('offers a back to login link on the form', () => {
    const link = byTestId('login-link');

    expect(link.textContent?.trim()).toBe('Retour à la connexion');
    expect(link.getAttribute('href')).toBe('/login');
  });

  // --- 400 du serveur ---

  // Cas 16
  it('shows a server email error under the field only', () => {
    auth.resendVerification.mockReturnValue(httpError(400, { errors: { email: ['m'] } }));

    fillAndSubmit();

    expect(emailErrors()).toEqual(['m']);
    expect(input().getAttribute('aria-invalid')).toBe('true');
    expect(alerts()).toHaveLength(0);
    expect(host().querySelector('form')).not.toBeNull();
  });

  // Cas 17
  it.each([
    ['a _global message', { _global: ['erreur globale'] }, 'erreur globale'],
    ['the message of an unknown key', { inconnu: ['clé inconnue'] }, 'clé inconnue'],
  ])('shows %s in the single top alert', (_label, errors, message) => {
    auth.resendVerification.mockReturnValue(httpError(400, { errors }));

    fillAndSubmit();

    expect(alerts()).toHaveLength(1);
    expect(alerts()[0].getAttribute('data-testid')).toBe('form-error');
    expect(alerts()[0].textContent).toContain(message);
    expect(emailErrors()).toEqual([]);
  });

  // Cas 18
  it.each([
    ['null', null],
    ['an empty object', {}],
  ])('shows the generic alert on a 400 with %s body', (_label, body) => {
    auth.resendVerification.mockReturnValue(httpError(400, body));

    fillAndSubmit();

    expect(alerts()).toHaveLength(1);
    expect(alerts()[0].textContent?.trim()).toBe(GENERIC_400);
    expect(emailErrors()).toEqual([]);
  });

  // Cas 19
  it('clears the server email error when the email changes', () => {
    auth.resendVerification.mockReturnValue(httpError(400, { errors: { email: ['mail refusé'] } }));
    fillAndSubmit();
    expect(emailErrors()).toEqual(['mail refusé']);

    fill('autre@parkyn.app');
    fixture.detectChanges();

    expect(emailErrors()).toEqual([]);
  });

  // Cas 20
  it('clears the alert when submitting again', () => {
    auth.resendVerification.mockReturnValueOnce(
      httpError(400, { errors: { email: ['m'], _global: ['erreur globale'] } }),
    );
    fillAndSubmit();
    expect(alerts()).toHaveLength(1);
    expect(emailErrors()).toEqual(['m']);

    auth.resendVerification.mockReturnValueOnce(new Subject<void>());
    submitAndRender();

    expect(auth.resendVerification).toHaveBeenCalledTimes(2);
    expect(alerts()).toHaveLength(0);
    expect(emailErrors()).toEqual([]);
  });

  // --- 429, 0, 5xx ---

  // Cas 21
  it.each([
    [{ 'Retry-After': '30' }, 'Trop de tentatives, réessayez dans 30 secondes'],
    [{}, 'Trop de tentatives, réessayez plus tard'],
  ])('shows the 429 message inline and keeps the form usable (%o)', (headers, expected) => {
    auth.resendVerification.mockReturnValue(httpError(429, null, headers));

    fillAndSubmit();

    expect(alerts()).toHaveLength(1);
    expect(alerts()[0].textContent?.trim()).toBe(expected);
    expect(submitButton().disabled).toBe(false);
  });

  // Cas 22
  it.each([0, 500])('shows no inline message on a %i and keeps the form', (status) => {
    auth.resendVerification.mockReturnValue(httpError(status));

    fillAndSubmit();

    expect(alerts()).toHaveLength(0);
    expect(maybeTestId('resend-success')).toBeNull();
    expect(host().querySelector('form')).not.toBeNull();
    expect(submitButton().disabled).toBe(false);
  });
});
