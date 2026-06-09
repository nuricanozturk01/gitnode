/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.gitnode.os.auth.adapters;

import dev.gitnode.os.auth.api.LdapConnectionTestResult;
import dev.gitnode.os.auth.api.OrganizationAdminPort;
import dev.gitnode.os.auth.api.OrganizationForm;
import dev.gitnode.os.auth.api.OrganizationInfo;
import dev.gitnode.os.auth.api.OrganizationLdapForm;
import dev.gitnode.os.auth.api.OrganizationSsoForm;
import dev.gitnode.os.auth.api.OrganizationUpdateForm;
import dev.gitnode.os.auth.api.SamlMetadataTestResult;
import dev.gitnode.os.auth.services.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@NullMarked
public class OrganizationAdminAdapter implements OrganizationAdminPort {

  private final OrganizationService organizationService;

  @Override
  public Page<OrganizationInfo> list(final Pageable pageable) {

    return this.organizationService.list(pageable);
  }

  @Override
  public OrganizationInfo getBySlug(final String slug) {

    return this.organizationService.getBySlug(slug);
  }

  @Override
  public OrganizationInfo create(final OrganizationForm form) {

    return this.organizationService.create(form);
  }

  @Override
  public OrganizationInfo update(final String slug, final OrganizationUpdateForm form) {

    return this.organizationService.update(slug, form);
  }

  @Override
  public void delete(final String slug) {

    this.organizationService.delete(slug);
  }

  @Override
  public OrganizationInfo updateSso(final String slug, final OrganizationSsoForm form) {

    return this.organizationService.updateSso(slug, form);
  }

  @Override
  public OrganizationInfo setSsoEnabled(final String slug, final boolean enabled) {

    return this.organizationService.setSsoEnabled(slug, enabled);
  }

  @Override
  public SamlMetadataTestResult testAndCacheMetadata(final String slug) {

    return this.organizationService.testAndCacheMetadata(slug);
  }

  @Override
  public OrganizationInfo updateLdap(final String slug, final OrganizationLdapForm form) {

    return this.organizationService.updateLdap(slug, form);
  }

  @Override
  public OrganizationInfo setLdapEnabled(final String slug, final boolean enabled) {

    return this.organizationService.setLdapEnabled(slug, enabled);
  }

  @Override
  public LdapConnectionTestResult testLdapConnection(final String slug) {

    return this.organizationService.testLdapConnection(slug);
  }

  @Override
  public long countOrganizations() {

    return this.organizationService.countOrganizations();
  }

  @Override
  public long countSsoEnabledOrganizations() {

    return this.organizationService.countSsoEnabledOrganizations();
  }
}
