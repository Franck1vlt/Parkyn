import { HttpRequest } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../../../environments/environment';
import { HANDLED_STATUSES } from '../../../../core/interceptors/error-handling';
import { Page } from '../../../../core/models/page.model';
import { ToastService } from '../../../../core/services/toast.service';
import { VehicleResponse } from '../../../../core/services/vehicles.service';
import { provideTestApp } from '../../../../../testing/test-providers';
import { VehiclesComponent } from './vehicles.component';

describe('VehiclesComponent', () => {
  let fixture: ComponentFixture<VehiclesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VehiclesComponent],
      providers: [provideTestApp()],
    }).compileComponents();

    fixture = TestBed.createComponent(VehiclesComponent);
  });

  // Test de fumée : l'injection de dépendances et le gabarit se résolvent sans erreur
  it('renders without error', () => {
    expect(() => fixture.detectChanges()).not.toThrow();
    expect(fixture.nativeElement.childElementCount).toBeGreaterThan(0);
  });
});

describe('VehiclesComponent error display', () => {
  const vehiclesUrl = `${environment.apiUrl}/vehicles`;
  const notificationsUrl = `${environment.apiUrl}/notifications`;
  const LOAD_ERROR_TEXT = 'Impossible de charger la liste. Rechargez la page.';
  const NOT_FOUND_TEXT = "Ce véhicule n'existe plus.";
  const DELETE_FORBIDDEN_TEXT = "Vous n'avez pas le droit de supprimer ce véhicule.";
  const LOAD_FORBIDDEN_TEXT = "Vous n'avez pas accès à la liste des véhicules.";

  let fixture: ComponentFixture<VehiclesComponent>;
  let httpMock: HttpTestingController;
  let show: ReturnType<typeof vi.spyOn>;
  let consoleError: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    // Timers simulés : le header relance ses notifications toutes les 2 minutes
    vi.useFakeTimers();
    consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
    await TestBed.configureTestingModule({
      imports: [VehiclesComponent],
      providers: [provideTestApp()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    show = vi.spyOn(TestBed.inject(ToastService), 'show');
    fixture = TestBed.createComponent(VehiclesComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    // Le header demande ses notifications : on les referme avant la vérification
    httpMock.match((r) => r.url === notificationsUrl).forEach((r) => r.flush([]));
    httpMock.verify();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  function host(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  const isList = (r: HttpRequest<unknown>) => r.method === 'GET' && r.url === vehiclesUrl;
  const isDelete = (r: HttpRequest<unknown>) =>
    r.method === 'DELETE' && r.url.startsWith(`${vehiclesUrl}/`);

  function vehicle(id: string): VehicleResponse {
    return {
      id,
      plateNumber: `AA-${id}-BB`,
      brand: 'Renault',
      model: 'Clio',
      year: 2022,
      mileageKM: 1000,
      condition: 'GOOD',
      status: 'AVAILABLE',
      commissionedAt: '2022-01-01',
      createdAt: '2022-01-01T00:00:00Z',
    };
  }

  function pageOf(totalElements: number, totalPages: number, number = 0): Page<VehicleResponse> {
    return { content: [vehicle('1')], page: { number, size: 10, totalElements, totalPages } };
  }

  function answerList(body: object | null, status = 200) {
    httpMock.expectOne(isList).flush(body, { status, statusText: 'Status' });
    fixture.detectChanges();
  }

  // Page chargée avec un véhicule et trois pages au total
  function loadFirstPage() {
    answerList(pageOf(25, 3));
  }

  function buttonByText(text: string): HTMLButtonElement | undefined {
    return Array.from(host().querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === text,
    );
  }

  function clickDelete() {
    const button = host().querySelector<HTMLButtonElement>('app-vehicle-card button');
    expect(button).not.toBeNull();
    button!.click();
  }

  function answerDelete(status: number) {
    httpMock.expectOne(isDelete).flush(null, { status, statusText: 'Status' });
    fixture.detectChanges();
  }

  function loadError(): HTMLElement | null {
    return host().querySelector<HTMLElement>('[data-testid="vehicles-load-error"]');
  }

  function changeFilter(value: string) {
    const select = host().querySelector<HTMLSelectElement>('select')!;
    select.value = value;
    select.dispatchEvent(new Event('change'));
  }

  // Cas 6 : garde, vert d'emblée
  it('shows the result count after a successful load', () => {
    answerList(pageOf(3, 1));

    expect(host().textContent).toContain('3 résultats');
    expect(loadError()).toBeNull();
  });

  // Cas 7
  it('shows an inline message instead of a fake empty list when the first load fails', () => {
    answerList({ message: 'SQL boom' }, 500);

    expect(host().textContent).not.toContain('0 résultats');
    expect(host().textContent).not.toMatch(/résultats?/);
    expect(loadError()).not.toBeNull();
    expect(loadError()!.textContent?.trim()).toBe(LOAD_ERROR_TEXT);
    expect(loadError()!.querySelector('button')).toBeNull();
    expect(buttonByText('Previous')).toBeUndefined();
    expect(buttonByText('Next')).toBeUndefined();
  });

  // Cas 8
  it('stops the loading state when the load fails', () => {
    expect(fixture.componentInstance.isLoading()).toBe(true);

    answerList(null, 500);

    expect(fixture.componentInstance.isLoading()).toBe(false);
  });

  // Cas 9
  it('toasts the load-vehicles message on a 403 and declares 403 as handled', () => {
    const request = httpMock.expectOne(isList);
    expect(request.request.context.get(HANDLED_STATUSES)).toContain(403);
    request.flush({ message: 'forbidden' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(show).toHaveBeenCalledTimes(1);
    expect(show.mock.calls[0][0]).toBe(LOAD_FORBIDDEN_TEXT);
  });

  // Cas 9 (suite) : un 403 de chargement ne laisse pas un faux compteur à 0
  it('shows the load error and no fake count when the list load returns a 403', () => {
    answerList({ message: 'forbidden' }, 403);

    expect(host().textContent).not.toContain('0 résultats');
    expect(host().textContent).not.toMatch(/résultats?/);
    expect(loadError()).not.toBeNull();
    expect(loadError()!.textContent?.trim()).toBe(LOAD_ERROR_TEXT);
  });

  // Cas 10 : garde, vert d'emblée
  it('reloads the list after a successful delete', () => {
    loadFirstPage();

    clickDelete();
    answerDelete(204);

    expect(httpMock.match(isList)).toHaveLength(1);
    expect(show).not.toHaveBeenCalled();
  });

  // Cas 11
  it('toasts the vanished-vehicle message when the delete returns a 404', () => {
    loadFirstPage();

    clickDelete();
    answerDelete(404);
    // Le rechargement n'est pas l'objet de ce test : on referme sa requête
    httpMock.match(isList);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show.mock.calls[0][0]).toBe(NOT_FOUND_TEXT);
  });

  // Cas 12 : le véhicule a disparu, la liste doit se rafraîchir
  it('reloads the list after a delete that returns a 404', () => {
    loadFirstPage();

    clickDelete();
    answerDelete(404);

    expect(httpMock.match(isList)).toHaveLength(1);
  });

  // Cas 13
  it('toasts the rights message and does not reload when the delete returns a 403', () => {
    loadFirstPage();

    clickDelete();
    answerDelete(403);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show.mock.calls[0][0]).toBe(DELETE_FORBIDDEN_TEXT);
    httpMock.expectNone(isList);
  });

  // Suppression refusée : la liste reste affichée, sans message de chargement
  it('keeps the list and shows no load error when the delete returns a 403', () => {
    loadFirstPage();

    clickDelete();
    answerDelete(403);

    expect(host().querySelectorAll('app-vehicle-card')).toHaveLength(1);
    expect(host().textContent).toContain('25 résultats');
    expect(loadError()).toBeNull();
  });

  // Suppression d'un véhicule disparu : le rechargement vide est une liste légitime
  it('shows an empty list and no load error after a 404 delete and a successful empty reload', () => {
    loadFirstPage();

    clickDelete();
    answerDelete(404);
    answerList({ content: [], page: { number: 0, size: 10, totalElements: 0, totalPages: 0 } });

    expect(host().querySelectorAll('app-vehicle-card')).toHaveLength(0);
    expect(host().textContent).toContain('0 résultats');
    expect(loadError()).toBeNull();
  });

  // Récupération : un chargement réussi efface l'erreur et réaffiche la liste
  it('hides the load error and shows the list again when a filter change succeeds after a failure', () => {
    answerList(null, 500);
    expect(loadError()).not.toBeNull();

    changeFilter('RESERVED');
    answerList(pageOf(3, 1));

    expect(loadError()).toBeNull();
    expect(host().querySelectorAll('app-vehicle-card')).toHaveLength(1);
    expect(host().textContent).toContain('3 résultats');
  });

  // Cas 14
  it('sends the delete request with 403 and 404 declared as handled', () => {
    loadFirstPage();

    clickDelete();

    const request = httpMock.expectOne(isDelete);
    expect(request.request.context.get(HANDLED_STATUSES)).toContain(403);
    expect(request.request.context.get(HANDLED_STATUSES)).toContain(404);
    request.flush(null, { status: 204, statusText: 'No Content' });
    httpMock.match(isList);
  });

  // Cas 15
  it.each([
    ['a failed load', () => answerList(null, 500)],
    [
      'a failed delete',
      () => {
        loadFirstPage();
        clickDelete();
        answerDelete(404);
        httpMock.match(isList);
      },
    ],
    ['a 403 on load', () => answerList({ message: 'forbidden' }, 403)],
    [
      'a 403 on delete',
      () => {
        loadFirstPage();
        clickDelete();
        answerDelete(403);
      },
    ],
  ])('never calls console.error after %s', (_label, scenario) => {
    scenario();

    expect(consoleError).not.toHaveBeenCalled();
  });

  // Cas 18 : un échec de page ou de filtre vaut un échec initial
  it.each([
    ['page change', () => buttonByText('Next')!.click()],
    [
      'filter change',
      () => {
        const select = host().querySelector<HTMLSelectElement>('select')!;
        select.value = 'RESERVED';
        select.dispatchEvent(new Event('change'));
      },
    ],
  ])('clears the loaded page and shows the load error when a %s fails', (_label, trigger) => {
    loadFirstPage();
    expect(host().querySelectorAll('app-vehicle-card')).toHaveLength(1);
    expect(host().textContent).toContain('25 résultats');

    trigger();
    answerList(null, 500);

    expect(host().querySelectorAll('app-vehicle-card')).toHaveLength(0);
    expect(host().textContent).not.toMatch(/résultats?/);
    expect(loadError()?.textContent?.trim()).toBe(LOAD_ERROR_TEXT);
    expect(buttonByText('Previous')).toBeUndefined();
    expect(buttonByText('Next')).toBeUndefined();
  });

  // Cas 18 (suite) : page effacée, l'index repasse à 0 comme après un échec initial
  it('resets the page index to 0 when a page change fails', () => {
    loadFirstPage();

    buttonByText('Next')!.click();
    expect(fixture.componentInstance.currentPage()).toBe(1);
    answerList(null, 500);

    expect(fixture.componentInstance.currentPage()).toBe(0);
  });
});
