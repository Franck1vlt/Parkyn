import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type NotificationType =
  | 'NEW_RESERVATION'
  | 'RESERVATION_ENDING_SOON'
  | 'VEHICLE_MAINTENANCE';

export interface NotificationResponse {
  id: string;
  type: NotificationType;
  isRead: boolean;
  reservationId: string | null;
  createdAt: string;
}

@Injectable({
  providedIn: 'root',
})
export class NotificationService {
  private readonly API_URL = `${environment.apiUrl}/notifications`;

  constructor(private http: HttpClient) {}

  getUserNotifications(): Observable<NotificationResponse[]> {
    return this.http.get<NotificationResponse[]>(this.API_URL);
  }

  markAsRead(id: string): Observable<void> {
    return this.http.patch<void>(`${this.API_URL}/${id}/read`, null);
  }

  markAllAsRead(): Observable<void> {
    return this.http.patch<void>(`${this.API_URL}/read-all`, null);
  }
}
