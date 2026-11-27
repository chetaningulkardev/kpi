import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-org-chart',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="max-w-5xl mx-auto px-4 py-8">
      <h1 class="text-2xl font-bold text-gray-800 mb-6">Organization Chart</h1>

      <div *ngIf="loading" class="text-center text-gray-400 py-20">Loading chart...</div>
      <div *ngIf="error" class="bg-red-50 border border-red-200 text-red-700 rounded-lg p-4 mb-4">{{ error }}</div>

      <div *ngIf="!loading && nodes.length > 0" class="space-y-2 overflow-x-auto">
        <ng-container *ngFor="let node of nodes">
          <ng-container *ngTemplateOutlet="nodeTemplate; context: { node: node, level: 0 }"></ng-container>
        </ng-container>
      </div>

      <div *ngIf="!loading && nodes.length === 0 && !error"
           class="text-center text-gray-400 py-20">
        No hierarchy data. Assign reporting managers to users to build the chart.
      </div>
    </div>

    <ng-template #nodeTemplate let-node="node" let-level="level">
      <div [style.padding-left.px]="level * 32"
           class="flex items-start gap-3 py-2">
        <div class="flex-shrink-0 flex items-center">
          <div *ngIf="level > 0" class="w-6 h-px bg-gray-300 mr-1"></div>
          <div class="w-9 h-9 rounded-full bg-blue-600 flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
            {{ node.full_name?.split(' ')?.[0]?.[0] }}{{ node.full_name?.split(' ')?.[1]?.[0] }}
          </div>
        </div>
        <div class="bg-white rounded-lg shadow-sm border border-gray-100 px-4 py-2 flex-1 min-w-0">
          <div class="flex flex-wrap items-center gap-2">
            <span class="font-semibold text-gray-800 text-sm">{{ node.full_name }}</span>
            <span *ngIf="node.role_name"
              class="px-2 py-0.5 bg-blue-100 text-blue-700 rounded-full text-xs">{{ node.role_name }}</span>
            <span *ngIf="node.team_name"
              class="px-2 py-0.5 bg-green-100 text-green-700 rounded-full text-xs">{{ node.team_name }}</span>
          </div>
          <p class="text-xs text-gray-400 mt-0.5">{{ node.employee_code }} · {{ node.email }}</p>
        </div>
      </div>

      <ng-container *ngFor="let child of node.children">
        <ng-container *ngTemplateOutlet="nodeTemplate; context: { node: child, level: level + 1 }"></ng-container>
      </ng-container>
    </ng-template>
  `
})
export class OrgChartComponent implements OnInit {
  nodes: any[] = [];
  loading = true;
  error = '';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.http.get<any>('/api/hierarchy/tree').subscribe({
      next: res => {
        this.nodes = res.data || [];
        this.loading = false;
      },
      error: err => {
        this.error = 'Failed to load org chart.';
        this.loading = false;
      }
    });
  }
}
