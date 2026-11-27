import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService, Toast } from '../../services/toast.service';

@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="fixed top-4 right-4 z-[100] flex flex-col gap-2 pointer-events-none" aria-live="polite" role="status">
      <div *ngFor="let toast of toastService.toasts(); trackBy: trackById"
        class="pointer-events-auto min-w-72 max-w-sm rounded-xl shadow-lg border px-4 py-3 flex items-start gap-3 animate-slide-in"
        [ngClass]="bgClass(toast.type)"
        role="alert">
        <!-- Icon -->
        <span class="mt-0.5 text-lg shrink-0" [ngClass]="iconClass(toast.type)">{{ icon(toast.type) }}</span>
        <!-- Message -->
        <p class="flex-1 text-sm font-medium" [ngClass]="textClass(toast.type)">{{ toast.message }}</p>
        <!-- Dismiss -->
        <button (click)="toastService.dismiss(toast.id)"
          class="shrink-0 text-sm leading-none opacity-60 hover:opacity-100 transition"
          [ngClass]="textClass(toast.type)"
          aria-label="Dismiss notification">✕</button>
      </div>
    </div>
  `,
  styles: [`
    @keyframes slide-in {
      from { transform: translateX(100%); opacity: 0; }
      to   { transform: translateX(0);   opacity: 1; }
    }
    .animate-slide-in { animation: slide-in 0.25s ease-out; }
  `]
})
export class ToastComponent {
   // noinspection JSUnusedGlobalSymbols - used in template
   readonly toastService = inject(ToastService);

   // noinspection JSUnusedGlobalSymbols - used in template trackBy
   trackById = (_: number, t: Toast) => t.id;

   // noinspection JSUnusedGlobalSymbols - used in template
   bgClass(type: Toast['type']): string {
     return {
       success: 'bg-emerald-50 border-emerald-200',
       error:   'bg-rose-50 border-rose-200',
       warning: 'bg-amber-50 border-amber-200',
       info:    'bg-blue-50 border-blue-200'
     }[type];
   }

   // noinspection JSUnusedGlobalSymbols - used in template
   textClass(type: Toast['type']): string {
     return {
       success: 'text-emerald-800',
       error:   'text-rose-800',
       warning: 'text-amber-800',
       info:    'text-blue-800'
     }[type];
   }

   // noinspection JSUnusedGlobalSymbols - used in template
   iconClass(type: Toast['type']): string {
     return {
       success: 'text-emerald-500',
       error:   'text-rose-500',
       warning: 'text-amber-500',
       info:    'text-blue-500'
     }[type];
   }

   // noinspection JSUnusedGlobalSymbols - used in template
   icon(type: Toast['type']): string {
    return { success: '✓', error: '✕', warning: '⚠', info: 'ℹ' }[type];
  }
}

