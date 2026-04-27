import { ReservationsComponent } from './components/reservations/reservations.component';

export const reservationsRoutes = [
  { path: 'reservations/:id/return', component: ReservationsComponent },
  { path: 'reservations/:id', component: ReservationsComponent },
  { path: 'reservations/new', component: ReservationsComponent },
  { path: 'reservations', component: ReservationsComponent },
];
