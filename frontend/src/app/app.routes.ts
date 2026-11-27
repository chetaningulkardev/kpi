import { Routes } from '@angular/router';
import { authGuard } from './services/auth.guard';
import { adminGuard } from './services/admin.guard';
import { managerGuard, scorecardGuard } from './services/manager.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', loadComponent: () => import('./components/login/login.component').then(m => m.LoginComponent) },
  { path: 'dashboard', loadComponent: () => import('./components/dashboard/dashboard.component').then(m => m.DashboardComponent), canActivate: [authGuard] },
  { path: 'profile', loadComponent: () => import('./components/profile/profile.component').then(m => m.ProfileComponent), canActivate: [authGuard] },
  { path: 'kpi-dashboard', loadComponent: () => import('./components/kpi-dashboard/kpi-dashboard.component').then(m => m.KPIDashboardComponent), canActivate: [authGuard] },
  { path: 'users', loadComponent: () => import('./components/user-management/user-management.component').then(m => m.UserManagementComponent), canActivate: [authGuard, adminGuard] },
  { path: 'roles', loadComponent: () => import('./components/role-management/role-management.component').then(m => m.RoleManagementComponent), canActivate: [authGuard, adminGuard] },
  { path: 'teams', loadComponent: () => import('./components/team-management/team-management.component').then(m => m.TeamManagementComponent), canActivate: [authGuard, adminGuard] },
  { path: 'tribes', loadComponent: () => import('./components/tribe-management/tribe-management.component').then(m => m.TribeManagementComponent), canActivate: [authGuard, adminGuard] },
  { path: 'portfolios', loadComponent: () => import('./components/portfolio-management/portfolio-management.component').then(m => m.PortfolioManagementComponent), canActivate: [authGuard, adminGuard] },
  { path: 'squads', loadComponent: () => import('./components/squad-management/squad-management.component').then(m => m.SquadManagementComponent), canActivate: [authGuard, adminGuard] },
  { path: 'assignments', loadComponent: () => import('./components/assignment-management/assignment-management.component').then(m => m.AssignmentManagementComponent), canActivate: [authGuard, adminGuard] },
  { path: 'organization', loadComponent: () => import('./components/organization-explorer/organization-explorer.component').then(m => m.OrganizationExplorerComponent), canActivate: [authGuard, adminGuard] },
  { path: 'org-chart', loadComponent: () => import('./components/org-chart/org-chart.component').then(m => m.OrgChartComponent), canActivate: [authGuard, adminGuard] },
  // Admin combined config page (full config in one view)
  { path: 'admin/config', loadComponent: () => import('./components/admin-config/admin-config.component').then(m => m.AdminConfigComponent), canActivate: [authGuard, adminGuard] },
  // KPI Management — each concern has its own dedicated route
  { path: 'kpi/cycles',     loadComponent: () => import('./components/kpi-management/kpi-review-cycles/kpi-review-cycles.component').then(m => m.KpiReviewCyclesComponent),   canActivate: [authGuard, adminGuard] },
  { path: 'kpi/categories', loadComponent: () => import('./components/kpi-management/kpi-categories/kpi-categories.component').then(m => m.KpiCategoriesComponent),           canActivate: [authGuard, adminGuard] },
  { path: 'kpi/frequencies',loadComponent: () => import('./components/kpi-management/kpi-frequencies/kpi-frequencies.component').then(m => m.KpiFrequenciesComponent),         canActivate: [authGuard, adminGuard] },
  { path: 'kpi/data-sources',loadComponent: () => import('./components/kpi-management/kpi-data-sources/kpi-data-sources.component').then(m => m.KpiDataSourcesComponent),      canActivate: [authGuard, adminGuard] },
  { path: 'kpi/masters',    loadComponent: () => import('./components/kpi-management/kpi-masters/kpi-masters.component').then(m => m.KpiMastersComponent),                   canActivate: [authGuard, adminGuard] },
  { path: 'kpi/templates',  loadComponent: () => import('./components/kpi-management/kpi-templates/kpi-templates.component').then(m => m.KpiTemplatesComponent),              canActivate: [authGuard, adminGuard] },
  { path: 'kpi/generate',   loadComponent: () => import('./components/kpi-management/kpi-generate/kpi-generate.component').then(m => m.KpiGenerateComponent),                canActivate: [authGuard, adminGuard] },
  // KPI Workflow — accessible to all authenticated users (employees & managers)
  { path: 'kpi/my-kpis',   loadComponent: () => import('./components/kpi-management/kpi-my-kpis/kpi-my-kpis.component').then(m => m.KpiMyKpisComponent),                      canActivate: [authGuard] },
  { path: 'kpi/my-performance', loadComponent: () => import('./components/kpi-management/my-performance/my-performance.component').then(m => m.MyPerformanceComponent),      canActivate: [authGuard] },
  { path: 'kpi/review',    loadComponent: () => import('./components/kpi-management/kpi-manager-review/kpi-manager-review.component').then(m => m.KpiManagerReviewComponent),  canActivate: [authGuard, managerGuard] },
  { path: 'kpi/team-flow', loadComponent: () => import('./components/kpi-management/team-assignment-flow/team-assignment-flow.component').then(m => m.TeamAssignmentFlowComponent), canActivate: [authGuard, managerGuard] },
  { path: 'kpi/scorecards',loadComponent: () => import('./components/kpi-management/kpi-scorecards/kpi-scorecards.component').then(m => m.KpiScorecardsComponent),               canActivate: [authGuard, scorecardGuard] },
  { path: 'notifications', loadComponent: () => import('./components/notifications/notifications.component').then(m => m.NotificationsComponent), canActivate: [authGuard] },
  { path: '**', redirectTo: 'login' }
];
