import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';

export type ToastType = 'error' | 'success';

export interface ToastItem {
  id: number;
  message: string;
  type: ToastType;
  count: number;
}

const DURATION_MS: Record<ToastType, number> = { error: 30_000, success: 10_000 };
const MAX_TOASTS = 10;

@Injectable({
  providedIn: 'root',
})
export class ToastService {
  private readonly displayed = signal<readonly ToastItem[]>([]);
  private readonly pending = signal<readonly ToastItem[]>([]);
  private readonly isExpanded = signal(false);

  readonly toasts = this.displayed.asReadonly();
  readonly pendingCount = computed(() => this.pending().length);
  readonly expanded = this.isExpanded.asReadonly();

  private nextId = 1;
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
    });
  }

  show(message: string, type: ToastType = 'error') {
    const shown = this.displayed().find((t) => t.message === message && t.type === type);
    if (shown) {
      this.displayed.update((list) => this.withCountPlusOne(list, shown.id));
      // Replié : timer remis à zéro ; déplié : durée pleine à la reprise
      if (this.isExpanded()) this.remaining.set(shown.id, DURATION_MS[type]);
      else this.startTimer(shown.id, DURATION_MS[type]);
      return;
    }

    const waiting = this.pending().find((t) => t.message === message && t.type === type);
    if (waiting) {
      this.pending.update((list) => this.withCountPlusOne(list, waiting.id));
      return;
    }

    const item: ToastItem = { id: this.nextId++, message, type, count: 1 };
    if (this.isExpanded()) {
      this.pending.update((list) => [...list, item]);
    } else {
      this.displayed.update((list) => [...list, item]);
      this.startTimer(item.id, DURATION_MS[type]);
    }
    this.enforceCap();
    this.collapseIfAlone();
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

  private withCountPlusOne(list: readonly ToastItem[], id: number): ToastItem[] {
    return list.map((t) => (t.id === id ? { ...t, count: t.count + 1 } : t));
  }

  // Plafond : le plus ancien toast affiché sort (affichés + en attente <= 10)
  private enforceCap(): void {
    while (
      this.displayed().length + this.pending().length > MAX_TOASTS &&
      this.displayed().length > 0
    ) {
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
    const waiting = this.pending();
    this.pending.set([]);
    this.displayed.update((list) => [...list, ...waiting]);
    this.isExpanded.set(false);
    // Reprise : temps restant pour les anciens, durée pleine pour les arrivés en attente
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
