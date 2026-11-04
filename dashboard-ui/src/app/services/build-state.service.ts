import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export interface BuildState {
  running: boolean;
  serviceName: string;
  mode: string;
  progress: number;
  logs: string[];
  status: 'idle' | 'running' | 'success' | 'error';
}

@Injectable({ providedIn: 'root' })
export class BuildStateService {
  private initialState: BuildState = {
    running: false,
    serviceName: '',
    mode: '',
    progress: 0,
    logs: [],
    status: 'idle'
  };

  private state$ = new BehaviorSubject<BuildState>({ ...this.initialState });
  private eventSource?: EventSource;

  getState() { return this.state$.asObservable(); }
  getSnapshot() { return this.state$.getValue(); }

  startBuild(serviceName: string, mode: string) {
    this.state$.next({
      running: true,
      serviceName,
      mode,
      progress: 0,
      logs: [],
      status: 'running'
    });
  }

  addLog(message: string, progress: number) {
    const current = this.state$.getValue();
    this.state$.next({
      ...current,
      logs: [...current.logs, message],
      progress: progress || current.progress
    });
  }

  setSuccess() {
    const current = this.state$.getValue();
    this.state$.next({ ...current, running: false, status: 'success', progress: 100 });
    this.closeEventSource();
  }

  setError(message?: string) {
    const current = this.state$.getValue();
    const logs = message ? [...current.logs, message] : current.logs;
    this.state$.next({ ...current, running: false, status: 'error', logs });
    this.closeEventSource();
  }

  setEventSource(es: EventSource) {
    this.closeEventSource();
    this.eventSource = es;
  }

  closeEventSource() {
    this.eventSource?.close();
    this.eventSource = undefined;
  }

  cancel() {
    this.closeEventSource();
    const current = this.state$.getValue();
    this.state$.next({
      ...current,
      running: false,
      status: 'error',
      logs: [...current.logs, '--- Build annule par l\'utilisateur ---']
    });
  }

  reset() {
    this.closeEventSource();
    this.state$.next({ ...this.initialState });
  }
}
