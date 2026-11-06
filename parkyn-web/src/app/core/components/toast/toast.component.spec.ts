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
  const byLabel = (label: string) => host().querySelector<HTMLButtonElement>(`button[aria-label="${label}"]`);
  const closeButtons = () =>
    Array.from(host().querySelectorAll<HTMLButtonElement>('button[aria-label="Fermer la notification"]'));
  const expandLabel = 'Déplier les notifications';
  const collapseLabel = 'Replier les notifications';

  // Publie des toasts puis rafraîchit la vue
  const show = (message: string, type: 'error' | 'success' = 'error') => {
    toastService.show(message, type);
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

  // C10 : un toast arrivé pendant le dépliage est signalé puis rejoint la pile
  it('flags a toast arriving while expanded and adds it on collapse', () => {
    show('First');
    show('Second');
    click(byLabel(expandLabel));

    show('Third');

    expect(host().textContent).not.toContain('Third');
    expect(byTestId('toast-pending')?.textContent?.trim()).toBe('+1 nouveau');

    click(byLabel(collapseLabel));

    expect(byTestId('toast-pending')).toBeNull();
    expect(host().textContent).toContain('Third');
    expect(host().textContent).not.toContain('Second');
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
});
