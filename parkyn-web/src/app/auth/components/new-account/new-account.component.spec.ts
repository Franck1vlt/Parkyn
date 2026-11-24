import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { provideTestApp } from '../../../../testing/test-providers';
import { AuthService } from '../../../core/services/auth.service';
import { NewAccountComponent } from './new-account.component';

describe('NewAccountComponent', () => {
  let fixture: ComponentFixture<NewAccountComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NewAccountComponent],
      providers: [provideTestApp()],
    }).compileComponents();

    fixture = TestBed.createComponent(NewAccountComponent);
  });

  // Test de fumée : l'injection de dépendances et le gabarit se résolvent sans erreur
  it('renders without error', () => {
    expect(() => fixture.detectChanges()).not.toThrow();
    expect(fixture.nativeElement.childElementCount).toBeGreaterThan(0);
  });
});

type Field = 'firstname' | 'lastname' | 'email' | 'password' | 'confirmPassword';

const FIELD_IDS: Record<Field, string> = {
  firstname: 'firstname',
  lastname: 'lastname',
  email: 'email',
  password: 'password',
  confirmPassword: 'confirm-password',
};
const ALL_FIELDS = Object.keys(FIELD_IDS) as Field[];

const VALID = {
  firstname: 'Jean',
  lastname: 'Dupont',
  email: 'jean@parkyn.app',
  password: 'un-mot-de-passe-12',
};

const GENERIC_400 = "Les informations saisies n'ont pas pu être validées. Veuillez réessayer.";
const OLD_FIXED_ERROR = 'Une erreur est survenue. Veuillez réessayer.';

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

