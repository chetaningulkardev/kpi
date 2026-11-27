import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Centralized API service for admin CRUD operations
 * (Users, Roles, Teams). The JWT token is attached automatically
 * by HttpTokenInterceptor.
 */
@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly base = '/api';

  constructor(private http: HttpClient) {}

  // ---------- Users ----------
  getUsers(): Observable<any> {
    return this.http.get<any>(`${this.base}/users`);
  }
  createUser(payload: any): Observable<any> {
    return this.http.post<any>(`${this.base}/users`, payload);
  }
  updateUser(id: number, payload: any): Observable<any> {
    return this.http.put<any>(`${this.base}/users/${id}`, payload);
  }
  deleteUser(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/users/${id}`);
  }
  activateUser(id: number): Observable<any> {
    return this.http.patch<any>(`${this.base}/users/${id}/activate`, {});
  }
  deactivateUser(id: number): Observable<any> {
    return this.http.patch<any>(`${this.base}/users/${id}/deactivate`, {});
  }

  // ---------- Roles ----------
  getRoles(): Observable<any> {
    return this.http.get<any>(`${this.base}/roles`);
  }
  getRoleHierarchy(): Observable<any> {
    return this.http.get<any>(`${this.base}/roles/hierarchy`);
  }
  createRole(payload: any): Observable<any> {
    return this.http.post<any>(`${this.base}/roles`, payload);
  }
  updateRole(id: number, payload: any): Observable<any> {
    return this.http.put<any>(`${this.base}/roles/${id}`, payload);
  }
  deleteRole(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/roles/${id}`);
  }
  enableRole(id: number): Observable<any> {
    return this.http.patch<any>(`${this.base}/roles/${id}/enable`, {});
  }
  disableRole(id: number): Observable<any> {
    return this.http.patch<any>(`${this.base}/roles/${id}/disable`, {});
  }

  // ---------- Teams ----------
  getTeams(): Observable<any> {
    return this.http.get<any>(`${this.base}/teams`);
  }
  createTeam(payload: any): Observable<any> {
    return this.http.post<any>(`${this.base}/teams`, payload);
  }
  updateTeam(id: number, payload: any): Observable<any> {
    return this.http.put<any>(`${this.base}/teams/${id}`, payload);
  }
  deleteTeam(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/teams/${id}`);
  }
  enableTeam(id: number): Observable<any> {
    return this.http.patch<any>(`${this.base}/teams/${id}/enable`, {});
  }
  disableTeam(id: number): Observable<any> {
    return this.http.patch<any>(`${this.base}/teams/${id}/disable`, {});
  }

  // ---------- Tribes ----------
  getTribes(): Observable<any> {
    return this.http.get<any>(`${this.base}/tribes`);
  }
  createTribe(payload: any): Observable<any> {
    return this.http.post<any>(`${this.base}/tribes`, payload);
  }
  updateTribe(id: number, payload: any): Observable<any> {
    return this.http.put<any>(`${this.base}/tribes/${id}`, payload);
  }
  deleteTribe(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/tribes/${id}`);
  }
  enableTribe(id: number): Observable<any> {
    return this.http.patch<any>(`${this.base}/tribes/${id}/enable`, {});
  }
  disableTribe(id: number): Observable<any> {
    return this.http.patch<any>(`${this.base}/tribes/${id}/disable`, {});
  }
  assignTribeLeads(id: number, leadIds: number[]): Observable<any> {
    return this.http.put<any>(`${this.base}/tribes/${id}/leads`, leadIds);
  }

  // ---------- Portfolios ----------
  getPortfolios(tribeId?: number): Observable<any> {
    const q = tribeId ? `?tribeId=${tribeId}` : '';
    return this.http.get<any>(`${this.base}/portfolios${q}`);
  }
  createPortfolio(payload: any): Observable<any> {
    return this.http.post<any>(`${this.base}/portfolios`, payload);
  }
  updatePortfolio(id: number, payload: any): Observable<any> {
    return this.http.put<any>(`${this.base}/portfolios/${id}`, payload);
  }
  deletePortfolio(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/portfolios/${id}`);
  }
  enablePortfolio(id: number): Observable<any> {
    return this.http.patch<any>(`${this.base}/portfolios/${id}/enable`, {});
  }
  disablePortfolio(id: number): Observable<any> {
    return this.http.patch<any>(`${this.base}/portfolios/${id}/disable`, {});
  }
  assignPortfolioLeads(id: number, leadIds: number[]): Observable<any> {
    return this.http.put<any>(`${this.base}/portfolios/${id}/leads`, leadIds);
  }

  // ---------- Squads ----------
  getSquads(portfolioId?: number): Observable<any> {
    const q = portfolioId ? `?portfolioId=${portfolioId}` : '';
    return this.http.get<any>(`${this.base}/squads${q}`);
  }
  createSquad(payload: any): Observable<any> {
    return this.http.post<any>(`${this.base}/squads`, payload);
  }
  updateSquad(id: number, payload: any): Observable<any> {
    return this.http.put<any>(`${this.base}/squads/${id}`, payload);
  }
  deleteSquad(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/squads/${id}`);
  }
  enableSquad(id: number): Observable<any> {
    return this.http.patch<any>(`${this.base}/squads/${id}/enable`, {});
  }
  disableSquad(id: number): Observable<any> {
    return this.http.patch<any>(`${this.base}/squads/${id}/disable`, {});
  }
  assignSquadLeads(id: number, leadIds: number[]): Observable<any> {
    return this.http.put<any>(`${this.base}/squads/${id}/leads`, leadIds);
  }

  // ---------- Assignments ----------
  getAllAssignments(): Observable<any> {
    return this.http.get<any>(`${this.base}/assignments`);
  }
  getUserAssignments(userId: number): Observable<any> {
    return this.http.get<any>(`${this.base}/assignments/user/${userId}`);
  }
  assignUserToTribe(payload: any): Observable<any> {
    return this.http.post<any>(`${this.base}/assignments/tribe`, payload);
  }
  assignUserToPortfolio(payload: any): Observable<any> {
    return this.http.post<any>(`${this.base}/assignments/portfolio`, payload);
  }
  assignUserToSquad(payload: any): Observable<any> {
    return this.http.post<any>(`${this.base}/assignments/squad`, payload);
  }
  removeTribeAssignment(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/assignments/tribe/${id}`);
  }
  removePortfolioAssignment(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/assignments/portfolio/${id}`);
  }
  removeSquadAssignment(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/assignments/squad/${id}`);
  }
  setUserAssignments(userId: number, payload: { tribeIds?: number[]; portfolioIds?: number[]; squadIds?: number[] }): Observable<any> {
    return this.http.post<any>(`${this.base}/assignments/user/${userId}`, payload);
  }

  // ---------- Organization ----------
  getOrganizationTree(): Observable<any> {
    return this.http.get<any>(`${this.base}/organization/tree`);
  }
}

