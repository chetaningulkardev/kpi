import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ConfigurationService, RoleGenerationReadiness, RoleOption, ReviewCycle } from '../../../services/configuration.service';

import { extractHttpError } from '../../../services/http-error.util';
import { AccessibleModalDirective } from '../../../directives/accessible-modal.directive';
interface RoleStat {
  roleId: number; roleCode: string; roleName: string;
  userCount: number; totalTemplates: number; activeTemplates: number;
  readyToGenerate: boolean; templateNames: string[];
}
interface CycleStat {
  cycleId: number; cycleCode: string; cycleName: string;
  status: string; kpisGenerated: boolean; kpiCount: number;
  startDate: string; endDate: string;
}
interface PreviewUser {
  userId: number; name: string; email: string;
  roles: string[]; hasTemplate: boolean; existingKpiCount: number; willGenerate: boolean;
}
interface PreviewResult {
  cycleCode: string; cycleName: string; cycleStatus: string;
  totalUsers: number; willGenerate: number; alreadyGenerated: number; noTemplate: number;
  users: PreviewUser[];
}

@Component({
  selector: 'app-kpi-generate',
  standalone: true,
  imports: [AccessibleModalDirective, CommonModule, FormsModule],
  templateUrl: './kpi-generate.component.html'
})
export class KpiGenerateComponent implements OnInit {
  loadingStats = false;
  generating = false;
  previewing = false;
  checkingReadiness = false;
  activeTab: 'overview' | 'byRole' | 'preview' = 'overview';

  roles: RoleOption[] = [];
  cycles: ReviewCycle[] = [];
  roleStats: RoleStat[] = [];
  cycleStats: CycleStat[] = [];
  totalActiveUsers = 0;
  rolesWithActiveTemplate = 0;

  selectedRoleCode = '';
  selectedCycleCode = '';
  selectedPreviewCycle = '';

  lastResult: { success: boolean; message: string; count?: number } | null = null;
  previewData: PreviewResult | null = null;
  roleReadiness: RoleGenerationReadiness | null = null;
  generatingCycleId: number | null = null;

  confirmPopup = {
    visible: false, title: '', message: '', actionLabel: 'Confirm', dangerous: false,
    action: null as null | (() => void)
  };

  constructor(private configService: ConfigurationService) {}

  ngOnInit(): void { this.loadAll(); }

  onRoleOrCycleChange(): void {
    // Keep feedback scoped to the currently selected role/cycle.
    this.lastResult = null;
    this.refreshRoleReadiness();
  }

  loadAll(): void {
    this.loadingStats = true;
    this.configService.getRoles().subscribe({
      next: (roles) => {
        this.roles = roles.filter(r => r.active);
        if (this.roles.length && !this.selectedRoleCode) {
          this.selectedRoleCode = this.roles[0].roleCode;
        }
        this.refreshRoleReadiness();
      },
      error: () => { this.lastResult = { success: false, message: 'Failed to load roles. Please refresh.' }; }
    });

    this.configService.getReviewCycles().subscribe({
      next: (cycles) => {
        this.cycles = cycles;
        if (!this.selectedCycleCode) {
          const open = cycles.find(c => ['OPEN', 'IN_PROGRESS'].includes(c.statusCode));
          const first = open ?? cycles[0];
          if (first) {
            this.selectedCycleCode = first.code;
            this.selectedPreviewCycle = first.code;
          }
        }
        this.refreshRoleReadiness();
      },
      error: () => { this.lastResult = { success: false, message: 'Failed to load review cycles. Please refresh.' }; }
    });

    this.configService.getGenerationStats().subscribe({
      next: (stats) => {
        this.totalActiveUsers = stats.totalActiveUsers ?? 0;
        this.rolesWithActiveTemplate = stats.rolesWithActiveTemplate ?? 0;
        this.roleStats = (stats.roleStats ?? []).filter((r: RoleStat) => r.userCount > 0 || r.totalTemplates > 0);
        this.cycleStats = stats.cycleStats ?? [];
        this.loadingStats = false;
      },
      error: () => { this.loadingStats = false; this.lastResult = { success: false, message: 'Failed to load generation statistics. Please refresh.' }; }
    });
  }

  refreshRoleReadiness(): void {
    this.roleReadiness = null;
    if (!this.selectedRoleCode || !this.selectedCycleCode) return;
    this.checkingReadiness = true;
    this.configService.getRoleGenerationReadiness(this.selectedRoleCode, this.selectedCycleCode).subscribe({
      next: (readiness) => { this.roleReadiness = readiness; },
      error: () => {
        this.roleReadiness = {
          roleCode: this.selectedRoleCode,
          cycleCode: this.selectedCycleCode,
          ready: false,
          cycleEligible: false,
          activeUsersInRole: 0,
          reasons: ['Unable to load readiness details.'],
          templates: []
        };
      }
    }).add(() => { this.checkingReadiness = false; });
  }

