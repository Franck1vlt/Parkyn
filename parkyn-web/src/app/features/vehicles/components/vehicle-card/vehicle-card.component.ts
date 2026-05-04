import { Component, EventEmitter, Input, Output } from '@angular/core';
import { VehicleResponse } from '../../../../core/services/vehicles.service';
import { RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';

@Component({
  selector: 'app-vehicle-card',
  standalone: true,
  imports: [RouterLink, DecimalPipe],
  templateUrl: './vehicle-card.component.html',
  styleUrl: './vehicle-card.component.css',
})
export class VehicleCardComponent {
  @Input() vehicle!: VehicleResponse;
  @Output() onDelete = new EventEmitter<string>();

  get statusLabel(): string {
    const labels: Record<string, string> = {
      AVAILABLE: 'Disponible',
      RESERVED: 'Réservé',
      MAINTENANCE: 'Maintenance',
      RETIRED: 'Retiré',
    };
    return labels[this.vehicle.status] ?? this.vehicle.status;
  }

  get statusClass(): string {
    const classes: Record<string, string> = {
      AVAILABLE: 'text-xs font-semibold px-2.5 py-1 rounded-full bg-green-100 text-green-700',
      RESERVED: 'text-xs font-semibold px-2.5 py-1 rounded-full bg-blue-100 text-blue-700',
      MAINTENANCE: 'text-xs font-semibold px-2.5 py-1 rounded-full bg-orange-100 text-orange-700',
      RETIRED: 'text-xs font-semibold px-2.5 py-1 rounded-full bg-gray-100 text-gray-500',
    };
    return classes[this.vehicle.status] ?? '';
  }
}
