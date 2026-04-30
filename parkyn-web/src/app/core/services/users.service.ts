import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Role } from './auth.service';
import { Page } from '../models/page.model';

export interface UserRequest {
  firstname: string;
  lastname: string;
  email: string;
  password: string;
  role: Role;
  active: boolean;
}

export interface UserUpdateRequest {
  firstname?: string;
  lastname?: string;
  email?: string;
  password?: string;
  role?: Role;
  active?: boolean;
}

export interface UserResponse {
  id: string;
  firstname: string;
  lastname: string;
  email: string;
  role: Role;
  active: boolean;
  createdAt: string;
}

@Injectable({
  providedIn: 'root',
})
export class UsersService {
  private readonly API_URL = `${environment.apiUrl}/users`;

  constructor(private http: HttpClient) {}

  getUsers(page = 0, size = 10, role?: string): Observable<Page<UserResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (role) params = params.set('role', role);
    return this.http.get<Page<UserResponse>>(this.API_URL, { params });
  }

  addUser(request: UserRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(this.API_URL, request);
  }

  getUserById(id: string): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${this.API_URL}/${id}`);
  }

  updateUserById(id: string, request: UserUpdateRequest): Observable<UserResponse> {
    return this.http.patch<UserResponse>(`${this.API_URL}/${id}`, request);
  }

  deleteUserById(id: string): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }

  getDrivers(): Observable<Page<UserResponse>> {
    return this.http.get<Page<UserResponse>>(`${this.API_URL}/drivers`);
  }

  searchDrivers(query: string): Observable<Page<UserResponse>> {
    const params = new HttpParams().set('search', query).set('size', '10');
    return this.http.get<Page<UserResponse>>(`${this.API_URL}/drivers`, { params });
  }

  reactivateUserById(id: string): Observable<UserResponse> {
    return this.http.patch<UserResponse>(`${this.API_URL}/${id}/reactivate`, {});
  }
}