  generateForCycle(cycle: CycleStat): void {
    this.openConfirmPopup({
      title: 'Generate KPI Assignments',
      message: `Generate KPI assignments for ALL eligible users in cycle "${cycle.cycleName}"? KPI records will be created for every active user with a matching active template.`,
      actionLabel: 'Generate', dangerous: false,
      action: () => {
        this.generatingCycleId = cycle.cycleId;
        this.configService.generateAllAssignments(cycle.cycleCode).subscribe({
          next: (res) => {
            if (res.status === 'SUCCESS') {
              this.lastResult = {
                success: true,
                message: `Created ${res.createdAssignments} KPI records in cycle "${cycle.cycleName}" (processed ${res.processedUsers ?? 0}, skipped ${res.skippedUsers ?? 0}).`,
                count: res.createdAssignments
              };
            } else if (res.status === 'PARTIAL_SUCCESS') {
              this.lastResult = {
                success: false,
                message: `Partially completed: created ${res.createdAssignments} KPI records (processed ${res.processedUsers ?? 0}, skipped ${res.skippedUsers ?? 0}, failed ${res.failedUsers ?? 0}).`
              };
            } else {
              this.lastResult = { success: false, message: `${res.error ?? 'Generation failed.'}` };
            }
            this.loadAll();
          },
          error: (err) => { this.lastResult = { success: false, message: this.extractError(err, 'Failed.') }; }
        }).add(() => { this.generatingCycleId = null; });
      }
    });
  }

  generateByRole(): void {
    if (!this.selectedRoleCode || !this.selectedCycleCode) {
      this.lastResult = { success: false, message: 'Select both role and review cycle first.' };
      return;
    }

    this.lastResult = null;
    this.generating = true;
    this.checkingReadiness = true;

    this.configService.getRoleGenerationReadiness(this.selectedRoleCode, this.selectedCycleCode).subscribe({
      next: (readiness) => {
        this.roleReadiness = readiness;
        if (!readiness.ready) {
          const reason = readiness.reasons?.length
            ? readiness.reasons.join(' | ')
            : 'Role/cycle setup is not eligible for generation.';
          this.lastResult = { success: false, message: reason };
          this.generating = false;
          return;
        }

        this.configService.generateAssignments(this.selectedRoleCode, this.selectedCycleCode).subscribe({
          next: (res) => {
            if (res.status === 'SUCCESS') {
              this.lastResult = {
                success: true,
                message: `Created ${res.createdAssignments} KPI records for ${this.selectedRoleCode} in cycle "${this.selectedCycleCode}" (processed ${res.processedUsers ?? 0}, skipped ${res.skippedUsers ?? 0}).`,
                count: res.createdAssignments
              };
            } else if (res.status === 'PARTIAL_SUCCESS') {
              this.lastResult = {
                success: false,
                message: `Partially completed: created ${res.createdAssignments} KPI records for ${this.selectedRoleCode} (processed ${res.processedUsers ?? 0}, skipped ${res.skippedUsers ?? 0}, failed ${res.failedUsers ?? 0}).`
              };
            } else {
              this.lastResult = { success: false, message: `${res.error ?? 'Generation failed.'}` };
            }
            this.loadAll();
          },
          error: (err) => {
            this.lastResult = { success: false, message: this.extractError(err, 'Failed.') };
          }
        }).add(() => { this.generating = false; });
      },
      error: (err) => {
        this.lastResult = { success: false, message: this.extractError(err, 'Readiness check failed.') };
        this.generating = false;
      }
    }).add(() => { this.checkingReadiness = false; });
  }

  loadPreview(): void {
    if (!this.selectedPreviewCycle) return;
    this.previewing = true;
    this.previewData = null;
    this.configService.previewGeneration(this.selectedPreviewCycle).subscribe({
      next: (data) => { this.previewData = data; this.previewing = false; },
      error: (err) => { this.lastResult = { success: false, message: this.extractError(err, 'Preview failed.') }; this.previewing = false; }
    });
  }

  canGenerate(cycle: CycleStat): boolean {
    return ['OPEN', 'IN_PROGRESS'].includes(cycle.status) && !cycle.kpisGenerated;
  }

  statusClass(status: string): string {
    const m: Record<string, string> = {
      DRAFT: 'bg-slate-100 text-slate-600 border-slate-300',
      OPEN: 'bg-emerald-100 text-emerald-700 border-emerald-300',
      IN_PROGRESS: 'bg-blue-100 text-blue-700 border-blue-300',
      UNDER_REVIEW: 'bg-amber-100 text-amber-700 border-amber-300',
      CALIBRATION: 'bg-orange-100 text-orange-700 border-orange-300',
      CLOSED: 'bg-rose-100 text-rose-700 border-rose-300'
    };
    return m[status] ?? 'bg-slate-100 text-slate-500';
  }

  get readyRoles(): RoleStat[] { return this.roleStats.filter(r => r.readyToGenerate); }
  get eligibleCycles(): CycleStat[] { return this.cycleStats.filter(c => ['OPEN', 'IN_PROGRESS'].includes(c.status)); }

  private openConfirmPopup(cfg: { title: string; message: string; actionLabel: string; dangerous: boolean; action: () => void }): void {
    this.confirmPopup = { visible: true, ...cfg };
  }

  cancelConfirmPopup(): void {
    this.confirmPopup.visible = false;
    this.confirmPopup.action = null;
  }

  confirmPopupAction(): void {
    const a = this.confirmPopup.action;
    this.cancelConfirmPopup();
    if (a) a();
  }

  private extractError(err: any, fb: string): string {
    return extractHttpError(err, fb);
  }
}
