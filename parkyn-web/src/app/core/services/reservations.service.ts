import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../models/page.model';

export interface ReservationRequest {
  id: string;
  vehicleId: string;
  driverId: string;
  driverFirstName: string;
  driverLastName: string;
  driverEmail: string;
  startDate: string;
  endDate: string;
  conditionBefore: string;
  mileageStart: number;
  notesBefore: string;
}

export interface ReturnVehicleRequest {
  conditionAfter: string;
  mileageEnd: number;
  damageReported: boolean;
  damageDetails: string;
  notesAfter: string;
}

export interface ReservationResponse {
  id: string;
  vehicleId: string;
  driverId: string;
  agentId: string;
  startDate: string;
  endDate: string;
  returnedAt: string;
  conditionBefore: string;
  mileageStart: number;
  notesBefore: string;
  conditionAfter: string;
  mileageEnd: number;
  damageReported: boolean;
  damageDetails: string;
  notesAfter: string;
}

@Injectable({
  providedIn: 'root',
})
export class ReservationsService {
  private readonly API_URL = `${environment.apiUrl}/reservations`;

  constructor(private http: HttpClient) {}

  getReservations(): Observable<Page<ReservationResponse>> {
    return this.http.get<Page<ReservationResponse>>(this.API_URL);
  }

  addReservation(request: ReservationRequest): Observable<ReservationResponse> {
    return this.http.post<ReservationResponse>(this.API_URL, request);
  }

  getReservationById(id: string): Observable<ReservationResponse> {
    return this.http.get<ReservationResponse>(`${this.API_URL}/${id}`);
  }

  returnVehicle(id: string, request: ReturnVehicleRequest): Observable<ReservationResponse> {
    return this.http.patch<ReservationResponse>(`${this.API_URL}/${id}/return`, request);
  }
}
