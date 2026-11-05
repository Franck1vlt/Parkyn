import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTestApp } from '../../../../../testing/test-providers';
import { UsersComponent } from './users.component';

describe('UsersComponent', () => {
  let fixture: ComponentFixture<UsersComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UsersComponent],
      providers: [provideTestApp()],
    }).compileComponents();

    fixture = TestBed.createComponent(UsersComponent);
  });

  // Test de fumée : l'injection de dépendances et le gabarit se résolvent sans erreur
  it('renders without error', () => {
    expect(() => fixture.detectChanges()).not.toThrow();
    expect(fixture.nativeElement.childElementCount).toBeGreaterThan(0);
  });
});
