import { Component, OnInit, signal } from '@angular/core';
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

  constructor(private vehiclesService: VehicleService) {}

  ngOnInit(): void {
    this.loadVehicles();
  }

  loadVehicles() {
    this.isLoading.set(true);
    this.vehiclesService
      .getVehicles(this.currentPage(), 10, this.selectedStatus() || undefined)
      .subscribe({
        next: (page) => {
          this.page.set(page);
        },
        error: (err) => console.error('Failed to load vehicles', err),
        complete: () => this.isLoading.set(false),
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
    this.vehiclesService.deleteVehicleById(id).subscribe({
      next: () => this.loadVehicles(),
      error: (err) => console.error('Erreur suppression', err),
    });
  }
}
