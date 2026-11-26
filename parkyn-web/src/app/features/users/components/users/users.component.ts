import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, OnInit, Output, signal } from '@angular/core';
import { finalize } from 'rxjs';
import {
  describeHttpError,
  httpErrorContext,
  isHandledHttpError,
} from '../../../../core/errors/http-error-messages';
import { ToastService } from '../../../../core/services/toast.service';
import { HeaderComponent } from '../../../../core/components/header/header.component';
import { UserResponse, UsersService } from '../../../../core/services/users.service';
import { Page } from '../../../../core/models/page.model';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../../core/services/auth.service';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [HeaderComponent, RouterLink],
  templateUrl: './users.component.html',
  styleUrl: './users.component.css',
})
export class UsersComponent implements OnInit {
  page = signal<Page<UserResponse> | undefined>(undefined);
  currentPage = signal(0);
  selectedRole = signal<string | null>(null);
  isLoading = signal(false);
  loadFailed = signal(false);
  @Output() onDelete = new EventEmitter<string>();

  constructor(
    private usersService: UsersService,
    private auth: AuthService,
    private toastService: ToastService,
  ) {}

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers() {
    this.isLoading.set(true);

    const context = httpErrorContext('load-users');
    const request$ = this.auth.hasRole('ADMIN')
      ? this.usersService.getUsers(
          this.currentPage(),
          10,
          this.selectedRole() || undefined,
          context,
        )
      : this.usersService.getDrivers(context);

    request$.pipe(finalize(() => this.isLoading.set(false))).subscribe({
      next: (page) => {
        this.page.set(page);
        this.loadFailed.set(false);
      },
      error: (err: HttpErrorResponse) => {
        // Page effacée : pas de faux compteur à 0
        this.page.set(undefined);
        this.currentPage.set(0);
        this.loadFailed.set(true);
        if (isHandledHttpError(err, 'load-users')) {
          this.toastService.show(describeHttpError(err, 'load-users'));
        }
      },
    });
  }

  onStatusChange(event: Event) {
    const value = (event.target as HTMLSelectElement).value;
    this.selectedRole.set(value || null);
    this.currentPage.set(0);
    this.loadUsers();
  }

  goTo(page: number) {
    this.currentPage.set(page);
    this.loadUsers();
  }

  getRoleLabel(role: string): string {
    const labels: Record<string, string> = {
      ADMIN: 'Admin',
      AGENT: 'Agent',
      DRIVER: 'Conducteur',
    };
    return labels[role] ?? role;
  }

  getRoleBadge(role: string): string {
    const classes: Record<string, string> = {
      ADMIN: 'bg-red-100 text-red-700 text-xs font-semibold px-2.5 py-1 rounded-full',
      AGENT: 'bg-blue-100 text-blue-700 text-xs font-semibold px-2.5 py-1 rounded-full',
      DRIVER: 'bg-green-100 text-green-700 text-xs font-semibold px-2.5 py-1 rounded-full',
    };
    return classes[role] ?? '';
  }

  get isAdmin(): boolean {
    return this.auth.hasRole('ADMIN');
  }
}
