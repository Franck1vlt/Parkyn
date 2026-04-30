import { Routes } from '@angular/router';
import { DetailsReservationComponent } from './components/details-reservation/details-reservation.component';
import { NewReservationComponent } from './components/new-reservation/new-reservation.component';
import { ReservationsComponent } from './components/reservations/reservations.component';
import { ReturnVehicleComponent } from './components/return-vehicle/return-vehicle.component';
import { AuthGuard } from '../../core/guards/auth.guard';
import { RoleGuard } from '../../core/guards/role.guard';

export const reservationsRoutes: Routes = [
  {
    path: 'reservations/:id/return',
    component: ReturnVehicleComponent,
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['ADMIN', 'AGENT'] },
  },
  {
    path: 'reservations/new',
    component: NewReservationComponent,
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['ADMIN', 'AGENT'] },
  },
  {
    path: 'reservations/:id',
    component: DetailsReservationComponent,
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['ADMIN', 'AGENT'] },
  },
  {
    path: 'reservations',
    component: ReservationsComponent,
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['ADMIN', 'AGENT'] },
  },
];
