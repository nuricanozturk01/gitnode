export function getApiBaseUrl(): string {
  return process.env.ORIGINHUB_API_BASE_URL ?? 'http://localhost:8080';
}

/** Angular app base URL for browser scenarios. */
export function getWebBaseUrl(): string {
  return process.env.ORIGINHUB_WEB_BASE_URL ?? 'http://localhost:4200';
}
