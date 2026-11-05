import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTestApp } from '../../../../../testing/test-providers';
import { DetailsReservationComponent } from './details-reservation.component';

describe('DetailsReservationComponent', () => {
  let fixture: ComponentFixture<DetailsReservationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DetailsReservationComponent],
      providers: [provideTestApp()],
    }).compileComponents();

    fixture = TestBed.createComponent(DetailsReservationComponent);
  });

  // Test de fumée : l'injection de dépendances et le gabarit se résolvent sans erreur
  it('renders without error', () => {
    expect(() => fixture.detectChanges()).not.toThrow();
    expect(fixture.nativeElement.childElementCount).toBeGreaterThan(0);
  });
});
