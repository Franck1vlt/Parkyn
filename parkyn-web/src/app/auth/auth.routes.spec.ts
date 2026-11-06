import { authRoutes } from './auth.routes';
import { VerifyEmailComponent } from './components/verify-email/verify-email.component';

describe('authRoutes', () => {
  // La page du lien de l'email est publique : aucune garde
  it('exposes verify-email as a public route', () => {
    const route = authRoutes.find((r) => r.path === 'verify-email');

    expect(route).toBeDefined();
    expect(route?.component).toBe(VerifyEmailComponent);
    expect(route?.canActivate).toBeUndefined();
    expect(route?.canActivateChild).toBeUndefined();
  });
});
