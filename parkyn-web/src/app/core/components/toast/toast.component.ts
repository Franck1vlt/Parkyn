import { Component, computed, inject } from '@angular/core';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [],
  templateUrl: './toast.component.html',
  styleUrl: './toast.component.css',
})
export class ToastComponent {
  toastService = inject(ToastService);

  readonly isStack = computed(() => this.toastService.toasts().length >= 2);
  readonly pendingLabel = computed(() => {
    const n = this.toastService.pendingCount();
    return `+${n} ${n === 1 ? 'nouveau' : 'nouveaux'}`;
  });
  // Replié : seulement le dernier toast ; déplié : la liste complète
  readonly visibleToasts = computed(() => {
    const all = this.toastService.toasts();
    return this.toastService.expanded() ? all : all.slice(-1);
  });
}
