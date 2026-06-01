/** REST path builders aligned with Spring {@code @RequestMapping} prefixes. */

export const authApi = '/api/auth';
export const usersApi = '/api/users';
export const repoApi = '/api/repo';
export const snippetsApi = '/api/snippets';
export const migrationApi = '/api/migration';
export const sshKeysApi = '/api/user/ssh-keys';

export function projectsApi(owner: string): string {
  return `/api/projects/${owner}`;
}

export function projectApi(owner: string, projectCode: string): string {
  return `${projectsApi(owner)}/${projectCode}`;
}

export function projectBoardsApi(owner: string, projectCode: string): string {
  return `${projectApi(owner, projectCode)}/boards`;
}

export function projectTasksApi(owner: string, projectCode: string): string {
  return `${projectApi(owner, projectCode)}/tasks`;
}

export function reposApi(owner: string, repo: string): string {
  return `/api/repos/${owner}/${repo}`;
}

export function repoBranchesApi(owner: string, repo: string): string {
  return `${reposApi(owner, repo)}/branches`;
}

export function repoCommitsApi(owner: string, repo: string): string {
  return `${reposApi(owner, repo)}/commits`;
}

export function repoIssuesApi(owner: string, repo: string): string {
  return `${reposApi(owner, repo)}/issues`;
}

export function repoPullsApi(owner: string, repo: string): string {
  return `${reposApi(owner, repo)}/pulls`;
}

export function repoPullApi(owner: string, repo: string, number: number): string {
  return `${repoPullsApi(owner, repo)}/${number}`;
}

export function repoTagsApi(owner: string, repo: string): string {
  return `${reposApi(owner, repo)}/tags`;
}

export function repoReleasesApi(owner: string, repo: string): string {
  return `${reposApi(owner, repo)}/releases`;
}

export function repoWebhooksApi(owner: string, repo: string): string {
  return `/api/repos/${owner}/${repo}/settings/webhooks`;
}

export function userWebhooksApi(username: string): string {
  return `/api/users/${username}/settings/webhooks`;
}

export function projectWebhooksApi(owner: string, projectCode: string): string {
  return `/api/${owner}/projects/${projectCode}/settings/webhooks`;
}
