import {
  ApplicationConfig,
  inject,
  LOCALE_ID,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { provideHttpClient, withInterceptors, withInterceptorsFromDi } from '@angular/common/http';
import { AuthInterceptor } from './core/interceptors/auth.interceptor';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { ErrorInterceptor } from './core/interceptors/error.interceptor';
import { AuthService } from './core/services/auth.service';

registerLocaleData(localeFr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([AuthInterceptor, ErrorInterceptor])),
    { provide: LOCALE_ID, useValue: 'fr-FR' },
    // Session restaurée avant la première navigation (les guards sont synchrones)
    provideAppInitializer(() => inject(AuthService).restoreSession()),
  ],
};
