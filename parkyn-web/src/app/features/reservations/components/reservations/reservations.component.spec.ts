import { HttpRequest } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../../../environments/environment';
import { provideTestApp } from '../../../../../testing/test-providers';
import { HANDLED_STATUSES } from '../../../../core/interceptors/error-handling';
import { Page } from '../../../../core/models/page.model';
import { ReservationResponse } from '../../../../core/services/reservations.service';
import { ToastService } from '../../../../core/services/toast.service';
import { VehicleResponse } from '../../../../core/services/vehicles.service';
import { ReservationsComponent } from './reservations.component';

describe('ReservationsComponent', () => {
  let fixture: ComponentFixture<ReservationsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReservationsComponent],
      providers: [provideTestApp()],
    }).compileComponents();

    fixture = TestBed.createComponent(ReservationsComponent);
  });

  // Test de fumée : l'injection de dépendances et le gabarit se résolvent sans erreur
  it('renders without error', () => {
    expect(() => fixture.detectChanges()).not.toThrow();
    expect(fixture.nativeElement.childElementCount).toBeGreaterThan(0);
  });
});

describe('ReservationsComponent error display', () => {
  const reservationsUrl = `${environment.apiUrl}/reservations`;
  const vehiclesUrl = `${environment.apiUrl}/vehicles`;
  const notificationsUrl = `${environment.apiUrl}/notifications`;
  const LOAD_ERROR_TEXT = 'Impossible de charger la liste. Rechargez la page.';
  const LOAD_FORBIDDEN_TEXT = "Vous n'avez pas accès à la liste des réservations.";
  const HEADERS = ['#', 'Véhicule', 'Agent', 'Conducteur', 'Début', 'Fin', 'Statut', 'Actions'];

  let fixture: ComponentFixture<ReservationsComponent>;
  let httpMock: HttpTestingController;
  let show: ReturnType<typeof vi.spyOn>;
  let consoleError: ReturnType<typeof vi.spyOn>;
  let consoleLog: ReturnType<typeof vi.spyOn>;

  const isList = (r: HttpRequest<unknown>) => r.method === 'GET' && r.url === reservationsUrl;
  const isVehicles = (r: HttpRequest<unknown>) => r.method === 'GET' && r.url === vehiclesUrl;

  beforeEach(async () => {
    // Timers simulés : le header relance ses notifications toutes les 2 minutes
    vi.useFakeTimers();
    consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
    consoleLog = vi.spyOn(console, 'log').mockImplementation(() => {});
    await TestBed.configureTestingModule({
      imports: [ReservationsComponent],
      providers: [provideTestApp()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    show = vi.spyOn(TestBed.inject(ToastService), 'show');
  });

  afterEach(() => {
    // Le header demande ses notifications et l'écran la liste des véhicules : on les referme
    httpMock.match((r) => r.url === notificationsUrl).forEach((r) => r.flush([]));
    httpMock.match(isVehicles).forEach((r) => r.flush(vehiclesPage([])));
    httpMock.verify();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  function start() {
    fixture = TestBed.createComponent(ReservationsComponent);
    fixture.detectChanges();
  }

  function host(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function reservation(id: string): ReservationResponse {
    return {
      id,
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
    };
  }

  function pageOf(totalElements: number, totalPages: number): Page<ReservationResponse> {
    return {
      content: [reservation('r1')],
      page: { number: 0, size: 10, totalElements, totalPages },
    };
  }

  function vehiclesPage(content: VehicleResponse[]): Page<VehicleResponse> {
    return {
      content,
      page: { number: 0, size: 10, totalElements: content.length, totalPages: 1 },
    };
  }

  function vehicle(): VehicleResponse {
    return {
      id: 'v1',
      plateNumber: 'AB-123-CD',
      brand: 'Peugeot',
      model: '208',
      year: 2022,
      mileageKM: 1000,
      condition: 'GOOD',
      status: 'AVAILABLE',
      commissionedAt: '2022-01-01',
      createdAt: '2022-01-01T00:00:00Z',
    };
  }

  function answerList(body: object | null, status = 200) {
    httpMock.expectOne(isList).flush(body, { status, statusText: 'Status' });
    fixture.detectChanges();
  }

  function answerVehicles(body: object | null, status = 200) {
    httpMock.expectOne(isVehicles).flush(body, { status, statusText: 'Status' });
    fixture.detectChanges();
  }

  function buttonByText(text: string): HTMLButtonElement | undefined {
    return Array.from(host().querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === text,
    );
  }

  function loadError(): HTMLElement | null {
    return host().querySelector<HTMLElement>('[data-testid="reservations-load-error"]');
  }

  function headerTexts(): string[] {
    return Array.from(host().querySelectorAll('thead th')).map((th) => th.textContent!.trim());
  }

  function dataRows(): NodeListOf<Element> {
    return host().querySelectorAll('tbody tr');
  }

  function changeFilter(value: string) {
    const select = host().querySelector<HTMLSelectElement>('select')!;
    select.value = value;
    select.dispatchEvent(new Event('change'));
  }

  it('shows the rows, the result count and the vehicle names after a successful load', () => {
    start();
    answerList(pageOf(3, 1));
    answerVehicles(vehiclesPage([vehicle()]));

    expect(headerTexts()).toEqual(HEADERS);
    expect(dataRows()).toHaveLength(1);
    expect(dataRows()[0].textContent).toContain('Hopper');
    expect(dataRows()[0].querySelectorAll('td')[1].textContent?.trim()).toBe(
      'Peugeot 208 (AB-123-CD)',
    );
    expect(host().textContent).toContain('3 résultats');
    expect(loadError()).toBeNull();
  });

  // Les en-têtes restent, le message d'erreur est sous le tableau, sans lignes, compteur ni pagination
  it.each([500, 403])(
    'keeps the table headers and shows the load error below them when the load fails with %i',
    (status: number) => {
      start();
      expect(fixture.componentInstance.isLoading()).toBe(true);

      answerList({ message: 'SQL boom' }, status);

      expect(headerTexts()).toEqual(HEADERS);
      expect(dataRows()).toHaveLength(0);
      expect(loadError()).not.toBeNull();
      expect(loadError()!.textContent?.trim()).toBe(LOAD_ERROR_TEXT);
      expect(loadError()!.querySelector('button')).toBeNull();
      const table = host().querySelector('table')!;
      expect(table.contains(loadError())).toBe(false);
      expect(table.compareDocumentPosition(loadError()!) & Node.DOCUMENT_POSITION_FOLLOWING).toBe(
        Node.DOCUMENT_POSITION_FOLLOWING,
      );
      expect(host().textContent).not.toMatch(/résultats?/);
      expect(buttonByText('Previous')).toBeUndefined();
      expect(buttonByText('Next')).toBeUndefined();
      expect(fixture.componentInstance.isLoading()).toBe(false);
    },
  );

  // Un seul toast avec le texte de l'action, et le 403 est déclaré comme géré à l'intercepteur
  it('toasts the load-reservations message once on a 403 and declares 403 as handled', () => {
    start();
    const request = httpMock.expectOne(isList);
    expect(request.request.context.get(HANDLED_STATUSES)).toContain(403);
    request.flush({ message: 'forbidden' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(show).toHaveBeenCalledTimes(1);
    expect(show.mock.calls[0][0]).toBe(LOAD_FORBIDDEN_TEXT);
  });

  // Le toast des autres statuts reste à la charge de l'intercepteur
  it('does not toast anything itself when the load fails with a 500', () => {
    start();
    answerList({ message: 'SQL boom' }, 500);

    expect(show).not.toHaveBeenCalled();
  });

  // Un échec au changement de page ou de filtre se comporte comme un échec du chargement initial
  it.each<[string, () => void]>([
    ['page change', () => buttonByText('Next')!.click()],
    ['filter change', () => changeFilter('ACTIVE')],
  ])(
    'clears the loaded page, resets the page index and shows the load error when a %s fails',
    (_label: string, trigger: () => void) => {
      start();
      answerList(pageOf(25, 3));
      expect(dataRows()).toHaveLength(1);
      expect(host().textContent).toContain('25 résultats');

      trigger();
      answerList(null, 500);

      expect(dataRows()).toHaveLength(0);
      expect(headerTexts()).toEqual(HEADERS);
      expect(host().textContent).not.toMatch(/résultats?/);
      expect(loadError()?.textContent?.trim()).toBe(LOAD_ERROR_TEXT);
      expect(buttonByText('Previous')).toBeUndefined();
      expect(buttonByText('Next')).toBeUndefined();
      expect(fixture.componentInstance.currentPage()).toBe(0);
      expect(fixture.componentInstance.isLoading()).toBe(false);
    },
  );

  // Un chargement réussi après un échec réaffiche la liste
  it('hides the load error and shows the list again when a filter change succeeds after a failure', () => {
    start();
    answerList(null, 500);

    changeFilter('RETURNED');
    answerList(pageOf(3, 1));

    expect(loadError()).toBeNull();
    expect(dataRows()).toHaveLength(1);
    expect(host().textContent).toContain('3 résultats');
  });

  // L'échec est affiché par l'écran, jamais journalisé dans la console
  it.each<[string, number]>([
    ['fails with 500', 500],
    ['fails with 403', 403],
    ['succeeds', 200],
  ])(
    'never calls console.error nor console.log when the load %s',
    (_label: string, status: number) => {
      start();
      answerList(status === 200 ? pageOf(3, 1) : { message: 'boom' }, status);

      expect(consoleError).not.toHaveBeenCalled();
      expect(consoleLog).not.toHaveBeenCalled();
    },
  );

  // L'échec de GET /vehicles ne casse pas la liste et ne lève pas d'erreur RxJS non gérée
  it('keeps the list and shows a dash for the vehicle when the vehicles call fails with a 500', () => {
    start();
    answerList(pageOf(1, 1));
    answerVehicles({ message: 'SQL boom' }, 500);

    expect(dataRows()).toHaveLength(1);
    expect(dataRows()[0].querySelectorAll('td')[1].textContent?.trim()).toBe('—');
    expect(loadError()).toBeNull();
    expect(fixture.componentInstance.isLoading()).toBe(false);
    // RxJS relance une erreur non gérée dans un setTimeout(0) : on avance les timers simulés pour la déclencher
    expect(() => vi.advanceTimersByTime(0)).not.toThrow();
  });
});
