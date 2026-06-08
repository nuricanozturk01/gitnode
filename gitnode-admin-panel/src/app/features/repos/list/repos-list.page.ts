import { Component, ChangeDetectionStrategy, inject, OnInit, signal } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { AdminRepoService } from '../../../core/admin/admin-repo.service';
import type { AdminRepoSummary } from '../../../core/admin/admin.models';
import { ADMIN_PAGE_SIZE } from '../../../core/organization/organization.models';
import { ToastService } from '../../../core/toast/toast.service';
import { PaginationComponent } from '../../../shared/components/pagination/pagination.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-repos-list',
  standalone: true,
  imports: [LucideAngularModule, PaginationComponent],
  templateUrl: './repos-list.page.html',
})
export class ReposListPage implements OnInit {
  private readonly adminRepoService = inject(AdminRepoService);
  private readonly toast = inject(ToastService);

  readonly loading = signal(true);
  readonly repos = signal<AdminRepoSummary[]>([]);
  readonly search = signal('');
  readonly owner = signal('');
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly pageSize = ADMIN_PAGE_SIZE;

  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    void this.load();
  }

  onSearchInput(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
    this.page.set(0);
    this.scheduleLoad();
  }

  onOwnerInput(event: Event): void {
    this.owner.set((event.target as HTMLInputElement).value);
    this.page.set(0);
    this.scheduleLoad();
  }

  async onPageChange(nextPage: number): Promise<void> {
    this.page.set(nextPage);
    await this.load();
  }

  formatDate(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '—';
    return date.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
  }

  private scheduleLoad(): void {
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => void this.load(), 300);
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    try {
      const res = await this.adminRepoService.list({
        page: this.page(),
        size: this.pageSize,
        q: this.search().trim() || undefined,
        owner: this.owner().trim() || undefined,
      });
      this.repos.set(res.content);
      this.totalPages.set(res.totalPages);
      this.totalElements.set(res.totalElements);
    } catch {
      this.toast.error('Failed to load repositories');
    } finally {
      this.loading.set(false);
    }
  }
}
