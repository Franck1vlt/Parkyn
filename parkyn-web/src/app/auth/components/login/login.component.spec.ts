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
});
