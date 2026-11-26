import { HttpRequest, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../../environments/environment';
import { ErrorInterceptor } from '../../../../core/interceptors/error.interceptor';
import { Page } from '../../../../core/models/page.model';
import { ReservationResponse } from '../../../../core/services/reservations.service';
import { ToastService } from '../../../../core/services/toast.service';
import { VehicleResponse } from '../../../../core/services/vehicles.service';
import { ReservationsComponent } from './reservations.component';

describe('ReservationsComponent with the real ErrorInterceptor', () => {
  const reservationsUrl = `${environment.apiUrl}/reservations`;
  const vehiclesUrl = `${environment.apiUrl}/vehicles`;
  const notificationsUrl = `${environment.apiUrl}/notifications`;
  const LOAD_FORBIDDEN_TEXT = "Vous n'avez pas accès à la liste des réservations.";
  let fixture: ComponentFixture<ReservationsComponent>;
  let httpMock: HttpTestingController;
  let show: ReturnType<typeof vi.spyOn>;

  const isList = (r: HttpRequest<unknown>) => r.method === 'GET' && r.url === reservationsUrl;
  const isVehicles = (r: HttpRequest<unknown>) => r.method === 'GET' && r.url === vehiclesUrl;

  const emptyVehicles: Page<VehicleResponse> = {
    content: [],
    page: { number: 0, size: 10, totalElements: 0, totalPages: 1 },
  };

  const reservationsPage: Page<ReservationResponse> = {
    content: [
      {
        id: 'r1',
        vehicleId: 'v1',
        driverId: 'd1',
        driverFirstName: 'Grace',
        driverLastName: 'Hopper',
        agentId: 'a1',
        agentFirstName: 'Ada',
        agentLastName: 'Lovelace',
        startDate: '2026-01-10T00:00:00Z',
        endDate: '2026-01-12T00:00:00Z',
        returnedAt: null,
        conditionBefore: 'GOOD',
        mileageStart: 1000,
        notesBefore: null,
        conditionAfter: null,
        mileageEnd: null,
        damageReported: null,
        damageDetails: null,
        notesAfter: null,
      },
    ],
    page: { number: 0, size: 10, totalElements: 1, totalPages: 1 },
  };

  beforeEach(async () => {
    // Timers simulés : le header relance ses notifications toutes les 2 minutes
    vi.useFakeTimers();
    vi.spyOn(console, 'error').mockImplementation(() => {});
    vi.spyOn(console, 'log').mockImplementation(() => {});
    await TestBed.configureTestingModule({
      imports: [ReservationsComponent],
      providers: [
        provideHttpClient(withInterceptors([ErrorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    show = vi.spyOn(TestBed.inject(ToastService), 'show');
    fixture = TestBed.createComponent(ReservationsComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    // Les requêtes du header et des véhicules restées ouvertes sont refermées explicitement
    httpMock.match((r) => r.url === notificationsUrl).forEach((r) => r.flush([]));
    httpMock.match(isVehicles).forEach((r) => r.flush(emptyVehicles));
    httpMock.verify();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  function failListWith(status: number) {
    httpMock.expectOne(isList).flush(null, { status, statusText: 'Status' });
    fixture.detectChanges();
  }

  // Le texte de l'action remplace le toast générique, sans doublon
  it('shows exactly one toast with the action text on a 403 list load, not the generic one', () => {
    failListWith(403);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show.mock.calls[0][0]).toBe(LOAD_FORBIDDEN_TEXT);
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

  // Un 403 sur l'appel secondaire des véhicules garde le toast de l'intercepteur
  it('shows only the interceptor toast when the vehicles call gets a 403 after a successful list', () => {
    httpMock.expectOne(isList).flush(reservationsPage);
    httpMock
      .expectOne(isVehicles)
      .flush({ message: 'forbidden' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Accès refusé');
    expect(show.mock.calls[0][0]).not.toBe(LOAD_FORBIDDEN_TEXT);
  });
});
