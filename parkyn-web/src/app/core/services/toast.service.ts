import { Injectable, signal } from '@angular/core';

interface Toast {
  message: string;
  type: 'error' | 'success';
  visible: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class ToastService {
  toast = signal<Toast>({ message: '', type: 'error', visible: false });

  show(message: string, type: 'error' | 'success' = 'error') {
    this.toast.set({ message, type, visible: true });
    setTimeout(() => this.toast.set({ message: '', type: 'error', visible: false }), 4000);
  }
}
