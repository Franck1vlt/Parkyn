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
  selector: 'app-agent-select',
  standalone: true,
  imports: [NgFor, NgIf],
  templateUrl: './agent-select.component.html',
  styleUrl: './agent-select.component.css',
})
export class AgentSelectComponent implements OnInit, OnDestroy {
  private searchSubject = new Subject<string>();
  private destroy$ = new Subject<void>();
  selectedAgentName = '';
  filteredAgents: UserResponse[] = [];
  isLoading = false;

  @Output() agentChange = new EventEmitter<string>();
  @Output() agentSelected = new EventEmitter<UserResponse>();

  constructor(private usersService: UsersService) {}

  ngOnInit(): void {
    this.searchSubject
      .pipe(
        debounceTime(200),
        distinctUntilChanged(),
        filter((query) => query.length >= 2),
        tap(() => (this.isLoading = true)),
        switchMap((query) => this.usersService.searchAgents(query)),
        takeUntil(this.destroy$),
      )
      .subscribe((page) => {
        this.filteredAgents = page.content;
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

  onSelect(agent: UserResponse) {
    this.agentChange.emit(agent.id);
    this.agentSelected.emit(agent);
    this.selectedAgentName = `${agent.firstname} ${agent.lastname}`;
    this.filteredAgents = [];
  }
}
