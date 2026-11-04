import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SliderModule } from 'primeng/slider';
import { InputNumberModule } from 'primeng/inputnumber';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-cost',
  standalone: true,
  imports: [CommonModule, FormsModule, TableModule, ButtonModule, SliderModule, InputNumberModule],
  templateUrl: './cost.component.html',
  styleUrls: ['./cost.component.scss']
})
export class CostComponent implements OnInit {
  invocations = 1000000;
  coldStartRatio = 5;
  estimates: any[] = [];
  totals: any[] = [];
  loading = false;
  providers = ['AWS Lambda','Google Cloud Run','Azure Container Apps'];

  constructor(private api: ApiService) {}
  ngOnInit() { this.simulate(); }

  simulate() {
    this.loading = true;
    this.api.simulateCost(this.invocations, this.coldStartRatio).subscribe({
      next: d => {
        this.estimates = d;
        const map = new Map<string,any>();
        d.forEach((e:any) => {
          const k = `${e.provider}|${e.mode}`;
          if (!map.has(k)) map.set(k,{provider:e.provider,mode:e.mode,total:0});
          map.get(k).total += e.totalCostUsd;
        });
        this.totals = Array.from(map.values()).map(t=>({...t,total:Math.round(t.total*100)/100}));
        this.loading = false;
      }
    });
  }

  getTotal(provider: string, mode: string) {
    return this.totals.find(t=>t.provider===provider&&t.mode===mode)?.total?.toFixed(2)||'0.00';
  }

  getSaving(provider: string): number {
    const j = this.totals.find(t=>t.provider===provider&&t.mode==='JVM');
    const n = this.totals.find(t=>t.provider===provider&&t.mode==='NATIVE');
    if (!j||!n||j.total===0) return 0;
    return Math.round((1-n.total/j.total)*100);
  }
}
