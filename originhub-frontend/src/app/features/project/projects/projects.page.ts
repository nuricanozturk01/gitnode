import { Component, inject, signal, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { ProjectService } from '../../../core/project/services/project.service';
import { TokenService } from '../../../core/auth/services/token.service';
import { ToastService } from '../../../core/toast/toast.service';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import type { ProjectInfo, ProjectForm } from '../../../domain/project/models/project-info.model';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';

@Component({
  selector: 'app-projects',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, RelativeTimePipe],
  templateUrl: './projects.page.html',
})
export class ProjectsPage implements OnInit {
  private readonly projectService = inject(ProjectService);
  private readonly tokenService = inject(TokenService);
  private readonly toastService = inject(ToastService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly router = inject(Router);

  readonly projects = signal<ProjectInfo[]>([]);
  readonly loading = signal(true);
  readonly showCreateModal = signal(false);
  readonly creating = signal(false);
  readonly currentPage = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  readonly form = signal<ProjectForm>({ name: '', codePrefix: '', description: '', isPublic: false });

  get owner(): string {
    return this.tokenService.getUsername() ?? '';
  }

  async ngOnInit(): Promise<void> {
    await this.loadProjects(0);
  }

  async goToPage(page: number): Promise<void> {
    if (page < 0 || page >= this.totalPages()) return;
    await this.loadProjects(page);
  }

  private async loadProjects(page: number): Promise<void> {
    this.loading.set(true);
    try {
      const data = await this.projectService.getAll(this.owner, page);
      this.projects.set(data.content);
      this.currentPage.set(data.number);
      this.totalPages.set(data.totalPages);
      this.totalElements.set(data.totalElements);
    } catch {
      this.toastService.error('Failed to load projects');
    } finally {
      this.loading.set(false);
    }
  }

  openCreateModal(): void {
    this.form.set({ name: '', codePrefix: '', description: '', isPublic: false });
    this.showCreateModal.set(true);
  }

  closeCreateModal(): void {
    this.showCreateModal.set(false);
  }

  onNameInput(event: Event): void {
    const name = (event.target as HTMLInputElement).value;
    const prefix = name
      .toUpperCase()
      .replace(/[^A-Z0-9]/g, '')
      .slice(0, 10);
    this.form.update((f) => ({ ...f, name, codePrefix: prefix || f.codePrefix }));
  }

  onPrefixInput(event: Event): void {
    const val = (event.target as HTMLInputElement).value
      .toUpperCase()
      .replace(/[^A-Z0-9]/g, '')
      .slice(0, 10);
    this.form.update((f) => ({ ...f, codePrefix: val }));
  }

  onDescInput(event: Event): void {
    const description = (event.target as HTMLInputElement).value;
    this.form.update((f) => ({ ...f, description }));
  }

  onVisibilityChange(isPublic: boolean): void {
    this.form.update((f) => ({ ...f, isPublic }));
  }

  async submitCreate(): Promise<void> {
    const f = this.form();
    if (!f.name.trim() || !f.codePrefix.trim()) return;
    this.creating.set(true);
    try {
      const project = await this.projectService.create(this.owner, f);
      this.projects.update((list) => [project, ...list]);
      this.closeCreateModal();
      this.router.navigate(['/projects', this.owner, project.codePrefix]);
    } catch {
      this.toastService.error('Failed to create project');
    } finally {
      this.creating.set(false);
    }
  }

  async deleteProject(project: ProjectInfo): Promise<void> {
    const ok = await this.confirmModal.confirm(
      `Delete project "${project.name}"?`,
      'This will permanently delete the project, all boards, columns, and tasks. This cannot be undone.',
      { confirmLabel: 'Delete project', variant: 'danger' },
    );
    if (!ok) return;
    try {
      await this.projectService.delete(this.owner, project.codePrefix);
      this.projects.update((list) => list.filter((p) => p.id !== project.id));
      this.toastService.success('Project deleted');
    } catch {
      this.toastService.error('Failed to delete project');
    }
  }
}
