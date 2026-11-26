import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, signal } from '@angular/core';
import { finalize } from 'rxjs';
import {
  describeHttpError,
  httpErrorContext,
  isHandledHttpError,
} from '../../../../core/errors/http-error-messages';
import { ToastService } from '../../../../core/services/toast.service';
import { HeaderComponent } from '../../../../core/components/header/header.component';
import { Page } from '../../../../core/models/page.model';
import {
  VehicleResponse,
  VehicleService,
  VehicleStatus,
} from '../../../../core/services/vehicles.service';
import { RouterLink } from '@angular/router';
import { VehicleCardComponent } from '../vehicle-card/vehicle-card.component';

@Component({
  selector: 'app-vehicles',
  standalone: true,
  imports: [HeaderComponent, VehicleCardComponent, RouterLink],
  templateUrl: './vehicles.component.html',
  styleUrl: './vehicles.component.css',
})
export class VehiclesComponent implements OnInit {
  page = signal<Page<VehicleResponse> | undefined>(undefined);
  currentPage = signal(0);
  selectedStatus = signal<string | null>(null);
  isLoading = signal(false);
  loadFailed = signal(false);

  constructor(
    private vehiclesService: VehicleService,
    private toastService: ToastService,
  ) {}

  ngOnInit(): void {
    this.loadVehicles();
  }

  loadVehicles() {
    this.isLoading.set(true);
    this.vehiclesService
      .getVehicles(
        this.currentPage(),
        10,
        this.selectedStatus() || undefined,
        httpErrorContext('load-vehicles'),
      )
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (page) => {
          this.page.set(page);
          this.loadFailed.set(false);
        },
        error: (err: HttpErrorResponse) => {
          // Page effacée : pas de faux compteur à 0
          this.page.set(undefined);
          this.currentPage.set(0);
          this.loadFailed.set(true);
          if (isHandledHttpError(err, 'load-vehicles')) {
            this.toastService.show(describeHttpError(err, 'load-vehicles'));
          }
        },
      });
  }

  onStatusChange(event: Event) {
    const value = (event.target as HTMLSelectElement).value;
    this.selectedStatus.set(value || null);
    this.currentPage.set(0);
    this.loadVehicles();
  }

  goTo(page: number) {
    this.currentPage.set(page);
    this.loadVehicles();
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
      AVAILABLE: 'bg-green-100 text-green-700',
      RESERVED: 'bg-blue-100 text-blue-700',
      MAINTENANCE: 'bg-orange-100 text-orange-700',
      RETIRED: 'bg-gray-100 text-gray-500',
    };
    return classes[status];
  }

  deleteVehicle(id: string) {
    this.vehiclesService.deleteVehicleById(id, httpErrorContext('delete-vehicle')).subscribe({
      next: () => this.loadVehicles(),
      error: (err: HttpErrorResponse) => {
        // Les autres statuts restent à l'intercepteur
        if (!isHandledHttpError(err, 'delete-vehicle')) return;
        this.toastService.show(describeHttpError(err, 'delete-vehicle'));
        // Véhicule disparu : on rafraîchit la liste
        if (err.status === 404) this.loadVehicles();
      },
    });
  }
}
