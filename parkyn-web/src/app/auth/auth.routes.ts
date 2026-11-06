import { Routes } from '@angular/router';
import { LoginComponent } from './components/login/login.component';
import { NewAccountComponent } from './components/new-account/new-account.component';
import { VerifyEmailComponent } from './components/verify-email/verify-email.component';

export const authRoutes: Routes = [
  { path: 'register', component: NewAccountComponent },
  { path: 'login', component: LoginComponent },
  { path: 'verify-email', component: VerifyEmailComponent },
];
