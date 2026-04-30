import { Routes } from '@angular/router';
import { authRoutes } from './auth/auth.routes';
import { LoginComponent } from './auth/components/login/login.component';
import { RoleGuard } from './core/guards/role.guard';
import { AuthGuard } from './core/guards/auth.guard';
import { ReservationsComponent } from './features/reservations/components/reservations/reservations.component';
import { reservationsRoutes } from './features/reservations/reservations.routes';
import { vehiclesRoutes } from './features/vehicles/vehicles.routes';
import { ProfileComponent } from './features/profile/profile.component';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { usersRoutes } from './features/users/users.routes';

export const routes: Routes = [
  ...usersRoutes,
  ...vehiclesRoutes,
  ...reservationsRoutes,
  ...authRoutes,
  {
    path: 'profile',
    component: ProfileComponent,
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['ADMIN', 'AGENT'] },
  },
  { path: '', component: DashboardComponent, canActivate: [AuthGuard] },
];
