import { Component, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import { debounceTime, distinctUntilChanged, Subject, switchMap, takeUntil } from 'rxjs';
import { UserResponse, UsersService } from '../../../../core/services/users.service';
import { NgFor, NgIf } from '@angular/common';

@Component({
  selector: 'app-driver-select',
  standalone: true,
  imports: [NgFor, NgIf],
  templateUrl: './driver-select.component.html',
  styleUrl: './driver-select.component.css',
})
export class DriverSelectComponent implements OnInit, OnDestroy {
  private searchSubject = new Subject<string>();
  private destroy$ = new Subject<void>();
  selectedDriverName = '';

  @Output() driverChange = new EventEmitter<string>();

  filteredDrivers: UserResponse[] = [];

  constructor(private usersService: UsersService) {}

  ngOnInit(): void {
    this.searchSubject
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((query) => this.usersService.searchDrivers(query)),
        takeUntil(this.destroy$),
      )
      .subscribe((page) => (this.filteredDrivers = page.content));
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onSearch(event: Event) {
    const query = (event.target as HTMLInputElement).value;
    this.searchSubject.next(query);
  }

  onSelect(driver: UserResponse) {
    this.driverChange.emit(driver.id);
    this.selectedDriverName = `${driver.firstname} ${driver.lastname}`;
    this.filteredDrivers = [];
  }
}
