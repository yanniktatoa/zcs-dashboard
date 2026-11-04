import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, interval, Subscription } from 'rxjs';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class OllamaStatusService implements OnDestroy {
  private available$ = new BehaviorSubject<boolean>(false);
  private sub!: Subscription;

  constructor(private api: ApiService) {
    this.check();
    // Re-vérifier toutes les 30 secondes
    this.sub = interval(30000).subscribe(() => this.check());
  }

  private check() {
    this.api.getAiStatus().subscribe({
      next: (s: any) => this.available$.next(s.available),
      error: () => this.available$.next(false)
    });
  }

  getStatus() { return this.available$.asObservable(); }
  isAvailable() { return this.available$.getValue(); }

  ngOnDestroy() { this.sub?.unsubscribe(); }
}
