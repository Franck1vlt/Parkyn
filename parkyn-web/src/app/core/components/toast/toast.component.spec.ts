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

  // Rien n'est affiché tant qu'aucun message n'est publié
  it('shows nothing by default', () => {
    expect(host().textContent?.trim()).toBe('');
  });

  // Affiche le message en rouge pour une erreur
  it('shows an error message in red', () => {
    toastService.show('Accès refusé');
    fixture.detectChanges();

    const toast = host().querySelector('div')!;
    expect(toast.textContent?.trim()).toBe('Accès refusé');
    expect(toast.classList).toContain('bg-red-500');
  });

  // Affiche le message en vert pour un succès
  it('shows a success message in green', () => {
    toastService.show('Enregistré', 'success');
    fixture.detectChanges();

    expect(host().querySelector('div')!.classList).toContain('bg-green-500');
  });

  // Disparaît au bout de 4 secondes
  it('disappears after 4 seconds', () => {
    toastService.show('Accès refusé');
    vi.advanceTimersByTime(3999);
    fixture.detectChanges();
    expect(host().textContent).toContain('Accès refusé');

    vi.advanceTimersByTime(1);
    fixture.detectChanges();
    expect(host().textContent?.trim()).toBe('');
  });
});
