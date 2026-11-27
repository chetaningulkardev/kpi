import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { KPI } from '../models/kpi.model';

@Injectable({
  providedIn: 'root'
})
export class KPIService {
  private apiUrl = '/api/kpis';

  constructor(private http: HttpClient) { }

  getAllKPIs(): Observable<KPI[]> {
    return this.http.get<any[]>(this.apiUrl).pipe(
      map((kpis) => (kpis || []).map((kpi) => this.mapApiKpiToUi(kpi)))
    );
  }

  getKPIById(id: number): Observable<KPI> {
    return this.http.get<any>(`${this.apiUrl}/${id}`).pipe(
      map((kpi) => this.mapApiKpiToUi(kpi))
    );
  }

  getKPIsByCategory(category: string): Observable<KPI[]> {
    return this.getAllKPIs().pipe(
      map((kpis) => kpis.filter((kpi) => kpi.category === category))
    );
  }

  getKPIsByStatus(status: string): Observable<KPI[]> {
    return this.http.get<any[]>(`${this.apiUrl}/status/${status}`).pipe(
      map((kpis) => (kpis || []).map((kpi) => this.mapApiKpiToUi(kpi)))
    );
  }

  createKPI(kpi: KPI): Observable<KPI> {
    return this.http.post<KPI>(this.apiUrl, kpi);
  }

  updateKPI(id: number, kpi: KPI): Observable<KPI> {
    return this.http.put<KPI>(`${this.apiUrl}/${id}`, kpi);
  }

  deleteKPI(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  private mapApiKpiToUi(kpi: any): KPI {
    const targetValue = Number(kpi?.target ?? 0);
    const currentValue = Number(kpi?.actualValue ?? kpi?.rating ?? 0);

    return {
      id: kpi?.id,
      name: kpi?.name ?? kpi?.kpiName ?? 'Unnamed KPI',
      description: kpi?.description ?? kpi?.kpiMaster?.description ?? '',
      value: Number.isFinite(currentValue) ? currentValue : 0,
      unit: kpi?.unit ?? kpi?.kpiMaster?.measurementUnit ?? '',
      target: Number.isFinite(targetValue) ? targetValue : 0,
      status: this.resolveStatus(kpi),
      category: kpi?.category ?? kpi?.categoryName ?? 'UNCATEGORIZED',
      lastUpdated: kpi?.updatedAt ? Date.parse(kpi.updatedAt) : Date.now()
    };
  }

  private resolveStatus(kpi: any): KPI['status'] {
    const rawStatus = String(kpi?.status ?? '').toUpperCase();
    if (rawStatus === 'APPROVED') return 'EXCELLENT';
    if (rawStatus === 'UNDER_REVIEW') return 'GOOD';
    if (rawStatus === 'SUBMITTED') return 'WARNING';
    if (rawStatus === 'REWORK_REQUIRED' || rawStatus === 'REJECTED') return 'CRITICAL';
    return 'GOOD';
  }
}

