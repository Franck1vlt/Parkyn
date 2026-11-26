import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page } from '../models/page.model';

export type VehicleStatus = 'AVAILABLE' | 'RESERVED' | 'MAINTENANCE' | 'RETIRED';

export interface VehicleRequest {
  plateNumber: string;
  brand: string;
  model: string;
  year: number;
  mileageKM: number;
  condition: string;
  commissionedAt: string;
}

export interface VehicleUpdateRequest {
  plateNumber?: string;
  brand?: string;
  model?: string;
  year?: number;
  mileageKM?: number;
  condition?: string;
  commissionedAt?: string;
}

export interface VehicleStatusRequest {
  status: VehicleStatus;
}

export interface VehicleResponse {
  id: string;
  plateNumber: string;
  brand: string;
  model: string;
  year: number;
  mileageKM: number;
  condition: string;
  status: VehicleStatus;
  commissionedAt: string;
  createdAt: string;
}

@Injectable({
  providedIn: 'root',
})
export class VehicleService {
  private readonly API_URL = `${environment.apiUrl}/vehicles`;

  constructor(private http: HttpClient) {}

  getVehicles(
    page: number = 0,
    size: number = 10,
    status?: string,
    context?: HttpContext,
  ): Observable<Page<VehicleResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<Page<VehicleResponse>>(this.API_URL, { params, context });
  }

  addVehicle(request: VehicleRequest, context?: HttpContext): Observable<VehicleResponse> {
    return this.http.post<VehicleResponse>(this.API_URL, request, { context });
  }

  getVehicleById(id: string): Observable<VehicleResponse> {
    return this.http.get<VehicleResponse>(`${this.API_URL}/${id}`);
  }

  updateVehicleById(id: string, request: VehicleUpdateRequest): Observable<VehicleResponse> {
    return this.http.patch<VehicleResponse>(`${this.API_URL}/${id}`, request);
  }

  deleteVehicleById(id: string, context?: HttpContext): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`, { context });
  }

  updateVehicleStatus(id: string, request: VehicleStatusRequest): Observable<VehicleResponse> {
    return this.http.patch<VehicleResponse>(`${this.API_URL}/${id}/status`, request);
  }

  searchVehicles(query: string): Observable<Page<VehicleResponse>> {
    const params = new HttpParams()
      .set('search', query)
      .set('status', 'AVAILABLE')
      .set('size', '10');
    return this.http.get<Page<VehicleResponse>>(this.API_URL, { params });
  }
}
