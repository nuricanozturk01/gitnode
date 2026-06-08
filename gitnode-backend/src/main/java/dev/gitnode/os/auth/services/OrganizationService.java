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

import dev.gitnode.os.auth.api.LdapConnectionTestResult;
import dev.gitnode.os.auth.api.OrganizationForm;
import dev.gitnode.os.auth.api.OrganizationInfo;
import dev.gitnode.os.auth.api.OrganizationLdapForm;
import dev.gitnode.os.auth.api.OrganizationSsoForm;
import dev.gitnode.os.auth.api.OrganizationUpdateForm;
import dev.gitnode.os.auth.api.SamlMetadataTestResult;
import dev.gitnode.os.auth.dtos.LdapDiscoverResponse;
import dev.gitnode.os.auth.dtos.SamlDiscoverResponse;
import dev.gitnode.os.auth.entities.Organization;
import dev.gitnode.os.auth.repositories.OrganizationRepository;
import dev.gitnode.os.shared.audit.annotations.Audited;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NullMarked
public class OrganizationService {

  private final OrganizationRepository organizationRepository;
  private final SamlMetadataService samlMetadataService;
  private final LdapConnectionService ldapConnectionService;

  public Page<OrganizationInfo> list(final Pageable pageable) {

    return this.organizationRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toInfo);
  }

  public OrganizationInfo getBySlug(final String slug) {

    return this.toInfo(this.requireBySlug(slug));
  }

  @Audited(
      action = "CREATE_ORGANIZATION",
      entityType = "ORGANIZATION",
      entityIdSpEL = "#result.id()",
      detailsSpEL = "'slug=' + #form.slug() + ', name=' + #form.name()")
  @Transactional
  public OrganizationInfo create(final OrganizationForm form) {

    final var slug = form.slug().trim().toLowerCase(Locale.getDefault());

    if (this.organizationRepository.existsBySlug(slug)) {
      throw new BadRequestException("organizationSlugExists");
    }

    final var organization = new Organization();
    organization.setName(form.name().trim());
    organization.setSlug(slug);
    organization.setEmailDomains(this.normalizeDomains(form.emailDomains()));

    return this.toInfo(this.organizationRepository.save(organization));
  }

  @Audited(
      action = "UPDATE_ORGANIZATION",
      entityType = "ORGANIZATION",
      detailsSpEL = "'slug=' + #slug")
  @Transactional
  public OrganizationInfo update(final String slug, final OrganizationUpdateForm form) {

    final var organization = this.requireBySlug(slug);
    organization.setName(form.name().trim());
    organization.setEmailDomains(this.normalizeDomains(form.emailDomains()));

    return this.toInfo(this.organizationRepository.save(organization));
  }

  @Audited(
      action = "DELETE_ORGANIZATION",
      entityType = "ORGANIZATION",
      detailsSpEL = "'slug=' + #slug")
  @Transactional
  public void delete(final String slug) {

    final var organization = this.requireBySlug(slug);
    this.organizationRepository.delete(organization);
  }

  @Audited(
      action = "UPDATE_ORGANIZATION_SSO",
      entityType = "ORGANIZATION",
      detailsSpEL = "'slug=' + #slug + ', ssoEnabled=' + #form.ssoEnabled()")
  @Transactional
  public OrganizationInfo updateSso(final String slug, final OrganizationSsoForm form) {

    final var organization = this.requireBySlug(slug);

    if (form.ssoEnabled() && organization.isLdapEnabled()) {
      throw new BadRequestException("ssoProtocolConflict");
    }

    organization.setSsoEnabled(form.ssoEnabled());

    this.setOrganizationFields(form, organization);

    if (organization.isSsoEnabled()
        && organization.getIdpMetadataUri() == null
        && (organization.getIdpMetadataXml() == null
            || organization.getIdpMetadataXml().isBlank())) {
      throw new BadRequestException("idpMetadataUriRequired");
    }

    return this.toInfo(this.organizationRepository.save(organization));
  }

  @Audited(
      action = "SET_ORGANIZATION_SSO_ENABLED",
      entityType = "ORGANIZATION",
      detailsSpEL = "'slug=' + #slug + ', enabled=' + #enabled")
  @Transactional
  public OrganizationInfo setSsoEnabled(final String slug, final boolean enabled) {

    final var organization = this.requireBySlug(slug);

    if (enabled && organization.isLdapEnabled()) {
      throw new BadRequestException("ssoProtocolConflict");
    }

    organization.setSsoEnabled(enabled);

    if (organization.isSsoEnabled()
        && organization.getIdpMetadataUri() == null
        && (organization.getIdpMetadataXml() == null
            || organization.getIdpMetadataXml().isBlank())) {
      throw new BadRequestException("idpMetadataUriRequired");
    }

    return this.toInfo(this.organizationRepository.save(organization));
  }

  @Audited(
      action = "TEST_ORGANIZATION_SSO",
      entityType = "ORGANIZATION",
      detailsSpEL = "'slug=' + #slug")
  @Transactional
  public SamlMetadataTestResult testAndCacheMetadata(final String slug) {

    final var organization = this.requireBySlug(slug);
    final var metadataUri = organization.getIdpMetadataUri();

    if (metadataUri == null || metadataUri.isBlank()) {
      throw new BadRequestException("idpMetadataUriRequired");
    }

    final var metadataXml = this.samlMetadataService.fetchMetadataXml(metadataUri);
    this.samlMetadataService.validateMetadataXml(metadataXml);

    organization.setIdpMetadataXml(metadataXml);
    this.organizationRepository.save(organization);

    return new SamlMetadataTestResult(true, "metadataValid", true);
  }

  @Audited(
      action = "UPDATE_ORGANIZATION_LDAP",
      entityType = "ORGANIZATION",
      detailsSpEL = "'slug=' + #slug + ', ldapEnabled=' + #form.ldapEnabled()")
  @Transactional
  public OrganizationInfo updateLdap(final String slug, final OrganizationLdapForm form) {

    final var organization = this.requireBySlug(slug);

    if (form.ldapEnabled() && organization.isSsoEnabled()) {
      throw new BadRequestException("ssoProtocolConflict");
    }

    organization.setLdapEnabled(form.ldapEnabled());
    this.applyLdapForm(form, organization);

    if (organization.isLdapEnabled() && !this.ldapConnectionService.isConfigured(organization)) {
      throw new BadRequestException("ldapConfigIncomplete");
    }

    return this.toInfo(this.organizationRepository.save(organization));
  }

  @Audited(
      action = "SET_ORGANIZATION_LDAP_ENABLED",
      entityType = "ORGANIZATION",
      detailsSpEL = "'slug=' + #slug + ', enabled=' + #enabled")
  @Transactional
  public OrganizationInfo setLdapEnabled(final String slug, final boolean enabled) {

    final var organization = this.requireBySlug(slug);

    if (enabled && organization.isSsoEnabled()) {
      throw new BadRequestException("ssoProtocolConflict");
    }

    organization.setLdapEnabled(enabled);

    if (organization.isLdapEnabled() && !this.ldapConnectionService.isConfigured(organization)) {
      throw new BadRequestException("ldapConfigIncomplete");
    }

    return this.toInfo(this.organizationRepository.save(organization));
  }

  @Audited(
      action = "TEST_ORGANIZATION_LDAP",
      entityType = "ORGANIZATION",
      detailsSpEL = "'slug=' + #slug")
  public LdapConnectionTestResult testLdapConnection(final String slug) {

    final var organization = this.requireBySlug(slug);
    this.ldapConnectionService.testConnection(organization);
    return new LdapConnectionTestResult(true, "ldapConnectionValid");
  }

  public @Nullable SamlDiscoverResponse discoverByEmail(final String email) {

    final var domain = this.extractEmailDomain(email);

    return this.organizationRepository
        .findSsoEnabledByEmailDomain(domain)
        .map(
            org -> {
              final var registrationId = org.getSlug();
              return new SamlDiscoverResponse(
                  registrationId,
                  registrationId,
                  "/saml2/authenticate/%s".formatted(registrationId));
            })
        .orElse(null);
  }

  public @Nullable LdapDiscoverResponse discoverLdapByEmail(final String email) {

    final var domain = this.extractEmailDomain(email);

    return this.organizationRepository
        .findLdapEnabledByEmailDomain(domain)
        .map(org -> new LdapDiscoverResponse(org.getSlug(), org.getName()))
        .orElse(null);
  }

  @Transactional(readOnly = true)
  public long countOrganizations() {

    return this.organizationRepository.count();
  }

  @Transactional(readOnly = true)
  public long countSsoEnabledOrganizations() {

    return this.organizationRepository.countBySsoEnabledTrue()
        + this.organizationRepository.countByLdapEnabledTrue();
  }

  public Organization requireBySlug(final String slug) {

    return this.organizationRepository
        .findBySlug(slug)
        .orElseThrow(() -> new ItemNotFoundException("organizationNotFound"));
  }

  private List<String> normalizeDomains(final List<String> domains) {

    return domains.stream()
        .map(String::trim)
        .map(d -> d.toLowerCase(Locale.getDefault()))
        .filter(d -> !d.isBlank())
        .distinct()
        .toList();
  }

  private OrganizationInfo toInfo(final Organization organization) {

    final var managerPassword = organization.getLdapManagerPassword();

    return new OrganizationInfo(
        organization.getId(),
        organization.getName(),
        organization.getSlug(),
        List.copyOf(organization.getEmailDomains()),
        organization.isSsoEnabled(),
        organization.getIdpMetadataUri(),
        organization.getIdpMetadataXml() != null && !organization.getIdpMetadataXml().isBlank(),
        organization.getEmailAttribute(),
        organization.getUsernameAttribute(),
        organization.getSpEntityId(),
        organization.isLdapEnabled(),
        this.ldapConnectionService.isConfigured(organization),
        organization.getLdapUrl(),
        organization.getLdapBaseDn(),
        organization.getLdapManagerDn(),
        managerPassword != null && !managerPassword.isBlank(),
        organization.getLdapUserSearchBase(),
        organization.getLdapUserSearchFilter(),
        organization.getLdapEmailAttribute(),
        organization.getLdapDisplayNameAttribute(),
        organization.isLdapUseStartTls(),
        organization.getLdapGroupSearchBase(),
        organization.getLdapGroupSearchFilter(),
        organization.getLdapGroupRoleAttribute(),
        organization.getLdapAdminGroupDns(),
        organization.getCreatedAt(),
        organization.getUpdatedAt());
  }

  private void setOrganizationFields(
      final OrganizationSsoForm form, final Organization organization) {

    if (form.idpMetadataUri() != null) {
      organization.setIdpMetadataUri(
          form.idpMetadataUri().isBlank() ? null : form.idpMetadataUri().trim());
    }

    if (form.emailAttribute() != null && !form.emailAttribute().isBlank()) {
      organization.setEmailAttribute(form.emailAttribute().trim());
    }

    organization.setUsernameAttribute(trimOrNull(form.usernameAttribute()));
    organization.setSpEntityId(trimOrNull(form.spEntityId()));
  }

  private void applyLdapForm(final OrganizationLdapForm form, final Organization organization) {

    this.applyLdapConnectionForm(form, organization);
    this.applyLdapSearchForm(form, organization);
    this.applyLdapGroupForm(form, organization);
  }

  private void applyLdapConnectionForm(
      final OrganizationLdapForm form, final Organization organization) {

    if (form.url() != null) {
      organization.setLdapUrl(LdapConnectionService.blankToNull(form.url()));
    }

    if (form.baseDn() != null) {
      organization.setLdapBaseDn(LdapConnectionService.blankToNull(form.baseDn()));
    }

    if (form.managerDn() != null) {
      organization.setLdapManagerDn(LdapConnectionService.blankToNull(form.managerDn()));
    }

    if (form.managerPassword() != null && !form.managerPassword().isBlank()) {
      organization.setLdapManagerPassword(form.managerPassword());
    }

    if (form.useStartTls() != null) {
      organization.setLdapUseStartTls(form.useStartTls());
    }
  }

  private void applyLdapSearchForm(
      final OrganizationLdapForm form, final Organization organization) {

    applyTrimmedIfPresent(form.userSearchBase(), organization::setLdapUserSearchBase);
    applyTrimmedIfPresent(form.userSearchFilter(), organization::setLdapUserSearchFilter);
    applyTrimmedIfPresent(form.emailAttribute(), organization::setLdapEmailAttribute);
    applyTrimmedIfPresent(form.displayNameAttribute(), organization::setLdapDisplayNameAttribute);
  }

  private static void applyTrimmedIfPresent(
      final @Nullable String value, final Consumer<String> setter) {

    if (value != null && !value.isBlank()) {
      setter.accept(value.trim());
    }
  }

  private void applyLdapGroupForm(
      final OrganizationLdapForm form, final Organization organization) {

    if (form.groupSearchBase() != null) {
      organization.setLdapGroupSearchBase(
          LdapConnectionService.blankToNull(form.groupSearchBase()));
    }

    if (form.groupSearchFilter() != null) {
      organization.setLdapGroupSearchFilter(
          LdapConnectionService.blankToNull(form.groupSearchFilter()));
    }

    if (form.groupRoleAttribute() != null) {
      organization.setLdapGroupRoleAttribute(
          LdapConnectionService.blankToNull(form.groupRoleAttribute()));
    }

    if (form.adminGroupDns() != null) {
      organization.setLdapAdminGroupDns(LdapConnectionService.blankToNull(form.adminGroupDns()));
    }
  }

  private String extractEmailDomain(final String email) {

    final var normalizedEmail = email.trim().toLowerCase(Locale.getDefault());
    final var atIndex = normalizedEmail.lastIndexOf('@');

    if (atIndex < 1 || atIndex == normalizedEmail.length() - 1) {
      throw new BadRequestException("invalidEmail");
    }

    return normalizedEmail.substring(atIndex + 1);
  }

  private static @Nullable String trimOrNull(final @Nullable String value) {

    return (value != null && !value.isBlank()) ? value.trim() : null;
  }
}
