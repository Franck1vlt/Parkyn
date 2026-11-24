import { HttpErrorResponse } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { tooManyRequestsMessage } from '../../../core/interceptors/error-handling';
import { parseValidationErrors } from '../../../core/errors/validation-errors';

const REQUIRED_MESSAGE = "L'adresse email est obligatoire.";

const GENERIC_400_MESSAGE =
  "Les informations saisies n'ont pas pu être validées. Veuillez réessayer.";

@Component({
  selector: 'app-resend-verification',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './resend-verification.component.html',
  styleUrl: './resend-verification.component.css',
})
export class ResendVerificationComponent {
  resendForm: FormGroup;
  isSubmitting = signal(false);
  submitted = signal(false);
  sent = signal(false);
  successEmail = signal('');
  // Erreurs du serveur sous le champ email, dans un signal pour ne pas bloquer la re-soumission
  serverEmailErrors = signal<string[]>([]);
  // Messages de l'alerte du haut : erreurs globales, clés inconnues, 400 illisible, 429
  alertMessages = signal<string[]>([]);

  constructor(
    private fb: FormBuilder,
    private auth: AuthService,
  ) {
    this.resendForm = this.fb.group({
      email: ['', [Validators.required, Validators.maxLength(254), Validators.email]],
    });

    // Modifier l'email efface ses erreurs serveur
    this.resendForm.get('email')!.valueChanges.subscribe(() => this.serverEmailErrors.set([]));
  }

  onSubmit() {
    if (this.isSubmitting()) return;
    this.submitted.set(true);
    if (this.resendForm.invalid) return;

    this.isSubmitting.set(true);
    this.serverEmailErrors.set([]);
    this.alertMessages.set([]);

    const email: string = this.resendForm.value.email;

    this.auth.resendVerification(email).subscribe({
      next: () => {
        this.successEmail.set(email);
        this.sent.set(true);
        this.isSubmitting.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.isSubmitting.set(false);
        this.showServerError(error);
      },
    });
  }

  // Messages sous le champ : erreurs serveur, sinon erreur locale après blur ou soumission
  emailMessages(): string[] {
    const server = this.serverEmailErrors();
    if (server.length) return server;

    const control = this.resendForm.get('email')!;
    if (!control.touched && !this.submitted()) return [];

    const local = this.localMessage();
    return local ? [local] : [];
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

    const alertMessages = [...parsed.global];
    for (const [key, messages] of Object.entries(parsed.fields)) {
      if (key === 'email') this.serverEmailErrors.set(messages);
      else alertMessages.push(...messages);
    }
    this.alertMessages.set(alertMessages);
  }

  // Un seul message local : requis, puis longueur, puis format
  private localMessage(): string | null {
    const errors = this.resendForm.get('email')!.errors;
    if (!errors) return null;
    if (errors['required']) return REQUIRED_MESSAGE;
    if (errors['maxlength']) return `${errors['maxlength'].requiredLength} caractères maximum.`;
    if (errors['email']) return "L'adresse email n'est pas valide.";
    return null;
  }
}
