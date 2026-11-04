import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { SidebarComponent } from './layout/sidebar/sidebar.component';
import { TopbarComponent } from './layout/topbar/topbar.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ToastModule, SidebarComponent, TopbarComponent],
  providers: [MessageService],
  template: `
    <p-toast></p-toast>
    <div class="dashboard-layout">
      <app-sidebar></app-sidebar>
      <div class="main-content">
        <app-topbar></app-topbar>
        <div class="page-content">
          <router-outlet></router-outlet>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .dashboard-layout { display:flex; height:100vh; overflow:hidden; background:var(--zcs-bg); }
    .main-content { flex:1; display:flex; flex-direction:column; overflow:hidden; min-width:0; background:var(--zcs-bg); }
    .page-content { flex:1; overflow-y:auto; padding:1.75rem 2rem; background:var(--zcs-bg); }
  `]
})
export class AppComponent {}
