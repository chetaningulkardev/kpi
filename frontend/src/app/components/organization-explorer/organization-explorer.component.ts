import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AdminApiService } from '../../services/admin-api.service';

@Component({
  selector: 'app-organization-explorer',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="max-w-5xl mx-auto px-4 py-8">
      <h1 class="text-2xl font-bold text-gray-800 mb-2">Organization Explorer</h1>
      <p class="text-sm text-gray-500 mb-6">Tribe → Portfolio → Squad → Members. Click a node to expand or collapse.</p>

      <div *ngIf="loading" class="text-center text-gray-400 py-20">Loading organization...</div>
      <div *ngIf="error" class="bg-red-50 border border-red-200 text-red-700 rounded-lg p-4 mb-4">{{ error }}</div>

      <div *ngIf="!loading && nodes.length > 0" class="space-y-2 overflow-x-auto">
        <ng-container *ngFor="let node of nodes">
          <ng-container *ngTemplateOutlet="nodeTpl; context: { node: node, level: 0 }"></ng-container>
        </ng-container>
      </div>

      <div *ngIf="!loading && nodes.length === 0 && !error" class="text-center text-gray-400 py-20">
        No organization structure yet. Create Tribes, Portfolios and Squads to build it.
      </div>
    </div>

    <ng-template #nodeTpl let-node="node" let-level="level">
      <div [style.margin-left.px]="level * 24">
        <button type="button" (click)="toggle(node)" [attr.aria-expanded]="node.children?.length ? isOpen(node) : null"
          class="w-full text-left flex flex-wrap items-center gap-2 py-2 px-3 rounded-lg hover:bg-gray-50 border border-gray-100 bg-white shadow-sm">
          <span *ngIf="node.children?.length" class="text-gray-400 text-xs w-4">
            {{ isOpen(node) ? '▼' : '▶' }}
          </span>
          <span *ngIf="!node.children?.length" class="w-4"></span>

          <span class="px-2 py-0.5 rounded-full text-xs font-semibold"
            [ngClass]="{
              'bg-indigo-100 text-indigo-700': node.type === 'TRIBE',
              'bg-sky-100 text-sky-700': node.type === 'PORTFOLIO',
              'bg-teal-100 text-teal-700': node.type === 'SQUAD',
              'bg-gray-100 text-gray-600': node.type === 'MEMBER'
            }">{{ node.type }}</span>

          <span class="text-sm font-medium text-gray-800">{{ node.name }}</span>
          <span *ngIf="node.code" class="text-xs text-gray-400 font-mono">{{ node.code }}</span>
          <span *ngIf="node.role_name" class="text-xs px-2 py-0.5 bg-blue-50 text-blue-700 rounded-full">{{ node.role_name }}</span>
          <span *ngIf="node.active === false" class="text-xs px-2 py-0.5 bg-red-50 text-red-600 rounded-full ml-auto">Inactive</span>
        </button>

        <div *ngIf="isOpen(node)" class="mt-1 space-y-1">
          <ng-container *ngFor="let child of node.children">
            <ng-container *ngTemplateOutlet="nodeTpl; context: { node: child, level: level + 1 }"></ng-container>
          </ng-container>
        </div>
      </div>
    </ng-template>
  `
})
export class OrganizationExplorerComponent implements OnInit {
  nodes: any[] = [];
  loading = true;
  error = '';
  private openSet = new Set<string>();

  constructor(private api: AdminApiService) {}

  ngOnInit(): void {
    this.api.getOrganizationTree().subscribe({
      next: res => {
        this.nodes = res.data || [];
        // open top-level tribes by default
        this.nodes.forEach(n => this.openSet.add(this.key(n)));
        this.loading = false;
      },
      error: () => { this.error = 'Failed to load organization tree.'; this.loading = false; }
    });
  }

  key(node: any): string { return `${node.type}-${node.id}`; }
  isOpen(node: any): boolean { return this.openSet.has(this.key(node)); }
  toggle(node: any): void {
    if (!node.children?.length) return;
    const k = this.key(node);
    if (this.openSet.has(k)) this.openSet.delete(k); else this.openSet.add(k);
  }
}
