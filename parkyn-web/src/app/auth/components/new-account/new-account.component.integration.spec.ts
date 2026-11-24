import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { ErrorInterceptor } from '../../../core/interceptors/error.interceptor';
import { ToastService } from '../../../core/services/toast.service';
import { NewAccountComponent } from './new-account.component';

describe('NewAccountComponent with the real AuthService and ErrorInterceptor', () => {
  const registerUrl = `${environment.apiUrl}/auth/register`;
  const GENERIC_400 = "Les informations saisies n'ont pas pu être validées. Veuillez réessayer.";
  let fixture: ComponentFixture<NewAccountComponent>;
  let httpMock: HttpTestingController;
  let show: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    vi.useFakeTimers();
    vi.spyOn(console, 'log').mockImplementation(() => {});
    await TestBed.configureTestingModule({
      imports: [NewAccountComponent],
      providers: [
        provideHttpClient(withInterceptors([ErrorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    show = vi.spyOn(TestBed.inject(ToastService), 'show');
    fixture = TestBed.createComponent(NewAccountComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  function host(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function fill(id: string, value: string) {
    const el = host().querySelector<HTMLInputElement>(`#${id}`)!;
    el.value = value;
    el.dispatchEvent(new Event('input'));
  }

  // Saisit un formulaire valide puis le soumet
  function fillAndSubmit() {
    fill('firstname', 'Jean');
    fill('lastname', 'Dupont');
    fill('email', 'jean@parkyn.app');
    fill('password', 'un-mot-de-passe-12');
    fill('confirm-password', 'un-mot-de-passe-12');
    host().querySelector('form')!.dispatchEvent(new Event('submit'));
  }

  function answer(
    body: object | string | null,
    status: number,
    headers: Record<string, string> = {},
  ) {
    httpMock.expectOne(registerUrl).flush(body, { status, statusText: 'Status', headers });
    fixture.detectChanges();
  }

  function errorsOf(id: string): string[] {
    return Array.from(host().querySelectorAll(`#${id}-error p`)).map(
      (p) => p.textContent?.trim() ?? '',
    );
  }

  function alerts(): HTMLElement[] {
    return Array.from(host().querySelectorAll<HTMLElement>('[role="alert"]'));
  }

  function submitButton(): HTMLButtonElement {
    return host().querySelector<HTMLButtonElement>('[data-testid="submit-button"]')!;
  }

  // E1 : 202, corps exact, message de succès, aucun toast
  it('sends the four fields and shows the success without toast on a 202', () => {
    fillAndSubmit();

    const request = httpMock.expectOne(registerUrl);
    expect(request.request.body).toEqual({
      firstname: 'Jean',
      lastname: 'Dupont',
      email: 'jean@parkyn.app',
      password: 'un-mot-de-passe-12',
    });
    request.flush(null, { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();

    const success = host().querySelector('[data-testid="register-success"]');
    expect(success).not.toBeNull();
    expect(success!.textContent).toContain('Vérifiez votre boîte mail');
    expect(host().querySelector('[data-testid="success-email"]')?.textContent?.trim()).toBe(
      'jean@parkyn.app',
    );
    expect(show).not.toHaveBeenCalled();
  });

  // E2 : prouve que HANDLED_STATUSES contient 400
  it('shows the field errors and no toast on a 400 with errors', () => {
    fillAndSubmit();

    answer({ errors: { firstname: ['prénom refusé'], email: ['mail refusé'] } }, 400);

    expect(errorsOf('firstname')).toEqual(['prénom refusé']);
    expect(errorsOf('email')).toEqual(['mail refusé']);
    expect(show).not.toHaveBeenCalled();
  });

  // E3 : 400 sans corps exploitable
  it('shows the generic alert and no toast on a 400 with a null body', () => {
    fillAndSubmit();

    answer(null, 400);

    expect(alerts()).toHaveLength(1);
    expect(alerts()[0].textContent?.trim()).toBe(GENERIC_400);
    expect(show).not.toHaveBeenCalled();
  });

  // E4 : prouve que le composant pose HANDLED_BY_SCREEN
  it('shows the inline countdown and no toast on a 429', () => {
    fillAndSubmit();

    answer(null, 429, { 'Retry-After': '30' });

    expect(alerts()).toHaveLength(1);
    expect(alerts()[0].textContent?.trim()).toBe('Trop de tentatives, réessayez dans 30 secondes');
    expect(show).not.toHaveBeenCalled();
  });

  // E5 : un 500 reste un toast global, formulaire réutilisable
  it('shows the server error toast, no alert and a reusable form on a 500', () => {
    fillAndSubmit();

    answer(null, 500);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Erreur serveur interne');
    expect(alerts()).toHaveLength(0);
    expect(host().textContent).not.toContain('Une erreur est survenue. Veuillez réessayer.');
    expect(host().querySelector('form')).not.toBeNull();
    expect(submitButton().disabled).toBe(false);
  });

  // E6 : erreur réseau, toast global seulement
  it('shows the unreachable toast and no alert on a network error', () => {
    fillAndSubmit();

    httpMock.expectOne(registerUrl).error(new ProgressEvent('error'));
    fixture.detectChanges();

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Serveur injoignable');
    expect(alerts()).toHaveLength(0);
    expect(host().textContent).not.toContain('Une erreur est survenue. Veuillez réessayer.');
    expect(submitButton().disabled).toBe(false);
  });
});
