import { Component } from '@angular/core';
import { HeaderComponent } from '../../../../core/components/header/header.component';
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
import { concat } from 'rxjs';

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
  ],
  templateUrl: './new-reservation.component.html',
  styleUrl: './new-reservation.component.css',
})
export class NewReservationComponent {
  reservationForm: FormGroup;
  isSubmitting = false;
  errorMessage = '';
  isAdmin: boolean;

  constructor(
    private fb: FormBuilder,
    private reservationsService: ReservationsService,
    private router: Router,
    private authService: AuthService,
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
      mileageStart: [null, [Validators.required, Validators.min(0)]],
      notesBefore: [''],
    });
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
    this.errorMessage = '';

    const formValue = this.reservationForm.value;

    const payload = {
      vehicleId: formValue.vehicleId,
      driverId: formValue.driverId,
      driverFirstName: formValue.driverFirstName,
      driverLastName: formValue.driverLastName,
      driverEmail: formValue.driverEmail,
      agentId: formValue.agentId || undefined,
      startDate: `${formValue.startDate}T00:00:00Z`,
      endDate: `${formValue.endDate}T00:00:00Z`,
      conditionBefore: formValue.conditionBefore,
      mileageStart: Number(formValue.mileageStart),
      notesBefore: formValue.notesBefore ?? '',
    };

    console.log('=== PAYLOAD ENVOYÉ ===', payload);
    console.log('Token JWT:', this.authService.getAccessToken());
    console.log('Rôle utilisateur:', this.authService.getRole());

    this.reservationsService
      .addReservation(payload)
      .subscribe({
        next: (res) => {
          console.log('=== SUCCÈS ===', res);
          this.router.navigate(['/reservations']);
        },
        error: (err) => {
          console.error('=== ERREUR ===', err);
          console.error('Status:', err.status);
          console.error('Body:', err.error);
          this.errorMessage = 'Une erreur est survenue. Veuillez réessayer.';
          this.isSubmitting = false;
        },
      });
  }
}
