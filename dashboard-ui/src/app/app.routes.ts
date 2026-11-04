import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'cluster', pathMatch: 'full' },
  { path: 'cluster', loadComponent: () => import('./pages/cluster/cluster.component').then(m => m.ClusterComponent) },
  { path: 'build', loadComponent: () => import('./pages/build/build.component').then(m => m.BuildComponent) },
  { path: 'benchmarks', loadComponent: () => import('./pages/benchmarks/benchmarks.component').then(m => m.BenchmarksComponent) },
  { path: 'comparison', loadComponent: () => import('./pages/comparison/comparison.component').then(m => m.ComparisonComponent) },
  { path: 'abtest', loadComponent: () => import('./pages/abtest/abtest.component').then(m => m.AbtestComponent) },
  { path: 'history', loadComponent: () => import('./pages/history/history.component').then(m => m.HistoryComponent) },
  { path: 'recommendations', loadComponent: () => import('./pages/recommendations/recommendations.component').then(m => m.RecommendationsComponent) },
  { path: 'cost', loadComponent: () => import('./pages/cost/cost.component').then(m => m.CostComponent) },
  { path: 'settings', loadComponent: () => import('./pages/settings/settings.component').then(m => m.SettingsComponent) },
  { path: '**', redirectTo: 'cluster' }
];
