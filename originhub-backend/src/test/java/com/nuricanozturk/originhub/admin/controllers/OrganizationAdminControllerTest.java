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
package com.nuricanozturk.originhub.admin.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.admin.services.PlatformAdminService;
import com.nuricanozturk.originhub.auth.api.LdapConnectionTestResult;
import com.nuricanozturk.originhub.auth.api.OrganizationAdminPort;
import com.nuricanozturk.originhub.auth.api.OrganizationForm;
import com.nuricanozturk.originhub.auth.api.OrganizationInfo;
import com.nuricanozturk.originhub.auth.api.OrganizationLdapForm;
import com.nuricanozturk.originhub.auth.api.OrganizationSsoForm;
import com.nuricanozturk.originhub.auth.api.OrganizationUpdateForm;
import com.nuricanozturk.originhub.auth.api.SamlMetadataTestResult;
import com.nuricanozturk.originhub.auth.api.SsoEnabledForm;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationAdminController unit tests")
class OrganizationAdminControllerTest {

  @Mock private OrganizationAdminPort organizationAdminPort;
  @Mock private PlatformAdminService platformAdminService;

  @InjectMocks private OrganizationAdminController organizationAdminController;

  private static OrganizationInfo orgInfo() {
    return new OrganizationInfo(
        UUID.randomUUID(),
        "Acme Corp",
        "acme",
        List.of("acme.com"),
        false,
        null,
        false,
        "email",
        null,
        null,
        false,
        false,
        null,
        null,
        null,
        false,
        "",
        "",
        "",
        "",
        false,
        null,
        null,
        null,
        null,
        Instant.EPOCH,
        Instant.EPOCH);
  }

  @Nested
  @DisplayName("GET /api/admin/organizations")
  class ListOrganizations {

    @Test
    @DisplayName("returns paginated organizations sorted by createdAt desc")
    void list_returnsPage() {
      when(organizationAdminPort.list(any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of(orgInfo())));

      var response = organizationAdminController.list(0, 10);

      verify(platformAdminService).requirePlatformAdmin();
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().content()).hasSize(1);
      verify(organizationAdminPort)
          .list(
              argThat(
                  pageable ->
                      pageable.getPageSize() == 10
                          && pageable.getSort().equals(Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @Test
    @DisplayName("caps page size at 100")
    void list_capsPageSize() {
      when(organizationAdminPort.list(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

      organizationAdminController.list(0, 500);

      verify(organizationAdminPort).list(argThat(pageable -> pageable.getPageSize() == 100));
    }
  }

  @Nested
  @DisplayName("GET /api/admin/organizations/{slug}")
  class GetBySlug {

    @Test
    @DisplayName("returns organization by slug")
    void getBySlug_returnsOrganization() {
      when(organizationAdminPort.getBySlug("acme")).thenReturn(orgInfo());

      var response = organizationAdminController.getBySlug("acme");

      verify(platformAdminService).requirePlatformAdmin();
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().slug()).isEqualTo("acme");
    }
  }

  @Nested
  @DisplayName("POST /api/admin/organizations")
  class Create {

    @Test
    @DisplayName("creates organization and returns 201")
    void create_returnsCreated() {
      var form = new OrganizationForm("Acme Corp", "acme", List.of("acme.com"));
      when(organizationAdminPort.create(form)).thenReturn(orgInfo());

      var response = organizationAdminController.create(form);

      verify(platformAdminService).requirePlatformAdmin();
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
    }
  }

  @Nested
  @DisplayName("PUT /api/admin/organizations/{slug}")
  class Update {

    @Test
    @DisplayName("updates organization")
    void update_returnsOrganization() {
      var form = new OrganizationUpdateForm("Acme Corp", List.of("acme.com"));
      when(organizationAdminPort.update("acme", form)).thenReturn(orgInfo());

      var response = organizationAdminController.update("acme", form);

      verify(platformAdminService).requirePlatformAdmin();
      assertThat(response.getBody()).isNotNull();
    }
  }

  @Nested
  @DisplayName("DELETE /api/admin/organizations/{slug}")
  class Delete {

    @Test
    @DisplayName("deletes organization and returns 204")
    void delete_returnsNoContent() {
      var response = organizationAdminController.delete("acme");

      verify(platformAdminService).requirePlatformAdmin();
      verify(organizationAdminPort).delete("acme");
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
  }

  @Nested
  @DisplayName("SSO endpoints")
  class Sso {

    @Test
    @DisplayName("PUT /{slug}/sso updates SSO configuration")
    void updateSso_delegatesToPort() {
      var form = new OrganizationSsoForm(true, "https://idp/metadata", "email", null, null);
      when(organizationAdminPort.updateSso("acme", form)).thenReturn(orgInfo());

      var response = organizationAdminController.updateSso("acme", form);

      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("PUT /{slug}/sso/enabled toggles SSO")
    void setSsoEnabled_delegatesToPort() {
      when(organizationAdminPort.setSsoEnabled("acme", true)).thenReturn(orgInfo());

      var response = organizationAdminController.setSsoEnabled("acme", new SsoEnabledForm(true));

      verify(organizationAdminPort).setSsoEnabled("acme", true);
      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("POST /{slug}/sso/test tests metadata")
    void testSso_returnsResult() {
      var result = new SamlMetadataTestResult(true, "ok", true);
      when(organizationAdminPort.testAndCacheMetadata("acme")).thenReturn(result);

      var response = organizationAdminController.testSso("acme");

      assertThat(response.getBody()).isEqualTo(result);
    }
  }

  @Nested
  @DisplayName("LDAP endpoints")
  class Ldap {

    @Test
    @DisplayName("PUT /{slug}/ldap updates LDAP configuration")
    void updateLdap_delegatesToPort() {
      var form =
          new OrganizationLdapForm(
              true,
              "ldap://localhost",
              "dc=acme,dc=com",
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null);
      when(organizationAdminPort.updateLdap("acme", form)).thenReturn(orgInfo());

      var response = organizationAdminController.updateLdap("acme", form);

      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("PUT /{slug}/ldap/enabled toggles LDAP")
    void setLdapEnabled_delegatesToPort() {
      when(organizationAdminPort.setLdapEnabled("acme", false)).thenReturn(orgInfo());

      var response = organizationAdminController.setLdapEnabled("acme", new SsoEnabledForm(false));

      verify(organizationAdminPort).setLdapEnabled("acme", false);
      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("POST /{slug}/ldap/test tests connection")
    void testLdap_returnsResult() {
      var result = new LdapConnectionTestResult(true, "connected");
      when(organizationAdminPort.testLdapConnection("acme")).thenReturn(result);

      var response = organizationAdminController.testLdap("acme");

      assertThat(response.getBody()).isEqualTo(result);
    }
  }
}
