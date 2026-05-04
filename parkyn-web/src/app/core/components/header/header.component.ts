import { Component, computed, OnInit, signal } from '@angular/core';
import { AuthService } from '../../services/auth.service';
import { DatePipe, NgFor, NgIf } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { NotificationResponse, NotificationService } from '../../services/notifications.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, NgIf, NgFor, DatePipe],
  templateUrl: './header.component.html',
  styleUrl: './header.component.css',
})
export class HeaderComponent implements OnInit {
  isProfileOpen = false;
  isMenuOpen = false;
  notifications = signal<NotificationResponse[]>([]);
  isNotificationsOpen = signal(false);

  constructor(
    private auth: AuthService,
    private notificationService: NotificationService,
  ) {}

  ngOnInit(): void {
    this.loadNotifications();
    setInterval(() => this.loadNotifications(), 120000);
  }

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

  unreadCount = computed(() => this.notifications().filter((n) => !n.isRead).length);

  loadNotifications() {
    this.notificationService.getUserNotifications().subscribe((n) => this.notifications.set(n));
  }

  toggleNotifications() {
    this.isNotificationsOpen.set(!this.isNotificationsOpen());
    if (this.isNotificationsOpen()) {
      this.loadNotifications();
    }
  }

  getNotificationLabel(type: string): string {
    const labels: Record<string, string> = {
      NEW_RESERVATION: 'Nouvelle réservation',
      RESERVATION_ENDING_SOON: 'Réservation bientôt terminée',
      VEHICLE_MAINTENANCE: 'Véhicule en maintenance',
    };
    return labels[type] ?? type;
  }

  markAllAsRead() {
    this.notificationService.markAllAsRead().subscribe(() => {
      this.notifications.update(n => n.map(notif => ({ ...notif, isRead: true })));
    });
  }

  markAsRead(notification: NotificationResponse) {
    if (notification.isRead) return;
    this.notificationService.markAsRead(notification.id).subscribe(() => {
      this.notifications.update((n) =>
        n.map((notif) => (notif.id === notification.id ? { ...notif, isRead: true } : notif)),
      );
    });
  }
}
