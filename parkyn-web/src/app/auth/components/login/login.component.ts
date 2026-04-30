import { Component, inject, OnInit } from '@angular/core';
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

  constructor(
    private auth: AuthService,
    private router: Router,
    private fb: FormBuilder,
  ) {}

  ngOnInit(): void {
    this.loginForm = this.fb.group({ email: [''], password: [''] });
  }

  onLogin() {
    this.auth
      .login(this.loginForm.value.email, this.loginForm.value.password)
      .pipe(switchMap(() => this.auth.currentUserDetails()))
      .subscribe({
        next: () => this.router.navigate(['/']),
        error: (err) => console.log(err),
      });
  }
}
