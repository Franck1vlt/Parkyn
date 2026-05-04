import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';
import { ReservationResponse } from './reservations.service';
import { VehicleResponse } from './vehicles.service';
import { UserResponse } from './users.service';

export interface AgentStat {
  agentName: string;
  count: number;
}

export interface MyStats {
  totalReservations: number;
  activeReservations: number;
  thisMonthReservations: number;
  lastMonthReservations: number;
}

export interface DashboardResponse {
  latestReservations: ReservationResponse[];
  vehiclesInMaintenance: VehicleResponse[];
  latestDrivers: UserResponse[];
  reservationsByAgent: AgentStat[] | null;
  myStats: MyStats | null;
}

@Injectable({
  providedIn: 'root',
})
export class DashboardService {
  private readonly API_URL = `${environment.apiUrl}/dashboard`;

  constructor(private http: HttpClient) {}

  getDashboard(): Observable<DashboardResponse> {
    return this.http.get<DashboardResponse>(this.API_URL);
  }
}
