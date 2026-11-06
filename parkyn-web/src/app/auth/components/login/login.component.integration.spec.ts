import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { ErrorInterceptor } from '../../../core/interceptors/error.interceptor';
import { ToastService } from '../../../core/services/toast.service';
import { LoginComponent } from './login.component';

describe('LoginComponent with the real AuthService and ErrorInterceptor', () => {
  const loginUrl = `${environment.apiUrl}/auth/login`;
  let fixture: ComponentFixture<LoginComponent>;
  let httpMock: HttpTestingController;
  let show: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    vi.useFakeTimers();
    vi.spyOn(console, 'log').mockImplementation(() => {});
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideHttpClient(withInterceptors([ErrorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    show = vi.spyOn(TestBed.inject(ToastService), 'show');
    fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  // Saisit des identifiants, soumet, et répond au login avec l'erreur donnée
  function failLogin(status: number, headers: Record<string, string> = {}) {
    const host = fixture.nativeElement as HTMLElement;
    const email = host.querySelector<HTMLInputElement>('#email')!;
    const password = host.querySelector<HTMLInputElement>('#password')!;
    email.value = 'jean@parkyn.app';
    email.dispatchEvent(new Event('input'));
    password.value = 'mauvais';
    password.dispatchEvent(new Event('input'));
    host.querySelector('form')!.dispatchEvent(new Event('submit'));

    httpMock.expectOne(loginUrl).flush(null, { status, statusText: 'Error', headers });
    fixture.detectChanges();
  }

  function alertText(): string | undefined {
    return (fixture.nativeElement as HTMLElement)
      .querySelector('[role="alert"]')
      ?.textContent?.trim();
  }

  // Le 401 reste dans l'écran : pas de toast
  it('shows the inline alert and no toast on a 401', () => {
    failLogin(401);

    expect(alertText()).toBe('Email ou mot de passe incorrect');
    expect(show).not.toHaveBeenCalled();
  });

  // Prouve que le composant pose bien le jeton HANDLED_BY_SCREEN
  it('shows the inline countdown and no toast on a 429', () => {
    failLogin(429, { 'Retry-After': '30' });

    expect(alertText()).toBe('Trop de tentatives, réessayez dans 30 secondes');
    expect(show).not.toHaveBeenCalled();
  });

  // Garde-fou : un 500 reste un toast global, sans alerte en ligne
  it('shows the server error toast and no inline alert on a 500', () => {
    failLogin(500);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Erreur serveur interne');
    expect(alertText()).toBeUndefined();
  });
});
