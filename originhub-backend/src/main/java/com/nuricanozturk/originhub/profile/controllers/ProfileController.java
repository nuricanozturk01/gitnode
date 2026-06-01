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
package com.nuricanozturk.originhub.profile.controllers;

import com.nuricanozturk.originhub.profile.dtos.ChangePasswordForm;
import com.nuricanozturk.originhub.profile.dtos.TenantPublicProfileDto;
import com.nuricanozturk.originhub.profile.dtos.TenantSearchResult;
import com.nuricanozturk.originhub.profile.dtos.UpdateDisplayNameForm;
import com.nuricanozturk.originhub.profile.dtos.UpdateProfileForm;
import com.nuricanozturk.originhub.profile.dtos.UpdateUsernameForm;
import com.nuricanozturk.originhub.profile.services.ProfileService;
import com.nuricanozturk.originhub.profile.services.TenantSearchService;
import com.nuricanozturk.originhub.shared.auth.services.JwtUtils;
import com.nuricanozturk.originhub.shared.tenant.dtos.TenantInfo;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
  private final JwtUtils jwtUtils;

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

  @GetMapping("/{username}")
  public ResponseEntity<TenantPublicProfileDto> getPublicProfile(
      @PathVariable final String username) {

    final var profile = this.profileService.getPublicProfile(username);

    return ResponseEntity.ok(profile);
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
}
