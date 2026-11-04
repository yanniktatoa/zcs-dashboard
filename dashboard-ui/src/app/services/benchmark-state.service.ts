import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export interface BenchmarkState {
  mode: string;
  running: boolean;
  progress: number;
  logs: string[];
  status: 'idle' | 'running' | 'success' | 'error';
  metrics: any[];
}

@Injectable({ providedIn: 'root' })
export class BenchmarkStateService {
  private jvmState$ = new BehaviorSubject<BenchmarkState>(this.initial('jvm'));
  private nativeState$ = new BehaviorSubject<BenchmarkState>(this.initial('native'));
  private jvmEs?: EventSource;
  private nativeEs?: EventSource;

  private initial(mode: string): BenchmarkState {
    return { mode, running: false, progress: 0, logs: [], status: 'idle', metrics: [] };
  }

  getJvm() { return this.jvmState$.asObservable(); }
  getNative() { return this.nativeState$.asObservable(); }
  getJvmSnapshot() { return this.jvmState$.getValue(); }
  getNativeSnapshot() { return this.nativeState$.getValue(); }

  start(mode: 'jvm' | 'native') {
    const state$ = mode === 'jvm' ? this.jvmState$ : this.nativeState$;
    state$.next({ ...this.initial(mode), running: true, status: 'running' });
  }

  addLog(mode: 'jvm' | 'native', message: string, progress: number) {
    const state$ = mode === 'jvm' ? this.jvmState$ : this.nativeState$;
    const cur = state$.getValue();
    state$.next({ ...cur, logs: [...cur.logs, message], progress: progress || cur.progress });
  }

  setSuccess(mode: 'jvm' | 'native') {
    const state$ = mode === 'jvm' ? this.jvmState$ : this.nativeState$;
    state$.next({ ...state$.getValue(), running: false, status: 'success', progress: 100 });
    this.closeEs(mode);
  }

  setError(mode: 'jvm' | 'native') {
    const state$ = mode === 'jvm' ? this.jvmState$ : this.nativeState$;
    state$.next({ ...state$.getValue(), running: false, status: 'error' });
    this.closeEs(mode);
  }

  setMetrics(mode: 'jvm' | 'native', metrics: any[]) {
    const state$ = mode === 'jvm' ? this.jvmState$ : this.nativeState$;
    state$.next({ ...state$.getValue(), metrics });
  }

  setEventSource(mode: 'jvm' | 'native', es: EventSource) {
    this.closeEs(mode);
    if (mode === 'jvm') this.jvmEs = es; else this.nativeEs = es;
  }

  closeEs(mode: 'jvm' | 'native') {
    if (mode === 'jvm') { this.jvmEs?.close(); this.jvmEs = undefined; }
    else { this.nativeEs?.close(); this.nativeEs = undefined; }
  }
}
