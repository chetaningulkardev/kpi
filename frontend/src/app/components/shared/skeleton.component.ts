import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * SkeletonComponent — animated placeholder while content loads.
 * Usage: <app-skeleton type="cards" [count]="6" />
 *        <app-skeleton type="table" [count]="5" />
 *        <app-skeleton type="stat-bar" />
 */
@Component({
  selector: 'app-skeleton',
  standalone: true,
  imports: [CommonModule],
  template: `
    <!-- Stat cards skeleton -->
    <div *ngIf="type === 'stats'" class="grid grid-cols-2 md:grid-cols-6 gap-4">
      <div *ngFor="let i of range(count)"
        class="bg-white rounded-xl shadow px-4 py-3 text-center animate-pulse">
        <div class="h-7 w-12 bg-slate-200 rounded mx-auto mb-2"></div>
        <div class="h-3 w-16 bg-slate-100 rounded mx-auto"></div>
      </div>
    </div>

    <!-- Card grid skeleton -->
    <div *ngIf="type === 'cards'" class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <div *ngFor="let i of range(count)"
        class="bg-white rounded-xl shadow border border-slate-100 p-5 space-y-3 animate-pulse">
        <div class="flex items-start justify-between gap-3">
          <div class="flex-1 space-y-2">
            <div class="h-4 bg-slate-200 rounded w-3/4"></div>
            <div class="h-3 bg-slate-100 rounded w-1/3"></div>
          </div>
          <div class="h-5 w-20 bg-slate-200 rounded-full"></div>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div class="h-14 bg-dt-50 rounded-lg"></div>
          <div class="h-14 bg-slate-50 rounded-lg"></div>
        </div>
        <div class="h-2 bg-slate-200 rounded-full"></div>
        <div class="flex justify-between">
          <div class="h-4 w-20 bg-slate-100 rounded"></div>
          <div class="h-4 w-16 bg-slate-100 rounded"></div>
        </div>
      </div>
    </div>

    <!-- Table skeleton -->
    <div *ngIf="type === 'table'" class="bg-white rounded-xl shadow overflow-hidden animate-pulse">
      <div class="bg-slate-50 border-b border-slate-200 px-4 py-3 flex gap-4">
        <div *ngFor="let i of range(5)" class="h-3 bg-slate-200 rounded flex-1"></div>
      </div>
      <div class="divide-y divide-slate-100">
        <div *ngFor="let i of range(count)" class="px-4 py-3 flex gap-4 items-center">
          <div class="flex-1 space-y-1.5">
            <div class="h-3.5 bg-slate-200 rounded w-2/3"></div>
            <div class="h-2.5 bg-slate-100 rounded w-1/3"></div>
          </div>
          <div class="h-3 w-16 bg-slate-100 rounded"></div>
          <div class="h-5 w-20 bg-slate-200 rounded-full"></div>
          <div class="h-6 w-14 bg-dt-100 rounded-lg"></div>
        </div>
      </div>
    </div>

    <!-- Single bar skeleton (for filters / header) -->
    <div *ngIf="type === 'bar'"
      class="bg-white rounded-xl shadow p-4 animate-pulse flex gap-3">
      <div *ngFor="let i of range(count)" class="h-8 flex-1 bg-slate-100 rounded-lg"></div>
    </div>
  `
})
export class SkeletonComponent {
   @Input() type: 'stats' | 'cards' | 'table' | 'bar' = 'cards';
   @Input() count = 6;

   // noinspection JSUnusedGlobalSymbols - used in template
   range(n: number): number[] {
     return Array.from({ length: n }, (_, i) => i);
   }
}

