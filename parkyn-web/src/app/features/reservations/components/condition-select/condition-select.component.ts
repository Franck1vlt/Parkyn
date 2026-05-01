import { NgFor } from '@angular/common';
import { Component, EventEmitter, Output } from '@angular/core';

@Component({
  selector: 'app-condition-select',
  standalone: true,
  imports: [NgFor],
  templateUrl: './condition-select.component.html',
  styleUrl: './condition-select.component.css',
})
export class ConditionSelectComponent {
  conditions: string[] = ['EXCELLENT', 'GOOD', 'FAIR', 'POOR'];

  @Output() conditionChange = new EventEmitter<string>();

  onSelect(event: Event) {
    const value = (event.target as HTMLSelectElement).value;
    this.conditionChange.emit(value);
  }

  getConditionLabel(condition: string): string {
    const labels: Record<string, string> = {
      EXCELLENT: 'Excellent',
      GOOD: 'Bon',
      FAIR: 'Moyen',
      POOR: 'Mauvais',
    };
    return labels[condition] || condition;
  }
}
