import { HttpErrorResponse } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import { HeaderComponent } from '../../../../core/components/header/header.component';
import { InlineErrorComponent } from '../../../../core/components/inline-error/inline-error.component';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ConditionSelectComponent } from '../condition-select/condition-select.component';
import { DriverSelectComponent } from '../driver-select/driver-select.component';
import { UserResponse } from '../../../../core/services/users.service';
import { VehicleSelectComponent } from '../../../vehicles/components/vehicle-select/vehicle-select.component';
import { VehicleResponse } from '../../../../core/services/vehicles.service';
import { ReservationsService } from '../../../../core/services/reservations.service';
import { AgentSelectComponent } from '../agent-select/agent-select.component';
import { AuthService } from '../../../../core/services/auth.service';
import { ToastService } from '../../../../core/services/toast.service';
import {
  describeHttpError,
  httpErrorContext,
  isHandledHttpError,
} from '../../../../core/errors/http-error-messages';
import {
  parseValidationErrors,
  splitValidationErrors,
} from '../../../../core/errors/validation-errors';

// Champs du formulaire qui ont leur zone d'erreur ; toute autre clé va dans l'alerte du haut
const SERVER_FIELDS: readonly string[] = ['startDate', 'endDate', 'mileageStart', 'notesBefore'];

// Date de début à envoyer : l'instant présent si c'est aujourd'hui (date locale), sinon minuit UTC
function toStartInstant(startDate: string): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  const today = `${now.getFullYear()}-${month}-${day}`;
  return startDate === today ? now.toISOString() : `${startDate}T00:00:00Z`;
}

@Component({
  selector: 'app-new-reservation',
  standalone: true,
  imports: [
    HeaderComponent,
    RouterLink,
    ReactiveFormsModule,
    ConditionSelectComponent,
    DriverSelectComponent,
    VehicleSelectComponent,
    AgentSelectComponent,
    InlineErrorComponent,
  ],
  templateUrl: './new-reservation.component.html',
  styleUrl: './new-reservation.component.css',
})
export class NewReservationComponent {
  reservationForm: FormGroup;
  isSubmitting = false;
  isAdmin: boolean;
  // Erreurs du serveur par champ ; un champ modifié perd les siennes
  serverErrors = signal<Record<string, string[]>>({});
  // Messages de l'alerte du haut : 400 illisible, clés inconnues, erreurs globales
  alertMessages = signal<string[]>([]);

  constructor(
    private fb: FormBuilder,
    private reservationsService: ReservationsService,
    private router: Router,
    private authService: AuthService,
    private toastService: ToastService,
  ) {
    this.isAdmin = this.authService.hasRole('ADMIN');
    this.reservationForm = this.fb.group({
      vehicleId: ['', Validators.required],
      driverId: ['', Validators.required],
      driverFirstName: ['', Validators.required],
      driverLastName: ['', Validators.required],
      driverEmail: ['', Validators.required],
      agentId: [''],
      startDate: ['', Validators.required],
      endDate: ['', Validators.required],
      conditionBefore: ['', Validators.required],
      mileageStart: [null, [Validators.required, Validators.min(1)]],
      notesBefore: [''],
    });

    for (const field of SERVER_FIELDS) {
      this.reservationForm.get(field)!.valueChanges.subscribe(() => this.clearServerErrors(field));
    }
  }

  onAgentSelected(agent: UserResponse) {
    this.reservationForm.patchValue({
      agentId: agent.id,
    });
  }

  onDriverSelected(driver: UserResponse) {
    this.reservationForm.patchValue({
      driverId: driver.id,
      driverFirstName: driver.firstname,
      driverLastName: driver.lastname,
      driverEmail: driver.email,
    });
  }

  onVehicleSelected(vehicle: VehicleResponse) {
    this.reservationForm.patchValue({
      vehicleId: vehicle.id,
    });
  }

  onConditionChange(condition: string) {
    this.reservationForm.get('conditionBefore')?.setValue(condition);
  }

  onSubmit() {
    if (this.reservationForm.invalid) return;

    this.isSubmitting = true;
    this.serverErrors.set({});
    this.alertMessages.set([]);

    const formValue = this.reservationForm.value;

    const payload = {
      vehicleId: formValue.vehicleId,
      driverId: formValue.driverId,
      driverFirstName: formValue.driverFirstName,
      driverLastName: formValue.driverLastName,
      driverEmail: formValue.driverEmail,
      agentId: formValue.agentId || undefined,
      startDate: toStartInstant(formValue.startDate),
      endDate: `${formValue.endDate}T00:00:00Z`,
      conditionBefore: formValue.conditionBefore,
      mileageStart: Number(formValue.mileageStart),
      notesBefore: formValue.notesBefore ?? '',
    };

    this.reservationsService
      .addReservation(payload, httpErrorContext('create-reservation'))
      .subscribe({
        next: () => this.router.navigate(['/reservations']),
        error: (error: HttpErrorResponse) => {
          this.isSubmitting = false;
          this.showServerError(error);
        },
      });
  }

  fieldErrors(field: string): string[] {
    return this.serverErrors()[field] ?? [];
  }

  private showServerError(error: HttpErrorResponse) {
    if (error.status === 400) {
      const { fields, alerts } = splitValidationErrors(
        parseValidationErrors(error.error),
        SERVER_FIELDS,
      );
      this.serverErrors.set(fields);
      // Rien de lisible dans le corps : texte générique du 400
      this.alertMessages.set(
        alerts.length > 0 || Object.keys(fields).length > 0
          ? alerts
          : [describeHttpError(error, 'create-reservation')],
      );
      return;
    }
    // Les autres statuts (0, 5xx) restent à l'intercepteur
    if (isHandledHttpError(error, 'create-reservation')) {
      this.toastService.show(describeHttpError(error, 'create-reservation'));
    }
  }

  private clearServerErrors(field: string) {
    if (!this.serverErrors()[field]) return;
    const remaining = { ...this.serverErrors() };
    delete remaining[field];
    this.serverErrors.set(remaining);
  }
}
