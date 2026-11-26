import { HttpErrorResponse } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import { HeaderComponent } from '../../../../core/components/header/header.component';
import { InlineErrorComponent } from '../../../../core/components/inline-error/inline-error.component';
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
import { ToastService } from '../../../../core/services/toast.service';
import {
  describeHttpError,
  httpErrorContext,
  isHandledHttpError,
} from '../../../../core/errors/http-error-messages';
import {
  parseValidationErrors,
  splitValidationErrors,
} from '../../../../core/errors/validation-errors';

// Champs du formulaire qui ont leur zone d'erreur ; toute autre clé va dans l'alerte du haut
const SERVER_FIELDS: readonly string[] = ['firstname', 'lastname', 'email', 'password', 'role'];

@Component({
  selector: 'app-new-user',
  standalone: true,
  imports: [HeaderComponent, RouterLink, ReactiveFormsModule, InlineErrorComponent],
  templateUrl: './new-user.component.html',
  styleUrl: './new-user.component.css',
})
export class NewUserComponent {
  userForm: FormGroup;
  isSubmitting = false;
  // Erreurs du serveur par champ ; un champ modifié perd les siennes
  serverErrors = signal<Record<string, string[]>>({});
  // Messages de l'alerte du haut : 400 illisible, clés inconnues, erreurs globales
  alertMessages = signal<string[]>([]);
  showPassword = false;
  showConfirmPassword = false;

  constructor(
    private fb: FormBuilder,
    private usersService: UsersService,
    private auth: AuthService,
    private router: Router,
    private toastService: ToastService,
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

    for (const field of SERVER_FIELDS) {
      this.userForm.get(field)!.valueChanges.subscribe(() => this.clearServerErrors(field));
    }
  }

  onSubmit() {
    if (this.userForm.invalid) return;

    this.isSubmitting = true;
    this.serverErrors.set({});
    this.alertMessages.set([]);

    const formValue = this.userForm.value;

    this.usersService
      .addUser(
        {
          firstname: formValue.firstname,
          lastname: formValue.lastname,
          email: formValue.email,
          password: formValue.password,
          role: formValue.role,
          active: true,
        },
        httpErrorContext('create-user'),
      )
      .subscribe({
        next: () => this.router.navigate(['/users']),
        error: (error: HttpErrorResponse) => {
          this.isSubmitting = false;
          this.showServerError(error);
        },
      });
  }

  fieldErrors(field: string): string[] {
    return this.serverErrors()[field] ?? [];
  }

  private showServerError(error: HttpErrorResponse) {
    if (error.status === 400) {
      const { fields, alerts } = splitValidationErrors(
        parseValidationErrors(error.error),
        SERVER_FIELDS,
      );
      this.serverErrors.set(fields);
      // Rien de lisible dans le corps : texte générique du 400
      this.alertMessages.set(
        alerts.length > 0 || Object.keys(fields).length > 0
          ? alerts
          : [describeHttpError(error, 'create-user')],
      );
      return;
    }
    // Email déjà pris : message en ligne sous le champ, pas de toast
    if (error.status === 409) {
      this.serverErrors.set({ email: [describeHttpError(error, 'create-user')] });
      return;
    }
    // Les autres statuts (0, 5xx) restent à l'intercepteur
    if (isHandledHttpError(error, 'create-user')) {
      this.toastService.show(describeHttpError(error, 'create-user'));
    }
  }

  private clearServerErrors(field: string) {
    if (!this.serverErrors()[field]) return;
    const remaining = { ...this.serverErrors() };
    delete remaining[field];
    this.serverErrors.set(remaining);
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
