import { environment } from '../../../environments/environment';

// Miroir de SecurityConfig.PUBLIC_AUTH_PATHS : un 401 y est une réponse métier, jamais de refresh
export const PUBLIC_AUTH_PATHS = [
  '/auth/login',
  '/auth/refresh',
  '/auth/register',
  '/auth/verify-email',
  '/auth/resend-verification',
] as const;

// Base fictive pour résoudre les URL relatives (apiUrl vaut '/api/v1' en production)
const BASE = 'http://localhost';
const PUBLIC_PATHNAMES = new Set(
  PUBLIC_AUTH_PATHS.map((path) => new URL(`${environment.apiUrl}${path}`, BASE).pathname),
);

// Compare le chemin exact (sans la chaîne de requête) à celui des endpoints publics
export function isPublicAuthEndpoint(url: string): boolean {
  return PUBLIC_PATHNAMES.has(new URL(url, BASE).pathname);
}
