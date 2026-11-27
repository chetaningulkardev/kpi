import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { catchError, map, Observable, throwError } from 'rxjs';

export interface LookupValue {
  id?: number;
  lookupType: string;
  code: string;
  label: string;
  description?: string;
  metadataJson?: string;
  active?: boolean;
}

export interface KpiTemplateItem {
  id?: number;
  kpiCode: string;
  kpiName: string;
  categoryCode: string;
  measurementTypeCode: string;
  targetValue?: string;
  weight: number;
  frequencyCode?: string;
}

export interface KpiTemplate {
  id?: number;
  code: string;
  name: string;
  roleCode: string;
  roleId?: number;
  roleName?: string;
  status?: string;
  active?: boolean;
  description?: string;
  items: KpiTemplateItem[];
}

export interface KpiCategory {
  id?: number;
  categoryCode: string;
  categoryName: string;
  description?: string;
  displayOrder?: number;
  active?: boolean;
}

export interface KpiFrequency {
  id?: number;
  frequencyCode: string;
  frequencyName: string;
  description?: string;
  displayOrder?: number;
  active?: boolean;
}

export interface KpiDataSource {
  id?: number;
  sourceCode: string;
  sourceName: string;
  description?: string;
  displayOrder?: number;
  active?: boolean;
}

export interface RoleOption {
  id: number;
  roleName: string;
  roleCode: string;
  active: boolean;
}

export interface KpiMaster {
  id?: number;
  kpiName: string;
  kpiCode: string;
  categoryId: number;
  categoryName?: string;
  description?: string;
  kpiType: string;
  targetType: string;
  measurementUnit: string;
  frequency: string;
  frequencyName?: string;
  dataSource: string;
  defaultWeight: number;
  active: boolean;
  roleIds?: number[];
  roleNames?: string[];
  roleCodes?: string[];
}

export interface ReviewCycle {
  id?: number;
  code: string;
  name: string;
  startDate: string;
  endDate: string;
  statusCode: string;
  description?: string;
  kpisGenerated?: boolean;
}

export interface KpiInstance {
  id: number;
  kpiName: string;
  kpiCode: string;
  categoryName: string;
  weight: number;
  target: string;
  frequency: string;
  status: string;
  actualValue?: number | null;
  comments?: string | null;
  justification?: string | null;
  rating?: number | null;
  reviewerComments?: string | null;
  user?: { id?: number; firstName?: string; first_name?: string; lastName?: string; last_name?: string; email?: string; };
  reviewer?: { id?: number; firstName?: string; first_name?: string; lastName?: string; last_name?: string; };
  reviewCycle?: { id?: number; cycleCode?: string; cycleName?: string; };
  snapshotData?: string;

  // Observed from a connected system and offered to the employee. Kept apart
  // from actualValue so the UI can present it as a suggestion rather than as
  // something the person entered.
  suggestedActualValue?: number | null;
  suggestedActualSource?: string | null;
  suggestedActualSummary?: string | null;
  suggestedActualUrl?: string | null;
  suggestedActualAt?: string | null;
}

/**
 * Why a collection attempt did or did not produce a number. Distinguished so
 * the UI can say "link your GitHub username", which is actionable, instead of
 * a generic failure.
 */
export type MetricOutcome =
  | 'COLLECTED'
  | 'NOT_AUTOMATED'
  | 'PROVIDER_UNAVAILABLE'
  | 'NO_IDENTITY'
  | 'NO_DATA'
  | 'FAILED';

export interface MetricCollectionResult {
  outcome: MetricOutcome;
  message: string;
  value?: number | null;
  source?: string | null;
  summary?: string | null;
  detailUrl?: string | null;
  collectedAt?: string | null;
}

/** Manager decision applied to a KPI. */
export type ReviewDecision = 'APPROVE' | 'REWORK' | 'REJECT';

export interface ManagerReviewPayload {
  /** Required for APPROVE; optional when sending work back. */
  rating?: number | null;
  reviewerComments?: string;
  decision?: ReviewDecision;
}

export interface BulkReviewResult {
  requested: number;
  succeeded: number;
  failed: number;
  failures: Array<{ kpiId: number; reason: string }>;
}

