import { Component } from '@angular/core';
import { HeaderComponent } from '../../../../core/components/header/header.component';
import { ConditionSelectComponent } from '../condition-select/condition-select.component';
import { DriverSelectComponent } from '../driver-select/driver-select.component';

@Component({
  selector: 'app-reservations',
  standalone: true,
  imports: [HeaderComponent, ConditionSelectComponent, DriverSelectComponent],
  templateUrl: './reservations.component.html',
  styleUrl: './reservations.component.css',
})
export class ReservationsComponent {
  onConditionSelected(value: string) {
    console.log('Selected condition:', value);
  }

  onDriverSelected(value: string) {
    console.log('Selected driver:', value);
  }
}
