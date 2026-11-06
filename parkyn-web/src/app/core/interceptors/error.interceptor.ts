import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ToastService } from '../services/toast.service';
import { HANDLED_BY_SCREEN, tooManyRequestsMessage } from './error-handling';

export const ErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const toastService = inject(ToastService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      const message = toastMessage(error, req.context.get(HANDLED_BY_SCREEN));
      if (message) toastService.show(message);
      return throwError(() => error);
    }),
  );
};

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
