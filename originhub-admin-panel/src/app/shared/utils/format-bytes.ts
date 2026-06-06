export function formatBytesAsGb(bytes: number, fractionDigits = 2): string {
  if (!Number.isFinite(bytes) || bytes < 0) {
    return '0 GB';
  }
  const gb = bytes / 1024 ** 3;
  if (gb < 0.01 && bytes > 0) {
    return '< 0.01 GB';
  }
  return `${gb.toFixed(fractionDigits)} GB`;
}
