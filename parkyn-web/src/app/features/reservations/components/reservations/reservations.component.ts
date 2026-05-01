import { Component, EventEmitter, OnInit, Output, signal } from '@angular/core';
import { HeaderComponent } from '../../../../core/components/header/header.component';
import { RouterLink } from '@angular/router';
import {
  ReservationResponse,
  ReservationsService,
} from '../../../../core/services/reservations.service';
import { Page } from '../../../../core/models/page.model';
import { DatePipe } from '@angular/common';
import { VehicleService } from '../../../../core/services/vehicles.service';

@Component({
  selector: 'app-reservations',
  standalone: true,
  imports: [HeaderComponent, RouterLink, DatePipe],
  templateUrl: './reservations.component.html',
  styleUrl: './reservations.component.css',
})
export class ReservationsComponent implements OnInit {
  page = signal<Page<ReservationResponse> | null>(null);
  currentPage = signal(0);
  selectedStatus = signal<string | null>(null);
  isLoading = signal(false);
  vehicleNames = signal(new Map<string, string>());
  @Output() onDelete = new EventEmitter<string>();

  constructor(
    private reservationsService: ReservationsService,
    private vehicleService: VehicleService,
  ) {}

  ngOnInit(): void {
    this.load();
    this.vehicleService.getVehicles().subscribe((vehicles) => {
      this.vehicleNames.set(
        new Map(vehicles.content.map((v) => [v.id, `${v.brand} ${v.model} (${v.plateNumber})`])),
      );
    });
  }

  load() {
    this.isLoading.set(true);
    this.reservationsService
      .getReservations(this.currentPage(), 10, this.selectedStatus() || undefined)
      .subscribe({
        next: (page) => {
          console.log('Première réservation :', page.content[0]);
          console.log('API response:', page);
          this.page.set(page);
        },
        error: (err) => console.error('Failed to load reservations', err),
        complete: () => this.isLoading.set(false),
      });
  }

  onStatusChange(event: Event) {
    const value = (event.target as HTMLSelectElement).value;
    this.selectedStatus.set(value || null);
    this.currentPage.set(0);
    this.load();
  }

  goTo(page: number) {
    this.currentPage.set(page);
    this.load();
  }

  isReturned(reservation: ReservationResponse): boolean {
    return !!reservation.returnedAt;
  }
}
