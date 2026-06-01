export interface LoginInfo {
  email: string;
  username: string;
  token: string;
  refreshToken: string;
  expiresIn: number;
  refreshExpiresIn: number;
}

export interface TenantInfo {
  id: string;
  username: string;
  email: string;
  displayName: string | null;
  avatarUrl: string | null;
  bio: string | null;
  website: string | null;
  location: string | null;
  profileReadme: string | null;
  isAdmin: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface RepoInfo {
  id: string;
  name: string;
  defaultBranch: string;
  isPrivate: boolean;
  description?: string | null;
}

export interface E2eSession {
  baseUrl: string;
  username: string;
  email: string;
  password: string;
  accessToken: string;
  refreshToken: string;
  authorization: string;
  repoName: string;
  repoId: string;
  projectCode: string;
  /** Second user for scenario intrusion tests (registered once in global setup). */
  intruderUsername: string;
  intruderEmail: string;
  intruderPassword: string;
  intruderAccessToken: string;
  intruderRefreshToken: string;
  intruderAuthorization: string;
  /** When true, teardown skips DELETE /api/users/me (accounts from .env). */
  preserveUsers?: boolean;
}
