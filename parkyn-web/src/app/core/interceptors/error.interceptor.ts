import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ToastService } from '../services/toast.service';
import { HANDLED_BY_SCREEN, HANDLED_STATUSES, tooManyRequestsMessage } from './error-handling';

const REQUEST_ID_PATTERN = /^[A-Za-z0-9-]{8,64}$/;

export const ErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const toastService = inject(ToastService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // Statut listé par l'écran : il l'affiche lui-même, pas de toast
      const handled = req.context.get(HANDLED_STATUSES).includes(error.status);
      const message = handled ? null : toastMessage(error, req.context.get(HANDLED_BY_SCREEN));
      if (message) {
        // 429 et statut 0 : jamais de référence (l'appel garde un seul argument)
        const requestId = error.status === 429 || error.status === 0 ? null : requestIdOf(error);
        if (requestId) toastService.show(message, 'error', requestId);
        else toastService.show(message);
      }
      return throwError(() => error);
    }),
  );
};

// Identifiant de requête de l'en-tête, ou null s'il est absent ou de format invalide
function requestIdOf(error: HttpErrorResponse): string | null {
  const id = error.headers?.get('X-Request-Id')?.trim();
  return id && REQUEST_ID_PATTERN.test(id) ? id : null;
}

// Message du toast, ou null si aucun toast ne doit s'afficher
function toastMessage(error: HttpErrorResponse, handledByScreen: boolean): string | null {
  if (error.status === 401) return null;
  if (error.status === 429) return handledByScreen ? null : tooManyRequestsMessage(error);
  if (error.status === 0) return 'Serveur injoignable';
  if (error.status >= 500) return 'Erreur serveur interne';
  if (error.status === 403) return 'Accès refusé';
  if (error.status === 404) return 'Ressource introuvable';
  if (error.status === 422) return error.error?.message ?? 'Opération non autorisée';
  return 'Une erreur est survenue';
}
