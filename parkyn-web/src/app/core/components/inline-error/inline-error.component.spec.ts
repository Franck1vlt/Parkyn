import { ComponentFixture, TestBed } from '@angular/core/testing';
import { InlineErrorComponent } from './inline-error.component';

describe('InlineErrorComponent', () => {
  let fixture: ComponentFixture<InlineErrorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [InlineErrorComponent] }).compileComponents();
    fixture = TestBed.createComponent(InlineErrorComponent);
  });

  function render(messages: string[], id: string) {
    fixture.componentRef.setInput('messages', messages);
    fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
  }

  function paragraphs(): HTMLElement[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('p[role="alert"]'),
    );
  }

  it('renders one alert paragraph per message, in order, inside a container with the given id', () => {
    render(['Immatriculation invalide.', 'Immatriculation déjà saisie.'], 'plateNumber-error');

    const rendered = paragraphs();
    expect(rendered.map((p: HTMLElement) => p.textContent?.trim())).toEqual([
      'Immatriculation invalide.',
      'Immatriculation déjà saisie.',
    ]);
    const container = (fixture.nativeElement as HTMLElement).querySelector('#plateNumber-error');
    expect(container).not.toBeNull();
    for (const p of rendered) {
      expect(p.parentElement).toBe(container);
    }
  });

  it('renders nothing when there is no message', () => {
    render([], 'plateNumber-error');

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelectorAll('p')).toHaveLength(0);
    expect(host.querySelectorAll('[role="alert"]')).toHaveLength(0);
  });
});
