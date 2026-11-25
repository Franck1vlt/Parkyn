import { ErrorHandler } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ToastService } from '../../services/toast.service';
import { ToastComponent } from './toast.component';

describe('ToastComponent', () => {
  let fixture: ComponentFixture<ToastComponent>;
  let toastService: ToastService;
  const host = () => fixture.nativeElement as HTMLElement;

  beforeEach(async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({ imports: [ToastComponent] }).compileComponents();

    toastService = TestBed.inject(ToastService);
    fixture = TestBed.createComponent(ToastComponent);
    fixture.detectChanges();
  });

  afterEach(() => vi.useRealTimers());

  const zone = () => host().querySelector('[data-testid="toast-zone"]');
  const byTestId = (id: string) => host().querySelector(`[data-testid="${id}"]`);
  const byLabel = (label: string) =>
    host().querySelector<HTMLButtonElement>(`button[aria-label="${label}"]`);
  const closeButtons = () =>
    Array.from(
      host().querySelectorAll<HTMLButtonElement>('button[aria-label="Fermer la notification"]'),
    );
  const expandLabel = 'Déplier les notifications';
  const collapseLabel = 'Replier les notifications';

  // Publie des toasts puis rafraîchit la vue
  const show = (message: string, type: 'error' | 'success' = 'error', requestId?: string) => {
    toastService.show(message, type, requestId);
    fixture.detectChanges();
  };
  const click = (button: HTMLButtonElement | null) => {
    button!.click();
    fixture.detectChanges();
  };
  const advance = (ms: number) => {
    vi.advanceTimersByTime(ms);
    fixture.detectChanges();
  };

  // C1 : rien n'est affiché tant qu'aucun message n'est publié
  it('shows nothing by default', () => {
    expect(zone()).toBeNull();
    expect(host().textContent?.trim()).toBe('');
  });

  // C2 : une erreur est rouge, role alert, sans élément de pile
  it('shows a single error as a red alert without stack controls', () => {
    show('Accès refusé');

    const toast = host().querySelector('[role="alert"]')!;
    expect(toast.textContent).toContain('Accès refusé');
    expect(toast.classList).toContain('bg-red-500');
    expect(byLabel(expandLabel)).toBeNull();
    expect(byTestId('toast-count')).toBeNull();
    expect(byTestId('toast-repeat')).toBeNull();
  });

  // C3 : un succès est vert, role status
  it('shows a success as a green status', () => {
    show('Enregistré', 'success');

    const toast = host().querySelector('[role="status"]')!;
    expect(toast.textContent).toContain('Enregistré');
    expect(toast.classList).toContain('bg-green-500');
    expect(host().querySelector('[role="alert"]')).toBeNull();
  });

  // C4 : la croix d'un toast seul retire la zone
  it('removes the zone when closing a single toast', () => {
    show('Accès refusé');

    const close = byLabel('Fermer la notification');
    expect(close?.tagName).toBe('BUTTON');
    expect(close?.getAttribute('type')).toBe('button');

    click(close);

    expect(zone()).toBeNull();
  });

  // C5 : une erreur disparaît à 30 s
  it('removes an error after 30 seconds', () => {
    show('Accès refusé');

    advance(29_999);
    expect(host().textContent).toContain('Accès refusé');

    advance(1);
    expect(host().textContent).not.toContain('Accès refusé');
    expect(zone()).toBeNull();
  });

  // C6 : deux toasts repliés montrent le dernier, le compteur et le chevron
  it('shows only the last toast with count and chevron when collapsed', () => {
    show('First');
    show('Second');

    expect(host().textContent).toContain('Second');
    expect(host().textContent).not.toContain('First');
    expect(byTestId('toast-count')?.textContent?.trim()).toBe('2');

    const chevron = byLabel(expandLabel);
    expect(chevron?.tagName).toBe('BUTTON');
    expect(chevron?.getAttribute('type')).toBe('button');
    expect(chevron?.getAttribute('aria-expanded')).toBe('false');
  });

  // C7 : le clic sur le chevron déplie la liste complète
  it('shows all toasts when the chevron is clicked', () => {
    show('First');
    show('Second');

    click(byLabel(expandLabel));

    expect(host().textContent).toContain('First');
    expect(host().textContent).toContain('Second');
    expect(byLabel(expandLabel)).toBeNull();
    expect(byLabel(collapseLabel)?.getAttribute('aria-expanded')).toBe('true');
  });

  // C8 : un second clic revient au repli
  it('collapses again on a second click', () => {
    show('First');
    show('Second');
    click(byLabel(expandLabel));

    click(byLabel(collapseLabel));

    expect(host().textContent).not.toContain('First');
    expect(host().textContent).toContain('Second');
    expect(byLabel(expandLabel)?.getAttribute('aria-expanded')).toBe('false');
  });

  // C9 : la croix d'une pile repliée ferme le dernier toast seulement
  it('closes only the last toast of a collapsed stack', () => {
    show('First');
    show('Second');

    click(byLabel('Fermer la notification'));

    expect(host().textContent).toContain('First');
    expect(host().textContent).not.toContain('Second');
    expect(byLabel(expandLabel)).toBeNull();
    expect(byLabel(collapseLabel)).toBeNull();
  });

  // C10 : un toast arrivé pendant le dépliage est visible tout de suite, à la fin de la pile
  it('shows a toast arriving while expanded at the top at once', () => {
    show('First');
    show('Second');
    click(byLabel(expandLabel));

    show('Third');

    const alertTexts = Array.from(host().querySelectorAll('[role="alert"]')).map(
      (a) => a.textContent,
    );
    expect(alertTexts).toHaveLength(3);
    expect(alertTexts[0]).toContain('First');
    expect(alertTexts[1]).toContain('Second');
    expect(alertTexts[2]).toContain('Third');
    expect(byTestId('toast-count')?.textContent?.trim()).toBe('3');
    expect(byTestId('toast-pending')).toBeNull();

    click(byLabel(collapseLabel));

    expect(host().textContent).toContain('Third');
    expect(host().textContent).not.toContain('Second');
    expect(host().textContent).not.toContain('First');
  });

  // C11 : le compteur ×2 apparaît sur un doublon seulement
  it('shows the repeat badge only on a duplicate', () => {
    show('Accès refusé');
    expect(byTestId('toast-repeat')).toBeNull();

    show('Accès refusé');

    expect(byTestId('toast-repeat')?.textContent?.trim()).toBe('×2');
  });

  // C12 : fermer un toast d'une pile dépliée à 2 la replie
  it('collapses an expanded stack when a close leaves one toast', () => {
    show('First');
    show('Second');
    click(byLabel(expandLabel));

    click(closeButtons()[0]);

    expect(host().textContent).not.toContain('First');
    expect(host().textContent).toContain('Second');
    expect(byLabel(expandLabel)).toBeNull();
    expect(byLabel(collapseLabel)).toBeNull();
    expect(byTestId('toast-count')).toBeNull();
    expect(closeButtons()).toHaveLength(1);
  });

  // C13 : quand tout a expiré, la zone disparaît du DOM
  it('removes the zone when all toasts have expired', () => {
    show('First');
    show('Second');
    expect(zone()).not.toBeNull();

    advance(30_000);

    expect(zone()).toBeNull();
  });

  describe('request reference', () => {
    let savedClipboard: PropertyDescriptor | undefined;
    let clipboardStubbed = false;

    // Remplace navigator.clipboard ; sans argument, le presse-papiers est absent
    const stubClipboard = (writeText?: ReturnType<typeof vi.fn>) => {
      if (!clipboardStubbed) {
        savedClipboard = Object.getOwnPropertyDescriptor(navigator, 'clipboard');
        clipboardStubbed = true;
      }
      Object.defineProperty(navigator, 'clipboard', {
        value: writeText ? { writeText } : undefined,
        configurable: true,
      });
    };

    afterEach(() => {
      if (!clipboardStubbed) return;
      if (savedClipboard) Object.defineProperty(navigator, 'clipboard', savedClipboard);
      else delete (navigator as unknown as Record<string, unknown>)['clipboard'];
      clipboardStubbed = false;
    });

    const copyButton = () => byTestId('toast-copy') as HTMLButtonElement | null;
    const copyButtons = () =>
      Array.from(host().querySelectorAll<HTMLButtonElement>('[data-testid="toast-copy"]'));
    const confirmationToast = () => host().querySelector('[role="status"]');
    const alerts = () => Array.from(host().querySelectorAll('[role="alert"]'));
    const resolvedWrite = () => vi.fn().mockResolvedValue(undefined);
    // Vide les microtâches (promesse du presse-papiers) puis rafraîchit la vue
    const flush = async () => {
      await vi.advanceTimersByTimeAsync(0);
      fixture.detectChanges();
    };
    // Clique sur l'icône copier (qui doit exister) puis attend la promesse
    const copy = async (button: HTMLButtonElement | null = copyButton()) => {
      expect(button).not.toBeNull();
      click(button);
      await flush();
    };
    // Photographie de la pile, pour vérifier un retour à l'état d'avant
    const snapshot = () => {
      const toggle = host().querySelector('button[aria-expanded]');
      return {
        alerts: alerts().map((a) => a.textContent?.trim()),
        count: byTestId('toast-count')?.textContent?.trim() ?? null,
        toggleLabel: toggle?.getAttribute('aria-label') ?? null,
        expanded: toggle?.getAttribute('aria-expanded') ?? null,
      };
    };
    const precedes = (first: Element, second: Element) =>
      Boolean(second.compareDocumentPosition(first) & Node.DOCUMENT_POSITION_PRECEDING);

    // C14 : une erreur avec identifiant affiche sa référence
    it('shows the reference on an error toast', () => {
      stubClipboard(resolvedWrite());

      show('Accès refusé', 'error', 'abc12345');

      expect(byTestId('toast-reference')?.textContent).toContain('Référence : abc12345');
    });

    // C15 : sans identifiant, ni référence ni icône
    it.each([
      ['absent', undefined],
      ['empty', ''],
    ])('shows no reference without a request id (%s)', (_label, requestId) => {
      stubClipboard(resolvedWrite());

      show('Accès refusé', 'error', requestId);

      expect(byTestId('toast-reference')).toBeNull();
      expect(byTestId('toast-copy')).toBeNull();
    });

    // C16 : un succès n'affiche jamais de référence
    it('shows no reference on a success toast', () => {
      stubClipboard(resolvedWrite());

      show('Enregistré', 'success', 'abc12345');

      expect(byTestId('toast-reference')).toBeNull();
      expect(byTestId('toast-copy')).toBeNull();
    });

    // C17 : l'icône copier est un vrai bouton accessible
    it('shows an accessible copy button when the clipboard is available', () => {
      stubClipboard(resolvedWrite());

      show('Accès refusé', 'error', 'abc12345');

      const button = copyButton();
      expect(button?.tagName).toBe('BUTTON');
      expect(button?.getAttribute('type')).toBe('button');
      expect(button?.getAttribute('aria-label')).toBe('Copier la référence');
      expect(button?.querySelector('svg[aria-hidden="true"]')).not.toBeNull();
    });

    // C18 : sans presse-papiers, la référence reste affichée sans icône
    it('hides the copy icon without clipboard support', () => {
      stubClipboard();

      show('Accès refusé', 'error', 'abc12345');

      expect(byTestId('toast-reference')?.textContent).toContain('Référence : abc12345');
      expect(byTestId('toast-copy')).toBeNull();
    });

    // C19 : pile dépliée, chaque toast a sa propre référence
    it('shows each toast own reference when expanded', () => {
      stubClipboard(resolvedWrite());
      show('First', 'error', 'aaaaaaaa');
      show('Second', 'error', 'bbbbbbbb');

      click(byLabel(expandLabel));

      const [first, second] = alerts();
      expect(alerts()).toHaveLength(2);
      expect(first.querySelector('[data-testid="toast-reference"]')?.textContent).toContain(
        'aaaaaaaa',
      );
      expect(first.textContent).not.toContain('bbbbbbbb');
      expect(second.querySelector('[data-testid="toast-reference"]')?.textContent).toContain(
        'bbbbbbbb',
      );
      expect(second.textContent).not.toContain('aaaaaaaa');
    });

    // C20 : on copie l'identifiant nu et on attend l'écriture avant de confirmer
    it('copies the bare id and waits for the write before confirming', async () => {
      let resolve!: () => void;
      const pendingWrite = new Promise<void>((res) => (resolve = res));
      const writeText = vi.fn().mockReturnValue(pendingWrite);
      stubClipboard(writeText);
      show('Accès refusé', 'error', 'abc12345');

      await copy();

      expect(writeText).toHaveBeenCalledTimes(1);
      expect(writeText).toHaveBeenCalledWith('abc12345');
      expect(confirmationToast()).toBeNull();
      resolve();
    });

    // C21 : après l'écriture, un toast vert confirme la copie
    it('shows a green confirmation toast after the copy', async () => {
      stubClipboard(resolvedWrite());
      show('Accès refusé', 'error', 'abc12345');

      await copy();

      const green = confirmationToast()!;
      expect(green).not.toBeNull();
      expect(green.classList).toContain('bg-green-500');
      expect(green.textContent).toContain('Référence copiée');
      expect(green.querySelector('[data-testid="toast-repeat"]')).toBeNull();
      expect(green.querySelector('button')).toBeNull();
      expect(alerts()).toHaveLength(1);
      expect(closeButtons()).toHaveLength(1);
    });

    // C22 : la confirmation est rendue au-dessus du toast d'erreur
    it('renders the confirmation above the error toast', async () => {
      stubClipboard(resolvedWrite());
      show('Accès refusé', 'error', 'abc12345');

      await copy();

      const green = confirmationToast()!;
      expect(green).not.toBeNull();
      expect(precedes(green, alerts()[0])).toBe(true);
      expect(zone()?.firstElementChild).toBe(green);
    });

    // C23 : la copie ne touche pas au timer de l'erreur
    it('leaves the error timer untouched by a copy', async () => {
      const writeText = resolvedWrite();
      stubClipboard(writeText);
      show('Accès refusé', 'error', 'abc12345');
      advance(10_000);

      await copy();
      expect(writeText).toHaveBeenCalledTimes(1);

      advance(19_999);
      expect(alerts()).toHaveLength(1);

      advance(1);
      expect(alerts()).toHaveLength(0);
    });

    // C24 : la confirmation disparaît à 2 s, l'erreur et la zone restent
    it('removes the confirmation after 2 seconds', async () => {
      stubClipboard(resolvedWrite());
      show('Accès refusé', 'error', 'abc12345');
      await copy();

      advance(1_999);
      expect(confirmationToast()).not.toBeNull();

      advance(1);
      expect(confirmationToast()).toBeNull();
      expect(alerts()).toHaveLength(1);
      expect(zone()).not.toBeNull();
    });

    // C25 : pile repliée, la confirmation passe au-dessus puis l'état revient
    it('shows the confirmation above a collapsed stack and restores it', async () => {
      stubClipboard(resolvedWrite());
      show('First', 'error', 'aaaaaaaa');
      show('Second', 'error', 'bbbbbbbb');
      const before = snapshot();

      await copy();

      const green = confirmationToast()!;
      expect(green).not.toBeNull();
      expect(zone()?.firstElementChild).toBe(green);
      expect(precedes(green, alerts()[0])).toBe(true);
      expect(alerts()).toHaveLength(1);
      expect(alerts()[0].textContent).toContain('Second');
      expect(byTestId('toast-count')?.textContent?.trim()).toBe('2');
      expect(byLabel(expandLabel)?.getAttribute('aria-expanded')).toBe('false');

      advance(2_000);

      expect(confirmationToast()).toBeNull();
      expect(snapshot()).toEqual(before);
    });

    // C26 : pile dépliée, la confirmation apparaît tout en haut, à l'instant
    it('shows the confirmation at the top of an expanded stack at once', async () => {
      stubClipboard(resolvedWrite());
      show('First', 'error', 'aaaaaaaa');
      show('Second', 'error', 'bbbbbbbb');
      click(byLabel(expandLabel));
      const before = snapshot();

      await copy(copyButtons()[0]);

      const green = confirmationToast()!;
      expect(green).not.toBeNull();
      expect(zone()?.firstElementChild).toBe(green);
      expect(precedes(green, byTestId('toast-count')!)).toBe(true);
      expect(byLabel(collapseLabel)?.getAttribute('aria-expanded')).toBe('true');
      expect(alerts()).toHaveLength(2);

      advance(2_000);

      expect(confirmationToast()).toBeNull();
      expect(snapshot()).toEqual(before);

      advance(60_000);
      expect(alerts()).toHaveLength(2);
    });

    // C27 : la confirmation ne touche pas au toast arrivé pendant le dépliage
    it('leaves a toast arrived while expanded untouched by the confirmation', async () => {
      stubClipboard(resolvedWrite());
      show('First', 'error', 'aaaaaaaa');
      show('Second', 'error', 'bbbbbbbb');
      click(byLabel(expandLabel));
      show('Third');
      expect(alerts()).toHaveLength(3);

      await copy(copyButtons()[0]);

      expect(confirmationToast()).not.toBeNull();
      expect(alerts()).toHaveLength(3);

      advance(2_000);
      expect(confirmationToast()).toBeNull();
      expect(alerts()).toHaveLength(3);

      click(byLabel(collapseLabel));
      expect(alerts()).toHaveLength(1);
      expect(host().textContent).toContain('Third');
      expect(host().textContent).not.toContain('Second');
      expect(host().textContent).not.toContain('First');
    });

    // C28 : des clics rapprochés ne dupliquent pas la confirmation
    it('does not duplicate the confirmation on rapid clicks', async () => {
      const writeText = resolvedWrite();
      stubClipboard(writeText);
      show('Accès refusé', 'error', 'abc12345');
      await copy();
      advance(1_000);

      await copy();

      expect(writeText).toHaveBeenCalledTimes(2);
      expect(host().querySelectorAll('[role="status"]')).toHaveLength(1);
      expect(byTestId('toast-repeat')).toBeNull();

      advance(1_999);
      expect(host().querySelectorAll('[role="status"]')).toHaveLength(1);

      advance(1);
      expect(confirmationToast()).toBeNull();
    });

    // C29 : une écriture rejetée ne produit rien
    it('does nothing when the write is rejected', async () => {
      const writeText = vi.fn().mockRejectedValue(new Error('denied'));
      stubClipboard(writeText);
      show('Accès refusé', 'error', 'abc12345');
      // Vitest tourne sous Node : un rejet non géré y déclenche 'unhandledRejection'
      const nodeProcess = (
        globalThis as unknown as {
          process: {
            on(event: string, listener: (reason: unknown) => void): void;
            off(event: string, listener: (reason: unknown) => void): void;
          };
        }
      ).process;
      const rejections: unknown[] = [];
      const onRejection = (reason: unknown) => rejections.push(reason);
      nodeProcess.on('unhandledRejection', onRejection);

      try {
        await copy();
        // Node signale les rejets après les microtâches : on attend une vraie macrotâche
        await new Promise<void>((resolve) => {
          const channel = new MessageChannel();
          channel.port1.onmessage = () => {
            channel.port1.close();
            resolve();
          };
          channel.port2.postMessage(null);
        });

        expect(rejections).toEqual([]);
      } finally {
        nodeProcess.off('unhandledRejection', onRejection);
      }

      expect(writeText).toHaveBeenCalledTimes(1);
      expect(confirmationToast()).toBeNull();
      expect(alerts()).toHaveLength(1);
      expect(byTestId('toast-repeat')).toBeNull();

      advance(29_999);
      expect(alerts()).toHaveLength(1);

      advance(1);
      expect(alerts()).toHaveLength(0);
    });

    // C32 : un writeText qui lève une exception synchrone est aussi silencieux
    it('does nothing when the write throws synchronously', async () => {
      const writeText = vi.fn(() => {
        throw new Error('denied');
      });
      stubClipboard(writeText);
      show('Accès refusé', 'error', 'abc12345');
      const handleError = vi
        .spyOn(TestBed.inject(ErrorHandler), 'handleError')
        .mockImplementation(() => undefined);
      const windowErrors: unknown[] = [];
      const onWindowError = (event: ErrorEvent) => {
        windowErrors.push(event.error);
        event.preventDefault();
      };
      window.addEventListener('error', onWindowError);

      try {
        await copy();
      } finally {
        window.removeEventListener('error', onWindowError);
      }

      expect(writeText).toHaveBeenCalledTimes(1);
      expect(handleError).not.toHaveBeenCalled();
      expect(windowErrors).toEqual([]);
      expect(confirmationToast()).toBeNull();
      expect(alerts()).toHaveLength(1);

      advance(30_000);
      expect(alerts()).toHaveLength(0);
    });

    // C30 : la confirmation survit à l'expiration de son erreur
    it('keeps the confirmation when its error toast expires first', async () => {
      stubClipboard(resolvedWrite());
      show('Accès refusé', 'error', 'abc12345');
      advance(29_000);
      await copy();

      advance(1_000);
      expect(alerts()).toHaveLength(0);
      expect(confirmationToast()).not.toBeNull();
      expect(zone()).not.toBeNull();

      advance(999);
      expect(confirmationToast()).not.toBeNull();

      advance(1);
      expect(confirmationToast()).toBeNull();
      expect(zone()).toBeNull();
    });

    // C31 : on copie l'identifiant du toast dont l'icône a été cliquée
    it('copies the id of the toast whose icon was clicked', async () => {
      const writeText = resolvedWrite();
      stubClipboard(writeText);
      show('First', 'error', 'aaaaaaaa');
      show('Second', 'error', 'bbbbbbbb');
      click(byLabel(expandLabel));

      await copy(copyButtons()[0]);

      expect(writeText).toHaveBeenCalledTimes(1);
      expect(writeText).toHaveBeenCalledWith('aaaaaaaa');
    });
  });
});
