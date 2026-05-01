import { Component } from '@angular/core';
import { HeaderComponent } from '../../../../core/components/header/header.component';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { VehicleService } from '../../../../core/services/vehicles.service';
import { ConditionSelectComponent } from '../../../reservations/components/condition-select/condition-select.component';

@Component({
  selector: 'app-new-vehicle',
  standalone: true,
  imports: [HeaderComponent, RouterLink, ReactiveFormsModule, ConditionSelectComponent],
  templateUrl: './new-vehicle.component.html',
  styleUrl: './new-vehicle.component.css',
})
export class NewVehicleComponent {
  vehicleForm: FormGroup;
  isSubmitting = false;
  errorMessage = '';

  constructor(
    private fb: FormBuilder,
    private vehicleService: VehicleService,
    private router: Router,
  ) {
    this.vehicleForm = this.fb.group({
      plateNumber: ['', Validators.required],
      brand: ['', Validators.required],
      model: ['', Validators.required],
      year: [null, [Validators.required, Validators.min(0)]],
      mileageKM: [null, [Validators.required, Validators.min(0)]],
      condition: ['', Validators.required],
      commissionedAt: ['', Validators.required],
    });
  }

  onSubmit() {
    if (this.vehicleForm.invalid) return;

    this.isSubmitting = true;
    this.errorMessage = '';

    const formValue = this.vehicleForm.value;

    this.vehicleService
      .addVehicle({
        plateNumber: formValue.plateNumber,
        brand: formValue.brand,
        model: formValue.model,
        year: Number(formValue.year),
        mileageKM: Number(formValue.mileageKM),
        condition: formValue.condition,
        commissionedAt: formValue.commissionedAt,
      })
      .subscribe({
        next: () => this.router.navigate(['/vehicles']),
        error: () => {
          this.errorMessage = 'Une erreur est survenue. Veuillez réessayer.';
          this.isSubmitting = false;
        },
      });
  }

  onConditionChange(condition: string) {
    this.vehicleForm.get('condition')?.setValue(condition);
  }
}
