import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AgentSelectComponent } from './agent-select.component';

describe('AgentSelectComponent', () => {
  let component: AgentSelectComponent;
  let fixture: ComponentFixture<AgentSelectComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AgentSelectComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AgentSelectComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
