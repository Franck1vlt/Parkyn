import { Routes } from '@angular/router';
import { authRoutes } from './auth/auth.routes';
import { LoginComponent } from './auth/components/login/login.component';
import { RoleGuard } from './core/guards/role.guard';
import { AuthGuard } from './core/guards/auth.guard';
import { ReservationsComponent } from './features/reservations/components/reservations/reservations.component';
import { reservationsRoutes } from './features/reservations/reservations.routes';

export const routes: Routes = [
  ...reservationsRoutes,
  ...authRoutes,
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  {
    path: 'reservations',
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['ADMIN', 'AGENT'] },
    component: ReservationsComponent,
  },
];
