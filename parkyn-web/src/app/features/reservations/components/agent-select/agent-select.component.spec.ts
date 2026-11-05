import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTestApp } from '../../../../../testing/test-providers';
import { AgentSelectComponent } from './agent-select.component';

describe('AgentSelectComponent', () => {
  let fixture: ComponentFixture<AgentSelectComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AgentSelectComponent],
      providers: [provideTestApp()],
    }).compileComponents();

    fixture = TestBed.createComponent(AgentSelectComponent);
  });

  // Test de fumée : l'injection de dépendances et le gabarit se résolvent sans erreur
  it('renders without error', () => {
    expect(() => fixture.detectChanges()).not.toThrow();
    expect(fixture.nativeElement.childElementCount).toBeGreaterThan(0);
  });
});
