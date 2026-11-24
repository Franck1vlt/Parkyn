import { authRoutes } from './auth.routes';
import { ResendVerificationComponent } from './components/resend-verification/resend-verification.component';
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

  // La page de renvoi du lien est publique : aucune garde
  it('exposes resend-verification as a public route', () => {
    const route = authRoutes.find((r) => r.path === 'resend-verification');

    expect(route).toBeDefined();
    expect(route?.component).toBe(ResendVerificationComponent);
    expect(route?.canActivate).toBeUndefined();
    expect(route?.canActivateChild).toBeUndefined();
  });
});
