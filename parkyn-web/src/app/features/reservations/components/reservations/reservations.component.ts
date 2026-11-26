import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, OnInit, Output, signal } from '@angular/core';
import { finalize } from 'rxjs';
import {
  describeHttpError,
  httpErrorContext,
  isHandledHttpError,
} from '../../../../core/errors/http-error-messages';
import { ToastService } from '../../../../core/services/toast.service';
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
  loadFailed = signal(false);
  vehicleNames = signal(new Map<string, string>());
  @Output() onDelete = new EventEmitter<string>();

  constructor(
    private reservationsService: ReservationsService,
    private vehicleService: VehicleService,
    private toastService: ToastService,
  ) {}

  ngOnInit(): void {
    this.load();
    this.vehicleService.getVehicles().subscribe({
      next: (vehicles) => {
        this.vehicleNames.set(
          new Map(vehicles.content.map((v) => [v.id, `${v.brand} ${v.model} (${v.plateNumber})`])),
        );
      },
      // Appel secondaire : s'il échoue, la liste reste affichée
      error: () => {},
    });
  }

  load() {
    this.isLoading.set(true);
    this.reservationsService
      .getReservations(
        this.currentPage(),
        10,
        this.selectedStatus() || undefined,
        httpErrorContext('load-reservations'),
      )
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (page) => {
          this.page.set(page);
          this.loadFailed.set(false);
        },
        error: (err: HttpErrorResponse) => {
          // Page effacée : pas de faux compteur à 0
          this.page.set(null);
          this.currentPage.set(0);
          this.loadFailed.set(true);
          if (isHandledHttpError(err, 'load-reservations')) {
            this.toastService.show(describeHttpError(err, 'load-reservations'));
          }
        },
      });
  }

  setStatus(event: Event) {
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
