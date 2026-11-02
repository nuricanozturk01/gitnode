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
package dev.gitnode.os.profile.controllers;

import dev.gitnode.os.profile.dtos.ChangePasswordForm;
import dev.gitnode.os.profile.dtos.TenantPublicProfileDto;
import dev.gitnode.os.profile.dtos.TenantSearchResult;
import dev.gitnode.os.profile.dtos.UpdateDisplayNameForm;
import dev.gitnode.os.profile.dtos.UpdateProfileForm;
import dev.gitnode.os.profile.dtos.UpdateUsernameForm;
import dev.gitnode.os.profile.dtos.UserContributionsResponse;
import dev.gitnode.os.profile.services.ProfileService;
import dev.gitnode.os.profile.services.TenantEmailService;
import dev.gitnode.os.profile.services.TenantSearchService;
import dev.gitnode.os.profile.services.UserContributionService;
import dev.gitnode.os.shared.auth.services.JwtUtils;
import dev.gitnode.os.shared.tenant.dtos.TenantInfo;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@NullMarked
public class ProfileController {

  private static final int MIN_LEN = 3;

  private final TenantSearchService tenantSearchService;
  private final ProfileService profileService;
  private final UserContributionService userContributionService;
  private final JwtUtils jwtUtils;
  private final TenantEmailService emailService;

  @GetMapping("/me")
  public ResponseEntity<TenantInfo> getMe(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);

    final var user = this.profileService.getTenantInfo(tenantId);

    return ResponseEntity.ok(user);
  }

  @PatchMapping("/me")
  public ResponseEntity<TenantInfo> updateMe(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      final @Valid @RequestBody UpdateUsernameForm form) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);

    final var user = this.profileService.updateUsername(tenantId, form);

    return ResponseEntity.ok(user);
  }

  @PatchMapping("/me/display-name")
  public ResponseEntity<TenantInfo> updateDisplayName(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      final @Valid @RequestBody UpdateDisplayNameForm form) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);

    final var user = this.profileService.updateDisplayName(tenantId, form);

    return ResponseEntity.ok(user);
  }

  @PatchMapping("/me/profile")
  public ResponseEntity<TenantInfo> updateProfile(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      final @Valid @RequestBody UpdateProfileForm form) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);

    final var user = this.profileService.updateProfile(tenantId, form);

    return ResponseEntity.ok(user);
  }

  public record EmailForm(
      @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 254)
          String email) {}

  @GetMapping("/me/emails")
  public List<TenantEmailService.ExtraEmail> listEmails(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
    return this.emailService.list(this.jwtUtils.extractUserId(authHeader));
  }

  @PostMapping("/me/emails")
  public ResponseEntity<TenantEmailService.ExtraEmail> addEmail(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      final @Valid @RequestBody EmailForm form) {
    final var created =
        this.emailService.add(this.jwtUtils.extractUserId(authHeader), form.email());
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @DeleteMapping("/me/emails/{emailId}")
  public ResponseEntity<Void> removeEmail(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      final @PathVariable UUID emailId) {
    this.emailService.remove(this.jwtUtils.extractUserId(authHeader), emailId);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/me/password")
  public ResponseEntity<Void> changePassword(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      final @Valid @RequestBody ChangePasswordForm form) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);

    this.profileService.changePassword(tenantId, form);

    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/me")
  public ResponseEntity<Void> deleteAccount(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);

    this.profileService.deleteAccount(tenantId);

    return ResponseEntity.noContent().build();
  }

  @GetMapping("/search")
  public ResponseEntity<List<TenantSearchResult>> search(@RequestParam("q") final String query) {

    final var trimmed = query.trim();

    if (trimmed.length() < MIN_LEN) {
      return ResponseEntity.ok(List.of());
    }

    final var results = this.tenantSearchService.search(trimmed);

    return ResponseEntity.ok(results);
  }

  @GetMapping("/{username}/contributions")
  public ResponseEntity<UserContributionsResponse> getContributions(
      @PathVariable final String username,
      final @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

    final var viewerTenantId = this.jwtUtils.tryExtractUserId(authHeader);
    final var contributions =
        this.userContributionService.getContributions(username, viewerTenantId);

    return ResponseEntity.ok(contributions);
  }

  @GetMapping("/{username}")
  public ResponseEntity<TenantPublicProfileDto> getPublicProfile(
      @PathVariable final String username) {

    final var profile = this.profileService.getPublicProfile(username);

    return ResponseEntity.ok(profile);
  }
}
