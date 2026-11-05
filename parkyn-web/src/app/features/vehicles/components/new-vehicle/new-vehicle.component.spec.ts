import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTestApp } from '../../../../../testing/test-providers';
import { NewVehicleComponent } from './new-vehicle.component';

describe('NewVehicleComponent', () => {
  let fixture: ComponentFixture<NewVehicleComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NewVehicleComponent],
      providers: [provideTestApp()],
    }).compileComponents();

    fixture = TestBed.createComponent(NewVehicleComponent);
  });

  // Test de fumée : l'injection de dépendances et le gabarit se résolvent sans erreur
  it('renders without error', () => {
    expect(() => fixture.detectChanges()).not.toThrow();
    expect(fixture.nativeElement.childElementCount).toBeGreaterThan(0);
  });
});
