import { TestBed } from '@angular/core/testing';
import { LUCIDE_ICONS, LucideIconProvider } from 'lucide-angular';
import { LUCIDE_ICONS as APP_ICONS } from '../../../../../lucide-icons';
import { LogStreamError, LogStreamService } from '../../../../../core/actions/services/log-stream.service';
import { LogViewerComponent } from './log-viewer.component';

describe('LogViewerComponent', () => {
  function create(streamResult: () => Promise<void>, onLine?: (cb: (l: unknown) => void) => void) {
    TestBed.configureTestingModule({
      imports: [LogViewerComponent],
      providers: [
        { provide: LUCIDE_ICONS, multi: true, useValue: new LucideIconProvider(APP_ICONS) },
        {
          provide: LogStreamService,
          useValue: {
            streamStepLogs: (_id: string, cb: (l: unknown) => void) => {
              onLine?.(cb);
              return streamResult();
            },
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(LogViewerComponent);
    fixture.componentRef.setInput('stepId', 'step-1');
    fixture.detectChanges();
    return fixture;
  }

  it('tells the visitor to sign in when the log request is unauthorized', async () => {
    const fixture = create(() => Promise.reject(new LogStreamError(401)));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance.errorStatus()).toBe(401);
    expect(fixture.componentInstance.emptyMessage()).toBe('Sign in to view the logs of this step.');
  });

  it('says there is no output only when the request succeeded without lines', async () => {
    const fixture = create(() => Promise.resolve());
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance.errorStatus()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('No log output for this step.');
  });

  it('filters lines by the search text', async () => {
    const fixture = create(
      () => Promise.resolve(),
      (cb) => {
        cb({ lineNumber: 1, content: 'alpha', level: 'info' });
        cb({ lineNumber: 2, content: 'beta', level: 'info' });
      },
    );
    fixture.componentRef.setInput('filter', 'BET');
    await fixture.whenStable();
    fixture.detectChanges();
    const lines = fixture.componentInstance.visibleLines().map((l) => l.content);
    expect(lines).toEqual(['beta']);
  });
});
