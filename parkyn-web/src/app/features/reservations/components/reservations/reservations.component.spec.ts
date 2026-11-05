import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTestApp } from '../../../../../testing/test-providers';
import { ReservationsComponent } from './reservations.component';

describe('ReservationsComponent', () => {
  let fixture: ComponentFixture<ReservationsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReservationsComponent],
      providers: [provideTestApp()],
    }).compileComponents();

    fixture = TestBed.createComponent(ReservationsComponent);
  });

  // Test de fumée : l'injection de dépendances et le gabarit se résolvent sans erreur
  it('renders without error', () => {
    expect(() => fixture.detectChanges()).not.toThrow();
    expect(fixture.nativeElement.childElementCount).toBeGreaterThan(0);
  });
});
