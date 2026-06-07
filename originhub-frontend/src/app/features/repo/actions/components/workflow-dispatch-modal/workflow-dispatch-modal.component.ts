import { ChangeDetectionStrategy, Component, OnInit, inject, input, output, signal } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { BranchService } from '../../../../../core/branch/services/branch.service';
import type { DispatchInput } from '../../../../../domain/actions/models/workflow-detail.model';
import type { BranchInfo } from '../../../../../domain/repository/models/branch-info.model';

export interface DispatchConfirmedEvent {
  ref: string;
  inputs: Record<string, string>;
}

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-workflow-dispatch-modal',
  standalone: true,
  imports: [LucideAngularModule],
  templateUrl: './workflow-dispatch-modal.component.html',
})
export class WorkflowDispatchModalComponent implements OnInit {
  private readonly branchService = inject(BranchService);

  readonly owner = input.required<string>();
  readonly repo = input.required<string>();
  readonly defaultBranch = input.required<string>();
  readonly dispatchInputs = input<DispatchInput[]>([]);

  readonly confirmed = output<DispatchConfirmedEvent>();
  readonly closed = output<void>();

  readonly branches = signal<BranchInfo[]>([]);
  readonly selectedBranch = signal('');
  readonly inputValues = signal<Record<string, string>>({});
  readonly loadingBranches = signal(false);

  ngOnInit(): void {
    this.selectedBranch.set(this.defaultBranch());
    this.resetInputValues();
    void this.loadBranches();
  }

  private resetInputValues(): void {
    const defaults: Record<string, string> = {};
    for (const inp of this.dispatchInputs()) {
      defaults[inp.name] = inp.defaultValue ?? (inp.type === 'boolean' ? 'false' : '');
    }
    this.inputValues.set(defaults);
  }

  private async loadBranches(): Promise<void> {
    this.loadingBranches.set(true);
    try {
      this.branches.set(await this.branchService.getAll(this.owner(), this.repo()));
    } catch {
      this.branches.set([]);
    } finally {
      this.loadingBranches.set(false);
    }
  }

  setInputValue(name: string, value: string): void {
    this.inputValues.update((current) => ({ ...current, [name]: value }));
  }

  setCheckboxValue(name: string, checked: boolean): void {
    this.setInputValue(name, checked ? 'true' : 'false');
  }

  isValid(): boolean {
    if (!this.selectedBranch()) return false;
    return this.dispatchInputs()
      .filter((i) => i.required)
      .every((i) => (this.inputValues()[i.name] ?? '').trim() !== '');
  }

  confirm(): void {
    if (!this.isValid()) return;
    const branch = this.selectedBranch();
    this.confirmed.emit({
      ref: branch.startsWith('refs/') ? branch : `refs/heads/${branch}`,
      inputs: { ...this.inputValues() },
    });
  }

  close(): void {
    this.closed.emit();
  }
}
