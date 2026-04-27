import { Routes } from '@angular/router';
import { VehiclesComponent } from './components/vehicles/vehicles.component';
import { NewVehicleComponent } from './components/new-vehicle/new-vehicle.component';
import { DetailsVehicleComponent } from './components/details-vehicle/details-vehicle.component';
import { AuthGuard } from '../../core/guards/auth.guard';
import { RoleGuard } from '../../core/guards/role.guard';

export const vehiclesRoutes: Routes = [
  {
    path: 'vehicles/:id',
    component: DetailsVehicleComponent,
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['ADMIN', 'AGENT'] },
  },
  {
    path: 'vehicles/new',
    component: NewVehicleComponent,
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['ADMIN', 'AGENT'] },
  },
  {
    path: 'vehicles',
    component: VehiclesComponent,
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['ADMIN', 'AGENT'] },
  },
];
