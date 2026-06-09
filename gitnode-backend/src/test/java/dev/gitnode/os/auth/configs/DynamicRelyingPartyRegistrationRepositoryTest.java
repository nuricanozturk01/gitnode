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
package dev.gitnode.os.auth.configs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.gitnode.os.auth.entities.Organization;
import dev.gitnode.os.auth.repositories.OrganizationRepository;
import dev.gitnode.os.auth.services.SamlRegistrationBuilder;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;

@ExtendWith(MockitoExtension.class)
@DisplayName("DynamicRelyingPartyRegistrationRepository unit tests")
class DynamicRelyingPartyRegistrationRepositoryTest {

  @Mock private OrganizationRepository organizationRepository;
  @Mock private SamlRegistrationBuilder registrationBuilder;

  @Test
  @DisplayName("findByRegistrationId returns org registration when SSO enabled")
  void findByRegistrationId_returnsOrgRegistration() {

    final var org = sampleOrg("acme", true);
    final var registration = sampleRegistration("acme");
    final var repository = this.createRepository("saml", "", "gitnode");

    when(organizationRepository.findBySlug("acme")).thenReturn(Optional.of(org));
    when(registrationBuilder.buildFromOrganization(org)).thenReturn(registration);

    assertThat(repository.findByRegistrationId("acme")).isEqualTo(registration);
  }

  @Test
  @DisplayName("findByRegistrationId uses legacy fallback when no SSO orgs exist")
  void findByRegistrationId_usesLegacyFallback() {

    final var registration = sampleRegistration("saml");
    final var repository =
        this.createRepository("saml", "https://idp.example.com/metadata", "gitnode");

    when(organizationRepository.findBySlug("saml")).thenReturn(Optional.empty());
    when(organizationRepository.countBySsoEnabledTrue()).thenReturn(0L);
    when(registrationBuilder.buildFromLegacy("saml", "https://idp.example.com/metadata", "gitnode"))
        .thenReturn(registration);

    assertThat(repository.findByRegistrationId("saml")).isEqualTo(registration);
  }

  @Test
  @DisplayName("findByRegistrationId skips legacy when DB orgs have SSO enabled")
  void findByRegistrationId_skipsLegacy_whenDbOrgsExist() {

    final var repository =
        this.createRepository("saml", "https://idp.example.com/metadata", "gitnode");

    when(organizationRepository.findBySlug("saml")).thenReturn(Optional.empty());
    when(organizationRepository.countBySsoEnabledTrue()).thenReturn(1L);

    assertThat(repository.findByRegistrationId("saml")).isNull();
  }

  private DynamicRelyingPartyRegistrationRepository createRepository(
      final String legacyRegistrationId,
      final String legacyMetadataUri,
      final String legacySpEntityId) {

    return new DynamicRelyingPartyRegistrationRepository(
        organizationRepository,
        registrationBuilder,
        legacyRegistrationId,
        legacyMetadataUri,
        legacySpEntityId);
  }

  private static Organization sampleOrg(final String slug, final boolean ssoEnabled) {

    final var org = new Organization();
    org.setId(UUID.randomUUID());
    org.setName("Test");
    org.setSlug(slug);
    org.setEmailDomains(List.of("example.com"));
    org.setSsoEnabled(ssoEnabled);
    org.setCreatedAt(Instant.now());
    org.setUpdatedAt(Instant.now());
    return org;
  }

  private static RelyingPartyRegistration sampleRegistration(final String registrationId) {

    return mock(RelyingPartyRegistration.class);
  }
}
