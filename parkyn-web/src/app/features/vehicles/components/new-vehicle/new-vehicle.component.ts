import { HttpErrorResponse } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import { HeaderComponent } from '../../../../core/components/header/header.component';
import { InlineErrorComponent } from '../../../../core/components/inline-error/inline-error.component';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { VehicleService } from '../../../../core/services/vehicles.service';
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
import { ConditionSelectComponent } from '../../../reservations/components/condition-select/condition-select.component';

// Champs du formulaire qui ont leur zone d'erreur ; toute autre clé va dans l'alerte du haut
const SERVER_FIELDS: readonly string[] = [
  'plateNumber',
  'brand',
  'model',
  'year',
  'mileageKM',
  'commissionedAt',
];

@Component({
  selector: 'app-new-vehicle',
  standalone: true,
  imports: [
    HeaderComponent,
    RouterLink,
    ReactiveFormsModule,
    ConditionSelectComponent,
    InlineErrorComponent,
  ],
  templateUrl: './new-vehicle.component.html',
  styleUrl: './new-vehicle.component.css',
})
export class NewVehicleComponent {
  vehicleForm: FormGroup;
  isSubmitting = false;
  // Erreurs du serveur par champ ; un champ modifié perd les siennes
  serverErrors = signal<Record<string, string[]>>({});
  // Messages de l'alerte du haut : 400 illisible, clés inconnues, erreurs globales
  alertMessages = signal<string[]>([]);

  constructor(
    private fb: FormBuilder,
    private vehicleService: VehicleService,
    private router: Router,
    private toastService: ToastService,
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

    for (const field of SERVER_FIELDS) {
      this.vehicleForm.get(field)!.valueChanges.subscribe(() => this.clearServerErrors(field));
    }
  }

  onSubmit() {
    if (this.vehicleForm.invalid) return;

    this.isSubmitting = true;
    this.serverErrors.set({});
    this.alertMessages.set([]);

    const formValue = this.vehicleForm.value;

    this.vehicleService
      .addVehicle(
        {
          plateNumber: formValue.plateNumber,
          brand: formValue.brand,
          model: formValue.model,
          year: Number(formValue.year),
          mileageKM: Number(formValue.mileageKM),
          condition: formValue.condition,
          commissionedAt: formValue.commissionedAt,
        },
        httpErrorContext('create-vehicle'),
      )
      .subscribe({
        next: () => this.router.navigate(['/vehicles']),
        error: (error: HttpErrorResponse) => {
          this.isSubmitting = false;
          this.showServerError(error);
        },
      });
  }

  onConditionChange(condition: string) {
    this.vehicleForm.get('condition')?.setValue(condition);
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
          : [describeHttpError(error, 'create-vehicle')],
      );
      return;
    }
    // Les autres statuts (0, 5xx) restent à l'intercepteur
    if (isHandledHttpError(error, 'create-vehicle')) {
      this.toastService.show(describeHttpError(error, 'create-vehicle'));
    }
  }

  private clearServerErrors(field: string) {
    if (!this.serverErrors()[field]) return;
    const remaining = { ...this.serverErrors() };
    delete remaining[field];
    this.serverErrors.set(remaining);
  }
}
