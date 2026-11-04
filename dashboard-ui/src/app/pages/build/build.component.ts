import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DropdownModule } from 'primeng/dropdown';
import { ProgressBarModule } from 'primeng/progressbar';
import { DividerModule } from 'primeng/divider';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { MessageService, ConfirmationService } from 'primeng/api';
import { Subscription } from 'rxjs';
import { ApiService } from '../../services/api.service';
import { BuildStateService, BuildState } from '../../services/build-state.service';

@Component({
  selector: 'app-build',
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonModule, DropdownModule,
            ProgressBarModule, DividerModule, ToastModule, ConfirmDialogModule],
  providers: [MessageService, ConfirmationService],
  templateUrl: './build.component.html',
  styleUrls: ['./build.component.scss']
})
export class BuildComponent implements OnInit, OnDestroy {
  services = ['catalog-service','user-service','loan-service','library-gateway'];
  selectedService = 'catalog-service';
  state!: BuildState;
  private sub!: Subscription;

  graalSteps = ['[1/8] Initializing','[2/8] Performing analysis',
    '[3/8] Building universe','[4/8] Parsing methods',
    '[5/8] Inlining methods','[6/8] Compiling methods',
    '[7/8] Layouting methods','[8/8] Creating image'];

  constructor(
    private api: ApiService,
    private buildState: BuildStateService,
    private msg: MessageService,
    private confirm: ConfirmationService
  ) {}

  ngOnInit() {
    this.sub = this.buildState.getState().subscribe(s => {
      this.state = s;
      if (s.serviceName) this.selectedService = s.serviceName;
    });
  }

  startBuild(mode: 'jvm' | 'native') {
    if (this.state?.running) return;
    this.buildState.startBuild(this.selectedService, mode);

    const call = mode === 'jvm'
      ? this.api.startJvmBuild(this.selectedService)
      : this.api.startNativeBuild(this.selectedService);

    call.subscribe({
      next: () => {
        const es = this.api.createEventSource(`/api/build/stream/${this.selectedService}`);
        this.buildState.setEventSource(es);
        es.onmessage = (e) => {
          const ev = JSON.parse(e.data);
          this.buildState.addLog(ev.message, ev.progress);
          if (ev.type === 'SUCCESS') {
            this.buildState.setSuccess();
            this.msg.add({ severity:'success', summary:'Build termine',
              detail:`${this.selectedService} (${mode.toUpperCase()})` });
          }
          if (ev.type === 'ERROR') {
            this.buildState.setError(ev.message);
            this.msg.add({ severity:'error', summary:'Erreur build', detail:ev.message });
          }
        };
        es.onerror = () => {
          if (this.state.running) this.buildState.setError();
        };
      },
      error: () => this.buildState.setError('Impossible de lancer le build')
    });
  }

  cancelBuild() {
    this.confirm.confirm({
      message: 'Annuler le build en cours ?',
      header: 'Confirmation',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.api.cancelBuild(this.state.serviceName).subscribe();
        this.buildState.cancel();
        this.msg.add({ severity:'warn', summary:'Build annule' });
      }
    });
  }

  clearLogs() { this.buildState.reset(); }

  getCurrentStep(): number {
    const logs = this.state?.logs?.join(' ') || '';
    for (let i = 8; i >= 1; i--) if (logs.includes(`[${i}/8]`)) return i;
    return 0;
  }

  getStepClass(i: number): string {
    const cur = this.getCurrentStep();
    if (i < cur) return 'pi pi-check-circle step-done';
    if (i === cur) return 'pi pi-spin pi-spinner step-current';
    return 'pi pi-circle step-pending';
  }

  ngOnDestroy() { this.sub?.unsubscribe(); }
}
