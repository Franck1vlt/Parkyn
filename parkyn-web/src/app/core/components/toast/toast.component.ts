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
  // Replié : seulement le dernier toast ; déplié : la liste complète
  readonly visibleToasts = computed(() => {
    const all = this.toastService.toasts();
    return this.toastService.expanded() ? all : all.slice(-1);
  });

  // Évalué au rendu : le presse-papiers n'existe pas partout (contexte non sécurisé)
  canCopy(): boolean {
    return typeof navigator.clipboard?.writeText === 'function';
  }

  // Copie l'identifiant nu ; confirmation seulement si l'écriture réussit, échec silencieux
  copyReference(requestId: string): void {
    try {
      navigator.clipboard.writeText(requestId).then(
        () => this.toastService.showConfirmation('Référence copiée'),
        () => undefined,
      );
    } catch {
      // Exception synchrone de writeText : rien à faire
    }
  }
}
