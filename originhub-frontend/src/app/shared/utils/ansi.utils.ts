// eslint-disable-next-line no-control-regex
const ANSI_PATTERN = /\x1b\[([0-9;]*)m/g;

const ANSI_CLASSES: Record<string, string> = {
  '1': 'ansi-bold',
  '30': 'ansi-fg-black',
  '31': 'ansi-fg-red',
  '32': 'ansi-fg-green',
  '33': 'ansi-fg-yellow',
  '34': 'ansi-fg-blue',
  '35': 'ansi-fg-magenta',
  '36': 'ansi-fg-cyan',
  '37': 'ansi-fg-white',
  '90': 'ansi-fg-bright-black',
  '91': 'ansi-fg-bright-red',
  '92': 'ansi-fg-bright-green',
  '93': 'ansi-fg-bright-yellow',
  '94': 'ansi-fg-bright-blue',
};

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/** Converts ANSI escape sequences to HTML spans with CSS classes. */
export function ansiToHtml(text: string): string {
  let result = '';
  let lastIndex = 0;
  const openClasses: string[] = [];
  let match: RegExpExecArray | null;

  const pattern = new RegExp(ANSI_PATTERN.source, 'g');

  while ((match = pattern.exec(text)) !== null) {
    result += escapeHtml(text.slice(lastIndex, match.index));
    lastIndex = pattern.lastIndex;

    const codes = match[1].split(';').filter(Boolean);
    if (codes.length === 0 || codes.includes('0')) {
      if (openClasses.length > 0) {
        result += '</span>';
        openClasses.length = 0;
      }
      continue;
    }

    for (const code of codes) {
      const cls = ANSI_CLASSES[code];
      if (cls) {
        if (openClasses.length > 0) {
          result += '</span>';
        }
        openClasses.push(cls);
        result += `<span class="${cls}">`;
      }
    }
  }

  result += escapeHtml(text.slice(lastIndex));
  if (openClasses.length > 0) {
    result += '</span>';
  }

  return result;
}
