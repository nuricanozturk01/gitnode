/** When the panel is served on admin.* but build-time apiUrl still points at localhost (K8s local). */
export function resolveAdminPanelApiUrl(env: { apiUrl: string }): void {
  if (typeof window === 'undefined') {
    return;
  }

  const host = window.location.hostname;
  const baked = env.apiUrl;
  const isLocalBuildDefault =
    baked.includes('localhost') || baked.includes('127.0.0.1');

  if (isLocalBuildDefault && host.startsWith('admin.')) {
    env.apiUrl = `${window.location.protocol}//api.${host.slice('admin.'.length)}`;
  }
}
