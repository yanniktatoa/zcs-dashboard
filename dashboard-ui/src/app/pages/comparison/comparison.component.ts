import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { ChartModule } from 'primeng/chart';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-comparison',
  standalone: true,
  imports: [CommonModule, TableModule, ButtonModule, ChartModule, ToastModule],
  providers: [MessageService],
  templateUrl: './comparison.component.html',
  styleUrls: ['./comparison.component.scss']
})
export class ComparisonComponent implements OnInit {
  comparison: any = {};
  services: string[] = [];
  loading = true;
  chartData: any = {};
  chartOptions: any = {};
  exporting = false;

  constructor(private api: ApiService, private msg: MessageService) {}

  ngOnInit() {
    this.chartOptions = {
      responsive:true, maintainAspectRatio:false,
      plugins:{
        legend:{
          labels:{ font:{ size:11, family: 'Inter' }, color:'#9CA3AF', usePointStyle:true, boxWidth:8, boxHeight:8 }
        }
      },
      scales:{
        y:{
          title:{ display:true, text:'Secondes', color:'#5A6472', font:{ size:11 } },
          ticks:{ color:'#5A6472', font:{ size:10 } },
          grid:{ color:'#1C2028' }
        },
        x:{
          ticks:{ color:'#9CA3AF', font:{ size:11 } },
          grid:{ display:false }
        }
      }
    };
    this.api.getComparison().subscribe({
      next: d => {
        this.comparison = d;
        this.services = Object.keys(d);
        const labels = this.services.map(s => s.replace('-service','').replace('library-',''));
        this.chartData = {
          labels,
          datasets: [
            { label:'JVM cold start (s)', data:this.services.map(s=>d[s]?.jvmColdStartS||0), backgroundColor:'#D1B37E', borderRadius:4, maxBarThickness:40 },
            { label:'Native cold start (s)', data:this.services.map(s=>d[s]?.nativeColdStartS||0), backgroundColor:'#5EEAD4', borderRadius:4, maxBarThickness:40 }
          ]
        };
        this.loading = false;
      }
    });
  }

  getGainClass(g: number) { return g >= 10 ? 'text-success' : g >= 5 ? 'text-warning' : ''; }

  exportPdf() {
    this.exporting = true;
    this.api.exportPdf({ format:'PDF', includeComparison:true, includeCostSimulation:true,
      includeRegression:true, invocationsPerMonth:1000000, coldStartRatioPercent:5 }).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href=url; a.download='benchmark-report.pdf'; a.click();
        URL.revokeObjectURL(url); this.exporting=false;
        this.msg.add({severity:'success', summary:'PDF exporte'});
      }, error: () => this.exporting=false
    });
  }

  exportLatex() {
    this.api.exportLatex({ format:'LATEX', includeComparison:true, includeCostSimulation:true,
      includeRegression:false, invocationsPerMonth:1000000, coldStartRatioPercent:5 }).subscribe({
      next: latex => {
        const blob = new Blob([latex],{type:'text/plain'});
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href=url; a.download='benchmark-report.tex'; a.click();
        URL.revokeObjectURL(url);
      }
    });
  }
}
