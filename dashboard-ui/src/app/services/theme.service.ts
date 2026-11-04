import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type ThemeMode = 'dark' | 'light';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private mode$ = new BehaviorSubject<ThemeMode>(this.getInitialMode());

  private getInitialMode(): ThemeMode {
    const saved = localStorage.getItem('zcs-theme') as ThemeMode | null;
    if (saved === 'dark' || saved === 'light') return saved;
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  constructor() {
    this.apply(this.mode$.getValue());
  }

  getMode() { return this.mode$.asObservable(); }
  getCurrentMode() { return this.mode$.getValue(); }

  toggle() {
    const next: ThemeMode = this.mode$.getValue() === 'dark' ? 'light' : 'dark';
    this.setMode(next);
  }

  setMode(mode: ThemeMode) {
    this.mode$.next(mode);
    localStorage.setItem('zcs-theme', mode);
    this.apply(mode);
  }

  private apply(mode: ThemeMode) {
    document.body.classList.toggle('zcs-dark', mode === 'dark');
    document.body.classList.toggle('zcs-light', mode === 'light');
  }
}
