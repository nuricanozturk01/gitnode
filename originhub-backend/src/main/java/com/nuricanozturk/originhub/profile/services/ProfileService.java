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
package com.nuricanozturk.originhub.profile.services;

import com.nuricanozturk.originhub.profile.dtos.ChangePasswordForm;
import com.nuricanozturk.originhub.profile.dtos.TenantPublicProfileDto;
import com.nuricanozturk.originhub.profile.dtos.UpdateDisplayNameForm;
import com.nuricanozturk.originhub.profile.dtos.UpdateUsernameForm;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.BadRequestException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemAlreadyExistsException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.profile.events.TenantDeletedEvent;
import com.nuricanozturk.originhub.shared.profile.events.UsernameChangedEvent;
import com.nuricanozturk.originhub.shared.tenant.dtos.TenantInfo;
import com.nuricanozturk.originhub.shared.tenant.mappers.TenantMapper;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class ProfileService {

  private final @NonNull TenantRepository tenantRepository;
  private final @NonNull TenantMapper tenantMapper;
  private final @NonNull ApplicationEventPublisher eventPublisher;
  private static final String USER_NOT_FOUND = "userNotFound";

  @Transactional
  public @NonNull TenantInfo updateUsername(
      final @NonNull UUID tenantId, final @NonNull UpdateUsernameForm form) {

    final var tenant =
        this.tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new ItemNotFoundException(USER_NOT_FOUND));

    final var oldUsername = tenant.getUsername().toLowerCase(Locale.getDefault());
    final var newUsername = form.getUsername().toLowerCase(Locale.getDefault());

    if (this.tenantRepository.existsByUsername(newUsername) && !newUsername.equals(oldUsername)) {

      throw new ItemAlreadyExistsException("usernameTaken");
    }

    tenant.setUsername(newUsername);
    final var saved = this.tenantRepository.save(tenant);

    this.eventPublisher.publishEvent(new UsernameChangedEvent(oldUsername, newUsername));

    return this.tenantMapper.toTenantInfo(saved);
  }

  @Transactional
  public @NonNull TenantInfo updateDisplayName(
      final @NonNull UUID tenantId, final @NonNull UpdateDisplayNameForm form) {

    final var tenant =
        this.tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new ItemNotFoundException(USER_NOT_FOUND));

    tenant.setDisplayName(
        form.getDisplayName() != null && !form.getDisplayName().isBlank()
            ? form.getDisplayName().trim()
            : null);

    final var saved = this.tenantRepository.save(tenant);

    return this.tenantMapper.toTenantInfo(saved);
  }

  @Transactional
  public void changePassword(final @NonNull UUID tenantId, final @NonNull ChangePasswordForm form) {

    final var tenant =
        this.tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new ItemNotFoundException(USER_NOT_FOUND));

    final var currentHash = DigestUtils.sha256Hex(form.getCurrentPassword() + tenant.getSalt());

    if (tenant.getHash() != null && !currentHash.equals(tenant.getHash())) {
      throw new BadRequestException("wrongPassword");
    }

    final var salt = RandomStringUtils.secure().nextAlphanumeric(16);
    tenant.setHash(DigestUtils.sha256Hex(form.getNewPassword() + salt));
    tenant.setSalt(salt);
    this.tenantRepository.save(tenant);
  }

  @Transactional
  public void deleteAccount(final @NonNull UUID tenantId) {

    final var tenantOpt = this.tenantRepository.findById(tenantId);

    if (tenantOpt.isEmpty()) {
      throw new ItemNotFoundException(USER_NOT_FOUND);
    }

    final var tenant = tenantOpt.get();

    this.tenantRepository.delete(tenant);

    this.eventPublisher.publishEvent(new TenantDeletedEvent(tenant.getUsername()));

    log.warn("User {} deleted account.", tenant.getUsername());
  }

  public @NonNull TenantInfo getTenantInfo(final @NonNull UUID tenantId) {

    final var tenant =
        this.tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new ItemNotFoundException(USER_NOT_FOUND));

    return this.tenantMapper.toTenantInfo(tenant);
  }

  public @NonNull TenantPublicProfileDto getPublicProfile(final @NonNull String username) {

    final var tenant =
        this.tenantRepository
            .findByUsername(username)
            .orElseThrow(() -> new ItemNotFoundException(USER_NOT_FOUND));

    final var displayName =
        tenant.getDisplayName() != null ? tenant.getDisplayName() : tenant.getUsername();

    final var avatarUrl = resolveAvatarUrl(tenant.getAvatarUrl(), tenant.getEmail());

    return new TenantPublicProfileDto(tenant.getUsername(), displayName, avatarUrl);
  }

  private static @NonNull String resolveAvatarUrl(
      final @Nullable String storedUrl, final @NonNull String email) {

    if (storedUrl != null && !storedUrl.isBlank()) {
      return storedUrl;
    }

    final var hash = DigestUtils.md5Hex(email.trim().toLowerCase(java.util.Locale.getDefault()));

    return "https://www.gravatar.com/avatar/" + hash + "?d=identicon";
  }
}
