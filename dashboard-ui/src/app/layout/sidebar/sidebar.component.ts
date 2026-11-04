import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router, NavigationEnd, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss']
})
export class SidebarComponent implements OnInit, OnDestroy {
  activeRoute = '';
  clusterOnline = false;
  private sub!: Subscription;

  waveformBars: number[] = Array.from({ length: 40 }, () => 15 + Math.random() * 85);

  navGroups = [
    { label: 'Cluster', items: [
      { label: 'Etat du cluster', icon: 'pi pi-server', route: '/cluster' },
      { label: 'Build', icon: 'pi pi-box', route: '/build' }
    ]},
    { label: 'Mesures', items: [
      { label: 'Benchmarks', icon: 'pi pi-chart-bar', route: '/benchmarks' },
      { label: 'Comparaison', icon: 'pi pi-arrows-h', route: '/comparison' },
      { label: 'A/B Test', icon: 'pi pi-sliders-h', route: '/abtest' },
      { label: 'Historique', icon: 'pi pi-history', route: '/history' }
    ]},
    { label: 'Outils', items: [
      { label: 'Recommandations', icon: 'pi pi-lightbulb', route: '/recommendations' },
      { label: 'Cout cloud', icon: 'pi pi-dollar', route: '/cost' }
    ]}
  ];

  constructor(private router: Router, private api: ApiService) {}

  ngOnInit() {
    this.activeRoute = this.router.url;
    this.sub = this.router.events
      .pipe(filter(e => e instanceof NavigationEnd))
      .subscribe((e: any) => this.activeRoute = e.url);
    this.api.getClusterInfo().subscribe({
      next: () => this.clusterOnline = true,
      error: () => this.clusterOnline = false
    });
  }

  navigate(route: string) { this.router.navigate([route]); }
  isActive(route: string) { return this.activeRoute.startsWith(route); }
  ngOnDestroy() { this.sub?.unsubscribe(); }
}
