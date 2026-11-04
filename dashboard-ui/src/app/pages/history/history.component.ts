import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SelectButtonModule } from 'primeng/selectbutton';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-history',
  standalone: true,
  imports: [CommonModule, FormsModule, SelectButtonModule],
  templateUrl: './history.component.html',
  styleUrls: ['./history.component.scss']
})
export class HistoryComponent implements OnInit {
  history: any[] = [];
  trends: any = {};
  alerts: any[] = [];
  loading = true;
  selectedMode = 'native';
  modes = [{label:'Native',value:'native'},{label:'JVM',value:'jvm'}];
  services = ['catalog-service','user-service','loan-service','library-gateway'];
  metrics = ['coldStart','memory','imageSize'];

  constructor(private api: ApiService) {}
  ngOnInit() { this.load(); }

  load() {
    this.loading = true;
    this.api.getHistory(this.selectedMode).subscribe({ next: d => { this.history=d; this.loading=false; }, error: () => this.loading=false });
    this.api.getTrends(this.selectedMode).subscribe(t => this.trends=t);
    this.api.checkRegression(this.selectedMode).subscribe(a => this.alerts=a);
  }

  getTrendIcon(l: string) {
    return l==='IMPROVING'?'pi pi-arrow-down text-success':l==='DEGRADING'?'pi pi-arrow-up text-danger':'pi pi-minus text-muted';
  }

  getTrend(svc: string, metric: string) { return this.trends[svc]?.[metric]||{label:'STABLE',dataPoints:0}; }
  getTrendClass(l: string) { return l==='IMPROVING'?'text-success':l==='DEGRADING'?'text-danger':'text-muted'; }

  getColdHistory() { return this.history.filter(h => h.metric==='coldStart'); }

  getSparklinePath(dataPoints: any[]): string {
    if (!dataPoints || dataPoints.length === 0) return '';
    const values = dataPoints.map(dp => dp.value || 0);
    const max = Math.max(...values, 0.01);
    const w = 120, h = 32, pad = 3;
    const pts = values.map((v, i) => {
      const x = pad + (i / Math.max(values.length - 1, 1)) * (w - pad * 2);
      const y = h - pad - (v / max) * (h - pad * 2);
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    });
    return 'M' + pts.join(' L');
  }

  getSparklineLastPoint(dataPoints: any[]): { x: number; y: number } {
    if (!dataPoints || dataPoints.length === 0) return { x: 0, y: 0 };
    const values = dataPoints.map(dp => dp.value || 0);
    const max = Math.max(...values, 0.01);
    const w = 120, h = 32, pad = 3;
    const i = values.length - 1;
    const x = pad + (i / Math.max(values.length - 1, 1)) * (w - pad * 2);
    const y = h - pad - (values[i] / max) * (h - pad * 2);
    return { x, y };
  }

  hasAlert(dataPoints: any[]): boolean {
    return (dataPoints || []).some(dp => dp.regression);
  }
}
