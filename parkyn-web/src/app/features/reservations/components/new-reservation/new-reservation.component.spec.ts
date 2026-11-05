import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTestApp } from '../../../../../testing/test-providers';
import { NewReservationComponent } from './new-reservation.component';

describe('NewReservationComponent', () => {
  let fixture: ComponentFixture<NewReservationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NewReservationComponent],
      providers: [provideTestApp()],
    }).compileComponents();

    fixture = TestBed.createComponent(NewReservationComponent);
  });

  // Test de fumée : l'injection de dépendances et le gabarit se résolvent sans erreur
  it('renders without error', () => {
    expect(() => fixture.detectChanges()).not.toThrow();
    expect(fixture.nativeElement.childElementCount).toBeGreaterThan(0);
  });
});
