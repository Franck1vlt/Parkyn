import { Component, input } from '@angular/core';

@Component({
  selector: 'app-inline-error',
  standalone: true,
  template: `
    @if (messages().length) {
      <div [id]="id()" class="mt-1">
        @for (message of messages(); track $index) {
          <p role="alert" class="text-sm text-red-600">{{ message }}</p>
        }
      </div>
    }
  `,
})
export class InlineErrorComponent {
  messages = input<string[]>([]);
  id = input.required<string>();
}
