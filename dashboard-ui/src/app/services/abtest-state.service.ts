import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export interface AbTestState {
  serviceName: string;
  running: boolean;
  setupDone: boolean;
  stats: any[];
  totalRequests: number;
  jvmSplit: number;
  testPath: string;
}

@Injectable({ providedIn: 'root' })
export class AbTestStateService {
  private state$ = new BehaviorSubject<AbTestState>({
    serviceName: 'catalog-service',
    running: false,
    setupDone: false,
    stats: [],
    totalRequests: 100,
    jvmSplit: 50,
    testPath: '/actuator/health'
  });

  getState() { return this.state$.asObservable(); }
  getSnapshot() { return this.state$.getValue(); }

  updateConfig(serviceName: string, totalRequests: number, jvmSplit: number, testPath: string) {
    this.state$.next({ ...this.state$.getValue(), serviceName, totalRequests, jvmSplit, testPath });
  }

  setSetupDone(done: boolean) {
    this.state$.next({ ...this.state$.getValue(), setupDone: done });
  }

  setRunning(running: boolean) {
    this.state$.next({ ...this.state$.getValue(), running });
  }

  setStats(stats: any[]) {
    this.state$.next({ ...this.state$.getValue(), stats, running: false });
  }

  reset() {
    const cur = this.state$.getValue();
    this.state$.next({ ...cur, stats: [], running: false });
  }
}
