import { Component, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import {
  debounceTime,
  distinctUntilChanged,
  filter,
  Subject,
  switchMap,
  takeUntil,
  tap,
} from 'rxjs';
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
  filteredDrivers: UserResponse[] = [];
  isLoading = false;

  @Output() driverChange = new EventEmitter<string>();
  @Output() driverSelected = new EventEmitter<UserResponse>();

  constructor(private usersService: UsersService) {}

  ngOnInit(): void {
    this.searchSubject
      .pipe(
        debounceTime(200),
        distinctUntilChanged(),
        filter((query) => query.length >= 2),
        tap(() => (this.isLoading = true)),
        switchMap((query) => this.usersService.searchDrivers(query)),
        takeUntil(this.destroy$),
      )
      .subscribe((page) => {
        this.filteredDrivers = page.content;
        this.isLoading = false;
      });
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
    this.driverSelected.emit(driver);
    this.selectedDriverName = `${driver.firstname} ${driver.lastname}`;
    this.filteredDrivers = [];
  }
}
