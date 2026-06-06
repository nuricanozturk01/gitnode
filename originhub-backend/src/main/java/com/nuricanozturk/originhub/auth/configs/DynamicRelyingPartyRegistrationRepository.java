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
package com.nuricanozturk.originhub.auth.configs;

import com.nuricanozturk.originhub.auth.repositories.OrganizationRepository;
import com.nuricanozturk.originhub.auth.services.SamlRegistrationBuilder;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;

@Slf4j
@NullMarked
public class DynamicRelyingPartyRegistrationRepository
    implements RelyingPartyRegistrationRepository {

  private final OrganizationRepository organizationRepository;
  private final SamlRegistrationBuilder registrationBuilder;
  private final String legacyRegistrationId;
  private final String legacyMetadataUri;
  private final String legacySpEntityId;

  public DynamicRelyingPartyRegistrationRepository(
      final OrganizationRepository organizationRepository,
      final SamlRegistrationBuilder registrationBuilder,
      @Value("${originhub.sso.saml.registration-id:saml}") final String legacyRegistrationId,
      @Value("${originhub.sso.saml.idp-metadata-uri:}") final String legacyMetadataUri,
      @Value("${originhub.sso.saml.sp-entity-id:originhub}") final String legacySpEntityId) {

    this.organizationRepository = organizationRepository;
    this.registrationBuilder = registrationBuilder;
    this.legacyRegistrationId = legacyRegistrationId;
    this.legacyMetadataUri = legacyMetadataUri;
    this.legacySpEntityId = legacySpEntityId;
  }

  @Override
  public @Nullable RelyingPartyRegistration findByRegistrationId(final String registrationId) {

    final var orgRegistration = this.findOrganizationRegistration(registrationId);

    if (orgRegistration != null) {
      return orgRegistration;
    }

    if (this.legacyRegistrationId.equals(registrationId)) {
      return this.legacyRegistration();
    }

    return null;
  }

  private @Nullable RelyingPartyRegistration findOrganizationRegistration(
      final String registrationId) {

    return this.organizationRepository
        .findBySlug(registrationId)
        .filter(org -> org.isSsoEnabled())
        .map(
            org -> {
              try {
                return this.registrationBuilder.buildFromOrganization(org);
              } catch (final Exception ex) {
                log.warn(
                    "SAML: failed to build registration for org '{}': {}",
                    org.getSlug(),
                    ex.getMessage());
                return null;
              }
            })
        .orElse(null);
  }

  private @Nullable RelyingPartyRegistration legacyRegistration() {

    if (this.organizationRepository.countBySsoEnabledTrue() > 0) {
      return null;
    }

    if (this.legacyMetadataUri.isBlank()) {
      return null;
    }

    return this.registrationBuilder.buildFromLegacy(
        this.legacyRegistrationId, this.legacyMetadataUri, this.legacySpEntityId);
  }
}
