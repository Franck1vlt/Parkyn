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
  ],
  templateUrl: './new-reservation.component.html',
  styleUrl: './new-reservation.component.css',
})
export class NewReservationComponent {
  reservationForm: FormGroup;
  isSubmitting = false;
  errorMessage = '';

  constructor(
    private fb: FormBuilder,
    private reservationsService: ReservationsService,
    private router: Router,
  ) {
    this.reservationForm = this.fb.group({
      vehicleId: ['', Validators.required],
      driverId: ['', Validators.required],
      driverFirstName: ['', Validators.required],
      driverLastName: ['', Validators.required],
      driverEmail: ['', Validators.required],
      startDate: ['', Validators.required],
      endDate: ['', Validators.required],
      conditionBefore: ['', Validators.required],
      mileageStart: [null, [Validators.required, Validators.min(0)]],
      notesBefore: [''],
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

    this.reservationsService.addReservation({
      id: '',
      vehicleId: formValue.vehicleId,
      driverId: formValue.driverId,
      driverFirstName: formValue.driverFirstName,
      driverLastName: formValue.driverLastName,
      driverEmail: formValue.driverEmail,
      startDate: formValue.startDate,
      endDate: formValue.endDate,
      conditionBefore: formValue.conditionBefore,
      mileageStart: Number(formValue.mileageStart),
      notesBefore: formValue.notesBefore ?? '',
    }).subscribe({
      next: () => this.router.navigate(['/reservations']),
      error: () => {
        this.errorMessage = 'Une erreur est survenue. Veuillez réessayer.';
        this.isSubmitting = false;
      },
    });
  }
}
