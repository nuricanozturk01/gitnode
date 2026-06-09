import { Component, ChangeDetectionStrategy, input, output, computed } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-pagination',
  standalone: true,
  imports: [LucideAngularModule],
  templateUrl: './pagination.component.html',
})
export class PaginationComponent {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly totalElements = input(0);
  readonly pageSize = input(10);
  readonly disabled = input(false);

  readonly pageChange = output<number>();

  readonly canPrev = computed(() => this.page() > 0);
  readonly canNext = computed(() => this.page() < this.totalPages() - 1);

  readonly summary = computed(() => {
    const total = this.totalElements();
    if (total === 0) return 'No results';
    const start = this.page() * this.pageSize() + 1;
    const end = Math.min(total, start + this.pageSize() - 1);
    return `${start}–${end} of ${total}`;
  });

  goPrev(): void {
    if (this.canPrev() && !this.disabled()) {
      this.pageChange.emit(this.page() - 1);
    }
  }

  goNext(): void {
    if (this.canNext() && !this.disabled()) {
      this.pageChange.emit(this.page() + 1);
    }
  }
}
