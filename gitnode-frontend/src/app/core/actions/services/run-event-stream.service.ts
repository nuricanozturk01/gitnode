import { Injectable, inject } from '@angular/core';
import { environment } from '../../../../environments/environment';
import { TokenService } from '../../auth/services/token.service';

@Injectable({ providedIn: 'root' })
export class RunEventStreamService {
  private readonly tokenService = inject(TokenService);

  streamRunEvents(
    owner: string,
    repo: string,
    runId: string,
    onEvent: () => void,
    onComplete: () => void,
    signal: AbortSignal,
  ): Promise<void> {
    const token = this.tokenService.getAccessToken();
    const url = `${environment.apiUrl}/api/repos/${owner}/${repo}/actions/runs/${runId}/events`;

    return fetch(url, {
      headers: {
        Authorization: `Bearer ${token ?? ''}`,
        Accept: 'text/event-stream',
      },
      signal,
    }).then(async (response) => {
      if (!response.ok || !response.body) {
        throw new Error(`Run event stream failed: ${response.status}`);
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
          const nameLine = event.split('\n').find((line) => line.startsWith('event:'));
          const eventName = nameLine ? nameLine.slice(6).trim() : '';

          if (eventName === 'run_completed') {
            onEvent();
            onComplete();
            return;
          } else if (eventName === 'run_updated') {
            onEvent();
          }
        }
      }
    });
  }
}
