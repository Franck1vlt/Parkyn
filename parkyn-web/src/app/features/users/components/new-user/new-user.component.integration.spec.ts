import { HttpRequest, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { environment } from '../../../../../environments/environment';
import { ErrorInterceptor } from '../../../../core/interceptors/error.interceptor';
import { AuthService } from '../../../../core/services/auth.service';
import { ToastService } from '../../../../core/services/toast.service';
import { NewUserComponent } from './new-user.component';

describe('NewUserComponent with the real ErrorInterceptor', () => {
  const usersUrl = `${environment.apiUrl}/users`;
  const notificationsUrl = `${environment.apiUrl}/notifications`;
  const FORBIDDEN_TEXT = "Vous n'avez pas le droit de créer cet utilisateur.";
  const CONFLICT_TEXT = 'Cette adresse email est déjà utilisée.';
  const OLD_FIXED_MESSAGE = 'Une erreur est survenue. Veuillez réessayer.';
  let fixture: ComponentFixture<NewUserComponent>;
  let httpMock: HttpTestingController;
  let show: ReturnType<typeof vi.spyOn>;
  let navigate: ReturnType<typeof vi.spyOn>;
  let consoleError: ReturnType<typeof vi.spyOn>;
  let consoleLog: ReturnType<typeof vi.spyOn>;

  const isCreate = (r: HttpRequest<unknown>) => r.method === 'POST' && r.url === usersUrl;

  beforeEach(async () => {
    // Timers simulés : le header relance ses notifications toutes les 2 minutes
    vi.useFakeTimers();
    consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
    consoleLog = vi.spyOn(console, 'log').mockImplementation(() => {});
    vi.spyOn(AuthService.prototype, 'hasRole').mockReturnValue(true);
    await TestBed.configureTestingModule({
      imports: [NewUserComponent],
      providers: [
        provideHttpClient(withInterceptors([ErrorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    show = vi.spyOn(TestBed.inject(ToastService), 'show');
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(NewUserComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock
      .match((r: HttpRequest<unknown>) => r.url === notificationsUrl)
      .forEach((r: TestRequest) => r.flush([]));
    httpMock.verify();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  function host(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function control(name: string): HTMLInputElement {
    return host().querySelector<HTMLInputElement>(`[formControlName="${name}"]`)!;
  }

  function fill(name: string, value: string) {
    const el = control(name);
    el.value = value;
    el.dispatchEvent(new Event('input'));
  }

  function fillAndSubmit() {
    fill('firstname', 'Jean');
    fill('lastname', 'Dupont');
    fill('email', 'jean@parkyn.app');
    fill('password', 'un-mot-de-passe-12');
    fill('confirmPassword', 'un-mot-de-passe-12');
    const role = control('role');
    role.value = 'AGENT';
    role.dispatchEvent(new Event('change'));
    host().querySelector('form')!.dispatchEvent(new Event('submit'));
  }

  function answer(body: object | string | null, status: number) {
    httpMock.expectOne(isCreate).flush(body, { status, statusText: 'Status' });
    fixture.detectChanges();
  }

  // Messages affichés sous un champ, retrouvés par l'aria-describedby de son champ
  function errorsOf(name: string): string[] {
    const id = control(name).getAttribute('aria-describedby');
    if (!id) return [];
    return Array.from(host().querySelectorAll(`div[id="${id}"] p[role="alert"]`)).map(
      (p: Element) => p.textContent?.trim() ?? '',
    );
  }

  function formAlert(): HTMLElement | null {
    return host().querySelector<HTMLElement>('[data-testid="form-alert"]');
  }

  function submitButton(): HTMLButtonElement {
    return host().querySelector<HTMLButtonElement>('button[type="submit"]')!;
  }

  it('shows the server messages under email and password and no toast on a 400', () => {
    fillAndSubmit();

    answer(
      {
        errors: {
          email: ["Le format de l'adresse email est invalide."],
          password: ['Le mot de passe est trop court.', 'Le mot de passe est trop simple.'],
        },
      },
      400,
    );

    expect(errorsOf('email')).toEqual(["Le format de l'adresse email est invalide."]);
    expect(errorsOf('password')).toEqual([
      'Le mot de passe est trop court.',
      'Le mot de passe est trop simple.',
    ]);
    expect(control('email').getAttribute('aria-invalid')).toBe('true');
    expect(control('password').getAttribute('aria-invalid')).toBe('true');
    expect(control('firstname').getAttribute('aria-invalid')).not.toBe('true');
    expect(formAlert()).toBeNull();
    expect(host().textContent).not.toContain(OLD_FIXED_MESSAGE);
    expect(show).not.toHaveBeenCalled();
    expect(submitButton().disabled).toBe(false);
  });

  it('shows the already used email inline under email and no toast on a 409', () => {
    fillAndSubmit();

    answer('Email already exists', 409);

    expect(errorsOf('email')).toEqual([CONFLICT_TEXT]);
    expect(control('email').getAttribute('aria-invalid')).toBe('true');
    expect(formAlert()).toBeNull();
    expect(host().textContent).not.toContain('Email already exists');
    expect(show).not.toHaveBeenCalled();
    expect(submitButton().disabled).toBe(false);
  });

  it('shows exactly one toast with the action text on a 403, not the generic one', () => {
    fillAndSubmit();

    answer('Forbidden by API', 403);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show.mock.calls[0][0]).toBe(FORBIDDEN_TEXT);
    expect(show.mock.calls[0][0]).not.toBe('Accès refusé');
    expect(formAlert()).toBeNull();
    expect(errorsOf('email')).toEqual([]);
    expect(host().textContent).not.toContain('Forbidden by API');
    expect(submitButton().disabled).toBe(false);
  });

  it('shows only the interceptor toast and no form message on a 500', () => {
    fillAndSubmit();

    answer('SQL boom', 500);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Erreur serveur interne');
    expect(formAlert()).toBeNull();
    expect(host().querySelectorAll('[role="alert"]')).toHaveLength(0);
    expect(host().textContent).not.toContain(OLD_FIXED_MESSAGE);
    expect(host().textContent).not.toContain('SQL boom');
    expect(submitButton().disabled).toBe(false);
  });

  it('clears the email error as soon as the email is modified', () => {
    fillAndSubmit();
    answer('Email already exists', 409);
    expect(errorsOf('email')).toEqual([CONFLICT_TEXT]);

    fill('email', 'autre@parkyn.app');
    fixture.detectChanges();

    expect(errorsOf('email')).toEqual([]);
    expect(control('email').getAttribute('aria-invalid')).not.toBe('true');
    expect(control('email').getAttribute('aria-describedby')).toBeNull();
  });

  it('sends the form values and navigates to the users list without toast on a 201', () => {
    fillAndSubmit();

    const request = httpMock.expectOne(isCreate);
    expect(request.request.body).toEqual({
      firstname: 'Jean',
      lastname: 'Dupont',
      email: 'jean@parkyn.app',
      password: 'un-mot-de-passe-12',
      role: 'AGENT',
      active: true,
    });
    request.flush({ id: 'user-1' }, { status: 201, statusText: 'Created' });
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith(['/users']);
    expect(show).not.toHaveBeenCalled();
    expect(formAlert()).toBeNull();
  });

  it('writes nothing to the console when the creation fails', () => {
    fillAndSubmit();

    answer('Forbidden by API', 403);

    expect(consoleError).not.toHaveBeenCalled();
    expect(consoleLog).not.toHaveBeenCalled();
  });
});
