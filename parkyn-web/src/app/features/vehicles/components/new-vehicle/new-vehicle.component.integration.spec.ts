import { HttpRequest, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { environment } from '../../../../../environments/environment';
import { ErrorInterceptor } from '../../../../core/interceptors/error.interceptor';
import { ToastService } from '../../../../core/services/toast.service';
import { NewVehicleComponent } from './new-vehicle.component';

describe('NewVehicleComponent with the real ErrorInterceptor', () => {
  const vehiclesUrl = `${environment.apiUrl}/vehicles`;
  const notificationsUrl = `${environment.apiUrl}/notifications`;
  const GENERIC_400 = "Les informations saisies n'ont pas pu être validées. Veuillez réessayer.";
  const FORBIDDEN_TEXT = "Vous n'avez pas le droit de créer un véhicule.";
  const OLD_FIXED_MESSAGE = 'Une erreur est survenue. Veuillez réessayer.';
  let fixture: ComponentFixture<NewVehicleComponent>;
  let httpMock: HttpTestingController;
  let show: ReturnType<typeof vi.spyOn>;
  let navigate: ReturnType<typeof vi.spyOn>;

  const isCreate = (r: HttpRequest<unknown>) => r.method === 'POST' && r.url === vehiclesUrl;

  beforeEach(async () => {
    // Timers simulés : le header relance ses notifications toutes les 2 minutes
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      imports: [NewVehicleComponent],
      providers: [
        provideHttpClient(withInterceptors([ErrorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    show = vi.spyOn(TestBed.inject(ToastService), 'show');
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(NewVehicleComponent);
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

  function fillAndSubmit() {
    fill('plateNumber', 'AB-123-CD');
    fill('brand', 'Renault');
    fill('model', 'Clio');
    fill('year', '2022');
    fill('mileageKM', '15000');
    fill('commissionedAt', '2024-01-15');
    const condition = host().querySelector<HTMLSelectElement>('app-condition-select select')!;
    condition.value = 'GOOD';
    condition.dispatchEvent(new Event('change'));
    host().querySelector('form')!.dispatchEvent(new Event('submit'));
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

  it('shows the server messages under their fields and no toast on a 400', () => {
    fillAndSubmit();

    answer(
      {
        errors: {
          plateNumber: ["Le format de l'immatriculation est invalide."],
          year: ["L'année est invalide.", "L'année est trop ancienne."],
        },
      },
      400,
    );

    expect(errorsOf('plateNumber')).toEqual(["Le format de l'immatriculation est invalide."]);
    expect(errorsOf('year')).toEqual(["L'année est invalide.", "L'année est trop ancienne."]);
    expect(control('plateNumber').getAttribute('aria-invalid')).toBe('true');
    expect(control('year').getAttribute('aria-invalid')).toBe('true');
    expect(control('brand').getAttribute('aria-invalid')).not.toBe('true');
    expect(formAlert()).toBeNull();
    expect(host().textContent).not.toContain(OLD_FIXED_MESSAGE);
    expect(show).not.toHaveBeenCalled();
    expect(submitButton().disabled).toBe(false);
  });

  it.each<[string, object | null]>([
    ['a null body', null],
    ['an errors object without messages', { errors: {} }],
  ])('shows the generic form alert and no toast on a 400 with %s', (_label: string, body) => {
    fillAndSubmit();

    answer(body, 400);

    expect(formAlert()).not.toBeNull();
    expect(formAlert()!.textContent?.trim()).toBe(GENERIC_400);
    expect(errorsOf('plateNumber')).toEqual([]);
    expect(show).not.toHaveBeenCalled();
    expect(submitButton().disabled).toBe(false);
  });

  it.each<[string, string]>([
    ['an unknown key', 'color'],
    ['the _global key', '_global'],
  ])('shows the message of %s in the form alert, not under a field', (_label: string, key) => {
    fillAndSubmit();

    answer({ errors: { plateNumber: ['plaque refusée'], [key]: ['message hors champ'] } }, 400);

    expect(formAlert()).not.toBeNull();
    expect(formAlert()!.textContent).toContain('message hors champ');
    expect(formAlert()!.textContent).not.toContain('plaque refusée');
    expect(errorsOf('plateNumber')).toEqual(['plaque refusée']);
    expect(show).not.toHaveBeenCalled();
  });

  it('shows exactly one toast with the action text on a 403, not the generic one', () => {
    fillAndSubmit();

    answer('Forbidden by API', 403);

    expect(show).toHaveBeenCalledTimes(1);
    expect(show.mock.calls[0][0]).toBe(FORBIDDEN_TEXT);
    expect(show.mock.calls[0][0]).not.toBe('Accès refusé');
    expect(formAlert()).toBeNull();
    expect(host().textContent).not.toContain('Forbidden by API');
    expect(submitButton().disabled).toBe(false);
  });

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
    answer({ errors: { plateNumber: ['plaque refusée'], year: ['année refusée'] } }, 400);
    expect(errorsOf('plateNumber')).toEqual(['plaque refusée']);

    fill('plateNumber', 'ZZ-999-ZZ');
    fixture.detectChanges();

    expect(errorsOf('plateNumber')).toEqual([]);
    expect(control('plateNumber').getAttribute('aria-invalid')).not.toBe('true');
    expect(control('plateNumber').getAttribute('aria-describedby')).toBeNull();
    expect(errorsOf('year')).toEqual(['année refusée']);
  });

  it('sends the form values and navigates to the vehicles list without toast on a 201', () => {
    fillAndSubmit();

    const request = httpMock.expectOne(isCreate);
    expect(request.request.body).toEqual({
      plateNumber: 'AB-123-CD',
      brand: 'Renault',
      model: 'Clio',
      year: 2022,
      mileageKM: 15000,
      condition: 'GOOD',
      commissionedAt: '2024-01-15',
    });
    request.flush({ id: 'vehicle-1' }, { status: 201, statusText: 'Created' });
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith(['/vehicles']);
    expect(show).not.toHaveBeenCalled();
    expect(formAlert()).toBeNull();
  });
});
