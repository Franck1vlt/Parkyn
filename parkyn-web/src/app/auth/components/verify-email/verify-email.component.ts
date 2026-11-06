import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

type VerifyState = 'loading' | 'success' | 'invalid' | 'expired' | 'error';

@Component({
  selector: 'app-verify-email',
  imports: [RouterLink],
  templateUrl: './verify-email.component.html',
  styleUrl: './verify-email.component.css',
})
export class VerifyEmailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  // Un seul état à la fois : le template n'affiche que le bloc correspondant
  readonly state = signal<VerifyState>('loading');
  // Gardé en mémoire pour « Réessayer » : l'URL, elle, n'en porte plus
  private token = '';

  ngOnInit(): void {
    const rawToken = this.route.snapshot.queryParamMap.get('token');
    // Lecture unique du snapshot : le retrait du paramètre ne relance pas la vérification
    if (rawToken !== null) this.stripTokenFromUrl();

    if (!rawToken || rawToken.trim() === '') {
      this.state.set('invalid');
      return;
    }
    this.token = rawToken;
    this.verify();
  }

  retry(): void {
    this.verify();
  }

  private verify(): void {
    this.state.set('loading');
    this.authService
      .verifyEmail(this.token)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        complete: () => this.state.set('success'),
        error: (error: HttpErrorResponse) => this.state.set(this.stateFor(error.status)),
      });
  }

  private stateFor(status: number): VerifyState {
    if (status === 400) return 'invalid';
    if (status === 410) return 'expired';
    return 'error';
  }

  // Le jeton ne doit pas rester dans la barre d'adresse ni l'historique
  private stripTokenFromUrl(): void {
    this.router.navigate([], {
      replaceUrl: true,
      queryParams: { token: null },
      queryParamsHandling: 'merge',
    });
  }
}
