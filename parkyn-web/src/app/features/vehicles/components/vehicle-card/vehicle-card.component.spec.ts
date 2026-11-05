import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTestApp } from '../../../../../testing/test-providers';
import { VehicleCardComponent } from './vehicle-card.component';
import { VehicleResponse } from '../../../../core/services/vehicles.service';

describe('VehicleCardComponent', () => {
  let fixture: ComponentFixture<VehicleCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VehicleCardComponent],
      providers: [provideTestApp()],
    }).compileComponents();

    fixture = TestBed.createComponent(VehicleCardComponent);
    fixture.componentRef.setInput('vehicle', {
      id: 'v1',
      plateNumber: 'AB-123-CD',
      brand: 'Renault',
      model: 'Clio',
      year: 2022,
      mileageKM: 12345,
      condition: 'GOOD',
      status: 'AVAILABLE',
      commissionedAt: '2022-01-01',
      createdAt: '2022-01-01',
    } as VehicleResponse);
  });

  // Test de fumée : l'injection de dépendances et le gabarit se résolvent sans erreur
  it('renders without error', () => {
    expect(() => fixture.detectChanges()).not.toThrow();
    expect(fixture.nativeElement.childElementCount).toBeGreaterThan(0);
  });
});
