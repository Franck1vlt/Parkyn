import { HttpRequest, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../../environments/environment';
import { ErrorInterceptor } from '../../../../core/interceptors/error.interceptor';
import { AuthService } from '../../../../core/services/auth.service';
import { ToastService } from '../../../../core/services/toast.service';
import { UsersComponent } from './users.component';

describe('UsersComponent with the real ErrorInterceptor', () => {
  const usersUrl = `${environment.apiUrl}/users`;
  const notificationsUrl = `${environment.apiUrl}/notifications`;
  let fixture: ComponentFixture<UsersComponent>;
  let httpMock: HttpTestingController;
  let show: ReturnType<typeof vi.spyOn>;

  const isList = (r: HttpRequest<unknown>) => r.method === 'GET' && r.url === usersUrl;

  beforeEach(async () => {
    // Timers simulés : le header relance ses notifications toutes les 2 minutes
    vi.useFakeTimers();
    vi.spyOn(console, 'error').mockImplementation(() => {});
    vi.spyOn(AuthService.prototype, 'hasRole').mockReturnValue(true);
    await TestBed.configureTestingModule({
      imports: [UsersComponent],
      providers: [
        provideHttpClient(withInterceptors([ErrorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    show = vi.spyOn(TestBed.inject(ToastService), 'show');
    fixture = TestBed.createComponent(UsersComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.match((r) => r.url === notificationsUrl).forEach((r) => r.flush([]));
    httpMock.verify();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  // Chaque test répond à la requête initiale de la liste : aucune requête ne reste ouverte
  function failListWith(status: number) {
    httpMock.expectOne(isList).flush(null, { status, statusText: 'Status' });
    fixture.detectChanges();
  }

  // Le texte de l'action remplace le toast générique, sans doublon
  it('shows exactly one toast with the action text on a 403 list load, not the generic one', () => {
    failListWith(403);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show.mock.calls[0][0]).toBe("Vous n'avez pas accès à la liste des utilisateurs.");
    expect(show.mock.calls[0][0]).not.toBe('Accès refusé');
  });

  // Le 404 n'est pas géré par l'écran : seul l'intercepteur affiche un toast
  it('shows only the interceptor toast on a 404 list load', () => {
    failListWith(404);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Ressource introuvable');
  });

  // Le 500 n'est pas géré par l'écran : seul l'intercepteur affiche un toast
  it('shows only the interceptor toast on a 500 list load', () => {
    failListWith(500);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Erreur serveur interne');
  });
});
