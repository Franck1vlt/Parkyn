import { Component, computed, OnInit, signal } from '@angular/core';
import { HeaderComponent } from '../../core/components/header/header.component';
import { DashboardResponse, DashboardService } from '../../core/services/dashboard.service';
import { AuthService } from '../../core/services/auth.service';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  describeHttpError,
  httpErrorContext,
  isHandledHttpError,
} from '../../core/errors/http-error-messages';
import { ToastService } from '../../core/services/toast.service';
import { VehicleService } from '../../core/services/vehicles.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [HeaderComponent, DatePipe, DecimalPipe],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
})
export class DashboardComponent implements OnInit {
  dashboard = signal<DashboardResponse | undefined>(undefined);
  isAdmin = computed(() => this.auth.currentUser?.role === 'ADMIN');
  vehicleNames = signal(new Map<string, string>());
  loadFailed = signal(false);

  constructor(
    private dashboardService: DashboardService,
    private auth: AuthService,
    private vehicleService: VehicleService,
    private toastService: ToastService,
  ) {}

  ngOnInit(): void {
    this.dashboardService.getDashboard(httpErrorContext('load-dashboard')).subscribe({
      next: (d) => {
        this.dashboard.set(d);
        this.loadFailed.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loadFailed.set(true);
        if (isHandledHttpError(err, 'load-dashboard')) {
          this.toastService.show(describeHttpError(err, 'load-dashboard'));
        }
      },
    });
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
    const classes: Record<string, string> = {
      EXCELLENT: 'text-green-600',
      GOOD: 'text-blue-600',
      FAIR: 'text-orange-500',
      POOR: 'text-red-500',
    };
    return condition ? (classes[condition] ?? '') : '';
  }

  getTrend(): { percent: number; up: boolean } | null {
    const stats = this.dashboard()?.myStats;
    if (!stats || stats.lastMonthReservations === 0) return null;
    const diff = stats.thisMonthReservations - stats.lastMonthReservations;
    const percent = Math.round((Math.abs(diff) / stats.lastMonthReservations) * 100);
    return { percent, up: diff >= 0 };
  }
}
