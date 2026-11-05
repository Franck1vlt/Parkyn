import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTestApp } from '../../../../../testing/test-providers';
import { DriverSelectComponent } from './driver-select.component';

describe('DriverSelectComponent', () => {
  let fixture: ComponentFixture<DriverSelectComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DriverSelectComponent],
      providers: [provideTestApp()],
    }).compileComponents();

    fixture = TestBed.createComponent(DriverSelectComponent);
  });

  // Test de fumée : l'injection de dépendances et le gabarit se résolvent sans erreur
  it('renders without error', () => {
    expect(() => fixture.detectChanges()).not.toThrow();
    expect(fixture.nativeElement.childElementCount).toBeGreaterThan(0);
  });
});
