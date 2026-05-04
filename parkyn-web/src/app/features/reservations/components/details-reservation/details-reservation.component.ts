import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { HeaderComponent } from '../../../../core/components/header/header.component';
import {
  ReservationResponse,
  ReservationsService,
} from '../../../../core/services/reservations.service';
import { ConditionSelectComponent } from '../condition-select/condition-select.component';

@Component({
  selector: 'app-details-reservation',
  standalone: true,
  imports: [HeaderComponent, RouterLink, DatePipe, DecimalPipe, ReactiveFormsModule, ConditionSelectComponent],
  templateUrl: './details-reservation.component.html',
  styleUrl: './details-reservation.component.css',
})
export class DetailsReservationComponent implements OnInit {
  reservation = signal<ReservationResponse | undefined>(undefined);
  isLoading = signal(false);
  showReturnModal = signal(false);
  returnForm: FormGroup;

  constructor(
    private route: ActivatedRoute,
    private reservationsService: ReservationsService,
    private fb: FormBuilder,
  ) {
    this.returnForm = this.fb.group({
      conditionAfter: [''],
      mileageEnd: [null],
      damageReported: [false],
      damageDetails: [''],
      notesAfter: [''],
    });
  }

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.isLoading.set(true);
    this.reservationsService.getReservationById(id).subscribe({
      next: (r) => {
        this.reservation.set(r);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error(err);
        this.isLoading.set(false);
      },
    });
  }

  getConditionLabel(condition: string | null | undefined): string {
    if (!condition) return '—';
    const labels: Record<string, string> = {
      EXCELLENT: 'Excellent',
      GOOD: 'Bon',
      FAIR: 'Moyen',
      POOR: 'Mauvais',
    };
    return labels[condition] || condition;
  }

  getConditionClass(condition: string | null | undefined): string {
    if (!condition) return 'text-gray-400';
    const classes: Record<string, string> = {
      EXCELLENT: 'text-green-700',
      GOOD: 'text-blue-700',
      FAIR: 'text-orange-700',
      POOR: 'text-red-500',
    };
    return classes[condition] || '';
  }

  onConditionChange(condition: string) {
    this.returnForm.get('conditionAfter')?.setValue(condition);
  }

  onReturnSubmit() {
    const id = this.reservation()!.id;
    this.reservationsService.returnVehicle(id, this.returnForm.value).subscribe({
      next: (updated) => {
        this.reservation.set(updated);
        this.showReturnModal.set(false);
      },
      error: (err) => console.error(err),
    });
  }
}
