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

import dev.gitnode.os.auth.entities.Organization;
import dev.gitnode.os.auth.repositories.OrganizationRepository;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@NullMarked
public class SamlRegistrationBuilder {

  private final OrganizationRepository organizationRepository;
  private final SamlMetadataService samlMetadataService;
  private final String defaultSpEntityId;
  private final String spSigningKeyPath;
  private final String spSigningCertPath;

  public SamlRegistrationBuilder(
      final OrganizationRepository organizationRepository,
      final SamlMetadataService samlMetadataService,
      @Value("${gitnode.sso.saml.sp-entity-id:gitnode}") final String defaultSpEntityId,
      @Value("${gitnode.sso.saml.sp-signing-key-path:}") final String spSigningKeyPath,
      @Value("${gitnode.sso.saml.sp-signing-cert-path:}") final String spSigningCertPath) {

    this.organizationRepository = organizationRepository;
    this.samlMetadataService = samlMetadataService;
    this.defaultSpEntityId = defaultSpEntityId;
    this.spSigningKeyPath = spSigningKeyPath;
    this.spSigningCertPath = spSigningCertPath;
  }

  public @Nullable RelyingPartyRegistration buildFromOrganization(final Organization organization) {

    final var hadCachedXml =
        organization.getIdpMetadataXml() != null && !organization.getIdpMetadataXml().isBlank();
    final var metadataXml =
        this.samlMetadataService.resolveMetadataXml(
            organization.getIdpMetadataXml(), organization.getIdpMetadataUri());

    if (metadataXml == null || metadataXml.isBlank()) {
      log.warn(
          "SAML: skipping org '{}' — no metadata available (uri={}, cached={})",
          organization.getSlug(),
          organization.getIdpMetadataUri(),
          organization.getIdpMetadataXml() != null);
      return null;
    }

    if (!hadCachedXml) {
      organization.setIdpMetadataXml(metadataXml);
      this.organizationRepository.save(organization);
      log.info("SAML: cached IdP metadata XML for org '{}'", organization.getSlug());
    }

    return this.buildRegistration(
        organization.getSlug(),
        metadataXml,
        organization.getSpEntityId() != null
            ? organization.getSpEntityId()
            : this.defaultSpEntityId);
  }

  public @Nullable RelyingPartyRegistration buildFromLegacy(
      final String registrationId, final String metadataUri, final String spEntityId) {

    if (metadataUri.isBlank()) {
      return null;
    }

    try {
      final var builder =
          RelyingPartyRegistrations.fromMetadataLocation(metadataUri)
              .registrationId(registrationId)
              .entityId(spEntityId);
      this.applySigningCredentials(builder);
      return builder.build();
    } catch (final Exception ex) {
      log.warn(
          "SAML: legacy registration '{}' unavailable — {}: {}",
          registrationId,
          ex.getClass().getSimpleName(),
          ex.getMessage());
      return null;
    }
  }

  private RelyingPartyRegistration buildRegistration(
      final String registrationId, final String metadataXml, final String spEntityId) {

    final var builder =
        RelyingPartyRegistrations.fromMetadata(
                new ByteArrayInputStream(metadataXml.getBytes(StandardCharsets.UTF_8)))
            .registrationId(registrationId)
            .entityId(spEntityId);
    this.applySigningCredentials(builder);
    return builder.build();
  }

  private void applySigningCredentials(final RelyingPartyRegistration.Builder builder) {

    if (this.spSigningKeyPath.isBlank() || this.spSigningCertPath.isBlank()) {
      return;
    }

    try {
      final var privateKey = this.loadPrivateKey(this.spSigningKeyPath);
      final var certificate = this.loadCertificate(this.spSigningCertPath);
      final var credential = Saml2X509Credential.signing(privateKey, certificate);
      final var decryptCredential = Saml2X509Credential.decryption(privateKey, certificate);
      builder
          .signingX509Credentials(creds -> creds.add(credential))
          .decryptionX509Credentials(creds -> creds.add(decryptCredential));
    } catch (final Exception ex) {
      log.warn("SAML: failed to load SP signing credentials: {}", ex.getMessage());
    }
  }

  private RSAPrivateKey loadPrivateKey(final String path) throws Exception {

    final var pem = Files.readString(Paths.get(path));
    final var keyData =
        pem.replaceAll("-----BEGIN(?: RSA)? PRIVATE KEY-----", "")
            .replaceAll("-----END(?: RSA)? PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    final var decoded = Base64.getDecoder().decode(keyData);
    final var spec = new PKCS8EncodedKeySpec(decoded);
    return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
  }

  private X509Certificate loadCertificate(final String path) throws IOException, Exception {

    try (final var fis = new FileInputStream(path)) {
      return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(fis);
    }
  }
}
