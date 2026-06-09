import net from 'node:net';

export const LDAP_DOCKER_RUN_HINT =
  'docker run -d --name gitnode-ldap -p 389:10389 ghcr.io/rroemhild/docker-test-openldap:master';

export const LDAP_PORT_MAPPING_HINT =
  'Map host port 389 to container port 10389 (-p 389:10389). Mapping 389:389 will fail.';

export async function assertLdapTcpReachable(url: string): Promise<void> {
  const parsed = new URL(url);
  const host = parsed.hostname;
  const port = Number(parsed.port || 389);

  await new Promise<void>((resolve, reject) => {
    const socket = net.createConnection({ host, port }, () => {
      socket.destroy();
      resolve();
    });

    socket.setTimeout(5000);
    socket.on('timeout', () => {
      socket.destroy();
      reject(
        new Error(
          `LDAP TCP timeout at ${url}.\nRun: ${LDAP_DOCKER_RUN_HINT}\n${LDAP_PORT_MAPPING_HINT}`,
        ),
      );
    });

    socket.on('error', (err: NodeJS.ErrnoException) => {
      const mappingHint =
        err.code === 'ECONNRESET' || err.code === 'ECONNREFUSED'
          ? `\n${LDAP_PORT_MAPPING_HINT}`
          : '';
      reject(
        new Error(
          `LDAP not reachable at ${url}: ${err.message}${mappingHint}\nRun: ${LDAP_DOCKER_RUN_HINT}`,
        ),
      );
    });
  });
}

export function formatLdapConnectionTestError(status: number, body: string): string {
  if (body.includes('ldapConnectionFailed')) {
    return [
      `LDAP connection test failed (${status}): backend could not bind to the directory.`,
      'Ensure E2E_LDAP_* values match your OpenLDAP image (defaults: Planet Express / fry).',
      LDAP_PORT_MAPPING_HINT,
      `Run: ${LDAP_DOCKER_RUN_HINT}`,
    ].join('\n');
  }

  return `LDAP connection test failed (${status}): ${body}`;
}
