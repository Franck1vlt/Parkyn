import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ToastService } from '../services/toast.service';

export const ErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const toastService = inject(ToastService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401) {
        let message = 'Une erreur est survenue';
        if (error.status === 403) message = 'Accès refusé';
        else if (error.status === 404) message = 'Ressource introuvable';
        else if (error.status === 422) message = error.error?.message ?? 'Opération non autorisée';
        else if (error.status === 500) message = 'Erreur serveur interne';
        toastService.show(message);
      }
      return throwError(() => error);
    }),
  );
};
