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
  const showMany = (n: number) => {
    for (let i = 0; i < n; i++) service.show(`m${i}`);
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

    // Un toast arrivant pendant le dépliage est ajouté tout de suite à la fin de la pile
    it('adds a toast arriving while expanded immediately', () => {
      service.show('A');
      service.show('B');
      service.toggle();

      service.show('C');

      expect(messages()).toEqual(['A', 'B', 'C']);
      expect(service.expanded()).toBe(true);
    });

    // Le toast arrivé déplié reste en pause, puis repart avec une durée pleine au repli
    it('keeps a toast arrived while expanded paused, then gives it a full timer on collapse', () => {
      service.show('A', 'success');
      service.show('B', 'success');
      service.toggle();
      service.show('C');
      expect(messages()).toEqual(['A', 'B', 'C']);

      vi.advanceTimersByTime(60_000);
      expect(messages()).toEqual(['A', 'B', 'C']);
      expect(service.expanded()).toBe(true);

      service.toggle();

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

    // Un id inconnu ne change rien
    it('ignores an unknown id', () => {
      service.show('A');
      const before = service.toasts();

      service.dismiss(987_654);

      expect(service.toasts()).toEqual(before);
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

    // Fermeture qui laisse 1 toast affiché : repli auto, temps restant conservé
    it('collapses when a dismissal leaves one displayed toast', () => {
      service.show('A');
      vi.advanceTimersByTime(5_000);
      service.show('B');
      vi.advanceTimersByTime(5_000);
      service.toggle();
      vi.advanceTimersByTime(60_000);

      service.dismiss(byMessage('A')!.id);

      expect(service.expanded()).toBe(false);
      expect(messages()).toEqual(['B']);

      // B avait 25 s restantes
      vi.advanceTimersByTime(24_999);
      expect(messages()).toEqual(['B']);

      vi.advanceTimersByTime(1);
      expect(service.toasts()).toEqual([]);
    });

    // Un toast arrivé déplié reste déplié après une fermeture, puis repart à durée pleine au repli
    it('stays expanded then collapses with a full timer when a dismissal leaves a toast arrived while expanded', () => {
      service.show('A');
      service.show('B');
      service.toggle();
      service.show('C');

      service.dismiss(byMessage('A')!.id);

      expect(messages()).toEqual(['B', 'C']);
      expect(service.expanded()).toBe(true);

      service.dismiss(byMessage('B')!.id);

      expect(messages()).toEqual(['C']);
      expect(service.expanded()).toBe(false);

      vi.advanceTimersByTime(29_999);
      expect(messages()).toEqual(['C']);

      vi.advanceTimersByTime(1);
      expect(service.toasts()).toEqual([]);
    });
  });

  describe('toggle', () => {
    // Moins de 2 toasts : toggle est sans effet
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

    // Plafond en déplié : le plus ancien sort, le nouveau n'est jamais évincé
    it('evicts the oldest toast when the expanded stack exceeds 10', () => {
      showMany(8);
      service.toggle();
      service.show('p1');
      service.show('p2');
      expect(service.toasts()).toHaveLength(10);

      service.show('p3');

      expect(messages()).toEqual(['m1', 'm2', 'm3', 'm4', 'm5', 'm6', 'm7', 'p1', 'p2', 'p3']);
      expect(service.expanded()).toBe(true);

      // Au repli, tout expire à 30 s sans timer orphelin
      service.toggle();
      expect(vi.getTimerCount()).toBe(10);

      vi.advanceTimersByTime(29_999);
      expect(service.toasts()).toHaveLength(10);

      vi.advanceTimersByTime(1);
      expect(service.toasts()).toEqual([]);
      expect(vi.getTimerCount()).toBe(0);
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

  describe('request id', () => {
    // L'identifiant de requête est conservé sur le toast
    it('stores the request id on the toast', () => {
      service.show('A', 'error', 'abc12345');

      expect(service.toasts()).toEqual([
        expect.objectContaining({ message: 'A', requestId: 'abc12345' }),
      ]);
    });

    // Un doublon replié garde le dernier identifiant
    it('keeps the latest request id when a duplicate arrives', () => {
      service.show('A', 'error', 'aaaaaaaa');
      service.show('A', 'error', 'bbbbbbbb');

      expect(service.toasts()).toEqual([
        expect.objectContaining({ message: 'A', count: 2, requestId: 'bbbbbbbb' }),
      ]);
    });

    // Un doublon sans identifiant n'écrase pas l'ancien
    it.each([
      ['no id', undefined],
      ['an empty id', ''],
    ])('keeps the previous request id when the duplicate has %s', (_label, requestId) => {
      service.show('A', 'error', 'aaaaaaaa');
      service.show('A', 'error', requestId);

      expect(service.toasts()).toEqual([
        expect.objectContaining({ message: 'A', count: 2, requestId: 'aaaaaaaa' }),
      ]);
    });

    // Pile dépliée, le doublon met à jour l'identifiant du toast affiché
    it('updates the request id of a displayed toast when expanded', () => {
      service.show('A', 'error', 'aaaaaaaa');
      service.show('B');
      service.toggle();

      service.show('A', 'error', 'bbbbbbbb');

      expect(byMessage('A')).toEqual(expect.objectContaining({ count: 2, requestId: 'bbbbbbbb' }));
    });

    // Le doublon d'un toast arrivé déplié fusionne tout de suite avec le dernier identifiant
    it('updates the request id of a toast arrived while expanded', () => {
      service.show('A');
      service.show('B');
      service.toggle();
      service.show('C', 'error', 'cccccccc');

      service.show('C', 'error', 'dddddddd');

      expect(byMessage('C')).toEqual(expect.objectContaining({ count: 2, requestId: 'dddddddd' }));
    });
  });

  describe('confirmation', () => {
    // La confirmation est exposée sans toucher à la pile
    it('exposes the confirmation without touching the stack', () => {
      service.show('A');
      const toastsBefore = service.toasts();

      service.showConfirmation('Référence copiée');

      expect(service.confirmation()).toBe('Référence copiée');
      expect(service.toasts()).toEqual(toastsBefore);
      expect(service.expanded()).toBe(false);
    });

    // La confirmation disparaît à 2 s pile
    it('clears the confirmation at exactly 2 seconds', () => {
      service.showConfirmation('Référence copiée');
      expect(service.confirmation()).toBe('Référence copiée');

      vi.advanceTimersByTime(1_999);
      expect(service.confirmation()).toBe('Référence copiée');

      vi.advanceTimersByTime(1);
      expect(service.confirmation()).toBeNull();
    });

    // Un second appel relance le délai de 2 s
    it('restarts the 2 second delay on a repeated confirmation', () => {
      service.showConfirmation('Référence copiée');
      expect(service.confirmation()).toBe('Référence copiée');
      vi.advanceTimersByTime(1_000);

      service.showConfirmation('Référence copiée');

      vi.advanceTimersByTime(1_999);
      expect(service.confirmation()).toBe('Référence copiée');

      vi.advanceTimersByTime(1);
      expect(service.confirmation()).toBeNull();
    });

    // La confirmation expire seule alors que la pile dépliée reste en pause
    it('expires on its own while the expanded stack stays paused', () => {
      service.show('A');
      service.show('B');
      service.toggle();
      service.show('C');

      expect(messages()).toEqual(['A', 'B', 'C']);

      service.showConfirmation('Référence copiée');
      expect(service.confirmation()).toBe('Référence copiée');
      expect(messages()).toEqual(['A', 'B', 'C']);
      vi.advanceTimersByTime(2_000);
      expect(service.confirmation()).toBeNull();

      vi.advanceTimersByTime(60_000);
      expect(messages()).toEqual(['A', 'B', 'C']);
      expect(service.expanded()).toBe(true);

      // Un toggle() pendant l'affichage replie la pile sans interrompre la confirmation
      service.showConfirmation('Référence copiée');
      service.toggle();
      expect(service.confirmation()).toBe('Référence copiée');
      expect(service.expanded()).toBe(false);
      expect(messages()).toEqual(['A', 'B', 'C']);

      vi.advanceTimersByTime(2_000);
      expect(service.confirmation()).toBeNull();
    });

    // Le timer d'un toast d'erreur n'est pas modifié
    it('does not change the timer of an error toast', () => {
      service.show('A');
      const timersBefore = vi.getTimerCount();
      vi.advanceTimersByTime(10_000);

      service.showConfirmation('Référence copiée');
      expect(service.confirmation()).toBe('Référence copiée');
      expect(vi.getTimerCount()).toBe(timersBefore + 1);

      vi.advanceTimersByTime(2_000);
      expect(service.confirmation()).toBeNull();
      expect(vi.getTimerCount()).toBe(timersBefore);

      vi.advanceTimersByTime(17_999);
      expect(messages()).toEqual(['A']);

      vi.advanceTimersByTime(1);
      expect(service.toasts()).toEqual([]);
    });

    // La confirmation ne compte pas dans le plafond de 10
    it('does not count the confirmation in the cap of 10', () => {
      showMany(10);
      const before = messages();

      service.showConfirmation('Référence copiée');

      expect(service.confirmation()).toBe('Référence copiée');
      expect(messages()).toEqual(before);
      expect(service.toasts()).toHaveLength(10);
    });

    // La destruction du service nettoie le timer de confirmation
    it('clears the confirmation timer on destroy', () => {
      service.showConfirmation('Référence copiée');
      expect(service.confirmation()).toBe('Référence copiée');

      TestBed.resetTestingModule();

      expect(vi.getTimerCount()).toBe(0);
    });
  });
});
