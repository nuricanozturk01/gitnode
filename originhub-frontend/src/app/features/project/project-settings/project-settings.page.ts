import { Component, inject, signal, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { ProjectService } from '../../../core/project/services/project.service';
import { TokenService } from '../../../core/auth/services/token.service';
import { ToastService } from '../../../core/toast/toast.service';

@Component({
  selector: 'app-project-settings',
  standalone: true,
  imports: [RouterLink, LucideAngularModule],
  templateUrl: './project-settings.page.html',
})
export class ProjectSettingsPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly projectService = inject(ProjectService);
  private readonly tokenService = inject(TokenService);
  private readonly toastService = inject(ToastService);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly projectName = signal('');
  readonly syncTaskStatusOnPrMerge = signal(true);

  get owner(): string {
    return this.tokenService.getUsername() ?? '';
  }

  get projectCode(): string {
    return this.route.snapshot.paramMap.get('projectCode') ?? '';
  }

  async ngOnInit(): Promise<void> {
    this.loading.set(true);
    try {
      const p = await this.projectService.get(this.owner, this.projectCode);
      this.projectName.set(p.name);
      this.syncTaskStatusOnPrMerge.set(p.syncTaskStatusOnPrMerge ?? true);
    } catch {
      this.toastService.error('Failed to load project');
      void this.router.navigate(['/projects']);
    } finally {
      this.loading.set(false);
    }
  }

  onSyncChange(checked: boolean): void {
    this.syncTaskStatusOnPrMerge.set(checked);
  }

  async save(): Promise<void> {
    this.saving.set(true);
    try {
      await this.projectService.update(this.owner, this.projectCode, {
        syncTaskStatusOnPrMerge: this.syncTaskStatusOnPrMerge(),
      });
      this.toastService.success('Settings saved');
    } catch {
      this.toastService.error('Failed to save settings');
    } finally {
      this.saving.set(false);
    }
  }
}
