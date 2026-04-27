import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DetailsVehicleComponent } from './details-vehicle.component';

describe('DetailsVehicleComponent', () => {
  let component: DetailsVehicleComponent;
  let fixture: ComponentFixture<DetailsVehicleComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DetailsVehicleComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(DetailsVehicleComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
