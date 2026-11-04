import type { WorkflowLogLine } from '../../domain/actions/models/workflow-step.model';

export type LogBadge = 'error' | 'warning' | 'notice' | 'debug';

/** One displayed row of a step's log: a plain line, an annotation, or a collapsible group header. */
export interface LogRow {
  kind: 'line' | 'group';
  line: WorkflowLogLine;
  /** What is shown: the line without its `##[...]` marker. */
  text: string;
  badge: LogBadge | null;
  /** True for lines inside an open group, drawn indented. */
  nested: boolean;
  /** Group headers only: how many lines the group holds and whether it is folded. */
  groupSize: number;
  collapsed: boolean;
}

const MARKER = /^##\[(group|endgroup|error|warning|notice|debug)\]/;

const BADGES: Record<string, LogBadge> = { error: 'error', warning: 'warning', notice: 'notice', debug: 'debug' };

/** Log lines that belong to the job summary, not to the log. */
export function isSummaryLine(line: WorkflowLogLine): boolean {
  return line.level === 'summary';
}

function parse(content: string): { marker: string | null; text: string } {
  const match = MARKER.exec(content);
  return match ? { marker: match[1], text: content.slice(match[0].length) } : { marker: null, text: content };
}

/**
 * Turns a step's log lines into rows. `##[group]`/`##[endgroup]` fold the lines between them
 * (folded unless their header line number is in `expanded`), `##[error]` and friends become
 * annotations. With `flat` (a search is active) nothing is folded so every matching line shows.
 */
export function buildLogRows(lines: WorkflowLogLine[], expanded: ReadonlySet<number>, flat: boolean): LogRow[] {
  const rows: LogRow[] = [];
  let open: LogRow | null = null;

  for (const line of lines) {
    if (isSummaryLine(line)) continue;
    const { marker, text } = parse(line.content);

    if (marker === 'endgroup') {
      open = null;
      continue;
    }
    if (marker === 'group' && !flat) {
      open = {
        kind: 'group',
        line,
        text: text.trim() || 'Group',
        badge: null,
        nested: false,
        groupSize: 0,
        collapsed: !expanded.has(line.lineNumber),
      };
      rows.push(open);
      continue;
    }

    if (open) {
      open.groupSize++;
      if (open.collapsed) continue;
    }
    rows.push({
      kind: 'line',
      line,
      text,
      badge: marker ? (BADGES[marker] ?? null) : null,
      nested: open !== null,
      groupSize: 0,
      collapsed: false,
    });
  }
  return rows;
}

/** `14:03:07.412` in the viewer's local time; empty when the line has no timestamp. */
export function formatLogTime(timestamp: string | null | undefined): string {
  if (!timestamp) return '';
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (n: number, width = 2) => String(n).padStart(width, '0');
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}.${pad(date.getMilliseconds(), 3)}`;
}
