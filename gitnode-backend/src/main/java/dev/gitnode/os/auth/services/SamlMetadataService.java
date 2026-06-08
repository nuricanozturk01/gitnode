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

import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@NullMarked
public class SamlMetadataService {

  private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(15);

  private final RestClient restClient;
  private final CircuitBreaker circuitBreaker;

  public SamlMetadataService(final CircuitBreakerRegistry circuitBreakerRegistry) {

    final var requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(FETCH_TIMEOUT);
    requestFactory.setReadTimeout(FETCH_TIMEOUT);
    this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("saml-metadata");
  }

  public String fetchMetadataXml(final String metadataUri) {

    if (metadataUri.isBlank()) {
      throw new BadRequestException("idpMetadataUriRequired");
    }

    try {
      return this.circuitBreaker.executeSupplier(
          () ->
              Objects.requireNonNull(
                  this.restClient.get().uri(metadataUri).retrieve().body(String.class)));
    } catch (final CallNotPermittedException ex) {
      log.warn("SAML metadata circuit OPEN for {}", metadataUri);
      throw new BadRequestException("idpMetadataFetchFailed");
    } catch (final RuntimeException ex) {
      log.warn("Failed to fetch SAML metadata from {}: {}", metadataUri, ex.getMessage());
      throw new BadRequestException("idpMetadataFetchFailed");
    }
  }

  public void validateMetadataXml(final String metadataXml) {

    if (metadataXml.isBlank()) {
      throw new BadRequestException("idpMetadataEmpty");
    }

    try {
      RelyingPartyRegistrations.fromMetadata(
              new ByteArrayInputStream(metadataXml.getBytes(StandardCharsets.UTF_8)))
          .registrationId("validation")
          .build();
    } catch (final Exception ex) {
      log.warn("Invalid SAML metadata XML: {}", ex.getMessage());
      throw new BadRequestException("idpMetadataInvalid");
    }
  }

  public @Nullable String resolveMetadataXml(
      final @Nullable String cachedXml, final @Nullable String metadataUri) {

    if (cachedXml != null && !cachedXml.isBlank()) {
      return cachedXml;
    }

    if (metadataUri == null || metadataUri.isBlank()) {
      return null;
    }

    return this.fetchMetadataXml(metadataUri);
  }
}
