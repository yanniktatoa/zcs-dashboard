import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { Router, NavigationEnd } from '@angular/router';
import { Subscription, interval } from 'rxjs';
import { filter, startWith } from 'rxjs/operators';
import { TooltipModule } from 'primeng/tooltip';
import { ApiService } from '../../services/api.service';
import { ThemeService, ThemeMode } from '../../services/theme.service';

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [CommonModule, DatePipe, TooltipModule],
  templateUrl: './topbar.component.html',
  styleUrls: ['./topbar.component.scss']
})
export class TopbarComponent implements OnInit, OnDestroy {
  pageTitle = 'Etat du cluster';
  lastUpdate = new Date();
  podsSummary = { total: 0, running: 0, native: 0 };
  private subs: Subscription[] = [];

  titles: Record<string, string> = {
    '/cluster':'Etat du cluster', '/build':'Build JVM / Native',
    '/benchmarks':'Benchmarks', '/comparison':'Comparaison JVM vs Native',
    '/abtest':'A/B Test en direct', '/history':'Historique des runs',
    '/recommendations':'Recommandations de build',
    '/cost':'Simulation de cout cloud', '/settings':'Parametres'
  };

  themeMode: ThemeMode = 'dark';

  constructor(private router: Router, private api: ApiService, private theme: ThemeService) {}

  ngOnInit() {
    this.themeMode = this.theme.getCurrentMode();
    this.subs.push(this.theme.getMode().subscribe(m => this.themeMode = m));
    this.subs.push(this.router.events
      .pipe(filter(e => e instanceof NavigationEnd))
      .subscribe((e: any) => this.pageTitle = this.titles[e.url] || 'Dashboard'));
    this.subs.push(interval(15000).pipe(startWith(0)).subscribe(() => this.refresh()));
  }

  refresh() {
    this.api.getPods().subscribe(pods => {
      this.podsSummary = {
        total: pods.length,
        running: pods.filter(p => p.status === 'Running').length,
        native: pods.filter(p => p.mode === 'NATIVE').length
      };
      this.lastUpdate = new Date();
    });
  }

  toggleTheme() { this.theme.toggle(); }

  ngOnDestroy() { this.subs.forEach(s => s.unsubscribe()); }
}
