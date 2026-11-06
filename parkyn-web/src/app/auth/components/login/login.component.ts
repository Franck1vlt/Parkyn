import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, signal } from '@angular/core';
import { tooManyRequestsMessage } from '../../../core/interceptors/error-handling';
import { AuthService } from '../../../core/services/auth.service';
import { Router } from '@angular/router';
import { ReactiveFormsModule, FormGroup, FormBuilder } from '@angular/forms';
import { switchMap } from 'rxjs';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent implements OnInit {
  loginForm!: FormGroup;
  showPassword = false;
  errorMessage = signal<string | null>(null);

  constructor(
    private auth: AuthService,
    private router: Router,
    private fb: FormBuilder,
  ) {}

  ngOnInit(): void {
    this.loginForm = this.fb.group({ email: [''], password: [''] });
  }

  onLogin() {
    this.errorMessage.set(null);
    this.auth
      .login(this.loginForm.value.email, this.loginForm.value.password)
      .pipe(switchMap(() => this.auth.currentUserDetails()))
      .subscribe({
        next: () => this.router.navigate(['/']),
        error: (err: HttpErrorResponse) => this.errorMessage.set(this.inlineMessage(err)),
      });
  }

  // Seuls 401 et 429 s'affichent ici ; les autres erreurs sont gérées par le toast global
  private inlineMessage(err: HttpErrorResponse): string | null {
    if (err.status === 401) return 'Email ou mot de passe incorrect';
    if (err.status === 429) return tooManyRequestsMessage(err);
    return null;
  }
}
