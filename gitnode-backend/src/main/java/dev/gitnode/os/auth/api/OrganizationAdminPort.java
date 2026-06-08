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
package dev.gitnode.os.auth.api;

import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@NullMarked
public interface OrganizationAdminPort {

  Page<OrganizationInfo> list(Pageable pageable);

  OrganizationInfo getBySlug(String slug);

  OrganizationInfo create(OrganizationForm form);

  OrganizationInfo update(String slug, OrganizationUpdateForm form);

  void delete(String slug);

  OrganizationInfo updateSso(String slug, OrganizationSsoForm form);

  OrganizationInfo setSsoEnabled(String slug, boolean enabled);

  SamlMetadataTestResult testAndCacheMetadata(String slug);

  OrganizationInfo updateLdap(String slug, OrganizationLdapForm form);

  OrganizationInfo setLdapEnabled(String slug, boolean enabled);

  LdapConnectionTestResult testLdapConnection(String slug);

  long countOrganizations();

  long countSsoEnabledOrganizations();
}
