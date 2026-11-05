import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTestApp } from '../../../../../testing/test-providers';
import { ConditionSelectComponent } from './condition-select.component';

describe('ConditionSelectComponent', () => {
  let fixture: ComponentFixture<ConditionSelectComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConditionSelectComponent],
      providers: [provideTestApp()],
    }).compileComponents();

    fixture = TestBed.createComponent(ConditionSelectComponent);
  });

  // Test de fumée : l'injection de dépendances et le gabarit se résolvent sans erreur
  it('renders without error', () => {
    expect(() => fixture.detectChanges()).not.toThrow();
    expect(fixture.nativeElement.childElementCount).toBeGreaterThan(0);
  });
});
