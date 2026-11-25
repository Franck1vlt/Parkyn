import { DestroyRef, inject, Injectable, Signal, signal } from '@angular/core';

export type ToastType = 'error' | 'success';

export interface ToastItem {
  id: number;
  message: string;
  type: ToastType;
  count: number;
  requestId?: string;
}

const DURATION_MS: Record<ToastType, number> = { error: 30_000, success: 10_000 };
const MAX_TOASTS = 10;
const CONFIRMATION_DURATION_MS = 2_000;

@Injectable({
  providedIn: 'root',
})
export class ToastService {
  private readonly displayed = signal<readonly ToastItem[]>([]);
  private readonly isExpanded = signal(false);
  private readonly confirmationMessage = signal<string | null>(null);

  readonly toasts = this.displayed.asReadonly();
  readonly expanded = this.isExpanded.asReadonly();
  readonly confirmation: Signal<string | null> = this.confirmationMessage.asReadonly();

  private nextId = 1;
  // Timer de la confirmation, hors des maps de la pile
  private confirmationTimer: ReturnType<typeof setTimeout> | undefined;
  // Un timer par toast affiché, avec son heure de fin pour calculer le temps restant
  private readonly timers = new Map<
    number,
    { handle: ReturnType<typeof setTimeout>; endsAt: number }
  >();
  // Temps restant des toasts dont le timer est en pause (pile dépliée)
  private readonly remaining = new Map<number, number>();

  constructor() {
    // À la destruction du service, plus aucun timer ne doit rester actif
    inject(DestroyRef).onDestroy(() => {
      this.timers.forEach((timer) => clearTimeout(timer.handle));
      this.timers.clear();
      this.remaining.clear();
      if (this.confirmationTimer !== undefined) clearTimeout(this.confirmationTimer);
    });
  }

  show(message: string, type: ToastType = 'error', requestId?: string) {
    const shown = this.displayed().find((t) => t.message === message && t.type === type);
    if (shown) {
      this.displayed.update((list) => this.withCountPlusOne(list, shown.id, requestId));
      // Replié : timer remis à zéro ; déplié : durée pleine à la reprise
      if (this.isExpanded()) this.remaining.set(shown.id, DURATION_MS[type]);
      else this.startTimer(shown.id, DURATION_MS[type]);
      return;
    }

    const item: ToastItem = {
      id: this.nextId++,
      message,
      type,
      count: 1,
      ...(requestId ? { requestId } : {}),
    };
    this.displayed.update((list) => [...list, item]);
    // Déplié : le timer reste en pause, il démarre au repli
    if (!this.isExpanded()) this.startTimer(item.id, DURATION_MS[type]);
    this.enforceCap();
  }

  // Confirmation éphémère, séparée de la pile (ni plafond, ni timers de la pile)
  showConfirmation(message: string): void {
    if (this.confirmationTimer !== undefined) clearTimeout(this.confirmationTimer);
    this.confirmationMessage.set(message);
    this.confirmationTimer = setTimeout(() => {
      this.confirmationMessage.set(null);
      this.confirmationTimer = undefined;
    }, CONFIRMATION_DURATION_MS);
  }

  dismiss(id: number): void {
    if (!this.displayed().some((t) => t.id === id)) return;
    this.clearTimer(id);
    this.remaining.delete(id);
    this.displayed.update((list) => list.filter((t) => t.id !== id));
    this.collapseIfAlone();
  }

  toggle(): void {
    if (this.isExpanded()) {
      this.collapse();
    } else if (this.displayed().length >= 2) {
      this.pauseAll();
      this.isExpanded.set(true);
    }
  }

  // Compteur +1 ; l'identifiant n'est remplacé que s'il n'est pas vide
  private withCountPlusOne(
    list: readonly ToastItem[],
    id: number,
    requestId?: string,
  ): ToastItem[] {
    return list.map((t) =>
      t.id === id ? { ...t, count: t.count + 1, ...(requestId ? { requestId } : {}) } : t,
    );
  }

  // Plafond : le plus ancien toast affiché sort (10 au maximum)
  private enforceCap(): void {
    while (this.displayed().length > MAX_TOASTS) {
      const oldest = this.displayed()[0];
      this.clearTimer(oldest.id);
      this.remaining.delete(oldest.id);
      this.displayed.update((list) => list.slice(1));
    }
  }

  // Pile dépliée réduite à 1 toast affiché : repli automatique
  private collapseIfAlone(): void {
    if (this.isExpanded() && this.displayed().length <= 1) this.collapse();
  }

  private collapse(): void {
    this.isExpanded.set(false);
    // Reprise : temps restant s'il est connu, durée pleine pour les arrivés dépliés
    for (const t of this.displayed()) {
      this.startTimer(t.id, this.remaining.get(t.id) ?? DURATION_MS[t.type]);
    }
    this.remaining.clear();
  }

  private pauseAll(): void {
    const now = Date.now();
    for (const [id, timer] of this.timers) {
      clearTimeout(timer.handle);
      this.remaining.set(id, Math.max(0, timer.endsAt - now));
    }
    this.timers.clear();
  }

  private startTimer(id: number, ms: number): void {
    this.clearTimer(id);
    const handle = setTimeout(() => this.dismiss(id), ms);
    this.timers.set(id, { handle, endsAt: Date.now() + ms });
  }

  private clearTimer(id: number): void {
    const timer = this.timers.get(id);
    if (!timer) return;
    clearTimeout(timer.handle);
    this.timers.delete(id);
  }
}