export interface KpiStatusHistoryEntry {
  id: number;
  fromStatus: string | null;
  toStatus: string;
  changedById: number | null;
  changedByName: string;
  changedAt: string;
  rating: number | null;
  note: string | null;
}

export interface KpiScorecard {
  id: number;
  userId: number | null;
  employeeName: string;
  role: string;
  employeeLevel: string;
  overallScore: number | null;
  maxScore: number | null;
  /** Server-computed. Null when the scorecard carries no weighted KPIs. */
  scorePercentage: number | null;
  categoryScores: Record<string, number>;
  status: string;
  calibrationNotes: string | null;
  calibratedByName: string | null;
  cycleCode: string | null;
  cycleName: string | null;
  cycleStartDate: string | null;
  cycleEndDate: string | null;
}

export interface CalibrationPayload {
  overallScore?: number | null;
  notes: string;
  status?: 'CALIBRATION' | 'FINAL' | 'PUBLISHED';
}

export interface ScorecardSummary {
  totalEmployees: number;
  averageScore: number;
  ratingDistribution: Record<string, number>;
  statusDistribution: Record<string, number>;
  minScore?: number;
  maxScore?: number;
}

export interface TeamAssignmentFlowRequest {
  userId: number;
  employeeName: string;
  email: string;
  pendingKpis: number;
  cycleCodes: string;
  statuses: string;
  reviewerId?: number | null;
  reviewerName?: string | null;
}

export interface TeamAssignmentFlow {
  transitionKey: string;
  fromRole: { roleCode: string; roleName: string };
  toRole: { roleCode: string; roleName: string };
  pendingUsers: number;
  pendingKpis: number;
  requests: TeamAssignmentFlowRequest[];
}

export interface TemplateReadiness {
  templateId: number;
  templateCode: string;
  templateName: string;
  activeItemCount: number;
  activeWeight: number;
  eligible: boolean;
  reasons: string[];
}

export interface RoleGenerationReadiness {
  roleCode: string;
  cycleCode: string;
  ready: boolean;
  cycleEligible: boolean;
  activeUsersInRole: number;
  reasons: string[];
  templates: TemplateReadiness[];
  error?: string;
}

export interface KpiTemplateItemCreateRequest {
  kpiMasterId: number;
  weight: number;
  target?: string;
}

export interface KpiTemplateItemResponse {
  id?: number;
  kpiMasterId?: number;
  kpiName?: string;
  kpiCode?: string;
  target?: string;
  weight?: number;
}

export interface KpiEvidence {
  id?: number;
  evidenceType: string;
  title: string;
  description?: string;
  contentUrl: string;
  fileType: string;
  fileSizeBytes: number;
  uploadedAt?: string;
  uploadedBy?: { id?: number; firstName?: string; lastName?: string; email?: string; };
}

/**
 * Payload shapes as they arrive over HTTP, before mapping.
 *
 * <p>Every field is optional and several appear under more than one name. That
 * is not defensiveness for its own sake: these endpoints have returned roles as
 * `role_name` and `roleName`, and template items nested under `items` and
 * `templateItems`, at different points. Writing the alternatives down is what
 * makes the mapping below readable — and means adding a field to a mapper now
 * fails to compile if the wire never carried it.
 */
interface WireRole {
  id?: number | string;
  role_name?: string;
  roleName?: string;
  role_code?: string;
  roleCode?: string;
  active?: boolean;
}

interface WireTemplateItem {
  id?: number;
  kpiCode?: string;
  kpiName?: string;
  categoryCode?: string;
  measurementTypeCode?: string;
  targetValue?: string | number;
  target?: string | number;
  weight?: number | string;
  frequencyCode?: string;
  kpiMaster?: {
    kpiCode?: string;
    kpiName?: string;
    measurementUnit?: string;
    category?: { categoryCode?: string };
    /** Sent as a nested object by one endpoint and a bare code by another. */
    frequency?: { frequencyCode?: string } | string;
  };
}

/** Reads a frequency code from either shape the API uses. */
function frequencyCodeOf(frequency: { frequencyCode?: string } | string | undefined): string {
  if (!frequency) {
    return '';
  }
  return typeof frequency === 'string' ? frequency : frequency.frequencyCode ?? '';
}

