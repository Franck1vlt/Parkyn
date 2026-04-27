import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';
import { AuthService, Role } from '../services/auth.service';

@Injectable({
  providedIn: 'root',
})
export class RoleGuard implements CanActivate {
  constructor(
    private auth: AuthService,
    private router: Router,
  ) {}

  canActivate(route: ActivatedRouteSnapshot): boolean {
    const expectedRoles = route.data['roles'] as Role[];
    console.log('expectedRoles:', expectedRoles);
    console.log('userRole:', this.auth.getRole());
    if (this.auth.hasRole(...expectedRoles)) {
      return true;
    }
    this.router.navigate(['/login']);
    return false;
  }
}
