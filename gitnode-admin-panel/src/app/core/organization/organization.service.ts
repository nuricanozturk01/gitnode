import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ADMIN_PAGE_SIZE,
  type CreateOrganizationRequest,
  type LdapConfigRequest,
  type LdapTestResult,
  type OrganizationDetail,
  type OrganizationSummary,
  type PageQuery,
  type PagedResponse,
  type SsoConfigRequest,
  type SsoEnabledRequest,
  type SsoTestResult,
  type UpdateOrganizationRequest,
} from './organization.models';

@Injectable({ providedIn: 'root' })
export class OrganizationService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/admin/organizations`;

  async list(query: PageQuery = {}): Promise<PagedResponse<OrganizationSummary>> {
    const params = this.pageParams(query);
    return firstValueFrom(this.http.get<PagedResponse<OrganizationSummary>>(this.base, { params }));
  }

  async get(slug: string): Promise<OrganizationDetail> {
    return firstValueFrom(this.http.get<OrganizationDetail>(`${this.base}/${encodeURIComponent(slug)}`));
  }

  async create(body: CreateOrganizationRequest): Promise<OrganizationDetail> {
    return firstValueFrom(this.http.post<OrganizationDetail>(this.base, body));
  }

  async update(slug: string, body: UpdateOrganizationRequest): Promise<OrganizationDetail> {
    return firstValueFrom(this.http.put<OrganizationDetail>(`${this.base}/${encodeURIComponent(slug)}`, body));
  }

  async delete(slug: string): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`${this.base}/${encodeURIComponent(slug)}`));
  }

  async updateSso(slug: string, body: SsoConfigRequest): Promise<OrganizationDetail> {
    return firstValueFrom(this.http.put<OrganizationDetail>(`${this.base}/${encodeURIComponent(slug)}/sso`, body));
  }

  async setSsoEnabled(slug: string, enabled: boolean): Promise<OrganizationDetail> {
    const body: SsoEnabledRequest = { enabled };
    return firstValueFrom(
      this.http.put<OrganizationDetail>(`${this.base}/${encodeURIComponent(slug)}/sso/enabled`, body),
    );
  }

  async testSso(slug: string): Promise<SsoTestResult> {
    return firstValueFrom(this.http.post<SsoTestResult>(`${this.base}/${encodeURIComponent(slug)}/sso/test`, {}));
  }

  async updateLdap(slug: string, body: LdapConfigRequest): Promise<OrganizationDetail> {
    return firstValueFrom(this.http.put<OrganizationDetail>(`${this.base}/${encodeURIComponent(slug)}/ldap`, body));
  }

  async setLdapEnabled(slug: string, enabled: boolean): Promise<OrganizationDetail> {
    const body: SsoEnabledRequest = { enabled };
    return firstValueFrom(
      this.http.put<OrganizationDetail>(`${this.base}/${encodeURIComponent(slug)}/ldap/enabled`, body),
    );
  }

  async testLdap(slug: string): Promise<LdapTestResult> {
    return firstValueFrom(this.http.post<LdapTestResult>(`${this.base}/${encodeURIComponent(slug)}/ldap/test`, {}));
  }

  private pageParams(query: PageQuery): HttpParams {
    return new HttpParams().set('page', String(query.page ?? 0)).set('size', String(query.size ?? ADMIN_PAGE_SIZE));
  }
}
