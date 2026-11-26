import { HttpRequest, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { ErrorInterceptor } from '../../core/interceptors/error.interceptor';
import { Page } from '../../core/models/page.model';
import { DashboardResponse } from '../../core/services/dashboard.service';
import { ToastService } from '../../core/services/toast.service';
import { VehicleResponse } from '../../core/services/vehicles.service';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent with the real ErrorInterceptor', () => {
  const dashboardUrl = `${environment.apiUrl}/dashboard`;
  const vehiclesUrl = `${environment.apiUrl}/vehicles`;
  const notificationsUrl = `${environment.apiUrl}/notifications`;
  const FORBIDDEN_TEXT = "Vous n'avez pas accès au tableau de bord.";
  let fixture: ComponentFixture<DashboardComponent>;
  let httpMock: HttpTestingController;
  let show: ReturnType<typeof vi.spyOn>;

  const isDashboard = (r: HttpRequest<unknown>) => r.method === 'GET' && r.url === dashboardUrl;
  const isVehicles = (r: HttpRequest<unknown>) => r.method === 'GET' && r.url === vehiclesUrl;

  const emptyVehicles: Page<VehicleResponse> = {
    content: [],
    page: { number: 0, size: 10, totalElements: 0, totalPages: 1 },
  };

  const dashboardBody: DashboardResponse = {
    latestReservations: [],
    vehiclesInMaintenance: [],
    latestDrivers: [],
    reservationsByAgent: null,
    myStats: {
      totalReservations: 12,
      activeReservations: 3,
      thisMonthReservations: 5,
      lastMonthReservations: 4,
    },
  };

  beforeEach(async () => {
    // Timers simulés : le header relance ses notifications toutes les 2 minutes
    vi.useFakeTimers();
    vi.spyOn(console, 'error').mockImplementation(() => {});
    vi.spyOn(console, 'log').mockImplementation(() => {});
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideHttpClient(withInterceptors([ErrorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    show = vi.spyOn(TestBed.inject(ToastService), 'show');
    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    // Les requêtes du header et des véhicules restées ouvertes sont refermées explicitement
    httpMock
      .match((r: HttpRequest<unknown>) => r.url === notificationsUrl)
      .forEach((r) => r.flush([]));
    httpMock.match(isVehicles).forEach((r) => r.flush(emptyVehicles));
    httpMock.verify();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  function failDashboardWith(status: number) {
    httpMock.expectOne(isDashboard).flush(null, { status, statusText: 'Status' });
    fixture.detectChanges();
  }

  // Le texte de l'action remplace le toast générique, sans doublon
  it('shows exactly one toast with the action text on a 403 dashboard load, not the generic one', () => {
    failDashboardWith(403);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show.mock.calls[0][0]).toBe(FORBIDDEN_TEXT);
    expect(show.mock.calls[0][0]).not.toBe('Accès refusé');
  });

  // Le 404 n'est pas géré par l'écran : seul l'intercepteur affiche un toast
  it('shows only the interceptor toast on a 404 dashboard load', () => {
    failDashboardWith(404);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Ressource introuvable');
  });

  // Le 500 n'est pas géré par l'écran : seul l'intercepteur affiche un toast
  it('shows only the interceptor toast on a 500 dashboard load', () => {
    failDashboardWith(500);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Erreur serveur interne');
  });

  // Un 403 sur l'appel secondaire des véhicules garde le toast de l'intercepteur
  it('shows only the interceptor toast when the vehicles call gets a 403 after a successful dashboard', () => {
    httpMock.expectOne(isDashboard).flush(dashboardBody);
    httpMock
      .expectOne(isVehicles)
      .flush({ message: 'forbidden' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Accès refusé');
    expect(show.mock.calls[0][0]).not.toBe(FORBIDDEN_TEXT);
  });
});
