import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { ErrorInterceptor } from '../../../core/interceptors/error.interceptor';
import { ToastService } from '../../../core/services/toast.service';
import { ResendVerificationComponent } from './resend-verification.component';

describe('ResendVerificationComponent with the real AuthService and ErrorInterceptor', () => {
  const resendUrl = `${environment.apiUrl}/auth/resend-verification`;
  let fixture: ComponentFixture<ResendVerificationComponent>;
  let httpMock: HttpTestingController;
  let show: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    vi.useFakeTimers();
    vi.spyOn(console, 'log').mockImplementation(() => {});
    await TestBed.configureTestingModule({
      imports: [ResendVerificationComponent],
      providers: [
        provideHttpClient(withInterceptors([ErrorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    show = vi.spyOn(TestBed.inject(ToastService), 'show');
    fixture = TestBed.createComponent(ResendVerificationComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  function host(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  // Saisit un email valide puis soumet le formulaire
  function fillAndSubmit() {
    const el = host().querySelector<HTMLInputElement>('#email');
    expect(el).not.toBeNull();
    el!.value = 'jean@parkyn.app';
    el!.dispatchEvent(new Event('input'));
    const form = host().querySelector('form');
    expect(form).not.toBeNull();
    form!.dispatchEvent(new Event('submit'));
  }

  function answer(
    body: object | string | null,
    status: number,
    headers: Record<string, string> = {},
  ) {
    httpMock.expectOne(resendUrl).flush(body, { status, statusText: 'Status', headers });
    fixture.detectChanges();
  }

  function emailErrors(): string[] {
    return Array.from(host().querySelectorAll('#email-error p')).map(
      (p) => p.textContent?.trim() ?? '',
    );
  }

  function alerts(): HTMLElement[] {
    return Array.from(host().querySelectorAll<HTMLElement>('[role="alert"]'));
  }

  function submitButton(): HTMLButtonElement {
    const el = host().querySelector<HTMLButtonElement>('[data-testid="submit-button"]');
    expect(el).not.toBeNull();
    return el as HTMLButtonElement;
  }

  // Cas 23 : 202, corps exact, succès neutre, aucun toast
  it('posts the email and shows the success without toast on a 202', () => {
    fillAndSubmit();

    const request = httpMock.expectOne(resendUrl);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'jean@parkyn.app' });
    request.flush(null, { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();

    const success = host().querySelector('[data-testid="resend-success"]');
    expect(success).not.toBeNull();
    expect(success!.textContent).toContain('Vérifiez votre boîte mail');
    expect(host().querySelector('[data-testid="success-email"]')?.textContent?.trim()).toBe(
      'jean@parkyn.app',
    );
    expect(show).not.toHaveBeenCalled();
  });

  // Cas 24 : prouve que HANDLED_STATUSES contient 400
  it('shows the field error and no toast on a 400', () => {
    fillAndSubmit();

    answer({ errors: { email: ['mail refusé'] } }, 400);

    expect(emailErrors()).toEqual(['mail refusé']);
    expect(show).not.toHaveBeenCalled();
  });

  // Cas 25 : prouve que le service pose HANDLED_BY_SCREEN
  it('shows the inline countdown and no toast on a 429', () => {
    fillAndSubmit();

    answer(null, 429, { 'Retry-After': '30' });

    expect(alerts()).toHaveLength(1);
    expect(alerts()[0].textContent?.trim()).toBe('Trop de tentatives, réessayez dans 30 secondes');
    expect(show).not.toHaveBeenCalled();
  });

  // Cas 26 : un 500 reste un toast global, formulaire réutilisable
  it('keeps the server error toast and the form on a 500', () => {
    fillAndSubmit();

    answer(null, 500);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Erreur serveur interne');
    expect(alerts()).toHaveLength(0);
    expect(host().querySelector('form')).not.toBeNull();
    expect(submitButton().disabled).toBe(false);
  });
});
