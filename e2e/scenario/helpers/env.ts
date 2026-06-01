export function getApiBaseUrl(): string {
  return process.env.ORIGINHUB_API_BASE_URL ?? 'http://localhost:8080';
}
