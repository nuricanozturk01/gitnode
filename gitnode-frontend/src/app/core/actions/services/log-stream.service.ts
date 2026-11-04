import { Injectable, inject } from '@angular/core';
import { environment } from '../../../../environments/environment';
import { TokenService } from '../../auth/services/token.service';
import type { WorkflowLogLine } from '../../../domain/actions/models/workflow-step.model';

/** The log endpoint answered with an error; `status` tells the UI what to say about it. */
export class LogStreamError extends Error {
  constructor(readonly status: number) {
    super(`Log stream failed: ${status}`);
    this.name = 'LogStreamError';
  }
}

@Injectable({ providedIn: 'root' })
export class LogStreamService {
  private readonly tokenService = inject(TokenService);

  streamStepLogs(stepId: string, onLine: (line: WorkflowLogLine) => void, signal: AbortSignal): Promise<void> {
    const token = this.tokenService.getAccessToken();
    const url = `${environment.apiUrl}/api/actions/steps/${stepId}/logs`;

    return fetch(url, {
      headers: {
        Authorization: `Bearer ${token ?? ''}`,
        Accept: 'text/event-stream',
      },
      signal,
    }).then(async (response) => {
      if (!response.ok || !response.body) {
        throw new LogStreamError(response.status);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const events = buffer.split('\n\n');
        buffer = events.pop() ?? '';

        for (const event of events) {
          const dataLine = event.split('\n').find((line) => line.startsWith('data:'));
          if (!dataLine) continue;

          const json = dataLine.slice(5).trim();
          if (!json) continue;

          try {
            const line = JSON.parse(json) as WorkflowLogLine;
            onLine(line);
          } catch {
            // ignore malformed SSE payloads
          }
        }
      }
    });
  }

  /** Reads every stored line of a finished step; rejects when the stream fails or takes too long. */
  async fetchStepLogs(stepId: string, timeoutMs = 15000): Promise<WorkflowLogLine[]> {
    const lines = new Map<number, WorkflowLogLine>();
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      await this.streamStepLogs(stepId, (line) => lines.set(line.lineNumber, line), controller.signal);
    } finally {
      clearTimeout(timer);
    }
    return [...lines.values()].sort((a, b) => a.lineNumber - b.lineNumber);
  }
}
