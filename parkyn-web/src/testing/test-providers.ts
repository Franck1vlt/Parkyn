import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

// Fournisseurs communs aux specs de composants : HTTP simulé (aucune requête réelle) et routeur vide
export const provideTestApp = () => [
  provideHttpClient(),
  provideHttpClientTesting(),
  provideRouter([]),
];
