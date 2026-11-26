import { HttpRequest } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../../../environments/environment';
import { provideTestApp } from '../../../../../testing/test-providers';
import { HANDLED_STATUSES } from '../../../../core/interceptors/error-handling';
import { Page } from '../../../../core/models/page.model';
import { AuthService } from '../../../../core/services/auth.service';
import { ToastService } from '../../../../core/services/toast.service';
import { UserResponse } from '../../../../core/services/users.service';
import { UsersComponent } from './users.component';

describe('UsersComponent', () => {
  let fixture: ComponentFixture<UsersComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UsersComponent],
      providers: [provideTestApp()],
    }).compileComponents();

    fixture = TestBed.createComponent(UsersComponent);
  });

  // Test de fumée : l'injection de dépendances et le gabarit se résolvent sans erreur
  it('renders without error', () => {
    expect(() => fixture.detectChanges()).not.toThrow();
    expect(fixture.nativeElement.childElementCount).toBeGreaterThan(0);
  });
});

describe('UsersComponent error display', () => {
  const usersUrl = `${environment.apiUrl}/users`;
  const driversUrl = `${environment.apiUrl}/users/drivers`;
  const notificationsUrl = `${environment.apiUrl}/notifications`;
  const LOAD_ERROR_TEXT = 'Impossible de charger la liste. Rechargez la page.';
  const LOAD_FORBIDDEN_TEXT = "Vous n'avez pas accès à la liste des utilisateurs.";
  const HEADERS = ['#', 'Nom', 'Email', 'Rôle', 'Statut', 'Actions'];

  let fixture: ComponentFixture<UsersComponent>;
  let httpMock: HttpTestingController;
  let show: ReturnType<typeof vi.spyOn>;
  let consoleError: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    // Timers simulés : le header relance ses notifications toutes les 2 minutes
    vi.useFakeTimers();
    consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
    await TestBed.configureTestingModule({
      imports: [UsersComponent],
      providers: [provideTestApp()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    show = vi.spyOn(TestBed.inject(ToastService), 'show');
  });

  afterEach(() => {
    // Le header demande ses notifications : on les referme avant la vérification
    httpMock.match((r) => r.url === notificationsUrl).forEach((r) => r.flush([]));
    httpMock.verify();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  // Le rôle doit être simulé avant le premier détect changes (ngOnInit charge la liste)
  function start(isAdmin = true) {
    vi.spyOn(AuthService.prototype, 'hasRole').mockReturnValue(isAdmin);
    fixture = TestBed.createComponent(UsersComponent);
    fixture.detectChanges();
  }

  function host(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  const isList = (r: HttpRequest<unknown>) => r.method === 'GET' && r.url === usersUrl;
  const isDrivers = (r: HttpRequest<unknown>) => r.method === 'GET' && r.url === driversUrl;

  function user(id: string): UserResponse {
    return {
      id,
      firstname: 'Ada',
      lastname: `Lovelace${id}`,
      email: `ada${id}@example.com`,
      role: 'DRIVER',
      active: true,
      createdAt: '2022-01-01T00:00:00Z',
    };
  }

  function pageOf(totalElements: number, totalPages: number, number = 0): Page<UserResponse> {
    return { content: [user('1')], page: { number, size: 10, totalElements, totalPages } };
  }

  function answerList(body: object | null, status = 200) {
    httpMock.expectOne(isList).flush(body, { status, statusText: 'Status' });
    fixture.detectChanges();
  }

  function answerDrivers(body: object | null, status = 200) {
    httpMock.expectOne(isDrivers).flush(body, { status, statusText: 'Status' });
    fixture.detectChanges();
  }

  function buttonByText(text: string): HTMLButtonElement | undefined {
    return Array.from(host().querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === text,
    );
  }

  function loadError(): HTMLElement | null {
    return host().querySelector<HTMLElement>('[data-testid="users-load-error"]');
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

  it('shows the rows and the result count after a successful load', () => {
    start();
    answerList(pageOf(3, 1));

    expect(headerTexts()).toEqual(HEADERS);
    expect(dataRows()).toHaveLength(1);
    expect(dataRows()[0].textContent).toContain('ada1@example.com');
    expect(host().textContent).toContain('3 résultats');
    expect(loadError()).toBeNull();
  });

  // Les en-têtes restent, le message d'erreur est sous le tableau, sans lignes, compteur ni pagination
  it.each([[500], [403]])(
    'keeps the table headers and shows the load error below them when the load fails with %i',
    (status) => {
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
  it('toasts the load-users message once on a 403 and declares 403 as handled', () => {
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
  it.each([
    ['page change', () => buttonByText('Next')!.click()],
    ['filter change', () => changeFilter('AGENT')],
  ])(
    'clears the loaded page, resets the page index and shows the load error when a %s fails',
    (_label, trigger) => {
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

    changeFilter('DRIVER');
    answerList(pageOf(3, 1));

    expect(loadError()).toBeNull();
    expect(dataRows()).toHaveLength(1);
    expect(host().textContent).toContain('3 résultats');
  });

  // L'échec est affiché par l'écran, jamais journalisé dans la console
  it.each([[500], [403]])('never calls console.error when the load fails with %i', (status) => {
    start();
    answerList({ message: 'boom' }, status);

    expect(consoleError).not.toHaveBeenCalled();
  });

  // Utilisateur non-admin (getDrivers) : mêmes comportements d'erreur que la liste admin
  it.each<[number, string[]]>([
    [403, [LOAD_FORBIDDEN_TEXT]],
    [500, []],
  ])(
    'shows the load error and no fake list when the drivers load fails with %i',
    (status, expectedToasts) => {
      start(false);
      const request = httpMock.expectOne(isDrivers);
      expect(request.request.context.get(HANDLED_STATUSES)).toContain(403);
      request.flush({ message: 'boom' }, { status, statusText: 'Status' });
      fixture.detectChanges();

      expect(headerTexts()).toEqual(HEADERS);
      expect(dataRows()).toHaveLength(0);
      expect(loadError()?.textContent?.trim()).toBe(LOAD_ERROR_TEXT);
      expect(host().textContent).not.toMatch(/résultats?/);
      expect(buttonByText('Previous')).toBeUndefined();
      expect(buttonByText('Next')).toBeUndefined();
      expect(fixture.componentInstance.isLoading()).toBe(false);
      expect(fixture.componentInstance.currentPage()).toBe(0);
      expect(show.mock.calls.map((call: unknown[]) => call[0])).toEqual(expectedToasts);
      expect(consoleError).not.toHaveBeenCalled();
    },
  );

  // Utilisateur non-admin : la liste des conducteurs se charge et s'affiche
  it('shows the drivers and the result count after a successful non-admin load', () => {
    start(false);
    answerDrivers(pageOf(3, 1));

    expect(dataRows()).toHaveLength(1);
    expect(host().textContent).toContain('3 résultats');
    expect(loadError()).toBeNull();
    expect(show).not.toHaveBeenCalled();
  });
});
