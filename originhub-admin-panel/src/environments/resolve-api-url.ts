/** Derive API base URL from admin.* host (K8s: admin.originhub.test → api.originhub.test). */
export function resolveAdminPanelApiUrl(env: { apiUrl: string }): void {
  if (typeof window === 'undefined') {
    return;
  }

  const host = window.location.hostname;
  if (host.startsWith('admin.')) {
    env.apiUrl = `${window.location.protocol}//api.${host.slice('admin.'.length)}`;
  }
}