describe('NewAccountComponent with a mocked AuthService', () => {
  let fixture: ComponentFixture<NewAccountComponent>;
  let navigate: ReturnType<typeof vi.spyOn>;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;
  const auth = { register: vi.fn() };

  beforeEach(async () => {
    vi.resetAllMocks();
    await TestBed.configureTestingModule({
      imports: [NewAccountComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();

    const router = TestBed.inject(Router);
    navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    navigateByUrl = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(NewAccountComponent);
    fixture.detectChanges();
  });

  // --- Helpers ---

  function host(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function input(field: Field): HTMLInputElement {
    return host().querySelector<HTMLInputElement>(`#${FIELD_IDS[field]}`)!;
  }

  function fill(field: Field, value: string) {
    const el = input(field);
    el.value = value;
    el.dispatchEvent(new Event('input'));
  }

  function blur(field: Field) {
    input(field).dispatchEvent(new Event('blur'));
  }

  // Remplit un formulaire valide ; la confirmation suit le mot de passe sauf surcharge
  function fillValid(overrides: Partial<Record<Field, string>> = {}) {
    const values = { ...VALID, ...overrides };
    fill('firstname', values.firstname);
    fill('lastname', values.lastname);
    fill('email', values.email);
    fill('password', values.password);
    fill('confirmPassword', overrides.confirmPassword ?? values.password);
  }

  function submit() {
    host().querySelector('form')!.dispatchEvent(new Event('submit'));
  }

  function submitAndRender() {
    submit();
    fixture.detectChanges();
  }

  function fillValidAndSubmit(overrides: Partial<Record<Field, string>> = {}) {
    fillValid(overrides);
    submitAndRender();
  }

  function errorsOf(field: Field): string[] {
    return Array.from(host().querySelectorAll(`#${FIELD_IDS[field]}-error p`)).map(
      (p) => p.textContent?.trim() ?? '',
    );
  }

  function alerts(): HTMLElement[] {
    return Array.from(host().querySelectorAll<HTMLElement>('[role="alert"]'));
  }

  function byTestId(id: string): HTMLElement | null {
    return host().querySelector<HTMLElement>(`[data-testid="${id}"]`);
  }

  function submitButton(): HTMLButtonElement {
    return byTestId('submit-button') as HTMLButtonElement;
  }

  function httpError(status: number, error: unknown = null, headers: Record<string, string> = {}) {
    return throwError(
      () => new HttpErrorResponse({ status, error, headers: new HttpHeaders(headers) }),
    );
  }

  // --- D-1 Structure et accessibilité ---

  // D1 : chaque champ a un id et un label associé
  it.each(ALL_FIELDS)('links a label to the %s field', (field) => {
    const id = FIELD_IDS[field];

    expect(host().querySelector(`input#${id}`)).not.toBeNull();
    expect(host().querySelector(`label[for="${id}"]`)).not.toBeNull();
  });

  // D2 : attributs autocomplete
  it.each([
    ['firstname', 'given-name'],
    ['lastname', 'family-name'],
    ['email', 'email'],
    ['password', 'new-password'],
    ['confirmPassword', 'new-password'],
  ] as [Field, string][])('sets autocomplete on the %s field', (field, expected) => {
    expect(input(field).getAttribute('autocomplete')).toBe(expected);
  });

  // --- D-2 Validation locale ---

  // D4 : formulaire vide soumis, les cinq messages requis s'affichent
  it('shows the five required messages when submitting an empty form', () => {
    submitAndRender();

    expect(auth.register).not.toHaveBeenCalled();
    expect(errorsOf('firstname')).toEqual(['Le prénom est obligatoire.']);
    expect(errorsOf('lastname')).toEqual(['Le nom est obligatoire.']);
    expect(errorsOf('email')).toEqual(["L'adresse email est obligatoire."]);
    expect(errorsOf('password')).toEqual(['Le mot de passe est obligatoire.']);
    expect(errorsOf('confirmPassword')).toEqual(['Confirmez le mot de passe.']);
  });

  // D5 : un champ vidé puis quitté affiche son message requis, et lui seul
  it.each([
    ['firstname', 'Le prénom est obligatoire.'],
    ['lastname', 'Le nom est obligatoire.'],
    ['email', "L'adresse email est obligatoire."],
    ['password', 'Le mot de passe est obligatoire.'],
    ['confirmPassword', 'Confirmez le mot de passe.'],
  ] as [Field, string][])('shows the required message on blur of %s only', (field, message) => {
    fill(field, 'x');
    fill(field, '');
    blur(field);
    fixture.detectChanges();

    expect(errorsOf(field)).toEqual([message]);
    for (const other of ALL_FIELDS.filter((f) => f !== field)) {
      expect(errorsOf(other)).toEqual([]);
    }
  });

  // D7 : prénom et nom, 51 caractères refusés
  it.each(['firstname', 'lastname'] as Field[])('rejects a %s of 51 characters', (field) => {
    fillValidAndSubmit({ [field]: 'a'.repeat(51) });

    expect(errorsOf(field)).toEqual(['50 caractères maximum.']);
    expect(auth.register).not.toHaveBeenCalled();
  });

  // D7 : prénom et nom, 50 caractères acceptés
  it.each(['firstname', 'lastname'] as Field[])('accepts a %s of 50 characters', (field) => {
    auth.register.mockReturnValue(of(undefined));

    fillValidAndSubmit({ [field]: 'a'.repeat(50) });

    expect(errorsOf(field)).toEqual([]);
    expect(auth.register).toHaveBeenCalledTimes(1);
  });

  // D8 : email de 254 caractères accepté
  it('accepts an email of 254 characters', () => {
    auth.register.mockReturnValue(of(undefined));
    const email = emailOfLength(254);
    expect(email).toHaveLength(254);

    fillValidAndSubmit({ email });

    expect(errorsOf('email')).toEqual([]);
    expect(auth.register).toHaveBeenCalledTimes(1);
  });

  // D8 : email de 255 caractères refusé
  it('rejects an email of 255 characters', () => {
    const email = emailOfLength(255);
    expect(email).toHaveLength(255);

    fillValidAndSubmit({ email });

    expect(errorsOf('email')).toEqual(['254 caractères maximum.']);
    expect(auth.register).not.toHaveBeenCalled();
  });

  // D9 : bornes du mot de passe, 12 et 128 acceptés
  it.each([12, 128])('accepts a password of %i characters', (length) => {
    auth.register.mockReturnValue(of(undefined));

    fillValidAndSubmit({ password: 'x'.repeat(length) });

    expect(errorsOf('password')).toEqual([]);
    expect(auth.register).toHaveBeenCalledTimes(1);
  });

  // D9 : 11 caractères refusés
  it('rejects a password of 11 characters', () => {
    fillValidAndSubmit({ password: 'x'.repeat(11) });

    expect(errorsOf('password')).toEqual(['12 caractères minimum.']);
    expect(auth.register).not.toHaveBeenCalled();
  });

  // D9 : 129 caractères refusés
  it('rejects a password of 129 characters', () => {
    fillValidAndSubmit({ password: 'x'.repeat(129) });

    expect(errorsOf('password')).toEqual(['128 caractères maximum.']);
    expect(auth.register).not.toHaveBeenCalled();
  });

  // D10 : email invalide
  it('rejects an invalid email', () => {
    fillValidAndSubmit({ email: 'pas-un-email' });

    expect(errorsOf('email')).toEqual(["L'adresse email n'est pas valide."]);
    expect(auth.register).not.toHaveBeenCalled();
  });

  // D11 : caractères non autorisés dans le prénom
  it('rejects a firstname with digits', () => {
    fillValidAndSubmit({ firstname: 'Jean3' });

    expect(errorsOf('firstname')).toEqual(['Contient des caractères non autorisés.']);
    expect(auth.register).not.toHaveBeenCalled();
  });

  // D12 : espaces seuls dans le prénom ou le nom
  it.each([
    ['firstname', 'Le prénom est obligatoire.'],
    ['lastname', 'Le nom est obligatoire.'],
  ] as [Field, string][])('treats spaces only in %s as missing', (field, message) => {
    fillValidAndSubmit({ [field]: '   ' });

    expect(errorsOf(field)).toEqual([message]);
    expect(auth.register).not.toHaveBeenCalled();
  });

  // D13 : noms valides envoyés tels quels
  it.each(['Jean-Pierre', "O'Brien", 'Éloïse'])('sends the valid name %s', (name) => {
    auth.register.mockReturnValue(of(undefined));

    fillValidAndSubmit({ firstname: name });

    expect(auth.register).toHaveBeenCalledWith(name, 'Dupont', 'jean@parkyn.app', VALID.password);
  });

  // D14 : confirmation différente
  it('shows the mismatch under the confirmation field', () => {
    fillValidAndSubmit({ confirmPassword: 'un-autre-mot-de-passe' });

    expect(errorsOf('confirmPassword')).toEqual(['Les mots de passe ne correspondent pas.']);
    expect(errorsOf('password')).toEqual([]);
    expect(auth.register).not.toHaveBeenCalled();
  });

  // D15 : champ invalide, aria-invalid et aria-describedby vers le message
  it('marks an invalid field and links it to its message', () => {
    fillValidAndSubmit({ firstname: '' });

    const firstname = input('firstname');
    expect(firstname.getAttribute('aria-invalid')).toBe('true');
    const describedBy = firstname.getAttribute('aria-describedby');
    expect(describedBy).toBe('firstname-error');
    expect(host().querySelector(`#${describedBy}`)?.textContent).toContain(
      'Le prénom est obligatoire.',
    );
  });

  // --- D-3 Soumission et double clic ---

  // D16 : formulaire valide, appel unique sans confirmation
  it('calls register once with the four values and no confirmation', () => {
    auth.register.mockReturnValue(of(undefined));

    fillValidAndSubmit();

    expect(auth.register).toHaveBeenCalledTimes(1);
    expect(auth.register).toHaveBeenCalledWith(
      'Jean',
      'Dupont',
      'jean@parkyn.app',
      'un-mot-de-passe-12',
    );
  });

  // D17 : double soumission avec requête en attente
  it('calls register only once when submitted twice while pending', () => {
    auth.register.mockReturnValue(new Subject<void>());
    fillValid();

    submit();
    submit();

    expect(auth.register).toHaveBeenCalledTimes(1);
  });

  // D42 : bouton désactivé tant que le formulaire est vide
  it('disables the submit button while the form is empty', () => {
    expect(submitButton().disabled).toBe(true);
  });

  // D43 : bouton activé quand tous les champs sont remplis et valides
  it('enables the submit button once every field is filled and valid', () => {
    fillValid();
    fixture.detectChanges();

    expect(submitButton().disabled).toBe(false);
  });

  // D44 : un seul champ invalide ou manquant suffit à désactiver le bouton
  it.each<[string, Partial<Record<Field, string>>]>([
    ['a blank firstname', { firstname: '' }],
    ['a blank lastname', { lastname: '' }],
    ['a blank email', { email: '' }],
    ['a blank password', { password: '', confirmPassword: '' }],
    ['a blank confirmation', { confirmPassword: '' }],
    ['spaces only in firstname', { firstname: '   ' }],
    ['digits in firstname', { firstname: 'Jean3' }],
    ['a lastname of 51 characters', { lastname: 'a'.repeat(51) }],
    ['an invalid email', { email: 'pas-un-email' }],
    ['a password of 11 characters', { password: 'x'.repeat(11), confirmPassword: 'x'.repeat(11) }],
    ['a different confirmation', { confirmPassword: 'un-autre-mot-de-passe' }],
  ])('disables the submit button with %s', (_label, overrides) => {
    fillValid(overrides);
    fixture.detectChanges();

    expect(submitButton().disabled).toBe(true);
  });

  // D45 : bouton désactivé pendant l'envoi
  it('disables the submit button while the request is pending', () => {
    auth.register.mockReturnValue(new Subject<void>());

    fillValidAndSubmit();

    expect(submitButton().disabled).toBe(true);
  });

  // D19 : après une erreur 500, le bouton est réactivé et on peut re-soumettre
  it('re-enables the button and allows a new submission after a 500', () => {
    auth.register.mockReturnValueOnce(httpError(500));
    fillValidAndSubmit();

    expect(submitButton()).not.toBeNull();
    expect(submitButton().disabled).toBe(false);

    auth.register.mockReturnValueOnce(new Subject<void>());
    submitAndRender();

    expect(auth.register).toHaveBeenCalledTimes(2);
  });

  // --- D-4 Succès ---

  // D20 : message de succès avec l'adresse saisie
  it('shows the check-your-mailbox status with the typed address', () => {
    auth.register.mockReturnValue(of(undefined));

    fillValidAndSubmit();

    const success = byTestId('register-success');
    expect(success).not.toBeNull();
    expect(success!.getAttribute('role')).toBe('status');
    expect(success!.textContent).toContain('Vérifiez votre boîte mail');
    expect(byTestId('success-email')?.textContent?.trim()).toBe('jean@parkyn.app');
  });

  // D21 : le formulaire disparaît
  it('removes the form after a success', () => {
    auth.register.mockReturnValue(of(undefined));

    fillValidAndSubmit();

    expect(host().querySelector('form')).toBeNull();
    for (const field of ALL_FIELDS) {
      expect(host().querySelector(`#${FIELD_IDS[field]}`)).toBeNull();
    }
  });

  // D22 : pas de navigation après le succès
  it('does not navigate after a success', () => {
    auth.register.mockReturnValue(of(undefined));

    fillValidAndSubmit();

    expect(byTestId('register-success')).not.toBeNull();
    expect(navigate).not.toHaveBeenCalled();
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  // D23 : lien vers la connexion, pas de renvoi du lien
  it('offers a login link and no resend button after a success', () => {
    auth.register.mockReturnValue(of(undefined));

    fillValidAndSubmit();

    expect(byTestId('login-link')?.getAttribute('href')).toBe('/login');
    expect(byTestId('resend-button')).toBeNull();
  });

  // D24 : message neutre, aucune indication sur l'existence du compte
  it('keeps the success message neutral about the account', () => {
    auth.register.mockReturnValue(of(undefined));

    fillValidAndSubmit();

    const text = byTestId('register-success')?.textContent ?? '';
    expect(text).toContain('Vérifiez votre boîte mail');
    expect(text.toLowerCase()).not.toContain('existe');
    expect(text.toLowerCase()).not.toContain('déjà');
  });

  // D25 : les mots de passe sont vidés du formulaire après le succès
  it('clears both passwords from the form after a success', () => {
    auth.register.mockReturnValue(of(undefined));

    fillValidAndSubmit();

    const value = fixture.componentInstance.accountForm.value;
    expect(value.password).toBe('');
    expect(value.confirmPassword).toBe('');
  });

  // --- D-5 400 du serveur ---

  // D27 : un message sous l'email seulement, sans alerte
  it('shows a server email error under the email field only', () => {
    auth.register.mockReturnValue(httpError(400, { errors: { email: ['m'] } }));

    fillValidAndSubmit();

    expect(errorsOf('email')).toEqual(['m']);
    expect(input('email').getAttribute('aria-invalid')).toBe('true');
    for (const other of ALL_FIELDS.filter((f) => f !== 'email')) {
      expect(errorsOf(other)).toEqual([]);
    }
    expect(alerts()).toHaveLength(0);
  });

  // D28 : chaque clé sous son champ
  it.each(['firstname', 'lastname', 'email', 'password'] as Field[])(
    'shows the server %s error under its own field',
    (field) => {
      auth.register.mockReturnValue(httpError(400, { errors: { [field]: [`msg-${field}`] } }));

      fillValidAndSubmit();

      expect(errorsOf(field)).toEqual([`msg-${field}`]);
      for (const other of ALL_FIELDS.filter((f) => f !== field)) {
        expect(errorsOf(other)).toEqual([]);
      }
    },
  );

  // D29 : plusieurs messages sur un champ, dans l'ordre
  it('shows every server message of a field in order', () => {
    auth.register.mockReturnValue(httpError(400, { errors: { password: ['premier', 'second'] } }));

    fillValidAndSubmit();

    expect(errorsOf('password')).toEqual(['premier', 'second']);
  });

  // D30 : _global dans l'alerte du haut
  it('shows a _global message in the single top alert', () => {
    auth.register.mockReturnValue(httpError(400, { errors: { _global: ['erreur globale'] } }));

    fillValidAndSubmit();

    expect(alerts()).toHaveLength(1);
    expect(alerts()[0].getAttribute('data-testid')).toBe('form-error');
    expect(alerts()[0].textContent).toContain('erreur globale');
    for (const field of ALL_FIELDS) {
      expect(errorsOf(field)).toEqual([]);
    }
  });

  // D31 : champ et _global ensemble, une seule alerte
  it('shows a field error and a _global alert together without duplicate alert', () => {
    auth.register.mockReturnValue(
      httpError(400, { errors: { email: ['m'], _global: ['erreur globale'] } }),
    );

    fillValidAndSubmit();

    expect(alerts()).toHaveLength(1);
    expect(alerts()[0].textContent).toContain('erreur globale');
    expect(errorsOf('email')).toEqual(['m']);
  });

  // D32 : clé inconnue dans l'alerte du haut
  it('shows the messages of an unknown key in the top alert', () => {
    auth.register.mockReturnValue(httpError(400, { errors: { inconnu: ['clé inconnue'] } }));

    fillValidAndSubmit();

    expect(alerts()).toHaveLength(1);
    expect(alerts()[0].textContent).toContain('clé inconnue');
    for (const field of ALL_FIELDS) {
      expect(errorsOf(field)).toEqual([]);
    }
  });

  // D33 : corps non conformes, message générique
  it.each([
    ['null', null],
    ['an empty object', {}],
    ['errors null', { errors: null }],
    ['errors as an array', { errors: [] }],
    ['a string', 'texte'],
  ])('shows the generic alert on a 400 with %s body', (_label, body) => {
    auth.register.mockReturnValue(httpError(400, body));

    fillValidAndSubmit();

    expect(alerts()).toHaveLength(1);
    expect(alerts()[0].textContent?.trim()).toBe(GENERIC_400);
    for (const field of ALL_FIELDS) {
      expect(errorsOf(field)).toEqual([]);
    }
  });

  // D35 : modifier l'email efface seulement ses erreurs serveur
  it('clears only the email server errors when the email changes', () => {
    auth.register.mockReturnValue(
      httpError(400, { errors: { email: ['mail refusé'], password: ['mot de passe refusé'] } }),
    );
    fillValidAndSubmit();
    expect(errorsOf('email')).toEqual(['mail refusé']);

    fill('email', 'autre@parkyn.app');
    fixture.detectChanges();

    expect(errorsOf('email')).toEqual([]);
    expect(errorsOf('password')).toEqual(['mot de passe refusé']);
  });

  // D36 : re-soumettre efface toutes les erreurs serveur et l'alerte
  it('clears every server error and the alert when submitting again', () => {
    auth.register.mockReturnValueOnce(
      httpError(400, { errors: { email: ['m'], _global: ['erreur globale'] } }),
    );
    fillValidAndSubmit();
    expect(errorsOf('email')).toEqual(['m']);
    expect(alerts()).toHaveLength(1);

    auth.register.mockReturnValueOnce(new Subject<void>());
    submitAndRender();

    expect(auth.register).toHaveBeenCalledTimes(2);
    for (const field of ALL_FIELDS) {
      expect(errorsOf(field)).toEqual([]);
    }
    expect(alerts()).toHaveLength(0);
  });

  // --- D-6 429, 0, 5xx ---

  // D38 : 429 avec décompte
  it.each([
    ['30', 'Trop de tentatives, réessayez dans 30 secondes'],
    ['2', 'Trop de tentatives, réessayez dans 2 secondes'],
    ['1', 'Trop de tentatives, réessayez dans 1 seconde'],
  ])('shows the countdown on a 429 with Retry-After %s', (retryAfter, expected) => {
    auth.register.mockReturnValue(httpError(429, null, { 'Retry-After': retryAfter }));

    fillValidAndSubmit();

    expect(alerts()).toHaveLength(1);
    expect(alerts()[0].textContent?.trim()).toBe(expected);
  });

  // D39 : 429 avec Retry-After inutilisable
  it.each([
    ['absent', {}],
    ['non numeric', { 'Retry-After': 'abc' }],
    ['zero', { 'Retry-After': '0' }],
    ['negative', { 'Retry-After': '-5' }],
  ])('shows the generic delay on a 429 when Retry-After is %s', (_label, headers) => {
    auth.register.mockReturnValue(httpError(429, null, headers));

    fillValidAndSubmit();

    expect(alerts()).toHaveLength(1);
    expect(alerts()[0].textContent?.trim()).toBe('Trop de tentatives, réessayez plus tard');
  });

  // D40 : 0, 500 et 503 restent au toast global, sans alerte ni ancien texte fixe
  it.each([0, 500, 503])('shows no inline message on a %i', (status) => {
    auth.register.mockReturnValue(httpError(status));

    fillValidAndSubmit();

    expect(alerts()).toHaveLength(0);
    expect(host().textContent).not.toContain(OLD_FIXED_ERROR);
    expect(host().querySelector('form')).not.toBeNull();
  });
});
