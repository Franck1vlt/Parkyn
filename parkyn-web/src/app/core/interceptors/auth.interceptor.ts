import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { catchError, map, of, switchMap, throwError } from 'rxjs';
import { isPublicAuthEndpoint } from './public-endpoints';

export const AuthInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const accessToken = authService.getAccessToken();

  if (accessToken) {
    const authReq = req.clone({
      headers: req.headers.set('Authorization', `Bearer ${accessToken}`),
    });
    return next(authReq).pipe(
      catchError((error) => {
        if (error.status === 401 && !isPublicAuthEndpoint(req.url)) {
          const currentToken = authService.getAccessToken();
          const token$ =
            currentToken && currentToken !== accessToken
              ? of(currentToken)
              : authService.refresh().pipe(
                  map((response) => response.access_token),
                  // Refresh refusé : l'appelant reçoit le 401 d'origine
                  catchError(() => throwError(() => error)),
                );

          // L'erreur de la requête rejouée (409, 404…) remonte telle quelle
          return token$.pipe(
            switchMap((token) =>
              next(
                req.clone({
                  headers: req.headers.set('Authorization', `Bearer ${token}`),
                }),
              ),
            ),
          );
        }
        return throwError(() => error);
      }),
    );
  }
  return next(req);
};
