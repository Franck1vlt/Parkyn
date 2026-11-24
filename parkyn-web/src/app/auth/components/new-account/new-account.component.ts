import { HttpErrorResponse } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { tooManyRequestsMessage } from '../../../core/interceptors/error-handling';
import { parseValidationErrors } from '../../../core/errors/validation-errors';
import { nameFormat, notBlank, passwordMatch } from '../../validators/register-validators';

type FieldName = 'firstname' | 'lastname' | 'email' | 'password' | 'confirmPassword';

// Champs que le serveur peut signaler ; toute autre clé va dans l'alerte du haut
const SERVER_FIELDS: readonly string[] = ['firstname', 'lastname', 'email', 'password'];

const REQUIRED_MESSAGES: Record<FieldName, string> = {
  firstname: 'Le prénom est obligatoire.',
  lastname: 'Le nom est obligatoire.',
  email: "L'adresse email est obligatoire.",
  password: 'Le mot de passe est obligatoire.',
  confirmPassword: 'Confirmez le mot de passe.',
};

const GENERIC_400_MESSAGE =
  "Les informations saisies n'ont pas pu être validées. Veuillez réessayer.";

@Component({
  selector: 'app-new-account',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './new-account.component.html',
  styleUrl: './new-account.component.css',
})
export class NewAccountComponent {
  accountForm: FormGroup;
  isSubmitting = signal(false);
  submitted = signal(false);
  registered = signal(false);
  successEmail = signal('');
  showPassword = signal(false);
  showConfirmPassword = signal(false);
  // Erreurs du serveur par champ, dans un signal pour ne pas bloquer la re-soumission
  serverErrors = signal<Record<string, string[]>>({});
  // Messages de l'alerte du haut : erreurs globales, clés inconnues, 400 illisible, 429
  alertMessages = signal<string[]>([]);

  constructor(
    private fb: FormBuilder,
    private auth: AuthService,
  ) {
    this.accountForm = this.fb.group(
      {
        firstname: ['', [Validators.required, notBlank, Validators.maxLength(50), nameFormat]],
        lastname: ['', [Validators.required, notBlank, Validators.maxLength(50), nameFormat]],
        email: ['', [Validators.required, Validators.maxLength(254), Validators.email]],
        password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
        confirmPassword: ['', Validators.required],
      },
      { validators: passwordMatch },
    );

    // Modifier un champ efface ses erreurs serveur
    for (const field of SERVER_FIELDS) {
      this.accountForm.get(field)!.valueChanges.subscribe(() => this.clearServerErrors(field));
    }
  }

  onSubmit() {
    if (this.isSubmitting()) return;
    this.submitted.set(true);
    if (this.accountForm.invalid) return;

    this.isSubmitting.set(true);
    this.serverErrors.set({});
    this.alertMessages.set([]);

    const { firstname, lastname, email, password } = this.accountForm.value;

    this.auth.register(firstname, lastname, email, password).subscribe({
      next: () => this.onRegistered(email),
      error: (error: HttpErrorResponse) => {
        this.isSubmitting.set(false);
        this.showServerError(error);
      },
    });
  }

  // Messages à afficher sous un champ : erreurs serveur, sinon erreur locale après blur ou soumission
  fieldMessages(field: FieldName): string[] {
    const server = this.serverErrors()[field];
    if (server?.length) return server;

    const control = this.accountForm.get(field)!;
    if (!control.touched && !this.submitted()) return [];

    const local = this.localMessage(field);
    return local ? [local] : [];
  }

  private onRegistered(email: string) {
    this.successEmail.set(email);
    // Les mots de passe ne restent pas dans le formulaire
    this.accountForm.patchValue({ password: '', confirmPassword: '' });
    this.registered.set(true);
    this.isSubmitting.set(false);
  }

  private showServerError(error: HttpErrorResponse) {
    if (error.status === 429) {
      this.alertMessages.set([tooManyRequestsMessage(error)]);
      return;
    }
    // 0 et 5xx : le toast global de l'intercepteur suffit
    if (error.status !== 400) return;

    const parsed = parseValidationErrors(error.error);
    if (!parsed) {
      this.alertMessages.set([GENERIC_400_MESSAGE]);
      return;
    }

    const fields: Record<string, string[]> = {};
    const alertMessages = [...parsed.global];
    for (const [key, messages] of Object.entries(parsed.fields)) {
      if (SERVER_FIELDS.includes(key)) fields[key] = messages;
      else alertMessages.push(...messages);
    }
    this.serverErrors.set(fields);
    this.alertMessages.set(alertMessages);
  }

  private clearServerErrors(field: string) {
    if (!this.serverErrors()[field]) return;
    const remaining = { ...this.serverErrors() };
    delete remaining[field];
    this.serverErrors.set(remaining);
  }

  // Un seul message local par champ : requis, puis longueur, puis format
  private localMessage(field: FieldName): string | null {
    const errors = this.accountForm.get(field)!.errors;

    if (field === 'confirmPassword') {
      if (errors?.['required']) return REQUIRED_MESSAGES[field];
      return this.accountForm.hasError('passwordMismatch')
        ? 'Les mots de passe ne correspondent pas.'
        : null;
    }

    if (!errors) return null;
    if (errors['required'] || errors['blank']) return REQUIRED_MESSAGES[field];
    if (errors['maxlength']) return `${errors['maxlength'].requiredLength} caractères maximum.`;
    if (errors['minlength']) return `${errors['minlength'].requiredLength} caractères minimum.`;
    if (errors['email']) return "L'adresse email n'est pas valide.";
    if (errors['nameFormat']) return 'Contient des caractères non autorisés.';
    return null;
  }
}
