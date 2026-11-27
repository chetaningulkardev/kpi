import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ConfigurationService, TeamAssignmentFlow, TeamAssignmentFlowRequest } from '../../../services/configuration.service';

@Component({
  selector: 'app-team-assignment-flow',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
  <section class="max-w-6xl mx-auto px-4 py-8 space-y-5">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="text-2xl font-bold text-slate-900">Team Assignment Flow</h1>
        <p class="text-sm text-slate-500">Track pending reviews by role transition and drill down to requester users.</p>
      </div>
      <button (click)="load()" [disabled]="loading"
        class="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50">
        {{ loading ? 'Refreshing...' : 'Refresh' }}
      </button>
    </div>

    <div class="grid gap-3 md:grid-cols-3">
      <div class="rounded-xl border bg-white p-4">
        <p class="text-xs font-semibold uppercase text-slate-500">Transitions</p>
        <p class="mt-1 text-3xl font-bold text-dt-700">{{ flows.length }}</p>
      </div>
      <div class="rounded-xl border bg-white p-4">
        <p class="text-xs font-semibold uppercase text-slate-500">Pending Users</p>
        <p class="mt-1 text-3xl font-bold text-amber-600">{{ totalPendingUsers }}</p>
      </div>
      <div class="rounded-xl border bg-white p-4">
        <p class="text-xs font-semibold uppercase text-slate-500">Pending KPIs</p>
        <p class="mt-1 text-3xl font-bold text-blue-700">{{ totalPendingKpis }}</p>
      </div>
    </div>

    <div *ngIf="loading" class="rounded-xl border bg-white p-8 text-center text-slate-400">Loading flow data...</div>

    <p *ngIf="error" role="alert" class="rounded-lg bg-red-50 p-4 text-red-700">{{ error }}</p>
    <div *ngIf="!loading && flows.length > 0" class="rounded-xl border bg-white overflow-x-auto">
      <table class="min-w-full text-sm">
        <thead class="bg-slate-50 border-b text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th class="px-4 py-3 text-left">From Role</th>
            <th class="px-4 py-3 text-left">To Role</th>
            <th class="px-4 py-3 text-right">Pending Users</th>
            <th class="px-4 py-3 text-right">Pending KPIs</th>
            <th class="px-4 py-3 text-right">Action</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          <tr *ngFor="let flow of flows; trackBy: trackByTransition" class="hover:bg-slate-50">
            <td class="px-4 py-3 font-semibold text-slate-800">{{ flow.fromRole.roleName }}</td>
            <td class="px-4 py-3 text-slate-700">{{ flow.toRole.roleName }}</td>
            <td class="px-4 py-3 text-right font-bold text-dt-700">{{ flow.pendingUsers }}</td>
            <td class="px-4 py-3 text-right font-semibold text-blue-700">{{ flow.pendingKpis }}</td>
            <td class="px-4 py-3 text-right">
              <button (click)="selectFlow(flow)" class="text-xs font-semibold text-dt-700 hover:text-dt-900 underline">Drill down</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div *ngIf="!loading && !error && flows.length === 0" class="rounded-xl border bg-white p-10 text-center text-slate-400">
      No pending team assignment flow right now.
    </div>

    <div *ngIf="selectedFlow" class="rounded-xl border bg-white overflow-x-auto">
      <div class="px-4 py-3 border-b bg-slate-50">
        <p class="text-sm font-semibold text-slate-900">{{ selectedFlow.fromRole.roleName }} -> {{ selectedFlow.toRole.roleName }}</p>
        <p class="text-xs text-slate-500">Requester users included in this transition.</p>
      </div>
      <table class="min-w-full text-sm">
        <thead class="bg-white border-b text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th class="px-4 py-3 text-left">User</th>
            <th class="px-4 py-3 text-left">Reviewer</th>
            <th class="px-4 py-3 text-right">Pending KPIs</th>
            <th class="px-4 py-3 text-left">Cycle(s)</th>
            <th class="px-4 py-3 text-left">Status</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          <tr *ngFor="let req of selectedFlow.requests; trackBy: trackByRequester" class="hover:bg-slate-50">
            <td class="px-4 py-3">
              <p class="font-semibold text-slate-800">{{ req.employeeName }}</p>
              <p class="text-xs text-slate-400">{{ req.email }}</p>
            </td>
            <td class="px-4 py-3 text-slate-700">{{ req.reviewerName || 'Unassigned' }}</td>
            <td class="px-4 py-3 text-right font-semibold text-dt-700">{{ req.pendingKpis }}</td>
            <td class="px-4 py-3 text-slate-600">{{ req.cycleCodes }}</td>
            <td class="px-4 py-3 text-slate-600">{{ req.statuses }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
  `
})
export class TeamAssignmentFlowComponent implements OnInit {
  flows: TeamAssignmentFlow[] = [];
  selectedFlow: TeamAssignmentFlow | null = null;
  loading = false;
  error = '';

  constructor(private configService: ConfigurationService) {}

  ngOnInit(): void {
    this.load();
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  get totalPendingUsers(): number {
    return new Set(this.flows.flatMap(flow => flow.requests.map(request => request.userId))).size;
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  get totalPendingKpis(): number {
    return this.flows.reduce((sum, flow) => sum + flow.pendingKpis, 0);
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.configService.getTeamAssignmentFlow().subscribe({
      next: (flows) => {
        this.flows = flows || [];
        this.selectedFlow = this.flows[0] ?? null;
        this.loading = false;
      },
      error: () => {
        this.error = 'Failed to load team assignment flow. Please try Refresh.';
        this.flows = [];
        this.selectedFlow = null;
        this.loading = false;
      }
    });
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  selectFlow(flow: TeamAssignmentFlow): void {
    this.selectedFlow = flow;
  }

  // noinspection JSUnusedGlobalSymbols - used in template
  trackByTransition = (_: number, flow: TeamAssignmentFlow): string => flow.transitionKey;

  // noinspection JSUnusedGlobalSymbols - used in template
  trackByRequester = (_: number, req: TeamAssignmentFlowRequest): number => req.userId;
}
