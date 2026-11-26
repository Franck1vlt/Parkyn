import { HttpRequest } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { provideTestApp } from '../../../testing/test-providers';
import { HANDLED_STATUSES } from '../../core/interceptors/error-handling';
import { Page } from '../../core/models/page.model';
import { AuthService, CurrentUser, Role } from '../../core/services/auth.service';
import { DashboardResponse } from '../../core/services/dashboard.service';
import { ToastService } from '../../core/services/toast.service';
import { VehicleResponse } from '../../core/services/vehicles.service';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [provideTestApp()],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
  });

  // Test de fumée : l'injection de dépendances et le gabarit se résolvent sans erreur
  it('renders without error', () => {
    expect(() => fixture.detectChanges()).not.toThrow();
    expect(fixture.nativeElement.childElementCount).toBeGreaterThan(0);
  });
});

describe('DashboardComponent error display', () => {
  const dashboardUrl = `${environment.apiUrl}/dashboard`;
  const vehiclesUrl = `${environment.apiUrl}/vehicles`;
  const notificationsUrl = `${environment.apiUrl}/notifications`;
  const LIST_ERROR_TEXT = 'Impossible de charger la liste. Rechargez la page.';
  const STATS_ERROR_TEXT = 'Impossible de charger les statistiques. Rechargez la page.';
  const FORBIDDEN_TEXT = "Vous n'avez pas accès au tableau de bord.";
  const EMPTY_TEXTS = ['Aucune réservation', 'Aucun véhicule', 'Aucun conducteur', 'Aucune donnée'];
  const RESERVATION_HEADERS = ['#', 'Véhicule', 'Conducteur', 'Début', 'Fin'];
  const AGENT_HEADERS = ['Agent', 'Réservations'];

  let fixture: ComponentFixture<DashboardComponent>;
  let httpMock: HttpTestingController;
  let show: ReturnType<typeof vi.spyOn>;
  let consoleError: ReturnType<typeof vi.spyOn>;
  let consoleLog: ReturnType<typeof vi.spyOn>;

  const isDashboard = (r: HttpRequest<unknown>) => r.method === 'GET' && r.url === dashboardUrl;
  const isVehicles = (r: HttpRequest<unknown>) => r.method === 'GET' && r.url === vehiclesUrl;

  beforeEach(async () => {
    // Timers simulés : le header relance ses notifications toutes les 2 minutes
    vi.useFakeTimers();
    consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
    consoleLog = vi.spyOn(console, 'log').mockImplementation(() => {});
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [provideTestApp()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    show = vi.spyOn(TestBed.inject(ToastService), 'show');
  });

  afterEach(() => {
    // Le header demande ses notifications et l'écran la liste des véhicules : on les referme
    httpMock
      .match((r: HttpRequest<unknown>) => r.url === notificationsUrl)
      .forEach((r) => r.flush([]));
    httpMock.match(isVehicles).forEach((r) => r.flush(vehiclesPage([])));
    httpMock.verify();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  // Le rôle vient de AuthService.currentUser (lu une seule fois) : à poser avant start()
  function asRole(role: Role) {
    const user: CurrentUser = {
      id: 'u1',
      firstname: 'Ada',
      lastname: 'Lovelace',
      email: 'ada@example.com',
      role,
    };
    vi.spyOn(AuthService.prototype, 'currentUser', 'get').mockReturnValue(user);
  }

  function start() {
    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
  }

  function host(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function vehicle(id = 'v1'): VehicleResponse {
    return {
      id,
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

  function vehiclesPage(content: VehicleResponse[]): Page<VehicleResponse> {
    return {
      content,
      page: { number: 0, size: 10, totalElements: content.length, totalPages: 1 },
    };
  }

  function dashboardData(admin: boolean): DashboardResponse {
    return {
      latestReservations: [
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
      vehiclesInMaintenance: [
        { ...vehicle('v2'), brand: 'Renault', model: 'Clio', plateNumber: 'EF-456-GH' },
      ],
      latestDrivers: [
        {
          id: 'd2',
          firstname: 'Alan',
          lastname: 'Turing',
          email: 'alan@example.com',
          role: 'DRIVER',
          active: true,
          createdAt: '2026-01-01T00:00:00Z',
        },
      ],
      reservationsByAgent: admin ? [{ agentName: 'Ada Lovelace', count: 7 }] : null,
      myStats: admin
        ? null
        : {
            totalReservations: 12,
            activeReservations: 3,
            thisMonthReservations: 5,
            lastMonthReservations: 4,
          },
    };
  }

  function answerDashboard(body: object | null, status = 200) {
    httpMock.expectOne(isDashboard).flush(body, { status, statusText: 'Status' });
    fixture.detectChanges();
  }

  function answerVehicles(body: object | null, status = 200) {
    httpMock.expectOne(isVehicles).flush(body, { status, statusText: 'Status' });
    fixture.detectChanges();
  }

  // Cadre d'une zone : h2 puis son conteneur d'en-tête puis la carte
  function zone(title: string): HTMLElement | null {
    const h2 = Array.from(host().querySelectorAll('h2')).find(
      (h: HTMLElement) => h.textContent?.trim() === title,
    );
    return h2?.parentElement?.parentElement ?? null;
  }

  function errorOf(testId: string): HTMLElement | null {
    return host().querySelector<HTMLElement>(`[data-testid="${testId}"]`);
  }

  function anyZoneError(): HTMLElement | null {
    return host().querySelector<HTMLElement>('[data-testid$="-error"]');
  }

  function headerTexts(card: HTMLElement): string[] {
    return Array.from(card.querySelectorAll('thead th')).map((th: Element) =>
      th.textContent!.trim(),
    );
  }

  function expectAfterTable(card: HTMLElement, error: HTMLElement) {
    const table = card.querySelector('table')!;
    expect(table.contains(error)).toBe(false);
    expect(table.compareDocumentPosition(error) & Node.DOCUMENT_POSITION_FOLLOWING).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    );
  }

  it('shows the four common zones and the agents table for an admin after a successful load', () => {
    asRole('ADMIN');
    start();
    answerDashboard(dashboardData(true));
    answerVehicles(vehiclesPage([vehicle()]));

    const reservations = zone('Dernières réservations')!;
    expect(headerTexts(reservations)).toEqual(RESERVATION_HEADERS);
    const rows = reservations.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(1);
    expect(rows[0].textContent).toContain('Hopper');
    expect(rows[0].querySelectorAll('td')[1].textContent?.trim()).toBe('Peugeot 208 (AB-123-CD)');
    expect(zone('En maintenance')!.textContent).toContain('Renault Clio');
    expect(zone('En maintenance')!.textContent).toContain('EF-456-GH');
    expect(zone('Derniers conducteurs')!.textContent).toContain('Alan Turing');
    expect(zone('Derniers conducteurs')!.textContent).toContain('alan@example.com');
    const agents = zone('Réservations par agent')!;
    expect(headerTexts(agents)).toEqual(AGENT_HEADERS);
    expect(agents.querySelectorAll('tbody tr')).toHaveLength(1);
    expect(agents.textContent).toContain('Ada Lovelace');
    expect(agents.textContent).toContain('7');
    expect(zone('Mes statistiques')).toBeNull();
    expect(anyZoneError()).toBeNull();
  });

  it('shows the four common zones and the stats cards for a non-admin after a successful load', () => {
    asRole('AGENT');
    start();
    answerDashboard(dashboardData(false));
    answerVehicles(vehiclesPage([vehicle()]));

    const reservations = zone('Dernières réservations')!;
    const rows = reservations.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(1);
    expect(rows[0].textContent).toContain('Hopper');
    expect(rows[0].querySelectorAll('td')[1].textContent?.trim()).toBe('Peugeot 208 (AB-123-CD)');
    expect(zone('En maintenance')!.textContent).toContain('Renault Clio');
    expect(zone('Derniers conducteurs')!.textContent).toContain('Alan Turing');
    const stats = zone('Mes statistiques')!;
    expect(stats.textContent).toContain('Total réservations');
    expect(stats.textContent).toContain('12');
    expect(stats.textContent).toContain('vs 4 le mois dernier');
    expect(zone('Réservations par agent')).toBeNull();
    expect(anyZoneError()).toBeNull();
  });

  // Un seul toast avec le texte de l'action, et le 403 est déclaré comme géré à l'intercepteur
  it('toasts the load-dashboard message once on a 403 and declares 403 as handled', () => {
    asRole('ADMIN');
    start();
    const request = httpMock.expectOne(isDashboard);
    expect(request.request.context.get(HANDLED_STATUSES)).toContain(403);
    request.flush({ message: 'forbidden' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(show).toHaveBeenCalledTimes(1);
    expect(show.mock.calls[0][0]).toBe(FORBIDDEN_TEXT);
  });

  // Le toast des autres statuts reste à la charge de l'intercepteur
  it('does not toast anything itself when the load fails with a 500', () => {
    asRole('ADMIN');
    start();
    answerDashboard({ message: 'SQL boom' }, 500);

    expect(show).not.toHaveBeenCalled();
  });

  // Chaque zone affiche son message d'erreur en gardant titre, cadre et en-têtes, sans faux état vide
  it.each([403, 500])(
    'shows one error message in each visible zone of an admin and no empty-state text when the load fails with %i',
    (status: number) => {
      asRole('ADMIN');
      start();
      answerDashboard({ message: 'SQL boom' }, status);

      const expected: [string, string][] = [
        ['Dernières réservations', 'dashboard-reservations-error'],
        ['En maintenance', 'dashboard-maintenance-error'],
        ['Derniers conducteurs', 'dashboard-drivers-error'],
        ['Réservations par agent', 'dashboard-agents-error'],
      ];
      for (const [title, testId] of expected) {
        const card = zone(title);
        expect(card, `zone ${title}`).not.toBeNull();
        const error = errorOf(testId);
        expect(error, testId).not.toBeNull();
        expect(error!.textContent?.trim()).toBe(LIST_ERROR_TEXT);
        expect(card!.contains(error)).toBe(true);
      }

      const reservations = zone('Dernières réservations')!;
      expect(headerTexts(reservations)).toEqual(RESERVATION_HEADERS);
      expect(reservations.querySelectorAll('tbody tr')).toHaveLength(0);
      expectAfterTable(reservations, errorOf('dashboard-reservations-error')!);

      const agents = zone('Réservations par agent')!;
      expect(headerTexts(agents)).toEqual(AGENT_HEADERS);
      expect(agents.querySelectorAll('tbody tr')).toHaveLength(0);
      expectAfterTable(agents, errorOf('dashboard-agents-error')!);

      for (const text of EMPTY_TEXTS) {
        expect(host().textContent).not.toContain(text);
      }
    },
  );

  // Un ADMIN voit la zone agents, jamais la zone statistiques
  it('shows the agents error and no stats error for an admin when the load fails', () => {
    asRole('ADMIN');
    start();
    answerDashboard({ message: 'SQL boom' }, 500);

    expect(errorOf('dashboard-agents-error')).not.toBeNull();
    expect(errorOf('dashboard-stats-error')).toBeNull();
    expect(zone('Mes statistiques')).toBeNull();
  });

  // Un non-ADMIN voit la zone statistiques, sans cartes vides ni zone agents
  it('shows the stats error and no agents error for a non-admin when the load fails', () => {
    asRole('AGENT');
    start();
    answerDashboard({ message: 'SQL boom' }, 500);

    const stats = zone('Mes statistiques');
    expect(stats).not.toBeNull();
    const error = errorOf('dashboard-stats-error');
    expect(error).not.toBeNull();
    expect(error!.textContent?.trim()).toBe(STATS_ERROR_TEXT);
    expect(stats!.contains(error)).toBe(true);
    expect(stats!.textContent).not.toContain('Total réservations');
    expect(stats!.textContent).not.toContain('En cours');
    expect(stats!.textContent).not.toMatch(/\d/);
    expect(errorOf('dashboard-agents-error')).toBeNull();
    expect(zone('Réservations par agent')).toBeNull();
    expect(errorOf('dashboard-reservations-error')).not.toBeNull();
  });

  // L'échec est affiché par l'écran, jamais journalisé dans la console
  it.each<[string, number]>([
    ['fails with 500', 500],
    ['fails with 403', 403],
    ['succeeds', 200],
  ])(
    'never calls console.error nor console.log when the load %s',
    (_label: string, status: number) => {
      asRole('ADMIN');
      start();
      answerDashboard(status === 200 ? dashboardData(true) : { message: 'boom' }, status);

      expect(consoleError).not.toHaveBeenCalled();
      expect(consoleLog).not.toHaveBeenCalled();
    },
  );

  // L'échec de GET /vehicles ne casse pas le dashboard et ne lève pas d'erreur RxJS non gérée
  it('keeps the dashboard and shows a dash for the vehicle when the vehicles call fails with a 500', () => {
    asRole('AGENT');
    start();
    answerDashboard(dashboardData(false));
    answerVehicles({ message: 'SQL boom' }, 500);

    const rows = zone('Dernières réservations')!.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(1);
    expect(rows[0].querySelectorAll('td')[1].textContent?.trim()).toBe('—');
    expect(zone('En maintenance')!.textContent).toContain('Renault Clio');
    expect(anyZoneError()).toBeNull();
    // RxJS relance une erreur non gérée dans un setTimeout(0) : on avance les timers simulés pour la déclencher
    expect(() => vi.advanceTimersByTime(0)).not.toThrow();
  });
});
