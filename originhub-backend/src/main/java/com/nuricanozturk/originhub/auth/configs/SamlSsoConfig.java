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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "originhub.sso.saml.enabled", havingValue = "true")
@NullMarked
public class SamlSsoConfig {

  @Bean
  public RelyingPartyRegistrationRepository relyingPartyRegistrationRepository(
      final OrganizationRepository organizationRepository,
      final SamlRegistrationBuilder registrationBuilder,
      @Value("${originhub.sso.saml.registration-id:saml}") final String legacyRegistrationId,
      @Value("${originhub.sso.saml.idp-metadata-uri:}") final String legacyMetadataUri,
      @Value("${originhub.sso.saml.sp-entity-id:originhub}") final String legacySpEntityId) {

    log.info(
        "SAML: using dynamic RelyingPartyRegistrationRepository (legacy fallback registration-id={})",
        legacyRegistrationId);

    return new DynamicRelyingPartyRegistrationRepository(
        organizationRepository,
        registrationBuilder,
        legacyRegistrationId,
        legacyMetadataUri,
        legacySpEntityId);
  }
}
