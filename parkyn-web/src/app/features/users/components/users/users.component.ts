import { Component } from '@angular/core';
import { HeaderComponent } from '../../../../core/components/header/header.component';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [HeaderComponent],
  templateUrl: './users.component.html',
  styleUrl: './users.component.css',
})
export class UsersComponent {}
