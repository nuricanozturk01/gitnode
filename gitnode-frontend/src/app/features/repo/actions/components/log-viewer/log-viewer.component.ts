import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { LogStreamService } from '../../../../../core/actions/services/log-stream.service';
import { ansiToHtml } from '../../../../../shared/utils/ansi.utils';
import type { WorkflowLogLine } from '../../../../../domain/actions/models/workflow-step.model';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-log-viewer',
  standalone: true,
  imports: [LucideAngularModule],
  templateUrl: './log-viewer.component.html',
  styleUrl: './log-viewer.component.css',
})
export class LogViewerComponent implements OnDestroy {
  private readonly logStreamService = inject(LogStreamService);

  readonly stepId = input.required<string>();
  readonly stepName = input<string>('Logs');
  readonly live = input<boolean>(false);

  readonly lines = signal<WorkflowLogLine[]>([]);
  readonly streaming = signal(false);
  readonly autoScroll = signal(true);

  private readonly scrollContainer = viewChild<ElementRef<HTMLElement>>('scrollContainer');
  private abortController: AbortController | null = null;

  constructor() {
    effect(() => {
      const id = this.stepId();
      const isLive = this.live();
      this.stopStream();
      this.lines.set([]);
      if (id) {
        void this.startStream(id, isLive);
      }
    });

    effect(() => {
      this.lines();
      if (this.autoScroll()) {
        queueMicrotask(() => this.scrollToEnd());
      }
    });
  }

  ngOnDestroy(): void {
    this.stopStream();
  }

  scrollToEnd(): void {
    const el = this.scrollContainer()?.nativeElement;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  }

  toggleAutoScroll(): void {
    this.autoScroll.update((v) => !v);
    if (this.autoScroll()) {
      this.scrollToEnd();
    }
  }

  lineHtml(content: string): string {
    return ansiToHtml(content);
  }

  isErrorLine(line: WorkflowLogLine): boolean {
    return line.level === 'error' || line.level === 'warn';
  }

  private async startStream(stepId: string, live: boolean): Promise<void> {
    this.streaming.set(true);
    this.abortController = new AbortController();

    let shouldRetry = false;
    try {
      await this.logStreamService.streamStepLogs(
        stepId,
        (line) => {
          this.lines.update((existing) => {
            if (existing.some((l) => l.lineNumber === line.lineNumber)) {
              return existing;
            }
            return [...existing, line].sort((a, b) => a.lineNumber - b.lineNumber);
          });
        },
        this.abortController.signal,
      );
    } catch {
      // stream closed or aborted
    } finally {
      if (live && !this.abortController?.signal.aborted) {
        shouldRetry = true;
      } else {
        this.streaming.set(false);
      }
    }

    if (shouldRetry) {
      await new Promise((resolve) => setTimeout(resolve, 1500));
      if (!this.abortController?.signal.aborted && this.stepId() === stepId) {
        void this.startStream(stepId, live);
      } else {
        this.streaming.set(false);
      }
    }
  }

  private stopStream(): void {
    this.abortController?.abort();
    this.abortController = null;
    this.streaming.set(false);
  }
}
