import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  BehaviorSubject,
  catchError,
  finalize,
  firstValueFrom,
  map,
  Observable,
  of,
  shareReplay,
  switchMap,
  tap,
  throwError,
} from 'rxjs';
import { environment } from '../../../environments/environment';
import { HANDLED_BY_SCREEN } from '../interceptors/error-handling';

export type Role = 'ADMIN' | 'AGENT' | 'DRIVER';

export interface CurrentUser {
  id: string;
  firstname: string;
  lastname: string;
  email: string;
  role: Role;
}

export interface LoginResponse {
  access_token: string;
  expired_at: string;
  refresh_expired_at: string;
  token_type: string;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly API_URL = `${environment.apiUrl}/auth`;
  private readonly ACCESS_TOKEN_KEY = 'parkyn_access_token';
  private readonly REFRESH_TOKEN_KEY = 'parkyn_refresh_token';
  private readonly USER_KEY = 'parkyn_user';
  // Access token en mémoire vive : perdu au rechargement, restauré par le refresh (cookie HttpOnly)
  private readonly accessToken = signal<string | null>(null);
  private refreshInFlight$: Observable<LoginResponse> | null = null;

  // Utilisateur en mémoire vive : chargé uniquement par GET /auth/me
  private currentUserSubject = new BehaviorSubject<CurrentUser | null>(null);
  currentUser$ = this.currentUserSubject.asObservable();

  constructor(
    private http: HttpClient,
    private router: Router,
  ) {
    this.http = http;
    this.router = router;
    this.removeLegacyStorageKeys();
  }

  login(email: String, password: String): Observable<LoginResponse> {
    // L'écran de login affiche lui-même les erreurs 401/429 : pas de toast
    const context = new HttpContext().set(HANDLED_BY_SCREEN, true);
    return this.http
      .post<LoginResponse>(`${this.API_URL}/login`, { email, password }, { context })
      .pipe(tap((response) => this.storeToken(response)));
  }

  register(firstname: string, lastname: string, email: string, password: string): Observable<void> {
    return this.http.post<void>(`${this.API_URL}/register`, {
      firstname,
      lastname,
      email,
      password,
    });
  }

  refresh(): Observable<LoginResponse> {
    if (!this.refreshInFlight$) {
      this.refreshInFlight$ = this.http.post<LoginResponse>(`${this.API_URL}/refresh`, {}).pipe(
        tap((response) => this.storeToken(response)),
        // Refresh refusé : la session est déjà morte côté serveur, pas de logout à envoyer
        catchError((error) => {
          this.clearSession();
          return throwError(() => error);
        }),
        finalize(() => {
          this.refreshInFlight$ = null;
        }),
        shareReplay(1),
      );
    }
    return this.refreshInFlight$;
  }

  // Au démarrage : retrouve la session depuis le cookie ; échec = déconnecté, sans logout ni redirection
  restoreSession(): Promise<void> {
    return firstValueFrom(
      this.http.post<LoginResponse>(`${this.API_URL}/refresh`, {}).pipe(
        tap((response) => this.storeToken(response)),
        switchMap(() => this.currentUserDetails()),
        map(() => undefined),
        catchError(() => {
          this.clearLocalSession();
          return of(undefined);
        }),
      ),
    );
  }

  logout(): void {
    // Sans session, rien à révoquer côté serveur
    if (!this.hasSession()) {
      this.router.navigate(['/login']);
      return;
    }
    this.http
      .post<void>(`${this.API_URL}/logout`, {})
      // Un refresh refusé pendant le logout a déjà effacé la session et redirigé
      .pipe(
        finalize(() => {
          if (this.hasSession()) this.clearSession();
        }),
      )
      .subscribe({ error: () => {} });
  }

  currentUserDetails(): Observable<CurrentUser> {
    return this.http
      .get<CurrentUser>(`${this.API_URL}/me`)
      .pipe(tap((user) => this.currentUserSubject.next(user)));
  }

  getAccessToken(): string | null {
    return this.accessToken();
  }

  isAuthenticated(): boolean {
    return !!this.getAccessToken();
  }

  getRole(): Role | null {
    return this.currentUserSubject.value?.role ?? null;
  }

  hasRole(...roles: Role[]): boolean {
    const role = this.getRole();
    return role !== null && roles.includes(role);
  }

  getEmail(): string | null {
    return this.currentUserSubject.value?.email ?? null;
  }

  get currentUser(): CurrentUser | null {
    return this.currentUserSubject.value;
  }

  private storeToken(response: LoginResponse): void {
    this.accessToken.set(response.access_token);
  }

  // Expand and Contract (transition) : à retirer quand plus aucun navigateur n'a ces anciennes clés
  private removeLegacyStorageKeys(): void {
    [this.ACCESS_TOKEN_KEY, this.REFRESH_TOKEN_KEY, this.USER_KEY].forEach((key) =>
      localStorage.removeItem(key),
    );
  }

  private hasSession(): boolean {
    return this.accessToken() !== null || this.currentUserSubject.value !== null;
  }

  // Efface la session locale et redirige vers /login, sans condition
  private clearSession(): void {
    this.clearLocalSession();
    this.router.navigate(['/login']);
  }

  private clearLocalSession(): void {
    this.accessToken.set(null);
    this.currentUserSubject.next(null);
  }
}
