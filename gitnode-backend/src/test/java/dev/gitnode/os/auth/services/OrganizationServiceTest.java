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
package dev.gitnode.os.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.auth.api.OrganizationForm;
import dev.gitnode.os.auth.api.OrganizationLdapForm;
import dev.gitnode.os.auth.api.OrganizationSsoForm;
import dev.gitnode.os.auth.entities.Organization;
import dev.gitnode.os.auth.repositories.OrganizationRepository;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationService unit tests")
class OrganizationServiceTest {

  @Mock private OrganizationRepository organizationRepository;
  @Mock private SamlMetadataService samlMetadataService;
  @Mock private LdapConnectionService ldapConnectionService;

  @InjectMocks private OrganizationService organizationService;

  @BeforeEach
  void setUp() {

    org.mockito.Mockito.lenient()
        .when(ldapConnectionService.isConfigured(org.mockito.ArgumentMatchers.any()))
        .thenReturn(false);
  }

  @Test
  @DisplayName("create normalizes slug and email domains")
  void create_normalizesSlugAndDomains() {

    when(organizationRepository.existsBySlug("acme")).thenReturn(false);
    when(organizationRepository.save(any(Organization.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    final var form = new OrganizationForm("Acme Corp", "ACME", List.of(" Acme.COM ", "acme.com"));

    final var result = organizationService.create(form);

    final ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
    verify(organizationRepository).save(captor.capture());

    assertThat(captor.getValue().getSlug()).isEqualTo("acme");
    assertThat(captor.getValue().getEmailDomains()).containsExactly("acme.com");
    assertThat(result.slug()).isEqualTo("acme");
  }

  @Test
  @DisplayName("discoverByEmail returns registration for matching domain")
  void discoverByEmail_returnsRegistration_forMatchingDomain() {

    final var org = sampleOrg("acme", List.of("acme.com"), true);

    when(organizationRepository.findSsoEnabledByEmailDomain("acme.com"))
        .thenReturn(Optional.of(org));

    final var result = organizationService.discoverByEmail("alice@acme.com");

    assertThat(result).isNotNull();
    assertThat(result.orgSlug()).isEqualTo("acme");
    assertThat(result.registrationId()).isEqualTo("acme");
    assertThat(result.redirectUrl()).isEqualTo("/saml2/authenticate/acme");
  }

  @Test
  @DisplayName("discoverByEmail returns null when no org matches")
  void discoverByEmail_returnsNull_whenNoOrgMatches() {

    when(organizationRepository.findSsoEnabledByEmailDomain("unknown.com"))
        .thenReturn(Optional.empty());

    assertThat(organizationService.discoverByEmail("user@unknown.com")).isNull();
  }

  @Test
  @DisplayName("discoverByEmail throws for invalid email")
  void discoverByEmail_throws_forInvalidEmail() {

    assertThatThrownBy(() -> organizationService.discoverByEmail("not-an-email"))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  @DisplayName("updateSso requires metadata when enabling SSO")
  void updateSso_throws_whenEnablingWithoutMetadata() {

    final var org = sampleOrg("acme", List.of("acme.com"), false);
    org.setIdpMetadataUri(null);
    org.setIdpMetadataXml(null);

    when(organizationRepository.findBySlug("acme")).thenReturn(Optional.of(org));

    final var form = new OrganizationSsoForm(true, null, null, null, null);

    assertThatThrownBy(() -> organizationService.updateSso("acme", form))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  @DisplayName("testAndCacheMetadata validates and stores XML")
  void testAndCacheMetadata_cachesXml() {

    final var org = sampleOrg("acme", List.of("acme.com"), true);
    org.setIdpMetadataUri("https://idp.example.com/metadata");

    when(organizationRepository.findBySlug("acme")).thenReturn(Optional.of(org));
    when(samlMetadataService.fetchMetadataXml("https://idp.example.com/metadata"))
        .thenReturn("<EntityDescriptor/>");
    when(organizationRepository.save(any(Organization.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    final var result = organizationService.testAndCacheMetadata("acme");

    final ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
    verify(organizationRepository).save(captor.capture());

    assertThat(result.valid()).isTrue();
    assertThat(result.cached()).isTrue();
    assertThat(captor.getValue().getIdpMetadataXml()).isEqualTo("<EntityDescriptor/>");
  }

  @Test
  @DisplayName("discoverLdapByEmail returns org for matching domain")
  void discoverLdapByEmail_returnsOrg_forMatchingDomain() {

    final var org = sampleOrg("acme", List.of("acme.com"), false);
    org.setLdapEnabled(true);
    org.setName("Acme Corp");

    when(organizationRepository.findLdapEnabledByEmailDomain("acme.com"))
        .thenReturn(Optional.of(org));

    final var result = organizationService.discoverLdapByEmail("alice@acme.com");

    assertThat(result).isNotNull();
    assertThat(result.orgSlug()).isEqualTo("acme");
    assertThat(result.orgName()).isEqualTo("Acme Corp");
  }

  @Test
  @DisplayName("updateLdap requires config when enabling LDAP")
  void updateLdap_throws_whenEnablingWithoutConfig() {

    final var org = sampleOrg("acme", List.of("acme.com"), false);

    when(organizationRepository.findBySlug("acme")).thenReturn(Optional.of(org));
    when(ldapConnectionService.isConfigured(org)).thenReturn(false);

    final var form =
        new OrganizationLdapForm(
            true, null, null, null, null, null, null, null, null, null, null, null, null, null);

    assertThatThrownBy(() -> organizationService.updateLdap("acme", form))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  @DisplayName("getBySlug throws when organization not found")
  void getBySlug_throws_whenNotFound() {

    when(organizationRepository.findBySlug("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> organizationService.getBySlug("missing"))
        .isInstanceOf(ItemNotFoundException.class);
  }

  private static Organization sampleOrg(
      final String slug, final List<String> domains, final boolean ssoEnabled) {

    final var org = new Organization();
    org.setId(UUID.randomUUID());
    org.setName("Test Org");
    org.setSlug(slug);
    org.setEmailDomains(domains);
    org.setSsoEnabled(ssoEnabled);
    org.setEmailAttribute("email");
    org.setCreatedAt(Instant.now());
    org.setUpdatedAt(Instant.now());
    return org;
  }
}
