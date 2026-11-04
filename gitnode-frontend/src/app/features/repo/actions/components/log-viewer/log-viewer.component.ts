import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  OnDestroy,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { LogStreamError, LogStreamService } from '../../../../../core/actions/services/log-stream.service';
import { ansiToHtml } from '../../../../../shared/utils/ansi.utils';
import { buildLogRows, formatLogTime, type LogRow } from '../../../../../shared/utils/log-rows.utils';
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
  /** Hides the toolbar when the surrounding page already shows the step header. */
  readonly compact = input<boolean>(false);
  /** Only lines containing this text (case-insensitive) are shown. */
  readonly filter = input<string>('');
  /** Shows when the server stored each line. */
  readonly showTimestamps = input<boolean>(false);

  readonly lines = signal<WorkflowLogLine[]>([]);
  readonly streaming = signal(false);
  readonly autoScroll = signal(true);
  /** HTTP status of a failed log request, so the viewer can say why there is nothing to show. */
  readonly errorStatus = signal<number | null>(null);

  /** Groups the user opened (by the line number of their header); all others start folded. */
  readonly expandedGroups = signal<ReadonlySet<number>>(new Set());

  readonly visibleLines = computed(() => {
    const term = this.filter().trim().toLocaleLowerCase('en-US');
    const all = this.lines();
    return term ? all.filter((l) => l.content.toLocaleLowerCase('en-US').includes(term)) : all;
  });

  private readonly scrollContainer = viewChild<ElementRef<HTMLElement>>('scrollContainer');
  private abortController: AbortController | null = null;

  /** What is shown: lines, annotations and folded groups. A search shows every match unfolded. */
  readonly rows = computed(() => buildLogRows(this.visibleLines(), this.expandedGroups(), this.filter().trim() !== ''));

  constructor() {
    effect(() => {
      const id = this.stepId();
      const isLive = this.live();
      this.stopStream();
      this.lines.set([]);
      this.errorStatus.set(null);
      this.expandedGroups.set(new Set());
      if (id) {
        void this.startStream(id, isLive);
      }
    });

    effect(() => {
      this.rows();
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

  /** What to show when there are no lines: why the request failed, or that the step is silent. */
  emptyMessage(): string {
    switch (this.errorStatus()) {
      case null:
        return this.filter().trim() ? 'No lines match your search.' : 'No log output for this step.';
      case 401:
        return 'Sign in to view the logs of this step.';
      case 403:
        return 'You do not have permission to view these logs.';
      case 404:
        return 'The logs of this step are no longer available.';
      default:
        return 'The logs could not be loaded. Try again later.';
    }
  }

  lineHtml(content: string): string {
    return ansiToHtml(content);
  }

  toggleGroup(row: LogRow): void {
    const key = row.line.lineNumber;
    this.expandedGroups.update((current) => {
      const next = new Set(current);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }

  time(row: LogRow): string {
    return formatLogTime(row.line.timestamp);
  }

  isErrorLine(line: WorkflowLogLine): boolean {
    return line.level === 'error' || line.level === 'warn';
  }

  private async startStream(stepId: string, live: boolean): Promise<void> {
    this.streaming.set(true);
    this.abortController = new AbortController();

    let shouldRetry = false;
    let failed = false;
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
    } catch (error) {
      // An aborted stream is normal; a failed request is shown instead of an empty log.
      if (error instanceof LogStreamError && !this.abortController?.signal.aborted) {
        this.errorStatus.set(error.status);
        failed = true;
      }
    } finally {
      if (live && !failed && !this.abortController?.signal.aborted) {
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
