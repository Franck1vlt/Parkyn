import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

// Lettres Unicode, espace, apostrophes droite et typographique, tiret
const ALLOWED_NAME_CHARS = /^[\p{L}\p{M} '’-]+$/u;
const HAS_LETTER = /\p{L}/u;

function isEmpty(value: unknown): boolean {
  return value === null || value === undefined || value === '';
}

// Refuse une valeur vide ou faite d'espaces seuls
export const notBlank: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  isEmpty(control.value) || String(control.value).trim() === '' ? { blank: true } : null;

// Refuse les caractères hors lettres, espace, apostrophe, tiret ; la valeur vide est laissée à required
export const nameFormat: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = control.value;
  if (isEmpty(value) || typeof value !== 'string') return null;
  return ALLOWED_NAME_CHARS.test(value) && HAS_LETTER.test(value) ? null : { nameFormat: true };
};

// Validateur de groupe : mot de passe et confirmation identiques ; deux champs vides laissés à required
export const passwordMatch: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const password = group.get('password')?.value;
  const confirmation = group.get('confirmPassword')?.value;
  if (isEmpty(password) && isEmpty(confirmation)) return null;
  return password === confirmation ? null : { passwordMismatch: true };
};
