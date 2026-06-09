import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { PgauditLogsPanelComponent } from '../audit/pgaudit/pgaudit-logs-panel.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-pgaudit-logs-page',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, PgauditLogsPanelComponent],
  templateUrl: './pgaudit-logs.page.html',
})
export class PgauditLogsPage {}
