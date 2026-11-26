import { HttpRequest, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter, Router } from '@angular/router';
import { environment } from '../../../../../environments/environment';
import { ErrorInterceptor } from '../../../../core/interceptors/error.interceptor';
import { ToastService } from '../../../../core/services/toast.service';
import { UserResponse } from '../../../../core/services/users.service';
import { VehicleResponse } from '../../../../core/services/vehicles.service';
import { VehicleSelectComponent } from '../../../vehicles/components/vehicle-select/vehicle-select.component';
import { DriverSelectComponent } from '../driver-select/driver-select.component';
import { NewReservationComponent } from './new-reservation.component';

interface FormOptions {
  startDate?: string;
  endDate?: string;
  mileageStart?: string;
}

describe('NewReservationComponent with the real ErrorInterceptor', () => {
  const reservationsUrl = `${environment.apiUrl}/reservations`;
  const notificationsUrl = `${environment.apiUrl}/notifications`;
  const GENERIC_400 = "Les informations saisies n'ont pas pu être validées. Veuillez réessayer.";
  const OLD_FIXED_MESSAGE = 'Une erreur est survenue. Veuillez réessayer.';
  const driver = {
    id: 'driver-1',
    firstname: 'Marie',
    lastname: 'Martin',
    email: 'marie.martin@parkyn.app',
  } as UserResponse;
  const vehicle = {
    id: 'vehicle-1',
    brand: 'Renault',
    model: 'Clio',
    plateNumber: 'AB-123-CD',
  } as VehicleResponse;
  let fixture: ComponentFixture<NewReservationComponent>;
  let httpMock: HttpTestingController;
  let show: ReturnType<typeof vi.spyOn>;
  let navigate: ReturnType<typeof vi.spyOn>;
  let consoleError: ReturnType<typeof vi.spyOn>;
  let consoleLog: ReturnType<typeof vi.spyOn>;

  const isCreate = (r: HttpRequest<unknown>) => r.method === 'POST' && r.url === reservationsUrl;

  beforeEach(async () => {
    // Timers simulés : le header relance ses notifications toutes les 2 minutes
    vi.useFakeTimers();
    consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
    consoleLog = vi.spyOn(console, 'log').mockImplementation(() => {});
    await TestBed.configureTestingModule({
      imports: [NewReservationComponent],
      providers: [
        provideHttpClient(withInterceptors([ErrorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    show = vi.spyOn(TestBed.inject(ToastService), 'show');
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(NewReservationComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock
      .match((r: HttpRequest<unknown>) => r.url === notificationsUrl)
      .forEach((r: TestRequest) => r.flush([]));
    httpMock.verify();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  function host(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function control(name: string): HTMLInputElement {
    return host().querySelector<HTMLInputElement>(`[formControlName="${name}"]`)!;
  }

  function fill(name: string, value: string) {
    const el = control(name);
    el.value = value;
    el.dispatchEvent(new Event('input'));
  }

  function fillForm(options: FormOptions = {}) {
    fixture.debugElement
      .query(By.directive(DriverSelectComponent))
      .componentInstance.onSelect(driver);
    fixture.debugElement
      .query(By.directive(VehicleSelectComponent))
      .componentInstance.onSelect(vehicle);
    fill('startDate', options.startDate ?? '2030-01-10');
    fill('endDate', options.endDate ?? '2030-01-12');
    fill('mileageStart', options.mileageStart ?? '15000');
    const condition = host().querySelector<HTMLSelectElement>('app-condition-select select')!;
    condition.value = 'GOOD';
    condition.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function submit() {
    host().querySelector('form')!.dispatchEvent(new Event('submit'));
  }

  function fillAndSubmit(options: FormOptions = {}) {
    fillForm(options);
    submit();
  }

  function answer(body: object | string | null, status: number) {
    httpMock.expectOne(isCreate).flush(body, { status, statusText: 'Status' });
    fixture.detectChanges();
  }

  // Messages affichés sous un champ, retrouvés par l'aria-describedby de son champ
  function errorsOf(name: string): string[] {
    const id = control(name).getAttribute('aria-describedby');
    if (!id) return [];
    return Array.from(host().querySelectorAll(`div[id="${id}"] p[role="alert"]`)).map(
      (p: Element) => p.textContent?.trim() ?? '',
    );
  }

  function formAlert(): HTMLElement | null {
    return host().querySelector<HTMLElement>('[data-testid="form-alert"]');
  }

  function submitButton(): HTMLButtonElement {
    return host().querySelector<HTMLButtonElement>('button[type="submit"]')!;
  }

  it('shows the server messages under mileage and dates and no toast on a 400', () => {
    fillAndSubmit();

    answer(
      {
        errors: {
          mileageStart: ['Le kilométrage doit être positif.'],
          startDate: ['La date de début est dans le passé.', 'La date de début est invalide.'],
          endDate: ['La date de fin est invalide.'],
        },
      },
      400,
    );

    expect(errorsOf('mileageStart')).toEqual(['Le kilométrage doit être positif.']);
    expect(errorsOf('startDate')).toEqual([
      'La date de début est dans le passé.',
      'La date de début est invalide.',
    ]);
    expect(errorsOf('endDate')).toEqual(['La date de fin est invalide.']);
    expect(control('mileageStart').getAttribute('aria-invalid')).toBe('true');
    expect(control('startDate').getAttribute('aria-invalid')).toBe('true');
    expect(control('endDate').getAttribute('aria-invalid')).toBe('true');
    expect(control('notesBefore').getAttribute('aria-invalid')).not.toBe('true');
    expect(formAlert()).toBeNull();
    expect(host().textContent).not.toContain(OLD_FIXED_MESSAGE);
    expect(show).not.toHaveBeenCalled();
    expect(submitButton().disabled).toBe(false);
  });

  // Les règles de dates du serveur répondent un texte brut, sans clé de champ
  it('shows the generic form alert and no toast on a 400 with a plain text body', () => {
    fillAndSubmit();

    answer('La date de fin doit être postérieure à la date de début', 400);

    expect(formAlert()).not.toBeNull();
    expect(formAlert()!.textContent?.trim()).toBe(GENERIC_400);
    expect(host().textContent).not.toContain('postérieure à la date de début');
    expect(errorsOf('startDate')).toEqual([]);
    expect(errorsOf('endDate')).toEqual([]);
    expect(errorsOf('mileageStart')).toEqual([]);
    expect(show).not.toHaveBeenCalled();
    expect(submitButton().disabled).toBe(false);
  });

  it.each<[number, string, string]>([
    [403, 'Forbidden by API', "Vous n'avez pas le droit de créer une réservation."],
    [404, 'Vehicle not found', "Ce véhicule ou ce conducteur n'existe plus."],
    [409, 'Véhicule non disponible', "Ce véhicule n'est pas disponible sur cette période."],
  ])(
    'shows exactly one toast with the action text on a %i',
    (status: number, apiBody: string, text: string) => {
      fillAndSubmit();

      answer(apiBody, status);

      expect(show).toHaveBeenCalledTimes(1);
      expect(show.mock.calls[0][0]).toBe(text);
      expect(formAlert()).toBeNull();
      expect(errorsOf('startDate')).toEqual([]);
      expect(errorsOf('mileageStart')).toEqual([]);
      expect(host().textContent).not.toContain(apiBody);
      expect(submitButton().disabled).toBe(false);
    },
  );

  it('shows only the interceptor toast and no form message on a 500', () => {
    fillAndSubmit();

    answer('SQL boom', 500);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Erreur serveur interne');
    expect(formAlert()).toBeNull();
    expect(host().querySelectorAll('[role="alert"]')).toHaveLength(0);
    expect(host().textContent).not.toContain(OLD_FIXED_MESSAGE);
    expect(host().textContent).not.toContain('SQL boom');
    expect(submitButton().disabled).toBe(false);
  });

  it('clears the error of a field as soon as that field is modified, and only that one', () => {
    fillAndSubmit();
    answer({ errors: { mileageStart: ['kilométrage refusé'], startDate: ['début refusé'] } }, 400);
    expect(errorsOf('mileageStart')).toEqual(['kilométrage refusé']);

    fill('mileageStart', '20000');
    fixture.detectChanges();

    expect(errorsOf('mileageStart')).toEqual([]);
    expect(control('mileageStart').getAttribute('aria-invalid')).not.toBe('true');
    expect(control('mileageStart').getAttribute('aria-describedby')).toBeNull();
    expect(errorsOf('startDate')).toEqual(['début refusé']);
  });

  it('sends the form values and navigates to the reservations list without toast on a 201', () => {
    fillAndSubmit();

    const request = httpMock.expectOne(isCreate);
    expect(request.request.body).toEqual({
      vehicleId: 'vehicle-1',
      driverId: 'driver-1',
      driverFirstName: 'Marie',
      driverLastName: 'Martin',
      driverEmail: 'marie.martin@parkyn.app',
      startDate: '2030-01-10T00:00:00Z',
      endDate: '2030-01-12T00:00:00Z',
      conditionBefore: 'GOOD',
      mileageStart: 15000,
      notesBefore: '',
    });
    request.flush({ id: 'reservation-1' }, { status: 201, statusText: 'Created' });
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith(['/reservations']);
    expect(show).not.toHaveBeenCalled();
    expect(formAlert()).toBeNull();
  });

  it('rejects a mileage of 0 in the form and sends nothing', () => {
    fillAndSubmit({ mileageStart: '0' });

    // match referme une éventuelle requête envoyée à tort, sans bloquer le nettoyage
    expect(httpMock.match(isCreate)).toHaveLength(0);
    expect(fixture.componentInstance.reservationForm.get('mileageStart')!.valid).toBe(false);
    expect(submitButton().disabled).toBe(true);
  });

  it('sends the current instant as start when the start date is today', () => {
    vi.setSystemTime(new Date('2026-11-26T12:00:00Z'));

    fillAndSubmit({ startDate: '2026-11-26', endDate: '2026-11-28' });

    const body = httpMock.expectOne(isCreate).request.body;
    expect(body.startDate).toMatch(/^2026-11-26T12:00:00(\.000)?Z$/);
    expect(body.endDate).toBe('2026-11-28T00:00:00Z');
  });

  it('sends midnight UTC as start when the start date is in the future', () => {
    vi.setSystemTime(new Date('2026-11-26T12:00:00Z'));

    fillAndSubmit({ startDate: '2026-11-27', endDate: '2026-11-28' });

    const body = httpMock.expectOne(isCreate).request.body;
    expect(body.startDate).toBe('2026-11-27T00:00:00Z');
    expect(body.endDate).toBe('2026-11-28T00:00:00Z');
  });

  it.each<[string, number, object | string | null]>([
    ['a 400 with field errors', 400, { errors: { mileageStart: ['kilométrage refusé'] } }],
    ['a 400 with a plain text body', 400, 'La date de fin est invalide'],
    ['a 403', 403, 'Forbidden by API'],
    ['a 500', 500, 'SQL boom'],
    ['a 201', 201, { id: 'reservation-1' }],
  ])('writes nothing to the console on %s', (_label: string, status: number, body) => {
    fillAndSubmit();

    answer(body, status);

    expect(consoleError).not.toHaveBeenCalled();
    expect(consoleLog).not.toHaveBeenCalled();
  });
});
