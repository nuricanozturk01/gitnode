/** Derive API base URL from admin.* hostname (e.g. admin.example.com → api.example.com). */
export function resolveAdminPanelApiUrl(env: { apiUrl: string }): void {
  if (typeof window === 'undefined') {
    return;
  }

  const host = window.location.hostname;
  if (host.startsWith('admin.')) {
    env.apiUrl = `${window.location.protocol}//api.${host.slice('admin.'.length)}`;
  }
}
