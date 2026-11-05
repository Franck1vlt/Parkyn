import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTestApp } from '../../../../../testing/test-providers';
import { DetailsVehicleComponent } from './details-vehicle.component';

describe('DetailsVehicleComponent', () => {
  let fixture: ComponentFixture<DetailsVehicleComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DetailsVehicleComponent],
      providers: [provideTestApp()],
    }).compileComponents();

    fixture = TestBed.createComponent(DetailsVehicleComponent);
  });

  // Test de fumée : l'injection de dépendances et le gabarit se résolvent sans erreur
  it('renders without error', () => {
    expect(() => fixture.detectChanges()).not.toThrow();
    expect(fixture.nativeElement.childElementCount).toBeGreaterThan(0);
  });
});
