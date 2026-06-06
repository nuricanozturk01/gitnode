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
import com.nuricanozturk.originhub.shared.repo.dtos.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/organizations")
@RequiredArgsConstructor
@NullMarked
@PreAuthorize("@platformAdminService.isCurrentUserPlatformAdmin()")
public class OrganizationAdminController {

  private static final int MAX_PAGE_SIZE = 100;

  private final OrganizationAdminPort organizationAdminPort;
  private final PlatformAdminService platformAdminService;

  @GetMapping
  public ResponseEntity<PageResponse<OrganizationInfo>> list(
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "10") final int size) {

    this.platformAdminService.requirePlatformAdmin();
    final var pageable =
        PageRequest.of(
            page, Math.min(size, MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "createdAt"));
    return ResponseEntity.ok(PageResponse.from(this.organizationAdminPort.list(pageable)));
  }

  @GetMapping("/{slug}")
  public ResponseEntity<OrganizationInfo> getBySlug(@PathVariable final String slug) {

    this.platformAdminService.requirePlatformAdmin();
    return ResponseEntity.ok(this.organizationAdminPort.getBySlug(slug));
  }

  @PostMapping
  public ResponseEntity<OrganizationInfo> create(@RequestBody @Valid final OrganizationForm form) {

    this.platformAdminService.requirePlatformAdmin();
    return ResponseEntity.status(HttpStatus.CREATED).body(this.organizationAdminPort.create(form));
  }

  @PutMapping("/{slug}")
  public ResponseEntity<OrganizationInfo> update(
      @PathVariable final String slug, @RequestBody @Valid final OrganizationUpdateForm form) {

    this.platformAdminService.requirePlatformAdmin();
    return ResponseEntity.ok(this.organizationAdminPort.update(slug, form));
  }

  @DeleteMapping("/{slug}")
  public ResponseEntity<Void> delete(@PathVariable final String slug) {

    this.platformAdminService.requirePlatformAdmin();
    this.organizationAdminPort.delete(slug);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{slug}/sso")
  public ResponseEntity<OrganizationInfo> updateSso(
      @PathVariable final String slug, @RequestBody @Valid final OrganizationSsoForm form) {

    this.platformAdminService.requirePlatformAdmin();
    return ResponseEntity.ok(this.organizationAdminPort.updateSso(slug, form));
  }

  @PutMapping("/{slug}/sso/enabled")
  public ResponseEntity<OrganizationInfo> setSsoEnabled(
      @PathVariable final String slug, @RequestBody @Valid final SsoEnabledForm form) {

    this.platformAdminService.requirePlatformAdmin();
    return ResponseEntity.ok(this.organizationAdminPort.setSsoEnabled(slug, form.enabled()));
  }

  @PostMapping("/{slug}/sso/test")
  public ResponseEntity<SamlMetadataTestResult> testSso(@PathVariable final String slug) {

    this.platformAdminService.requirePlatformAdmin();
    return ResponseEntity.ok(this.organizationAdminPort.testAndCacheMetadata(slug));
  }

  @PutMapping("/{slug}/ldap")
  public ResponseEntity<OrganizationInfo> updateLdap(
      @PathVariable final String slug, @RequestBody @Valid final OrganizationLdapForm form) {

    this.platformAdminService.requirePlatformAdmin();
    return ResponseEntity.ok(this.organizationAdminPort.updateLdap(slug, form));
  }

  @PutMapping("/{slug}/ldap/enabled")
  public ResponseEntity<OrganizationInfo> setLdapEnabled(
      @PathVariable final String slug, @RequestBody @Valid final SsoEnabledForm form) {

    this.platformAdminService.requirePlatformAdmin();
    return ResponseEntity.ok(this.organizationAdminPort.setLdapEnabled(slug, form.enabled()));
  }

  @PostMapping("/{slug}/ldap/test")
  public ResponseEntity<LdapConnectionTestResult> testLdap(@PathVariable final String slug) {

    this.platformAdminService.requirePlatformAdmin();
    return ResponseEntity.ok(this.organizationAdminPort.testLdapConnection(slug));
  }
}
