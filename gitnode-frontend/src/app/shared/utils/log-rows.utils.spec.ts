import type { WorkflowLogLine } from '../../domain/actions/models/workflow-step.model';
import { buildLogRows, formatLogTime } from './log-rows.utils';

const lines = (...contents: string[]): WorkflowLogLine[] =>
  contents.map((content, i) => ({ lineNumber: i + 1, content, level: 'info' }));

describe('buildLogRows', () => {
  it('shows plain lines as they are', () => {
    const rows = buildLogRows(lines('one', 'two'), new Set(), false);
    expect(rows.map((r) => r.text)).toEqual(['one', 'two']);
    expect(rows.every((r) => r.kind === 'line' && r.badge === null && !r.nested)).toBe(true);
  });

  it('folds the lines of a group until it is expanded', () => {
    const log = lines('before', '##[group]Build', 'a', 'b', '##[endgroup]', 'after');

    const folded = buildLogRows(log, new Set(), false);
    expect(folded.map((r) => r.text)).toEqual(['before', 'Build', 'after']);
    expect(folded[1]).toEqual(jasmine.objectContaining({ kind: 'group', groupSize: 2, collapsed: true }));

    const open = buildLogRows(log, new Set([2]), false);
    expect(open.map((r) => r.text)).toEqual(['before', 'Build', 'a', 'b', 'after']);
    expect(open[1].collapsed).toBe(false);
    expect(open.slice(2, 4).every((r) => r.nested)).toBe(true);
    expect(open[4].nested).toBe(false);
  });

  it('keeps counting the lines of a group that is still open (a running step)', () => {
    const rows = buildLogRows(lines('##[group]Running', 'x', 'y'), new Set(), false);
    expect(rows.length).toBe(1);
    expect(rows[0].groupSize).toBe(2);
  });

  it('turns ##[error], ##[warning], ##[notice] and ##[debug] into annotations', () => {
    const rows = buildLogRows(
      lines('##[error]boom', '##[warning]careful', '##[notice]fyi', '##[debug]detail'),
      new Set(),
      false,
    );
    expect(rows.map((r) => [r.badge, r.text])).toEqual([
      ['error', 'boom'],
      ['warning', 'careful'],
      ['notice', 'fyi'],
      ['debug', 'detail'],
    ]);
  });

  it('shows an annotation inside a folded group only once it is expanded', () => {
    const log = lines('##[group]G', '##[error]inside', '##[endgroup]');
    expect(buildLogRows(log, new Set(), false).length).toBe(1);
    expect(buildLogRows(log, new Set([1]), false).map((r) => r.badge)).toEqual([null, 'error']);
  });

  it('does not fold while searching, so every matching line is visible', () => {
    const rows = buildLogRows(lines('##[group]Build', 'needle', '##[endgroup]'), new Set(), true);
    expect(rows.map((r) => r.text)).toEqual(['Build', 'needle']);
    expect(rows.every((r) => r.kind === 'line')).toBe(true);
  });

  it('leaves job summary lines out of the log', () => {
    const log: WorkflowLogLine[] = [
      { lineNumber: 1, content: 'real', level: 'info' },
      { lineNumber: 2, content: '## Summary', level: 'summary' },
    ];
    expect(buildLogRows(log, new Set(), false).map((r) => r.text)).toEqual(['real']);
  });

  it('treats an unclosed ##[endgroup] or a stray marker text as ordinary output', () => {
    expect(buildLogRows(lines('##[endgroup]', 'plain'), new Set(), false).map((r) => r.text)).toEqual(['plain']);
    expect(buildLogRows(lines('not ##[error] a marker'), new Set(), false)[0].badge).toBeNull();
  });

  it('names an untitled group', () => {
    expect(buildLogRows(lines('##[group]'), new Set(), false)[0].text).toBe('Group');
  });
});

describe('formatLogTime', () => {
  it('formats a timestamp as local hh:mm:ss.SSS', () => {
    const text = formatLogTime('2026-11-04T21:12:59.818Z');
    expect(text).toMatch(/^\d{2}:\d{2}:\d{2}\.818$/);
  });

  it('is empty without a usable timestamp', () => {
    expect(formatLogTime(undefined)).toBe('');
    expect(formatLogTime(null)).toBe('');
    expect(formatLogTime('not a date')).toBe('');
  });
});
