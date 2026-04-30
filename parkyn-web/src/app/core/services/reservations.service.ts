import { HttpClient, HttpParams } from '@angular/common/http';
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
  driverFirstName: string;
  driverLastName: string;
  agentId: string;
  startDate: string;
  endDate: string;
  returnedAt: string | null;
  conditionBefore: string;
  mileageStart: number;
  notesBefore: string | null;
  conditionAfter: string | null;
  mileageEnd: number | null;
  damageReported: boolean | null;
  damageDetails: string | null;
  notesAfter: string | null;
}

@Injectable({
  providedIn: 'root',
})
export class ReservationsService {
  private readonly API_URL = `${environment.apiUrl}/reservations`;

  constructor(private http: HttpClient) {}

  getReservations(
    page: number = 0,
    size: number = 10,
    status?: string,
  ): Observable<Page<ReservationResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<Page<ReservationResponse>>(this.API_URL, { params });
  }

  addReservation(request: ReservationRequest): Observable<ReservationResponse> {
    return this.http.post<ReservationResponse>(this.API_URL, request);
  }

  getReservationById(id: string): Observable<ReservationResponse> {
    return this.http.get<ReservationResponse>(`${this.API_URL}/${id}`);
  }

  getReservationsByVehicle(vehicleId: string, page = 0, size = 10): Observable<Page<ReservationResponse>> {
    const params = new HttpParams().set('vehicleId', vehicleId).set('page', page).set('size', size);
    return this.http.get<Page<ReservationResponse>>(this.API_URL, { params });
  }

  returnVehicle(id: string, request: ReturnVehicleRequest): Observable<ReservationResponse> {
    return this.http.patch<ReservationResponse>(`${this.API_URL}/${id}/return`, request);
  }
}
