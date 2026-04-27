import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, tap } from 'rxjs';
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
  refresh_token: string;
  refresh_token_expired_at: string;
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

  refresh(): Observable<LoginResponse> {
    const refreshToken = this.getRefreshToken();
    return this.http
      .post<LoginResponse>(`${this.API_URL}/refresh`, { refreshToken })
      .pipe(tap((response) => this.storeToken(response)));
  }

  logout(): void {
    this.http.post<LoginResponse>(`${this.API_URL}/logout`, {}).subscribe({
      complete: () => this.clearSession(),
      error: () => this.clearSession(),
    });
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
    return localStorage.getItem(this.ACCESS_TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(this.REFRESH_TOKEN_KEY);
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

  get currentUser(): CurrentUser | null {
    return this.currentUserSubject.value;
  }

  private storeToken(response: LoginResponse): void {
    localStorage.setItem(this.ACCESS_TOKEN_KEY, response.access_token);
    localStorage.setItem(this.REFRESH_TOKEN_KEY, response.refresh_token);
  }

  private getStoredUser(): CurrentUser | null {
    const stored = localStorage.getItem(this.USER_KEY);
    return stored ? JSON.parse(stored) : null;
  }

  private clearSession(): void {
    localStorage.removeItem(this.ACCESS_TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.currentUserSubject.next(null);
    this.router.navigate(['/login']);
  }
}
