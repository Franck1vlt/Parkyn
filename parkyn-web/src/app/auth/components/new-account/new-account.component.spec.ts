import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTestApp } from '../../../../testing/test-providers';
import { NewAccountComponent } from './new-account.component';

describe('NewAccountComponent', () => {
  let fixture: ComponentFixture<NewAccountComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NewAccountComponent],
      providers: [provideTestApp()],
    }).compileComponents();

    fixture = TestBed.createComponent(NewAccountComponent);
  });

  // Test de fumée : l'injection de dépendances et le gabarit se résolvent sans erreur
  it('renders without error', () => {
    expect(() => fixture.detectChanges()).not.toThrow();
    expect(fixture.nativeElement.childElementCount).toBeGreaterThan(0);
  });
});
