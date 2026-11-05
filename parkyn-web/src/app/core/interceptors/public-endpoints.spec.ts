import { environment } from '../../../environments/environment';
import { isPublicAuthEndpoint, PUBLIC_AUTH_PATHS } from './public-endpoints';

describe('isPublicAuthEndpoint', () => {
  const api = environment.apiUrl;

  // Reconnaît chaque endpoint public de l'API
  it.each(PUBLIC_AUTH_PATHS)('recognizes %s', (path) => {
    expect(isPublicAuthEndpoint(`${api}${path}`)).toBe(true);
  });

  // Ignore la chaîne de requête
  it('ignores the query string', () => {
    expect(isPublicAuthEndpoint(`${api}/auth/verify-email?token=abc`)).toBe(true);
  });

  // Les endpoints authentifiés ne sont pas publics, même sous /auth
  it.each(['/auth/me', '/auth/logout', '/auth/sessions', '/users', '/vehicles'])(
    'rejects %s',
    (path) => {
      expect(isPublicAuthEndpoint(`${api}${path}`)).toBe(false);
    },
  );

  // Correspondance exacte du chemin : ni préfixe, ni suffixe, ni autre racine
  it.each([`${api}/auth/login-history`, `${api}/auth/login/extra`, '/other/auth/login'])(
    'rejects the look-alike %s',
    (url) => {
      expect(isPublicAuthEndpoint(url)).toBe(false);
    },
  );
});
