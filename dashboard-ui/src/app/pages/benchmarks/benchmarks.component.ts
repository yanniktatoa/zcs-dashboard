import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { ProgressBarModule } from 'primeng/progressbar';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { Subscription } from 'rxjs';
import { ApiService } from '../../services/api.service';
import { BenchmarkStateService, BenchmarkState } from '../../services/benchmark-state.service';

@Component({
  selector: 'app-benchmarks',
  standalone: true,
  imports: [CommonModule, ButtonModule, TableModule, ProgressBarModule, ToastModule],
  providers: [MessageService],
  templateUrl: './benchmarks.component.html',
  styleUrls: ['./benchmarks.component.scss']
})
export class BenchmarksComponent implements OnInit, OnDestroy {
  jvmState!: BenchmarkState;
  nativeState!: BenchmarkState;
  private subs: Subscription[] = [];
  services = ['catalog-service','user-service','loan-service','library-gateway'];

  constructor(
    private api: ApiService,
    private benchState: BenchmarkStateService,
    private msg: MessageService
  ) {}

  ngOnInit() {
    this.subs.push(this.benchState.getJvm().subscribe(s => this.jvmState = s));
    this.subs.push(this.benchState.getNative().subscribe(s => this.nativeState = s));

    if (this.jvmState.metrics.length === 0 && this.nativeState.metrics.length === 0) {
      this.loadMetrics();
    }
  }

  loadMetrics() {
    this.api.getMetrics().subscribe({
      next: (d: any) => {
        this.benchState.setMetrics('jvm', d.jvm || []);
        this.benchState.setMetrics('native', d.native || []);
      }
    });
  }

  startBenchmark(mode: 'jvm' | 'native') {
    const state = mode === 'jvm' ? this.jvmState : this.nativeState;
    if (state.running) return;

    this.benchState.start(mode);

    // Ouvrir le stream AVANT de lancer
    const es = this.api.createEventSource(`/api/benchmark/stream/${mode}`);
    this.benchState.setEventSource(mode, es);

    es.onmessage = (e) => {
      const ev = JSON.parse(e.data);
      this.benchState.addLog(mode, ev.message, ev.progress);
      if (ev.type === 'SUCCESS') {
        this.benchState.setSuccess(mode);
        this.msg.add({ severity:'success', summary:'Benchmark termine', detail:`${mode.toUpperCase()} termine` });
        this.loadMetrics();
      }
      if (ev.type === 'ERROR') { this.benchState.setError(mode); }
    };

    es.onerror = () => { if (state.running) this.benchState.setError(mode); };

    setTimeout(() => {
      this.api.startBenchmark(mode).subscribe({
        error: () => this.benchState.setError(mode)
      });
    }, 500);
  }

  getM(metrics: any[], svc: string) { return metrics.find(m => m.service === svc) || {}; }

  ngOnDestroy() { this.subs.forEach(s => s.unsubscribe()); }
}
