import { HttpRequest, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../../environments/environment';
import { ErrorInterceptor } from '../../../../core/interceptors/error.interceptor';
import { ToastService } from '../../../../core/services/toast.service';
import { VehiclesComponent } from './vehicles.component';

describe('VehiclesComponent with the real ErrorInterceptor', () => {
  const vehiclesUrl = `${environment.apiUrl}/vehicles`;
  const notificationsUrl = `${environment.apiUrl}/notifications`;
  let fixture: ComponentFixture<VehiclesComponent>;
  let httpMock: HttpTestingController;
  let show: ReturnType<typeof vi.spyOn>;

  const isList = (r: HttpRequest<unknown>) => r.method === 'GET' && r.url === vehiclesUrl;
  const isDelete = (r: HttpRequest<unknown>) =>
    r.method === 'DELETE' && r.url.startsWith(`${vehiclesUrl}/`);

  beforeEach(async () => {
    // Timers simulés : le header relance ses notifications toutes les 2 minutes
    vi.useFakeTimers();
    vi.spyOn(console, 'error').mockImplementation(() => {});
    await TestBed.configureTestingModule({
      imports: [VehiclesComponent],
      providers: [
        provideHttpClient(withInterceptors([ErrorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    show = vi.spyOn(TestBed.inject(ToastService), 'show');
    fixture = TestBed.createComponent(VehiclesComponent);
    fixture.detectChanges();

    // Page chargée avec un véhicule, prête à être supprimé
    httpMock.expectOne(isList).flush(
      {
        content: [
          {
            id: '1',
            plateNumber: 'AA-001-BB',
            brand: 'Renault',
            model: 'Clio',
            year: 2022,
            mileageKM: 1000,
            condition: 'GOOD',
            status: 'AVAILABLE',
            commissionedAt: '2022-01-01',
            createdAt: '2022-01-01T00:00:00Z',
          },
        ],
        page: { number: 0, size: 10, totalElements: 1, totalPages: 1 },
      },
      { status: 200, statusText: 'OK' },
    );
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.match((r) => r.url === notificationsUrl).forEach((r) => r.flush([]));
    httpMock.verify();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  function deleteWith(status: number) {
    const button = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      'app-vehicle-card button',
    );
    expect(button).not.toBeNull();
    button!.click();
    httpMock.expectOne(isDelete).flush(null, { status, statusText: 'Status' });
    fixture.detectChanges();
  }

  // Cas 16 : le texte de l'action remplace le toast générique, sans doublon
  it('shows exactly one toast with the action text on a 404 delete, not the generic one', () => {
    deleteWith(404);
    // Le rechargement n'est pas l'objet de ce test : on referme sa requête
    httpMock.match(isList);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show.mock.calls[0][0]).toBe("Ce véhicule n'existe plus.");
    expect(show.mock.calls[0][0]).not.toBe('Ressource introuvable');
  });

  // Recharge la liste par le filtre et la fait échouer avec ce statut
  function failListWith(status: number) {
    const select = (fixture.nativeElement as HTMLElement).querySelector<HTMLSelectElement>(
      'select',
    )!;
    select.value = 'RESERVED';
    select.dispatchEvent(new Event('change'));
    httpMock.expectOne(isList).flush(null, { status, statusText: 'Status' });
    fixture.detectChanges();
  }

  // Garde, vert d'emblée : statut non déclaré, un seul toast, celui de l'intercepteur
  it('shows only the interceptor toast on a 400 delete', () => {
    deleteWith(400);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Une erreur est survenue');
  });

  // Garde, vert d'emblée : le 404 de chargement n'est pas déclaré
  it('shows only the interceptor toast on a 404 list load', () => {
    failListWith(404);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Ressource introuvable');
  });

  // Garde, vert d'emblée : le 500 reste au seul intercepteur
  it('shows only the interceptor toast on a 500 list load', () => {
    failListWith(500);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Erreur serveur interne');
  });

  // Cas 17 : garde, vert d'emblée ; le 500 reste au seul intercepteur
  it('keeps a single interceptor toast and does not reload on a 500 delete', () => {
    deleteWith(500);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Erreur serveur interne');
    httpMock.expectNone(isList);
  });
});
