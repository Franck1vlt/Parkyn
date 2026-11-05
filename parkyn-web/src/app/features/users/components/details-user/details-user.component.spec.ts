import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTestApp } from '../../../../../testing/test-providers';
import { DetailsUserComponent } from './details-user.component';

describe('DetailsUserComponent', () => {
  let fixture: ComponentFixture<DetailsUserComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DetailsUserComponent],
      providers: [provideTestApp()],
    }).compileComponents();

    fixture = TestBed.createComponent(DetailsUserComponent);
  });

  // Test de fumée : l'injection de dépendances et le gabarit se résolvent sans erreur
  it('renders without error', () => {
    expect(() => fixture.detectChanges()).not.toThrow();
    expect(fixture.nativeElement.childElementCount).toBeGreaterThan(0);
  });
});
