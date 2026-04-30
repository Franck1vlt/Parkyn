import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import {
  VehicleResponse,
  VehicleService,
  VehicleStatus,
} from '../../../../core/services/vehicles.service';
import { RouterLink } from '@angular/router';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HeaderComponent } from '../../../../core/components/header/header.component';
import {
  ReservationResponse,
  ReservationsService,
} from '../../../../core/services/reservations.service';
import { Page } from '../../../../core/models/page.model';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';

@Component({
  selector: 'app-details-vehicle',
  standalone: true,
  imports: [HeaderComponent, RouterLink, DatePipe, DecimalPipe, ReactiveFormsModule],
  templateUrl: './details-vehicle.component.html',
  styleUrl: './details-vehicle.component.css',
})
export class DetailsVehicleComponent implements OnInit {
  page = signal<Page<VehicleResponse> | undefined>(undefined);
  currentPage = signal(0);
  selectedStatus = signal<string | null>(null);
  isLoading = signal(false);
  vehicle = signal<VehicleResponse | undefined>(undefined);
  reservations = signal<ReservationResponse[]>([]);
  showDeleteModal = signal(false);
  showUpdateModal = signal(false);
  vehicleUpdateForm: FormGroup;

  constructor(
    private route: ActivatedRoute,
    private vehicleService: VehicleService,
    private reservationsService: ReservationsService,
    private fb: FormBuilder,
    private router: Router,
  ) {
    this.vehicleUpdateForm = this.fb.group({
      brand: [''],
      model: [''],
      plateNumber: [''],
      year: [null],
      mileageKM: [null],
      commissionedAt: [''],
    });
  }

  ngOnInit() {
    this.loadVehicles();
    const id = this.route.snapshot.paramMap.get('id')!;
    this.vehicleService.getVehicleById(id).subscribe((v) => this.vehicle.set(v));
    this.reservationsService
      .getReservationsByVehicle(id)
      .subscribe((page) => this.reservations.set(page.content));
  }

  getStatusLabel(status: VehicleStatus): string {
    const labels: Record<VehicleStatus, string> = {
      AVAILABLE: 'Disponible',
      RESERVED: 'Réservé',
      MAINTENANCE: 'Maintenance',
      RETIRED: 'Retiré',
    };
    return labels[status];
  }

  getStatusClass(status: VehicleStatus): string {
    const classes: Record<VehicleStatus, string> = {
      AVAILABLE: 'text-green-700',
      RESERVED: 'text-blue-700',
      MAINTENANCE: 'text-orange-700',
      RETIRED: 'text-gray-500',
    };
    return classes[status];
  }

  loadVehicles() {
    this.isLoading.set(true);
    this.vehicleService
      .getVehicles(this.currentPage(), 10, this.selectedStatus() || undefined)
      .subscribe({
        next: (page) => {
          this.page.set(page);
        },
        error: (err) => console.error('Failed to load vehicles', err),
        complete: () => this.isLoading.set(false),
      });
  }

  deleteVehicle(id: string) {
    this.vehicleService.deleteVehicleById(id).subscribe({
      next: () => this.loadVehicles(),
      error: (err) => console.error('Erreur suppression', err),
    });
  }

  openUpdateModal() {
    const v = this.vehicle();
    if (!v) return;
    this.vehicleUpdateForm.patchValue({
      brand: v.brand,
      model: v.model,
      plateNumber: v.plateNumber,
      year: v.year,
      mileageKM: v.mileageKM,
      commissionedAt: v.commissionedAt,
    });
    this.showUpdateModal.set(true);
  }

  onSubmit() {
    const id = this.vehicle()!.id;
    this.vehicleService.updateVehicleById(id, this.vehicleUpdateForm.value).subscribe({
      next: (updated) => {
        this.vehicle.set(updated);
        this.showUpdateModal.set(false);
      },
      error: (err) => console.error('Erreur mise à jour', err),
    });
  }
}
