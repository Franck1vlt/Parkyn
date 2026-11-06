import { HttpContextToken, HttpErrorResponse } from '@angular/common/http';

// Posé par un écran qui affiche lui-même l'erreur : l'intercepteur ne toaste pas
export const HANDLED_BY_SCREEN = new HttpContextToken<boolean>(() => false);

// Statuts qu'un écran affiche lui-même : l'intercepteur ne les toaste pas
export const HANDLED_STATUSES = new HttpContextToken<readonly number[]>(() => []);

// Message d'un 429 : décompte si Retry-After est un entier > 0, sinon repli
export function tooManyRequestsMessage(error: HttpErrorResponse): string {
  const header = error.headers?.get('Retry-After')?.trim();
  const seconds = header && /^\d+$/.test(header) ? Number(header) : 0;
  if (seconds <= 0) return 'Trop de tentatives, réessayez plus tard';
  return `Trop de tentatives, réessayez dans ${seconds} ${seconds === 1 ? 'seconde' : 'secondes'}`;
}
