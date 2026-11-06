import { TestBed } from '@angular/core/testing';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({});
    service = TestBed.inject(ToastService);
  });

  afterEach(() => vi.useRealTimers());

  const messages = () => service.toasts().map((t) => t.message);
  const byMessage = (message: string) => service.toasts().find((t) => t.message === message);
  // Affiche n messages distincts "m0", "m1"...
  const showMany = (n: number, prefix = 'm') => {
    for (let i = 0; i < n; i++) service.show(`${prefix}${i}`);
  };

  describe('show', () => {
    // Un toast est ajouté avec un id, un compteur à 1 et le type erreur par défaut
    it('adds a toast with id, count 1 and error type by default', () => {
      service.show('Accès refusé');

      expect(service.toasts()).toEqual([
        { id: expect.any(Number), message: 'Accès refusé', type: 'error', count: 1 },
      ]);
    });

    // Une erreur dure 30 s pile
    it('removes an error toast at exactly 30 seconds', () => {
      service.show('Accès refusé');

      vi.advanceTimersByTime(29_999);
      expect(messages()).toEqual(['Accès refusé']);

      vi.advanceTimersByTime(1);
      expect(service.toasts()).toEqual([]);
    });

    // Un succès dure 10 s pile
    it('removes a success toast at exactly 10 seconds', () => {
      service.show('Enregistré', 'success');

      vi.advanceTimersByTime(9_999);
      expect(messages()).toEqual(['Enregistré']);

      vi.advanceTimersByTime(1);
      expect(service.toasts()).toEqual([]);
    });

    // Chaque toast a son propre timer : B n'est pas coupé par celui de A
    it('uses one timer per toast', () => {
      service.show('A');
      vi.advanceTimersByTime(20_000);
      service.show('B');

      vi.advanceTimersByTime(10_000);
      expect(messages()).toEqual(['B']);

      vi.advanceTimersByTime(19_999);
      expect(messages()).toEqual(['B']);

      vi.advanceTimersByTime(1);
      expect(service.toasts()).toEqual([]);
    });

    // Même message et type différent : pas de fusion
    it('does not merge same message with a different type', () => {
      service.show('Info', 'error');
      service.show('Info', 'success');

      expect(service.toasts()).toEqual([
        expect.objectContaining({ message: 'Info', type: 'error', count: 1 }),
        expect.objectContaining({ message: 'Info', type: 'success', count: 1 }),
      ]);
    });
  });

  describe('duplicates', () => {
    // Doublon en repli : un seul toast, compteur 2, timer remis à zéro
    it('merges a duplicate when collapsed and resets its timer', () => {
      service.show('A');
      vi.advanceTimersByTime(20_000);
      service.show('A');

      expect(service.toasts()).toEqual([expect.objectContaining({ message: 'A', count: 2 })]);

      vi.advanceTimersByTime(29_999);
      expect(messages()).toEqual(['A']);

      vi.advanceTimersByTime(1);
      expect(service.toasts()).toEqual([]);
    });

    // Doublon pendant le dépliage : compteur +1 tout de suite, pas de nouvelle ligne
    it('increments the displayed toast count at once when expanded', () => {
      service.show('A');
      service.show('B');
      service.toggle();

      service.show('A');

      expect(service.toasts()).toEqual([
        expect.objectContaining({ message: 'A', count: 2 }),
        expect.objectContaining({ message: 'B', count: 1 }),
      ]);
      expect(service.pendingCount()).toBe(0);
    });

    // Les timers restent en pause malgré le doublon
    it('keeps timers paused when a duplicate arrives while expanded', () => {
      service.show('A');
      service.show('B');
      service.toggle();
      service.show('A');

      vi.advanceTimersByTime(60_000);

      expect(messages()).toEqual(['A', 'B']);
      expect(service.expanded()).toBe(true);
    });

    // Le toast fusionné pendant le dépliage repart avec une durée pleine au repli
    it('gives a full duration to a toast merged while expanded', () => {
      service.show('A');
      service.show('B');
      vi.advanceTimersByTime(5_000);
      service.toggle();
      service.show('A');
      vi.advanceTimersByTime(100_000);
      service.toggle();

      vi.advanceTimersByTime(25_000);
      expect(messages()).toEqual(['A']);

      vi.advanceTimersByTime(4_999);
      expect(messages()).toEqual(['A']);

      vi.advanceTimersByTime(1);
      expect(service.toasts()).toEqual([]);
    });

    // Doublon d'un toast en attente : fusionne dans le toast en attente
    it('merges a duplicate of a pending toast into it', () => {
      service.show('A');
      service.show('B');
      service.toggle();
      service.show('C');

      service.show('C');
      expect(service.pendingCount()).toBe(1);

      service.toggle();
      expect(service.toasts()).toEqual([
        expect.objectContaining({ message: 'A', count: 1 }),
        expect.objectContaining({ message: 'B', count: 1 }),
        expect.objectContaining({ message: 'C', count: 2 }),
      ]);
    });
  });

  describe('pause and resume', () => {
    // Dépliée, la pile ne perd aucun toast même après longtemps
    it('pauses all timers while expanded', () => {
      service.show('A');
      service.show('B');
      vi.advanceTimersByTime(10_000);

      service.toggle();
      vi.advanceTimersByTime(60_000);

      expect(service.expanded()).toBe(true);
      expect(messages()).toEqual(['A', 'B']);
    });

    // Au repli, chaque toast reprend avec son temps restant exact
    it('resumes with the exact remaining time on collapse', () => {
      service.show('A');
      vi.advanceTimersByTime(5_000);
      service.show('B');
      vi.advanceTimersByTime(7_000);
      service.toggle();
      vi.advanceTimersByTime(100_000);
      service.toggle();

      // A: 18 s restantes, B: 23 s restantes
      vi.advanceTimersByTime(17_999);
      expect(messages()).toEqual(['A', 'B']);

      vi.advanceTimersByTime(1);
      expect(messages()).toEqual(['B']);

      vi.advanceTimersByTime(5_000);
      expect(service.toasts()).toEqual([]);
    });

    // Un toast arrivant pendant le dépliage est mis en attente
    it('puts a toast arriving while expanded on hold', () => {
      service.show('A');
      service.show('B');
      service.toggle();

      service.show('C');

      expect(messages()).toEqual(['A', 'B']);
      expect(service.pendingCount()).toBe(1);
    });

    // Au repli, le toast en attente rejoint la pile avec un timer plein
    it('adds the pending toast on collapse with a full timer', () => {
      service.show('A', 'success');
      service.show('B', 'success');
      service.toggle();
      service.show('C');
      vi.advanceTimersByTime(5_000);

      service.toggle();

      expect(service.pendingCount()).toBe(0);
      expect(messages()).toEqual(['A', 'B', 'C']);

      vi.advanceTimersByTime(10_000);
      expect(messages()).toEqual(['C']);

      vi.advanceTimersByTime(19_999);
      expect(messages()).toEqual(['C']);

      vi.advanceTimersByTime(1);
      expect(service.toasts()).toEqual([]);
    });

    // Expiration en repli : le toast part, la pile reste repliée
    it('removes an expired toast when collapsed and stays collapsed', () => {
      service.show('A');
      vi.advanceTimersByTime(10_000);
      service.show('B');

      vi.advanceTimersByTime(20_000);

      expect(messages()).toEqual(['B']);
      expect(service.expanded()).toBe(false);
    });
  });

  describe('dismiss', () => {
    // dismiss retire le toast et nettoie son timer
    it('removes the toast and clears its timer', () => {
      service.show('A');
      expect(service.toasts()).toHaveLength(1);
      const timersBefore = vi.getTimerCount();

      service.dismiss(service.toasts()[0].id);

      expect(service.toasts()).toEqual([]);
      expect(vi.getTimerCount()).toBe(timersBefore - 1);

      vi.advanceTimersByTime(60_000);
      expect(service.toasts()).toEqual([]);
    });

    // Un id inconnu ne change rien (vert dès le départ avec le stub)
    it('ignores an unknown id', () => {
      service.show('A');
      const before = service.toasts();
      const pendingBefore = service.pendingCount();

      service.dismiss(987_654);

      expect(service.toasts()).toEqual(before);
      expect(service.pendingCount()).toBe(pendingBefore);
    });

    // Fermeture à 3 toasts pendant le dépliage : reste déplié, timers en pause, temps restant gardé
    it('stays expanded and keeps remaining times when a dismissal leaves 2 toasts', () => {
      service.show('A');
      vi.advanceTimersByTime(5_000);
      service.show('B');
      vi.advanceTimersByTime(5_000);
      service.show('C');
      vi.advanceTimersByTime(2_000);
      service.toggle();
      vi.advanceTimersByTime(60_000);

      service.dismiss(byMessage('B')!.id);

      expect(messages()).toEqual(['A', 'C']);
      expect(service.expanded()).toBe(true);

      vi.advanceTimersByTime(60_000);
      expect(messages()).toEqual(['A', 'C']);

      service.toggle();

      // A: 18 s restantes, C: 28 s restantes
      vi.advanceTimersByTime(17_999);
      expect(messages()).toEqual(['A', 'C']);

      vi.advanceTimersByTime(1);
      expect(messages()).toEqual(['C']);

      vi.advanceTimersByTime(9_999);
      expect(messages()).toEqual(['C']);

      vi.advanceTimersByTime(1);
      expect(service.toasts()).toEqual([]);
    });

    // Fermeture qui laisse 1 toast affiché : repli auto, jonction, temps restant conservé
    it('collapses when a dismissal leaves one displayed toast', () => {
      service.show('A');
      vi.advanceTimersByTime(5_000);
      service.show('B');
      vi.advanceTimersByTime(5_000);
      service.toggle();
      vi.advanceTimersByTime(60_000);
      service.show('C');

      service.dismiss(byMessage('A')!.id);

      expect(service.expanded()).toBe(false);
      expect(service.pendingCount()).toBe(0);
      expect(messages()).toEqual(['B', 'C']);

      // B avait 25 s restantes, C démarre à 30 s
      vi.advanceTimersByTime(24_999);
      expect(messages()).toEqual(['B', 'C']);

      vi.advanceTimersByTime(1);
      expect(messages()).toEqual(['C']);

      vi.advanceTimersByTime(4_999);
      expect(messages()).toEqual(['C']);

      vi.advanceTimersByTime(1);
      expect(service.toasts()).toEqual([]);
    });
  });

  describe('toggle', () => {
    // Moins de 2 toasts : toggle est sans effet (vert dès le départ avec le stub)
    it('does nothing with fewer than 2 toasts', () => {
      service.toggle();
      expect(service.expanded()).toBe(false);

      service.show('A');
      service.toggle();
      expect(service.expanded()).toBe(false);
    });
  });

  describe('cap of 10 toasts', () => {
    // Le 11e toast fait sortir le plus ancien et son timer est nettoyé
    it('evicts the oldest toast beyond 10 and clears its timer', () => {
      showMany(11);

      expect(messages()).toEqual(['m1', 'm2', 'm3', 'm4', 'm5', 'm6', 'm7', 'm8', 'm9', 'm10']);
      expect(vi.getTimerCount()).toBe(10);
    });

    // Plafond avec en-attente : le plus ancien AFFICHÉ sort
    it('evicts the oldest displayed toast when pending ones count in the cap', () => {
      showMany(8);
      service.toggle();
      service.show('p1');
      service.show('p2');
      expect(service.toasts()).toHaveLength(8);
      expect(service.pendingCount()).toBe(2);

      service.show('p3');

      expect(messages()).toEqual(['m1', 'm2', 'm3', 'm4', 'm5', 'm6', 'm7']);
      expect(service.pendingCount()).toBe(3);
    });

    // Plafond laissant 1 toast affiché : repli auto et jonction des en-attente
    it('collapses and merges pending toasts when the cap leaves one displayed', () => {
      showMany(2);
      service.toggle();
      showMany(8, 'p');
      expect(service.pendingCount()).toBe(8);

      service.show('last');

      expect(service.expanded()).toBe(false);
      expect(service.pendingCount()).toBe(0);
      expect(service.toasts()).toHaveLength(10);
      expect(messages()[0]).toBe('m1');
      expect(messages()[9]).toBe('last');

      // Les timers sont démarrés pour tous
      vi.advanceTimersByTime(29_999);
      expect(service.toasts()).toHaveLength(10);

      vi.advanceTimersByTime(1);
      expect(service.toasts()).toEqual([]);
    });

    // Un doublon au plafond ne fait sortir personne
    it('evicts nobody when a duplicate arrives at the cap', () => {
      showMany(10);
      const before = messages();

      service.show('m0');

      expect(messages()).toEqual(before);
      expect(service.toasts()).toHaveLength(10);
      expect(byMessage('m0')?.count).toBe(2);
    });
  });
});
