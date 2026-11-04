import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private baseUrl = 'http://localhost:8090';

  constructor(private http: HttpClient) {}

  setBaseUrl(url: string) { this.baseUrl = url; }
  getBaseUrl() { return this.baseUrl; }

  getClusterInfo(): Observable<any> { return this.http.get(`${this.baseUrl}/api/cluster/info`); }
  getPods(): Observable<any[]> { return this.http.get<any[]>(`${this.baseUrl}/api/cluster/pods`); }
  discoverServices(): Observable<any[]> { return this.http.get<any[]>(`${this.baseUrl}/api/cluster/services/discover`); }
  switchMode(serviceName: string, mode: string): Observable<any> { return this.http.post(`${this.baseUrl}/api/cluster/deploy`, { serviceName, mode }); }
  scaleDeployment(serviceName: string, replicas: number): Observable<any> { return this.http.post(`${this.baseUrl}/api/cluster/scale/${serviceName}/${replicas}`, {}); }
  restartDeployment(serviceName: string): Observable<any> { return this.http.post(`${this.baseUrl}/api/cluster/restart/${serviceName}`, {}); }

  startJvmBuild(serviceName: string): Observable<any> { return this.http.post(`${this.baseUrl}/api/build/jvm`, { serviceName }); }
  startNativeBuild(serviceName: string): Observable<any> { return this.http.post(`${this.baseUrl}/api/build/native`, { serviceName }); }
  cancelBuild(serviceName: string): Observable<any> { return this.http.delete(`${this.baseUrl}/api/build/cancel/${serviceName}`); }

  startBenchmark(mode: string): Observable<any> { return this.http.post(`${this.baseUrl}/api/benchmark/start/${mode}`, {}); }
  getBenchmarkStatus(mode: string): Observable<any> { return this.http.get(`${this.baseUrl}/api/benchmark/status/${mode}`); }
  getMetrics(): Observable<any> { return this.http.get(`${this.baseUrl}/api/benchmark/metrics`); }
  getComparison(): Observable<any> { return this.http.get(`${this.baseUrl}/api/benchmark/comparison`); }

  getMonitoringMetrics(): Observable<any[]> { return this.http.get<any[]>(`${this.baseUrl}/api/monitoring/metrics`); }
  getStaticLogs(podName: string, lines: number = 100): Observable<any> { return this.http.get(`${this.baseUrl}/api/monitoring/logs/${podName}?lines=${lines}`); }

  checkRegression(mode: string): Observable<any[]> { return this.http.get<any[]>(`${this.baseUrl}/api/regression/check/${mode}`); }
  getHistory(mode: string): Observable<any[]> { return this.http.get<any[]>(`${this.baseUrl}/api/history/${mode}`); }
  getTrends(mode: string): Observable<any> { return this.http.get(`${this.baseUrl}/api/history/trends/${mode}`); }

  simulateCost(invocations: number, coldStartRatio: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/api/cost/simulate?invocationsPerMonth=${invocations}&coldStartRatioPercent=${coldStartRatio}`);
  }

  setupAbTest(serviceName: string): Observable<any> { return this.http.post(`${this.baseUrl}/api/abtest/setup/${serviceName}`, {}); }
  runAbTest(request: any): Observable<any> { return this.http.post(`${this.baseUrl}/api/abtest/run`, request); }
  getAbTestStats(serviceName: string): Observable<any[]> { return this.http.get<any[]>(`${this.baseUrl}/api/abtest/stats/${serviceName}`); }
  teardownAbTest(serviceName: string): Observable<any> { return this.http.delete(`${this.baseUrl}/api/abtest/teardown/${serviceName}`); }

  // IA via Ollama
  analyzeLogsAi(serviceName: string, logContent: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/api/ai/analyze`, { serviceName, logContent });
  }

  getAiStatus(): Observable<any> { return this.http.get(`${this.baseUrl}/api/ai/status`); }

  createEventSourceAiStream(serviceName: string, logContent: string): EventSource {
    const params = encodeURIComponent(JSON.stringify({ serviceName, logContent }));
    return new EventSource(`${this.baseUrl}/api/ai/analyze/stream?data=${params}`);
  }

  exportPdf(request: any): Observable<Blob> { return this.http.post(`${this.baseUrl}/api/export/pdf`, request, { responseType: 'blob' }); }
  exportLatex(request: any): Observable<string> { return this.http.post(`${this.baseUrl}/api/export/latex`, request, { responseType: 'text' }); }

  getConfig(): Observable<any> { return this.http.get(`${this.baseUrl}/api/config`); }
  updateConfig(key: string, value: any): Observable<any> { return this.http.put(`${this.baseUrl}/api/config/${key}`, value); }

  createEventSource(path: string): EventSource { return new EventSource(`${this.baseUrl}${path}`); }
}
