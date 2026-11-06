import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { environment } from '../../../../environments/environment';
import { ErrorInterceptor } from '../../../core/interceptors/error.interceptor';
import { ToastService } from '../../../core/services/toast.service';
import { VerifyEmailComponent } from './verify-email.component';

@Component({ template: '' })
class LoginStubComponent {}

describe('VerifyEmailComponent with the real AuthService and ErrorInterceptor', () => {
  const verifyUrl = `${environment.apiUrl}/auth/verify-email`;
  let httpMock: HttpTestingController;
  let show: ReturnType<typeof vi.spyOn>;
  let harness: RouterTestingHarness;
  let host: HTMLElement;

  beforeEach(async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      imports: [VerifyEmailComponent],
      providers: [
        provideHttpClient(withInterceptors([ErrorInterceptor])),
        provideHttpClientTesting(),
        // Routes factices : la vraie route verify-email n'existe pas encore
        provideRouter([
          { path: 'verify-email', component: VerifyEmailComponent },
          { path: 'login', component: LoginStubComponent },
        ]),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    show = vi.spyOn(TestBed.inject(ToastService), 'show');
  });

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  async function open(url = '/verify-email?token=abc'): Promise<void> {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, VerifyEmailComponent);
    harness.detectChanges();
    host = harness.routeNativeElement as HTMLElement;
  }

  // Répond au POST de vérification puis rend l'état obtenu
  function answer(status: number, headers: Record<string, string> = {}): void {
    httpMock.expectOne(verifyUrl).flush(null, { status, statusText: 'Status', headers });
    harness.detectChanges();
  }

  function byId(id: string): HTMLElement | null {
    return host.querySelector<HTMLElement>(`[data-testid="${id}"]`);
  }

  function stateBlocks(): HTMLElement[] {
    return Array.from(host.querySelectorAll<HTMLElement>('[role="status"], [role="alert"]'));
  }

  it('verifies the token once and shows the success without toast', async () => {
    await open();

    const request = httpMock.expectOne(verifyUrl);
    expect(request.request.body).toEqual({ token: 'abc' });
    request.flush(null, { status: 200, statusText: 'OK' });
    harness.detectChanges();

    expect(stateBlocks()).toHaveLength(1);
    expect(byId('verify-success')?.getAttribute('role')).toBe('status');
    expect(show).not.toHaveBeenCalled();
  });

  it('shows the invalid alert and no toast on a 400', async () => {
    await open();

    answer(400);

    expect(stateBlocks()).toHaveLength(1);
    expect(byId('verify-invalid')?.getAttribute('role')).toBe('alert');
    expect(show).not.toHaveBeenCalled();
  });

  it('shows the expired alert and no toast on a 410', async () => {
    await open();

    answer(410);

    expect(stateBlocks()).toHaveLength(1);
    expect(byId('verify-expired')?.getAttribute('role')).toBe('alert');
    expect(show).not.toHaveBeenCalled();
  });

  it('keeps the global toast and shows the generic alert on a 429', async () => {
    await open();

    answer(429, { 'Retry-After': '30' });

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Trop de tentatives, réessayez dans 30 secondes');
    expect(stateBlocks()).toHaveLength(1);
    expect(byId('verify-error')?.getAttribute('role')).toBe('alert');
    expect(byId('retry-button')).not.toBeNull();
  });

  it('keeps the server error toast and shows the generic alert on a 500', async () => {
    await open();

    answer(500);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Erreur serveur interne');
    expect(stateBlocks()).toHaveLength(1);
    expect(byId('verify-error')?.getAttribute('role')).toBe('alert');
    expect(byId('retry-button')).not.toBeNull();
  });

  it('keeps the unreachable toast and shows the generic alert on a network error', async () => {
    await open();

    httpMock.expectOne(verifyUrl).error(new ProgressEvent('error'));
    harness.detectChanges();

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Serveur injoignable');
    expect(stateBlocks()).toHaveLength(1);
    expect(byId('verify-error')?.getAttribute('role')).toBe('alert');
    expect(byId('retry-button')).not.toBeNull();
  });

  // Le retrait du jeton de l'URL ne doit ni relancer l'appel ni changer l'état
  it('calls once, strips the token from the URL and keeps the success state', async () => {
    await open('/verify-email?token=abc');

    httpMock.expectOne(verifyUrl).flush(null, { status: 200, statusText: 'OK' });
    await vi.advanceTimersByTimeAsync(0);
    harness.detectChanges();

    const router = TestBed.inject(Router);
    expect(router.url).not.toContain('token');
    expect(router.url).toBe('/verify-email');
    expect(byId('verify-success')).not.toBeNull();
    expect(stateBlocks()).toHaveLength(1);
    httpMock.expectNone(verifyUrl);
  });

  it('navigates to /login when clicking the login link', async () => {
    await open();
    answer(200);
    const link = byId('login-link');
    expect(link).not.toBeNull();

    link!.click();
    await vi.advanceTimersByTimeAsync(0);

    expect(TestBed.inject(Router).url).toBe('/login');
  });
});
