import { Routes } from '@angular/router';
import { AuthGuard } from '../../core/guards/auth.guard';
import { RoleGuard } from '../../core/guards/role.guard';
import { UsersComponent } from './components/users/users.component';
import { NewUserComponent } from './components/new-user/new-user.component';
import { DetailsUserComponent } from './components/details-user/details-user.component';

export const usersRoutes: Routes = [
  {
    path: 'users/new',
    component: NewUserComponent,
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['ADMIN', 'AGENT'] },
  },
  {
    path: 'users/:id',
    component: DetailsUserComponent,
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['ADMIN', 'AGENT'] },
  },
  {
    path: 'users',
    component: UsersComponent,
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['ADMIN', 'AGENT'] },
  },
];
