import { Component } from '@angular/core';
import { HeaderComponent } from '../../core/components/header/header.component';
import { NgIf } from '@angular/common';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [HeaderComponent],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.css',
})
export class ProfileComponent {}
