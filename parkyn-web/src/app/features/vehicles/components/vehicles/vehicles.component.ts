import { Component } from '@angular/core';
import { HeaderComponent } from '../../../../core/components/header/header.component';

@Component({
  selector: 'app-vehicles',
  standalone: true,
  imports: [HeaderComponent],
  templateUrl: './vehicles.component.html',
  styleUrl: './vehicles.component.css',
})
export class VehiclesComponent {}
