import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTestApp } from '../../../../../testing/test-providers';
import { VehicleSelectComponent } from './vehicle-select.component';

describe('VehicleSelectComponent', () => {
  let fixture: ComponentFixture<VehicleSelectComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VehicleSelectComponent],
      providers: [provideTestApp()],
    }).compileComponents();

    fixture = TestBed.createComponent(VehicleSelectComponent);
  });

  // Test de fumée : l'injection de dépendances et le gabarit se résolvent sans erreur
  it('renders without error', () => {
    expect(() => fixture.detectChanges()).not.toThrow();
    expect(fixture.nativeElement.childElementCount).toBeGreaterThan(0);
  });
});
