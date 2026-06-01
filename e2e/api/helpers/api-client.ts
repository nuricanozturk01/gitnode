import type { APIRequestContext } from '@playwright/test';

import type { E2eSession, LoginInfo, TenantInfo } from './types';

export class ApiClient {
  constructor(
    private readonly request: APIRequestContext,
    private readonly session: E2eSession,
  ) {}

  get owner(): string {
    return this.session.username;
  }

  get repo(): string {
    return this.session.repoName;
  }

  get projectCode(): string {
    return this.session.projectCode;
  }

  authHeaders(): Record<string, string> {
    return { Authorization: this.session.authorization };
  }

  async getMe(): Promise<TenantInfo> {
    const response = await this.request.get('/api/users/me', {
      headers: this.authHeaders(),
    });
    return (await response.json()) as TenantInfo;
  }

  async login(usernameOrEmail: string, password: string): Promise<LoginInfo> {
    const response = await this.request.post('/api/auth/login', {
      data: { usernameOrEmail, password },
    });
    return (await response.json()) as LoginInfo;
  }

  async register(
    username: string,
    email: string,
    password: string,
  ): Promise<LoginInfo> {
    const response = await this.request.post('/api/auth/register', {
      data: { username, email, password },
    });
    return (await response.json()) as LoginInfo;
  }
}
