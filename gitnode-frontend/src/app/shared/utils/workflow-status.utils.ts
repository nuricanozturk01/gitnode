export type WorkflowStatusTone = 'success' | 'error' | 'warning' | 'primary' | 'neutral';

/** Locale-safe lowercasing — avoids tr-TR turning ONLINE → onlıne. */
export function normalizeStatusKey(status: string | null | undefined): string {
  return (status ?? '').trim().toLocaleLowerCase('en-US');
}

export function workflowStatusTone(status: string | null | undefined): WorkflowStatusTone {
  if (!status) return 'neutral';

  switch (normalizeStatusKey(status)) {
    case 'success':
    case 'completed':
    case 'online':
      return 'success';
    case 'failure':
    case 'failed':
    case 'error':
      return 'error';
    case 'in_progress':
    case 'running':
    case 'busy':
      return 'warning';
    case 'queued':
    case 'pending':
    case 'waiting':
      return 'primary';
    case 'cancelled':
    case 'skipped':
    case 'offline':
      return 'neutral';
    default:
      return 'neutral';
  }
}

export function workflowStatusBadgeClass(status: string | null | undefined): string {
  return `badge-pill badge-pill--${workflowStatusTone(status)}`;
}

export function workflowStatusIconClass(status: string | null | undefined): string {
  switch (workflowStatusTone(status)) {
    case 'success':
      return 'text-success';
    case 'error':
      return 'text-error';
    case 'warning':
      return 'text-warning';
    case 'primary':
      return 'text-primary';
    default:
      return 'text-base-content/40';
  }
}

export function resolveWorkflowDisplayStatus(status: string | null | undefined, conclusion?: string | null): string {
  const activeStatus = normalizeStatusKey(status);
  if (activeStatus === 'in_progress' || activeStatus === 'queued' || activeStatus === 'waiting') {
    return activeStatus;
  }
  if (conclusion) return normalizeStatusKey(conclusion);
  return activeStatus || 'unknown';
}

export function workflowStatusIconName(status: string | null | undefined, conclusion?: string | null): string {
  const activeStatus = normalizeStatusKey(status);
  const display = resolveWorkflowDisplayStatus(status, conclusion);

  if (activeStatus === 'in_progress' || display === 'in_progress' || display === 'running' || display === 'busy') {
    return 'loader2';
  }

  switch (workflowStatusTone(display)) {
    case 'success':
      return 'checkCircle';
    case 'error':
      return 'xCircle';
    case 'primary':
      return 'circleDot';
    default:
      if (display === 'cancelled' || display === 'skipped') {
        return 'xCircle';
      }
      if (display === 'offline') {
        return 'circleAlert';
      }
      return 'circleDot';
  }
}

export function workflowStatusIconSpinning(status: string | null | undefined, conclusion?: string | null): boolean {
  const activeStatus = normalizeStatusKey(status);
  const display = resolveWorkflowDisplayStatus(status, conclusion);
  return activeStatus === 'in_progress' || display === 'in_progress' || display === 'running' || display === 'busy';
}

export function workflowStatusLabel(status: string | null | undefined): string {
  if (!status) return 'Unknown';
  return normalizeStatusKey(status).replace(/_/g, ' ');
}

export function isRunActive(status: string): boolean {
  const key = normalizeStatusKey(status);
  return key === 'queued' || key === 'in_progress';
}

const RUNNER_STATUS_LABELS: Record<string, string> = {
  online: 'Online',
  busy: 'Busy',
  offline: 'Offline',
};

export function runnerStatusTone(status: string | null | undefined): WorkflowStatusTone {
  switch (normalizeStatusKey(status)) {
    case 'online':
      return 'success';
    case 'busy':
      return 'warning';
    case 'offline':
      return 'error';
    default:
      return 'neutral';
  }
}

export function runnerStatusBadgeClass(status: string | null | undefined): string {
  return `badge-pill badge-pill--${runnerStatusTone(status)}`;
}

export function runnerStatusIconClass(status: string | null | undefined): string {
  switch (runnerStatusTone(status)) {
    case 'success':
      return 'text-success';
    case 'warning':
      return 'text-warning';
    case 'error':
      return 'text-error';
    default:
      return 'text-base-content/40';
  }
}

export function runnerStatusIconName(status: string | null | undefined): string {
  switch (runnerStatusTone(status)) {
    case 'success':
      return 'checkCircle';
    case 'warning':
      return 'loader2';
    case 'error':
      return 'circleAlert';
    default:
      return 'circleDot';
  }
}

export function runnerStatusIconSpinning(status: string | null | undefined): boolean {
  return normalizeStatusKey(status) === 'busy';
}

export function runnerStatusLabel(status: string | null | undefined): string {
  if (!status) return 'Unknown';
  return RUNNER_STATUS_LABELS[normalizeStatusKey(status)] ?? status;
}

/** "1m 5s", "12s", "1h 2m" — GitHub style; empty when the start is unknown. */
export function formatDuration(
  start: string | null | undefined,
  end?: string | null,
  now: number = Date.now(),
): string {
  if (!start) return '';
  const from = Date.parse(start);
  if (Number.isNaN(from)) return '';
  const toParsed = end ? Date.parse(end) : now;
  const to = Number.isNaN(toParsed) ? now : toParsed;
  const total = Math.max(0, Math.round((to - from) / 1000));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  if (hours > 0) return `${hours}h ${minutes}m`;
  if (minutes > 0) return `${minutes}m ${seconds}s`;
  return `${seconds}s`;
}

/** First line of a commit message, or the fallback when there is none. */
export function commitTitle(message: string | null | undefined, fallback: string): string {
  const first = (message ?? '').split('\n', 1)[0]?.trim();
  return first ? first : fallback;
}

/** "refs/heads/main" → "main", "refs/tags/v1" → "v1". */
export function shortRef(ref: string | null | undefined): string {
  return (ref ?? '').replace(/^refs\/(heads|tags)\//, '');
}

/**
 * The title of a run, as GitHub shows it: the commit message for pushes and pull requests, the
 * workflow name for everything else (manual dispatches, schedules, ...).
 */
export function runTitle(run: {
  triggerEvent: string;
  headCommitMessage: string | null;
  workflowName: string;
}): string {
  const usesCommit = run.triggerEvent === 'push' || run.triggerEvent === 'pull_request';
  return usesCommit ? commitTitle(run.headCommitMessage, run.workflowName) : run.workflowName;
}
