import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DropdownModule } from 'primeng/dropdown';
import { Subscription } from 'rxjs';
import { ApiService } from '../../services/api.service';
import { OllamaStatusService } from '../../services/ollama-status.service';

@Component({
  selector: 'app-recommendations',
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonModule, DropdownModule],
  templateUrl: './recommendations.component.html',
  styleUrls: ['./recommendations.component.scss']
})
export class RecommendationsComponent implements OnInit, OnDestroy {
  services = ['catalog-service','user-service','loan-service','library-gateway'];
  selectedService = 'loan-service';
  logContent = '';
  aiAnalysis = '';
  rules: any[] = [];
  loading = false;
  aiAvailable = false;
  streamingTokens = '';
  private statusSub!: Subscription;

  constructor(private api: ApiService, private ollamaStatus: OllamaStatusService) {}

  ngOnInit() {
    this.statusSub = this.ollamaStatus.getStatus().subscribe(
      (available: boolean) => this.aiAvailable = available
    );
  }

  analyze() {
    if (!this.logContent.trim()) return;
    this.loading = true;
    this.aiAnalysis = '';
    this.streamingTokens = '';
    this.api.analyzeLogsAi(this.selectedService, this.logContent).subscribe({
      next: (res: any) => { 
        this.aiAnalysis = res.analysis; 
        this.rules = res.rules || [];
        this.loading = false; 
      },
      error: () => {
        this.aiAnalysis = 'Erreur lors de l\'analyse. Vérifiez qu\'Ollama est en cours d\'exécution.';
        this.loading = false;
      }
    });
  }

  clearAll() {
    this.logContent = '';
    this.aiAnalysis = '';
    this.streamingTokens = '';
  }

  formatAnalysis(text: string): string {
    return text
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/### (.+)/g, '<h4>$1</h4>')
      .replace(/## (.+)/g, '<h3>$1</h3>')
      .replace(/`([^`]+)`/g, '<code>$1</code>')
      .replace(/```[\w]*\n?([\s\S]+?)```/g, '<pre><code>$1</code></pre>')
      .replace(/\n/g, '<br>');
  }

  ngOnDestroy() { this.statusSub?.unsubscribe(); }
}
