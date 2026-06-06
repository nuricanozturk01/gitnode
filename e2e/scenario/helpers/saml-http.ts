export interface HtmlForm {
  action: string;
  method: 'GET' | 'POST';
  fields: Record<string, string>;
}

export function parseHtmlForm(html: string): HtmlForm | null {
  const formTag = (/<form[^>]*>/i.exec(html))?.[0];
  if (!formTag) {
    return null;
  }

  const actionMatch = /action="([^"]*)"/i.exec(formTag);
  const methodMatch = /method="([^"]*)"/i.exec(formTag);
  if (!actionMatch) {
    return null;
  }

  const fields: Record<string, string> = {};
  for (const match of html.matchAll(/<input[^>]*name="([^"]*)"[^>]*value="([^"]*)"[^>]*>/gi)) {
    fields[match[1]] = match[2];
  }
  for (const match of html.matchAll(/<input[^>]*value="([^"]*)"[^>]*name="([^"]*)"[^>]*>/gi)) {
    fields[match[2]] = match[1];
  }

  const method = (methodMatch?.[1] ?? 'post').toUpperCase();
  if (method !== 'GET' && method !== 'POST') {
    return null;
  }

  return {
    action: actionMatch[1],
    method,
    fields,
  };
}

export function resolveUrl(baseUrl: string, location: string): string {
  return new URL(location, baseUrl).href;
}
