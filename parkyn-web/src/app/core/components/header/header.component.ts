import { Component } from '@angular/core';
import { AuthService } from '../../services/auth.service';
import { NgIf } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, NgIf],
  templateUrl: './header.component.html',
  styleUrl: './header.component.css',
})
export class HeaderComponent {
  isProfileOpen = false;
  isMenuOpen = false;

  constructor(private auth: AuthService) {}

  get currentUser() {
    return this.auth.currentUser;
  }

  get initials(): string {
    const user = this.auth.currentUser;
    if (!user) return '';
    return `${user.firstname[0]}${user.lastname[0]}`.toUpperCase();
  }

  toggleProfile() {
    this.isProfileOpen = !this.isProfileOpen;
  }

  toggleMenu() {
    this.isMenuOpen = !this.isMenuOpen;
  }

  logout() {
    this.auth.logout();
  }
}
