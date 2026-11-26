import { HttpContext, HttpErrorResponse } from '@angular/common/http';
import { HANDLED_STATUSES } from '../interceptors/error-handling';

export type HttpAction =
  | 'load-vehicles'
  | 'delete-vehicle'
  | 'load-users'
  | 'load-reservations'
  | 'load-dashboard'
  | 'create-vehicle'
  | 'create-user'
  | 'create-reservation';

const GENERIC_MESSAGE = 'Une erreur est survenue';
const GENERIC_400_MESSAGE =
  "Les informations saisies n'ont pas pu être validées. Veuillez réessayer.";

// Source unique : action puis statut puis texte (alimente le message et le contexte HTTP)
const MESSAGES: Record<HttpAction, Readonly<Record<number, string>>> = {
  'load-vehicles': {
    403: "Vous n'avez pas accès à la liste des véhicules.",
  },
  'load-reservations': {
    403: "Vous n'avez pas accès à la liste des réservations.",
  },
  'load-dashboard': {
    403: "Vous n'avez pas accès au tableau de bord.",
  },
  'load-users': {
    403: "Vous n'avez pas accès à la liste des utilisateurs.",
  },
  'create-vehicle': {
    400: GENERIC_400_MESSAGE,
    403: "Vous n'avez pas le droit de créer un véhicule.",
  },
  'create-user': {
    400: GENERIC_400_MESSAGE,
    403: "Vous n'avez pas le droit de créer cet utilisateur.",
    409: 'Cette adresse email est déjà utilisée.',
  },
  'create-reservation': {
    400: GENERIC_400_MESSAGE,
    403: "Vous n'avez pas le droit de créer une réservation.",
    404: "Ce véhicule ou ce conducteur n'existe plus.",
    409: "Ce véhicule n'est pas disponible sur cette période.",
  },
  'delete-vehicle': {
    403: "Vous n'avez pas le droit de supprimer ce véhicule.",
    404: "Ce véhicule n'existe plus.",
    409: 'Ce véhicule a des réservations à venir ou en cours : annulez-les avant de le supprimer.',
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