interface WireReviewCycle {
  id?: number;
  code?: string;
  cycleCode?: string;
  reviewCycleCode?: string;
  name?: string;
  cycleName?: string;
  startDate?: string;
  endDate?: string;
  statusCode?: string;
  status?: string;
  description?: string;
  kpisGenerated?: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class ConfigurationService {

  constructor(private http: HttpClient) {}

  getLookups(type: string): Observable<LookupValue[]> {
    return this.http.get<LookupValue[]>(`/api/config/lookups/${type}`);
  }

  getTemplates(): Observable<KpiTemplate[]> {
    return this.http.get<any[]>('/api/config/kpi-templates').pipe(
      map((templates) => (templates || []).map((template) => ({
        id: template.id,
        code: template.code ?? template.templateCode ?? '',
        name: template.name ?? template.templateName ?? '',
        roleCode: template.roleCode ?? template.role?.roleCode ?? template.role?.roleName ?? 'UNASSIGNED',
        roleId: template.role?.id,
        roleName: template.role?.roleName ?? '',
        status: template.status ?? '',
        active: !!template.active,
        description: template.description ?? '',
        items: (template.items ?? template.templateItems ?? []).map((item: WireTemplateItem) => ({
          id: item.id,
          kpiCode: item.kpiCode ?? item.kpiMaster?.kpiCode ?? '',
          kpiName: item.kpiName ?? item.kpiMaster?.kpiName ?? '',
          categoryCode: item.categoryCode ?? item.kpiMaster?.category?.categoryCode ?? '',
          measurementTypeCode: item.measurementTypeCode ?? item.kpiMaster?.measurementUnit ?? '',
          targetValue: item.targetValue ?? item.target ?? '',
          weight: Number(item.weight ?? 0),
          frequencyCode: item.frequencyCode ?? frequencyCodeOf(item.kpiMaster?.frequency)
        }))
      })))
    );
  }

  createTemplate(payload: { templateName: string; templateCode: string; description?: string; roleId?: number | null }): Observable<KpiTemplate> {
    return this.http.post<any>('/api/config/kpi-templates', payload).pipe(
      map((template) => ({
        id: template.id,
        code: template.templateCode ?? payload.templateCode,
        name: template.templateName ?? payload.templateName,
        roleCode: template.role?.roleCode ?? template.role?.roleName ?? 'UNASSIGNED',
        roleId: template.role?.id,
        roleName: template.role?.roleName ?? '',
        status: template.status ?? '',
        active: !!template.active,
        description: template.description ?? payload.description ?? '',
        items: []
      }))
    );
  }

  updateTemplate(id: number, payload: { templateName: string; description?: string; roleId?: number | null }): Observable<KpiTemplate> {
    return this.http.put<any>(`/api/config/kpi-templates/${id}`, payload).pipe(
      map((template) => ({
        id: template.id,
        code: template.templateCode ?? '',
        name: template.templateName ?? payload.templateName,
        roleCode: template.role?.roleCode ?? template.role?.roleName ?? 'UNASSIGNED',
        roleId: template.role?.id,
        roleName: template.role?.roleName ?? '',
        status: template.status ?? '',
        active: !!template.active,
        description: template.description ?? payload.description ?? '',
        items: []
      }))
    );
  }

  deleteTemplate(id: number): Observable<void> {
    return this.http.delete<void>(`/api/config/kpi-templates/${id}`);
  }

  getReviewCycles(): Observable<ReviewCycle[]> {
    const mapCycles = (cycles: WireReviewCycle[]) => (cycles || []).map((cycle) => ({
      id: cycle.id,
      code: cycle.code ?? cycle.cycleCode ?? cycle.reviewCycleCode ?? '',
      name: cycle.name ?? cycle.cycleName ?? '',
      startDate: cycle.startDate ?? '',
      endDate: cycle.endDate ?? '',
      statusCode: cycle.statusCode ?? cycle.status ?? 'DRAFT',
      description: cycle.description ?? '',
      kpisGenerated: cycle.kpisGenerated ?? false
    }));

    return this.http.get<any[]>('/api/config/review-cycles').pipe(
      catchError((err) => {
        if (err?.status === 401 || err?.status === 403) {
          return this.http.get<any[]>('/api/config/review-cycles/available');
        }
        return throwError(() => err);
      }),
      map(mapCycles)
    );
  }

  createReviewCycle(payload: {
    cycleName: string;
    cycleCode: string;
    startDate: string;
    endDate: string;
    status: string;
    description?: string;
  }): Observable<ReviewCycle> {
    return this.http.post<any>('/api/config/review-cycles', payload).pipe(
      map((cycle) => ({
        id: cycle.id,
        code: cycle.cycleCode,
        name: cycle.cycleName,
        startDate: cycle.startDate,
        endDate: cycle.endDate,
        statusCode: cycle.status,
        description: cycle.description ?? ''
      }))
    );
  }

  updateReviewCycle(id: number, payload: {
    cycleName?: string;
    startDate?: string;
    endDate?: string;
    status?: string;
    description?: string;
  }): Observable<ReviewCycle> {
    return this.http.put<any>(`/api/config/review-cycles/${id}`, payload).pipe(
      map((cycle) => ({
        id: cycle.id,
        code: cycle.cycleCode,
        name: cycle.cycleName,
        startDate: cycle.startDate,
        endDate: cycle.endDate,
        statusCode: cycle.status,
        description: cycle.description ?? ''
      }))
    );
  }

  deleteReviewCycle(id: number): Observable<void> {
    return this.http.delete<void>(`/api/config/review-cycles/${id}`);
  }

  getKpiCategories(): Observable<KpiCategory[]> {
    return this.http.get<any[]>('/api/kpi/categories').pipe(
      map((categories) => (categories || []).map((category) => ({
        id: category.id,
        categoryCode: category.categoryCode ?? '',
        categoryName: category.categoryName ?? '',
        description: category.description ?? '',
        displayOrder: category.displayOrder ?? 999,
        active: category.active ?? true
      })))
    );
  }

  createKpiCategory(payload: KpiCategory): Observable<KpiCategory> {
    return this.http.post<KpiCategory>('/api/kpi/categories', payload);
  }

  updateKpiCategory(id: number, payload: Partial<KpiCategory>): Observable<KpiCategory> {
    return this.http.put<KpiCategory>(`/api/kpi/categories/${id}`, payload);
  }

  deleteKpiCategory(id: number): Observable<void> {
    return this.http.delete<void>(`/api/kpi/categories/${id}`);
  }

  getKpiMasters(filters?: { categoryId?: number; roleId?: number }): Observable<KpiMaster[]> {
    let params = new HttpParams();
    if (filters?.categoryId) {
      params = params.set('categoryId', String(filters.categoryId));
    }
    if (filters?.roleId) {
      params = params.set('roleId', String(filters.roleId));
    }
    return this.http.get<any[]>('/api/kpi/masters', { params }).pipe(
      map((masters) => (masters || []).map((master) => ({
        id: master.id,
        kpiName: master.kpiName ?? '',
        kpiCode: master.kpiCode ?? '',
        categoryId: master.category?.id,
        categoryName: master.category?.categoryName ?? '',
        description: master.description ?? '',
        kpiType: master.kpiType ?? 'NUMBER',
        targetType: master.targetType ?? 'NUMBER_TARGET',
        measurementUnit: master.measurementUnit ?? '',
        frequency: master.frequency?.frequencyCode ?? master.frequency ?? 'MONTHLY',
        frequencyName: master.frequency?.frequencyName ?? master.frequency ?? 'MONTHLY',
        dataSource: master.dataSource ?? 'Manual',
        defaultWeight: Number(master.defaultWeight ?? 10),
        active: master.active ?? true,
        roleIds: (master.roles ?? []).map((role: WireRole) => Number(role.id)).filter((id: number) => !Number.isNaN(id)),
        roleNames: (master.roles ?? []).map((role: WireRole) => role.roleName).filter((name: string) => !!name),
        roleCodes: (master.roles ?? []).map((role: WireRole) => role.roleCode).filter((code: string) => !!code)
      })))
    );
  }

  getRoles(): Observable<RoleOption[]> {
    return this.http.get<any>('/api/roles').pipe(
      map((response) => (response?.data ?? []).map((role: WireRole) => ({
        id: Number(role.id),
        roleName: role.role_name ?? role.roleName ?? '',
        roleCode: role.role_code ?? role.roleCode ?? '',
        active: role.active ?? true
      } as RoleOption)))
    );
  }

  getKpiFrequencies(): Observable<KpiFrequency[]> {
    return this.http.get<any[]>('/api/kpi/frequencies').pipe(
      map((frequencies) => (frequencies || []).map((frequency) => ({
        id: frequency.id,
        frequencyCode: frequency.frequencyCode ?? '',
        frequencyName: frequency.frequencyName ?? '',
        description: frequency.description ?? '',
        displayOrder: frequency.displayOrder ?? 999,
        active: frequency.active ?? true
      })))
    );
  }

  createKpiFrequency(payload: KpiFrequency): Observable<KpiFrequency> {
    return this.http.post<KpiFrequency>('/api/kpi/frequencies', payload);
  }

  updateKpiFrequency(id: number, payload: Partial<KpiFrequency>): Observable<KpiFrequency> {
    return this.http.put<KpiFrequency>(`/api/kpi/frequencies/${id}`, payload);
  }

  deleteKpiFrequency(id: number): Observable<void> {
    return this.http.delete<void>(`/api/kpi/frequencies/${id}`);
  }

  getKpiDataSources(): Observable<KpiDataSource[]> {
    return this.http.get<any[]>('/api/kpi/data-sources').pipe(
      map((sources) => (sources || []).map((source) => ({
        id: source.id,
        sourceCode: source.sourceCode ?? '',
        sourceName: source.sourceName ?? '',
        description: source.description ?? '',
        displayOrder: source.displayOrder ?? 999,
        active: source.active ?? true
      })))
    );
  }

  createKpiDataSource(payload: KpiDataSource): Observable<KpiDataSource> {
    return this.http.post<KpiDataSource>('/api/kpi/data-sources', payload);
  }

  updateKpiDataSource(id: number, payload: Partial<KpiDataSource>): Observable<KpiDataSource> {
    return this.http.put<KpiDataSource>(`/api/kpi/data-sources/${id}`, payload);
  }

  deleteKpiDataSource(id: number): Observable<void> {
    return this.http.delete<void>(`/api/kpi/data-sources/${id}`);
  }

  createKpiMaster(payload: KpiMaster): Observable<KpiMaster> {
    return this.http.post<KpiMaster>('/api/kpi/masters', payload);
  }

  updateKpiMaster(id: number, payload: Partial<KpiMaster>): Observable<KpiMaster> {
    return this.http.put<KpiMaster>(`/api/kpi/masters/${id}`, payload);
  }

  deleteKpiMaster(id: number): Observable<void> {
    return this.http.delete<void>(`/api/kpi/masters/${id}`);
  }

  generateAssignments(roleCode: string, cycleCode: string): Observable<{
    createdAssignments: number;
    status: 'SUCCESS' | 'PARTIAL_SUCCESS' | 'FAILED';
    processedUsers?: number;
    skippedUsers?: number;
    failedUsers?: number;
    error?: string;
  }> {
    return this.http.post<any>(
      `/api/assignments/generate-by-role?roleCode=${encodeURIComponent(roleCode)}&cycleCode=${encodeURIComponent(cycleCode)}`,
      {}
    );
  }

  generateAllAssignments(cycleCode: string): Observable<{
    createdAssignments: number;
    status: 'SUCCESS' | 'PARTIAL_SUCCESS' | 'FAILED';
    processedUsers?: number;
    skippedUsers?: number;
    failedUsers?: number;
    error?: string;
  }> {
    return this.http.post<any>(
      `/api/assignments/generate?cycleCode=${encodeURIComponent(cycleCode)}`,
      {}
    );
  }

  getGenerationStats(): Observable<any> {
    return this.http.get<any>('/api/assignments/stats');
  }

  previewGeneration(cycleCode: string): Observable<any> {
    return this.http.get<any>(`/api/assignments/preview?cycleCode=${encodeURIComponent(cycleCode)}`);
  }

  getRoleGenerationReadiness(roleCode: string, cycleCode: string): Observable<RoleGenerationReadiness> {
    return this.http.get<RoleGenerationReadiness>(
      `/api/assignments/readiness-by-role?roleCode=${encodeURIComponent(roleCode)}&cycleCode=${encodeURIComponent(cycleCode)}`
    );
  }

  getTemplateById(id: number): Observable<KpiTemplate> {
    return this.http.get<any>(`/api/config/kpi-templates/${id}`).pipe(
      map((template) => ({
        id: template.id,
        code: template.code ?? template.templateCode ?? '',
        name: template.name ?? template.templateName ?? '',
        roleCode: template.roleCode ?? template.role?.roleCode ?? template.role?.roleName ?? 'UNASSIGNED',
        roleId: template.role?.id,
        roleName: template.role?.roleName ?? '',
        status: template.status ?? '',
        active: !!template.active,
        description: template.description ?? '',
        items: (template.items ?? template.templateItems ?? []).map((item: WireTemplateItem) => ({
          id: item.id,
          kpiCode: item.kpiCode ?? item.kpiMaster?.kpiCode ?? '',
          kpiName: item.kpiName ?? item.kpiMaster?.kpiName ?? '',
          categoryCode: item.categoryCode ?? item.kpiMaster?.category?.categoryCode ?? '',
          measurementTypeCode: item.measurementTypeCode ?? item.kpiMaster?.measurementUnit ?? '',
          targetValue: item.targetValue ?? item.target ?? '',
          weight: Number(item.weight ?? 0),
          frequencyCode: item.frequencyCode ?? frequencyCodeOf(item.kpiMaster?.frequency)
        }))
      }))
    );
  }

  addTemplateItem(templateId: number, payload: KpiTemplateItemCreateRequest): Observable<KpiTemplateItemResponse> {
    return this.http.post<any>(`/api/config/kpi-templates/${templateId}/items`, payload).pipe(
      map((item) => ({
        id: item.id,
        kpiMasterId: item.kpiMaster?.id,
        kpiCode: item.kpiMaster?.kpiCode,
        kpiName: item.kpiMaster?.kpiName,
        target: item.target,
        weight: Number(item.weight ?? 0)
      }))
    );
  }

  removeTemplateItem(templateId: number, itemId: number): Observable<void> {
    return this.http.delete<void>(`/api/config/kpi-templates/${templateId}/items/${itemId}`);
  }

  activateTemplate(id: number): Observable<void> {
    return this.http.post<void>(`/api/config/kpi-templates/${id}/activate`, {});
  }

  cloneTemplate(id: number): Observable<KpiTemplate> {
    return this.http.post<any>(`/api/config/kpi-templates/${id}/clone`, {}).pipe(
      map((template) => ({
        id: template.id,
        code: template.code ?? template.templateCode ?? '',
        name: template.name ?? template.templateName ?? '',
        roleCode: template.roleCode ?? template.role?.roleCode ?? template.role?.roleName ?? 'UNASSIGNED',
        roleId: template.role?.id,
        roleName: template.role?.roleName ?? '',
        status: template.status ?? '',
        active: !!template.active,
        description: template.description ?? '',
        items: []
      }))
    );
  }

  archiveTemplate(id: number): Observable<void> {
    return this.http.post<void>(`/api/config/kpi-templates/${id}/archive`, {});
  }

  openReviewCycle(id: number): Observable<ReviewCycle> {
    return this.http.post<any>(`/api/config/review-cycles/${id}/open`, {}).pipe(
      map((cycle) => ({
        id: cycle.id,
        code: cycle.cycleCode,
        name: cycle.cycleName,
        startDate: cycle.startDate,
        endDate: cycle.endDate,
        statusCode: cycle.status,
        description: cycle.description ?? '',
        kpisGenerated: cycle.kpisGenerated ?? false
      }))
    );
  }

  closeReviewCycle(id: number): Observable<ReviewCycle> {
    return this.http.post<any>(`/api/config/review-cycles/${id}/close`, {}).pipe(
      map((cycle) => ({
        id: cycle.id,
        code: cycle.cycleCode,
        name: cycle.cycleName,
        startDate: cycle.startDate,
        endDate: cycle.endDate,
        statusCode: cycle.status,
        description: cycle.description ?? '',
        kpisGenerated: cycle.kpisGenerated ?? false
      }))
    );
  }

  generateCycleKpis(id: number): Observable<string> {
    return this.http.post(`/api/config/review-cycles/${id}/generate-kpis`, {}, { responseType: 'text' });
  }

  // ── KPI Review Workflow ─────────────────────────────────────────────────────

  private mapKpiInstance(raw: Partial<KpiInstance> & { id: number }): KpiInstance {
    return {
      id: raw.id,
      kpiName: raw.kpiName ?? '',
      kpiCode: raw.kpiCode ?? '',
      categoryName: raw.categoryName ?? '',
      weight: Number(raw.weight ?? 0),
      target: raw.target ?? '',
      frequency: raw.frequency ?? '',
      status: raw.status ?? 'PENDING',
      actualValue: raw.actualValue ?? null,
      comments: raw.comments ?? null,
      justification: raw.justification ?? null,
      rating: raw.rating ?? null,
      reviewerComments: raw.reviewerComments ?? null,
      user: raw.user ?? undefined,
      reviewer: raw.reviewer ?? undefined,
      reviewCycle: raw.reviewCycle ?? undefined,
      snapshotData: raw.snapshotData,
      suggestedActualValue: raw.suggestedActualValue ?? null,
      suggestedActualSource: raw.suggestedActualSource ?? null,
      suggestedActualSummary: raw.suggestedActualSummary ?? null,
      suggestedActualUrl: raw.suggestedActualUrl ?? null,
      suggestedActualAt: raw.suggestedActualAt ?? null
    };
  }

  getMyKpis(cycleCode: string): Observable<KpiInstance[]> {
    return this.http.get<any[]>(`/api/kpi/my-kpis?cycleCode=${encodeURIComponent(cycleCode)}`).pipe(
      map((list) => (list || []).map(k => this.mapKpiInstance(k)))
    );
  }

  submitSelfAssessment(kpiId: number, payload: { actualValue?: number; comments?: string; justification?: string }): Observable<KpiInstance> {
    return this.http.post<any>(`/api/kpi/self-assess/${kpiId}`, payload).pipe(
      map(k => this.mapKpiInstance(k))
    );
  }

  /**
   * Asks the connected system for this KPI's current value.
   *
   * <p>Resolves rather than errors for "not automated" or "not connected" —
   * those are normal answers the user can act on, and the API returns 200 with
   * an outcome for them.</p>
   */
  collectMetric(kpiId: number): Observable<MetricCollectionResult> {
    return this.http.post<MetricCollectionResult>(`/api/kpi/${kpiId}/collect-metric`, {});
  }

  uploadEvidenceFile(kpiId: number, file: File, title?: string, description?: string): Observable<KpiEvidence> {
    const formData = new FormData();
    formData.append('file', file);
    if (title?.trim()) {
      formData.append('title', title.trim());
    }
    if (description?.trim()) {
      formData.append('description', description.trim());
    }

    return this.http.post<any>(`/api/kpi/review/evidence/upload/${kpiId}`, formData).pipe(
      map((evidence) => ({
        id: evidence.id,
        evidenceType: evidence.evidenceType ?? 'FILE',
        title: evidence.title ?? file.name,
        description: evidence.description ?? '',
        contentUrl: evidence.contentUrl ?? '',
        fileType: evidence.fileType ?? '',
        fileSizeBytes: Number(evidence.fileSizeBytes ?? file.size ?? 0),
        uploadedAt: evidence.uploadedAt,
        uploadedBy: evidence.uploadedBy
      }))
    );
  }

  getEvidenceForKpi(kpiId: number): Observable<KpiEvidence[]> {
    return this.http.get<any[]>(`/api/kpi/review/evidence/${kpiId}`).pipe(
      map((items) => (items || []).map((evidence) => ({
        id: evidence.id,
        evidenceType: evidence.evidenceType ?? 'FILE',
        title: evidence.title ?? '',
        description: evidence.description ?? '',
        contentUrl: evidence.contentUrl ?? '',
        fileType: evidence.fileType ?? '',
        fileSizeBytes: Number(evidence.fileSizeBytes ?? 0),
        uploadedAt: evidence.uploadedAt,
        uploadedBy: evidence.uploadedBy
      })))
    );
  }

  getPendingReviews(): Observable<KpiInstance[]> {
    return this.http.get<any[]>('/api/kpi/review/pending-reviews').pipe(
      map((list) => (list || []).map(k => this.mapKpiInstance(k)))
    );
  }

  // noinspection JSUnusedGlobalSymbols - used by manager review drill-down
  getTeamAssignmentFlow(): Observable<TeamAssignmentFlow[]> {
    return this.http.get<TeamAssignmentFlow[]>('/api/kpi/review/team-assignment-flow');
  }

  // noinspection JSUnusedGlobalSymbols - used by review screens
  getEmployeeKpisForReview(cycleCode: string, userId: number): Observable<KpiInstance[]> {
    return this.http.get<any[]>(`/api/kpi/review/employee-kpis?cycleCode=${encodeURIComponent(cycleCode)}&userId=${userId}`).pipe(
      map((list) => (list || []).map(k => this.mapKpiInstance(k)))
    );
  }

  submitManagerReview(kpiId: number, payload: ManagerReviewPayload): Observable<KpiInstance> {
    return this.http.post<any>(`/api/kpi/review/manager-review/${kpiId}`, payload).pipe(
      map(k => this.mapKpiInstance(k))
    );
  }

  /** Apply one decision (approve / rework / reject) to many KPIs at once. */
  submitBulkManagerReview(kpiIds: number[], review: ManagerReviewPayload): Observable<BulkReviewResult> {
    return this.http.post<BulkReviewResult>('/api/kpi/review/bulk-review', { kpiIds, review });
  }

  /** Full status-transition audit trail for a KPI. */
  getKpiStatusHistory(kpiId: number): Observable<KpiStatusHistoryEntry[]> {
    return this.http.get<KpiStatusHistoryEntry[]>(`/api/kpi/review/history/${kpiId}`);
  }

  deleteEvidence(evidenceId: number): Observable<void> {
    return this.http.delete<void>(`/api/kpi/review/evidence/item/${evidenceId}`);
  }

  downloadEvidenceFile(url: string): Observable<void> {
    return this.http.get(url, { responseType: 'blob' }).pipe(map(blob => {
      const objectUrl = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = objectUrl;
      link.download = decodeURIComponent(url.split('/').pop() || 'evidence');
      link.click();
      setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
    }));
  }

  // ─── Scorecards & calibration ──────────────────────────────────────────────

  getScorecards(cycleCode: string): Observable<KpiScorecard[]> {
    return this.http.get<KpiScorecard[]>(`/api/kpi/scorecards?cycleCode=${encodeURIComponent(cycleCode)}`);
  }

  getMyScorecard(cycleCode: string): Observable<KpiScorecard | null> {
    return this.http.get<KpiScorecard>(`/api/kpi/scorecards/me?cycleCode=${encodeURIComponent(cycleCode)}`);
  }

  getMyScorecardHistory(): Observable<KpiScorecard[]> {
    return this.http.get<KpiScorecard[]>('/api/kpi/scorecards/me/history');
  }

  generateScorecards(cycleCode: string): Observable<{ cycleCode: string; scorecards: number }> {
    return this.http.post<{ cycleCode: string; scorecards: number }>(
      `/api/kpi/scorecards/generate?cycleCode=${encodeURIComponent(cycleCode)}`, {});
  }

  getScorecardSummary(cycleCode: string): Observable<ScorecardSummary> {
    return this.http.get<ScorecardSummary>(`/api/kpi/scorecards/summary?cycleCode=${encodeURIComponent(cycleCode)}`);
  }

  calibrateScorecard(scorecardId: number, payload: CalibrationPayload): Observable<KpiScorecard> {
    return this.http.post<KpiScorecard>(`/api/kpi/scorecards/${scorecardId}/calibrate`, payload);
  }

  /** Server-generated CSV for a whole cycle. */
  exportScorecards(cycleCode: string): Observable<Blob> {
    return this.http.get(`/api/kpi/scorecards/export?cycleCode=${encodeURIComponent(cycleCode)}`, {
      responseType: 'blob'
    });
  }
}
