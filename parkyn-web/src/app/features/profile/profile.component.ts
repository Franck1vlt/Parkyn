import { Component } from '@angular/core';
import { HeaderComponent } from '../../core/components/header/header.component';
import { NgClass, NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [HeaderComponent, RouterLink, NgClass],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.css',
})
export class ProfileComponent {
  constructor(private auth: AuthService) {}

  get initials(): string {
    const user = this.auth.currentUser;
    if (!user) return '';
    return `${user.firstname[0]}${user.lastname[0]}`.toUpperCase();
  }

  get userName(): string {
    const user = this.auth.currentUser;
    if (!user) return '';
    return `${user.firstname} ${user.lastname}`;
  }

  get userRole(): string {
    const role = this.auth.getRole();
    if (!role) return '';
    return `${role}`.toUpperCase();
  }

  get userEmail(): string {
    const email = this.auth.getEmail();
    if (!email) return '';
    return `${email}`.toLowerCase();
  }

  get roleAccentColor(): string {
    const colors: Record<string, string> = {
      ADMIN: 'bg-red-600',
      AGENT: 'bg-blue-600',
      DRIVER: 'bg-green-600',
    };
    return colors[this.userRole] ?? 'bg-gray-600';
  }

  get roleBadgeColor(): string {
    const colors: Record<string, string> = {
      ADMIN: 'bg-red-100 text-red-700 border-red-300',
      AGENT: 'bg-blue-100 text-blue-700 border-blue-300',
      DRIVER: 'bg-green-100 text-green-700 border-green-300',
    };
    return colors[this.userRole] ?? 'bg-gray-100 text-gray-700 border-gray-300';
  }

  get permissions() {
    const role = this.auth.getRole();
    return [
      {
        label: 'Gestion des véhicules',
        description: 'Consulter, ajouter, modifier, supprimer',
        allowed: role === 'ADMIN' || role === 'AGENT',
      },
      {
        label: 'Gestion des réservations',
        description: 'Créer et clôturer des réservations',
        allowed: role === 'ADMIN' || role === 'AGENT',
      },
      {
        label: 'Gestion des utilisateurs',
        description: 'Créer et gérer les utilisateurs',
        allowed: role === 'ADMIN',
      },
      {
        label: 'Gestion des conducteurs',
        description: 'Créer et modifier les conducteurs',
        allowed: role === 'ADMIN' || role === 'AGENT',
      },
      {
        label: 'Réactiver les utilisateurs',
        description: "Réactiver les comptes d'utilisateurs désactivés",
        allowed: role === 'ADMIN',
      },
      {
        label: 'Administration',
        description: 'Accès complet à toutes les fonctionnalités',
        allowed: role === 'ADMIN',
      },
    ];
  }
}
