import { Component } from '@angular/core';
import { HeaderComponent } from '../../../../core/components/header/header.component';
import { Router, RouterLink } from '@angular/router';
import {
  AbstractControl,
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { UsersService } from '../../../../core/services/users.service';
import { AuthService } from '../../../../core/services/auth.service';
import { JsonPipe } from '@angular/common';

@Component({
  selector: 'app-new-user',
  standalone: true,
  imports: [HeaderComponent, RouterLink, ReactiveFormsModule, JsonPipe],
  templateUrl: './new-user.component.html',
  styleUrl: './new-user.component.css',
})
export class NewUserComponent {
  userForm: FormGroup;
  isSubmitting = false;
  errorMessage = '';
  showPassword = false;
  showConfirmPassword = false;

  constructor(
    private fb: FormBuilder,
    private usersService: UsersService,
    private auth: AuthService,
    private router: Router,
  ) {
    this.userForm = this.fb.group(
      {
        firstname: ['', Validators.required],
        lastname: ['', Validators.required],
        email: ['', [Validators.required, Validators.email]],
        password: ['', Validators.required],
        confirmPassword: ['', Validators.required],
        role: ['', Validators.required],
      },
      { validators: this.passwordMatchValidator },
    );

    if (!this.isAdmin) {
      this.userForm.get('role')?.setValue('DRIVER');
    }
  }

  onSubmit() {
    if (this.userForm.invalid) return;

    this.isSubmitting = true;
    this.errorMessage = '';

    const formValue = this.userForm.value;

    this.usersService
      .addUser({
        firstname: formValue.firstname,
        lastname: formValue.lastname,
        email: formValue.email,
        password: formValue.password,
        role: formValue.role,
        active: true,
      })
      .subscribe({
        next: () => this.router.navigate(['/users']),
        error: () => {
          this.errorMessage = 'Une erreur est survenue. Veuillez réessayer.';
          this.isSubmitting = false;
        },
      });
  }

  passwordMatchValidator(form: AbstractControl) {
    const password = form.get('password')?.value;
    const confirm = form.get('confirmPassword')?.value;
    return password === confirm ? null : { passwordMismatch: true };
  }

  get isAdmin(): boolean {
    return this.auth.hasRole('ADMIN');
  }
}
