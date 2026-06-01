export function getApiBaseUrl(): string {
  return process.env.ORIGINHUB_API_BASE_URL ?? 'http://localhost:8080';
}

export function getFrontendBaseUrl(): string {
  return process.env.ORIGINHUB_FRONTEND_BASE_URL ?? 'http://localhost:4200';
}
