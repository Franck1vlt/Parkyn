import { HttpClient } from '@angular/common/http';
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
  private readonly USER_KEY = 'parkyn_user';
  // Access token en mémoire vive : perdu au rechargement, restauré par le refresh (cookie HttpOnly)
  private readonly accessToken = signal<string | null>(null);
  private refreshInFlight$: Observable<LoginResponse> | null = null;

  private currentUserSubject = new BehaviorSubject<CurrentUser | null>(this.getStoredUser());
  currentUser$ = this.currentUserSubject.asObservable();

  constructor(
    private http: HttpClient,
    private router: Router,
  ) {
    this.http = http;
    this.router = router;
  }

  login(email: String, password: String): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.API_URL}/login`, { email, password })
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
        catchError((error) => {
          this.logout();
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
    this.http
      .post<void>(`${this.API_URL}/logout`, {})
      .pipe(finalize(() => this.clearSession()))
      .subscribe({ error: () => {} });
  }

  currentUserDetails(): Observable<CurrentUser> {
    return this.http.get<CurrentUser>(`${this.API_URL}/me`).pipe(
      tap((user) => {
        localStorage.setItem(this.USER_KEY, JSON.stringify(user));
        this.currentUserSubject.next(user);
      }),
    );
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

  private getStoredUser(): CurrentUser | null {
    const stored = localStorage.getItem(this.USER_KEY);
    return stored ? JSON.parse(stored) : null;
  }

  private clearSession(): void {
    this.clearLocalSession();
    this.router.navigate(['/login']);
  }

  private clearLocalSession(): void {
    this.accessToken.set(null);
    localStorage.removeItem(this.USER_KEY);
    this.currentUserSubject.next(null);
  }
}
