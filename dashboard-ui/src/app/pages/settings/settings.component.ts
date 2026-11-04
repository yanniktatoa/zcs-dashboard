import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { ToastModule } from 'primeng/toast';
import { SliderModule } from 'primeng/slider';
import { InputSwitchModule } from 'primeng/inputswitch';
import { MessageService } from 'primeng/api';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, InputTextModule, ButtonModule, ToastModule, SliderModule, InputSwitchModule],
  providers: [MessageService],
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.scss']
})
export class SettingsComponent implements OnInit {
  config: any = { basePath:'', resultsPath:'', namespace:'', kubeContext:'' };
  loading = false;
  saved = false;
  clusterReachable = true;

  autoRefresh = true;
  regressionAlerts = true;
  useMilliseconds = false;
  regressionThreshold = 20;
  maxNativeMemory = 200;

  constructor(private api: ApiService, private msg: MessageService) {}

  ngOnInit() {
    this.api.getConfig().subscribe({
      next: c => this.config = { kubeContext:'', ...c },
      error: () => this.clusterReachable = false
    });
    this.api.getClusterInfo().subscribe({
      next: () => this.clusterReachable = true,
      error: () => this.clusterReachable = false
    });
  }

  save() {
    this.loading = true;
    this.saved = false;
    let done = 0;
    const calls = [
      this.api.updateConfig('namespace', {namespace:this.config.namespace}),
      this.api.updateConfig('basePath', {basePath:this.config.basePath}),
      this.api.updateConfig('resultsPath', {resultsPath:this.config.resultsPath})
    ];
    calls.forEach(c => c.subscribe({
      next: () => {
        if (++done === calls.length) {
          this.loading = false;
          this.saved = true;
          this.msg.add({ severity:'success', summary:'Configuration mise à jour' });
          setTimeout(() => this.saved = false, 2500);
        }
      },
      error: () => this.loading = false
    }));
  }
}
