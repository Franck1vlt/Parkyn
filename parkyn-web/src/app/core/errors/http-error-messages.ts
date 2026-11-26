import { HttpContext, HttpErrorResponse } from '@angular/common/http';
import { HANDLED_STATUSES } from '../interceptors/error-handling';

export type HttpAction = 'load-vehicles' | 'delete-vehicle';

const GENERIC_MESSAGE = 'Une erreur est survenue';

// Source unique : action puis statut puis texte (alimente le message et le contexte HTTP)
const MESSAGES: Record<HttpAction, Readonly<Record<number, string>>> = {
  'load-vehicles': {
    403: "Vous n'avez pas accès à la liste des véhicules.",
  },
  'delete-vehicle': {
    403: "Vous n'avez pas le droit de supprimer ce véhicule.",
    404: "Ce véhicule n'existe plus.",
  },
};

// Vrai si l'écran affiche lui-même ce statut pour cette action
export function isHandledHttpError(error: HttpErrorResponse, action: HttpAction): boolean {
  return error.status in MESSAGES[action];
}

// Texte français de l'action pour ce statut, jamais le corps de l'API
export function describeHttpError(error: HttpErrorResponse, action: HttpAction): string {
  return MESSAGES[action][error.status] ?? GENERIC_MESSAGE;
}

// Contexte HTTP : l'intercepteur ne toaste pas les statuts qui ont un texte
export function httpErrorContext(action: HttpAction): HttpContext {
  const statuses = Object.keys(MESSAGES[action]).map(Number);
  return new HttpContext().set(HANDLED_STATUSES, statuses);
}
