import { NgFor, NgIf } from '@angular/common';
import { Component, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import { VehicleResponse, VehicleService } from '../../../../core/services/vehicles.service';
import {
  debounceTime,
  distinctUntilChanged,
  filter,
  Subject,
  switchMap,
  takeUntil,
  tap,
} from 'rxjs';

@Component({
  selector: 'app-vehicle-select',
  standalone: true,
  imports: [NgFor, NgIf],
  templateUrl: './vehicle-select.component.html',
  styleUrl: './vehicle-select.component.css',
})
export class VehicleSelectComponent implements OnInit, OnDestroy {
  private searchSubject = new Subject<string>();
  private destroy$ = new Subject<void>();
  selectedVehicleName = '';
  filteredVehicles: VehicleResponse[] = [];
  isLoading = false;

  @Output() vehicleChange = new EventEmitter<string>();
  @Output() vehicleSelected = new EventEmitter<VehicleResponse>();

  constructor(private vehiclesService: VehicleService) {}

  ngOnInit(): void {
    this.searchSubject
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        filter((query) => query.length >= 2),
        tap(() => (this.isLoading = true)),
        switchMap((query) => this.vehiclesService.searchVehicles(query)),
        takeUntil(this.destroy$),
      )
      .subscribe((page) => {
        this.filteredVehicles = page.content;
        this.isLoading = false;
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onSearch(event: Event) {
    const query = (event.target as HTMLInputElement).value;
    this.searchSubject.next(query);
  }

  onSelect(vehicle: VehicleResponse) {
    this.vehicleChange.emit(vehicle.id);
    this.vehicleSelected.emit(vehicle);
    this.selectedVehicleName = `${vehicle.brand} ${vehicle.model} (${vehicle.plateNumber})`;
    this.filteredVehicles = [];
  }
}
