import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DropdownModule } from 'primeng/dropdown';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SliderModule } from 'primeng/slider';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { Subscription } from 'rxjs';
import { ApiService } from '../../services/api.service';
import { AbTestStateService, AbTestState } from '../../services/abtest-state.service';

@Component({
  selector: 'app-abtest',
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonModule, DropdownModule,
            InputNumberModule, InputTextModule, SliderModule, ToastModule],
  providers: [MessageService],
  templateUrl: './abtest.component.html',
  styleUrls: ['./abtest.component.scss']
})
export class AbtestComponent implements OnInit, OnDestroy {
  services = ['catalog-service','user-service','loan-service','library-gateway'];
  state!: AbTestState;
  private sub!: Subscription;
  private pollInterval?: any;

  constructor(
    private api: ApiService,
    private abState: AbTestStateService,
    private msg: MessageService
  ) {}

  ngOnInit() {
    this.sub = this.abState.getState().subscribe(s => this.state = s);
  }

  updateConfig() {
    this.abState.updateConfig(
      this.state.serviceName,
      this.state.totalRequests,
      this.state.jvmSplit,
      this.state.testPath
    );
  }

  setup() {
    this.api.setupAbTest(this.state.serviceName).subscribe({
      next: () => {
        this.abState.setSetupDone(true);
        this.msg.add({ severity:'info', summary:'Pods A/B deployes',
          detail:`${this.state.serviceName} JVM + Native en parallele` });
      },
      error: () => this.msg.add({ severity:'error', summary:'Erreur setup' })
    });
  }

  runTest() {
    this.abState.setRunning(true);
    this.abState.reset();
    this.api.runAbTest({
      serviceName: this.state.serviceName,
      totalRequests: this.state.totalRequests,
      jvmSplitPercent: this.state.jvmSplit,
      testPath: this.state.testPath
    }).subscribe({
      next: () => {
        this.pollInterval = setInterval(() => this.pollStats(), 2000);
        setTimeout(() => {
          clearInterval(this.pollInterval);
          this.abState.setRunning(false);
          this.pollStats();
        }, this.state.totalRequests * 80 + 5000);
      }
    });
  }

  pollStats() {
    this.api.getAbTestStats(this.state.serviceName).subscribe({
      next: s => this.abState.setStats(s)
    });
  }

  teardown() {
    this.api.teardownAbTest(this.state.serviceName).subscribe({
      next: () => {
        this.abState.setSetupDone(false);
        this.abState.reset();
        this.msg.add({ severity:'info', summary:'Pods A/B supprimes' });
      }
    });
  }

  getNative() { return this.state.stats.find(s => s.variant === 'NATIVE'); }
  getJvm() { return this.state.stats.find(s => s.variant === 'JVM'); }

  getImprovement() {
    const n = this.getNative(); const j = this.getJvm();
    if (!n || !j || j.avgLatencyMs === 0) return 0;
    return Math.round((1 - n.avgLatencyMs / j.avgLatencyMs) * 100);
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
    clearInterval(this.pollInterval);
  }
}
