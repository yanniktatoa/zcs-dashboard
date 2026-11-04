import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { interval, Subscription } from 'rxjs';
import { startWith } from 'rxjs/operators';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { TooltipModule } from 'primeng/tooltip';
import { SkeletonModule } from 'primeng/skeleton';
import { MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-cluster',
  standalone: true,
  imports: [CommonModule, TableModule, ButtonModule, TagModule,
            DialogModule, TooltipModule, SkeletonModule, ToastModule],
  providers: [MessageService],
  templateUrl: './cluster.component.html',
  styleUrls: ['./cluster.component.scss']
})
export class ClusterComponent implements OnInit, OnDestroy {
  pods: any[] = [];
  metrics: any[] = [];
  clusterInfo: any = {};
  loading = true;
  showLogsDialog = false;
  selectedPod: any = null;
  podLogs = '';
  logsLoading = false;
  private logsEs?: EventSource;
  private pollSub!: Subscription;

  constructor(private api: ApiService, private msg: MessageService) {}

  ngOnInit() {
    this.api.getClusterInfo().subscribe(i => this.clusterInfo = i);
    this.pollSub = interval(10000).pipe(startWith(0)).subscribe(() => {
      this.loadPods();
      this.loadMetrics();
    });
  }

  loadPods() {
    this.api.getPods().subscribe({
      next: p => { this.pods = p; this.loading = false; },
      error: () => this.loading = false
    });
  }

  loadMetrics() {
    this.api.getMonitoringMetrics().subscribe({ next: m => this.metrics = m, error: () => {} });
  }

  getMetric(svc: string) { return this.metrics.find(m => m.service === svc) || {}; }

  switchMode(pod: any) {
    const newMode = pod.mode === 'NATIVE' ? 'JVM' : 'NATIVE';
    this.api.switchMode(pod.service, newMode).subscribe({
      next: () => {
        this.msg.add({ severity:'success', summary:'Mode change', detail:`${pod.service} → ${newMode}` });
        setTimeout(() => this.loadPods(), 3000);
      },
      error: () => this.msg.add({ severity:'error', summary:'Erreur', detail:'Echec du changement de mode' })
    });
  }

  restartPod(pod: any) {
    this.api.restartDeployment(pod.service).subscribe({
      next: () => {
        this.msg.add({ severity:'info', summary:'Redemarrage', detail:`${pod.service} redemarre` });
        setTimeout(() => this.loadPods(), 5000);
      }
    });
  }

  openLogs(pod: any) {
    this.selectedPod = pod;
    this.showLogsDialog = true;
    this.podLogs = '';
    this.logsLoading = true;
    this.logsEs?.close();
    this.logsEs = this.api.createEventSource(`/api/monitoring/logs/${pod.name}/stream`);
    this.logsEs.onmessage = (e) => { this.podLogs += e.data + '\n'; this.logsLoading = false; };
    this.logsEs.onerror = () => { this.logsLoading = false; };
  }

  closeLogs() { this.showLogsDialog = false; this.logsEs?.close(); this.podLogs = ''; }

  getSeverity(status: string): any {
    return status === 'Running' ? 'success' : status === 'Pending' ? 'warning' : 'danger';
  }

  isApp(pod: any) {
    return !['mysql','redis','kafka','zookeeper','zipkin'].some(i => pod.service.includes(i));
  }

  get appPods() { return this.pods.filter(p => this.isApp(p)); }
  get infraPods() { return this.pods.filter(p => !this.isApp(p)); }
  get nativeCount() { return this.appPods.filter(p => p.mode === 'NATIVE').length; }
  get runningCount() { return this.pods.filter(p => p.status === 'Running').length; }

  ngOnDestroy() { this.pollSub?.unsubscribe(); this.logsEs?.close(); }
}
