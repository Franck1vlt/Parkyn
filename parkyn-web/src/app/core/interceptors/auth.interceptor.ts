import { HttpInterceptor, HttpInterceptorFn } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { catchError, switchMap, throwError } from 'rxjs';

export const AuthInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const accessToken = authService.getAccessToken();

  if (accessToken) {
    const authReq = req.clone({
      headers: req.headers.set('Authorization', `Bearer ${accessToken}`),
    });
    return next(authReq).pipe(
      catchError((error) => {
        if (
          error.status === 401 &&
          !req.url.includes('/auth/refresh') &&
          !req.url.includes('/auth/logout')
        ) {
          return authService.refresh().pipe(
            switchMap((response) => {
              const retryReq = req.clone({
                headers: req.headers.set('Authorization', `Bearer ${response.access_token}`),
              });
              return next(retryReq);
            }),
            catchError(() => {
              authService.logout();
              return throwError(() => error);
            }),
          );
        }

        if (error.status === 401) {
          authService.logout();
        }
        return throwError(() => error);
      }),
    );
  }
  return next(req);
};
