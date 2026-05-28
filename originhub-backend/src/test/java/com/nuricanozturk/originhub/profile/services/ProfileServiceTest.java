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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.mappers.TenantMapper;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileService unit tests")
class ProfileServiceTest {

  @Mock private TenantRepository tenantRepository;
  @Mock private TenantMapper tenantMapper;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private ProfileService profileService;

  @Test
  @DisplayName("updateUsername throws ItemNotFoundException when user not found")
  void updateUsername_throws_whenUserNotFound() {
    UUID tenantId = UUID.randomUUID();
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> profileService.updateUsername(tenantId, new UpdateUsernameForm("newuser")))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("userNotFound");
  }

  @Test
  @DisplayName("updateUsername throws ItemAlreadyExistsException when username taken by another")
  void updateUsername_throws_whenUsernameTaken() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    tenant.setUsername("olduser");
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantRepository.existsByUsername("newuser")).thenReturn(true);

    assertThatThrownBy(
            () -> profileService.updateUsername(tenantId, new UpdateUsernameForm("newuser")))
        .isInstanceOf(ItemAlreadyExistsException.class)
        .hasMessageContaining("usernameTaken");
  }

  @Test
  @DisplayName("updateUsername saves and publishes UsernameChangedEvent")
  void updateUsername_savesAndPublishesEvent() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    tenant.setUsername("OldUser");
    TenantInfo expectedInfo =
        new TenantInfo(
            tenantId, "newuser", "e@e.com", "newuser", null, false, Instant.EPOCH, Instant.EPOCH);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantRepository.existsByUsername("newuser")).thenReturn(false);
    when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));
    when(tenantMapper.toTenantInfo(any(Tenant.class))).thenReturn(expectedInfo);

    TenantInfo result = profileService.updateUsername(tenantId, new UpdateUsernameForm("newuser"));

    assertThat(result).isSameAs(expectedInfo);
    assertThat(tenant.getUsername()).isEqualTo("newuser");
    ArgumentCaptor<UsernameChangedEvent> captor =
        ArgumentCaptor.forClass(UsernameChangedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().oldUsername()).isEqualTo("olduser");
    assertThat(captor.getValue().newUsername()).isEqualTo("newuser");
  }

  @Test
  @DisplayName("updateDisplayName throws when user not found")
  void updateDisplayName_throws_whenUserNotFound() {
    UUID tenantId = UUID.randomUUID();
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> profileService.updateDisplayName(tenantId, new UpdateDisplayNameForm("Display")))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("userNotFound");
  }

  @Test
  @DisplayName("updateDisplayName sets displayName to null when blank")
  void updateDisplayName_setsNull_whenBlank() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    tenant.setDisplayName("Old");
    TenantInfo expected =
        new TenantInfo(tenantId, "u", "e@e.com", "u", null, false, Instant.EPOCH, Instant.EPOCH);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));
    when(tenantMapper.toTenantInfo(any(Tenant.class))).thenReturn(expected);

    profileService.updateDisplayName(tenantId, new UpdateDisplayNameForm("   "));

    assertThat(tenant.getDisplayName()).isNull();
  }

  @Test
  @DisplayName("updateDisplayName trims and sets displayName")
  void updateDisplayName_trimsAndSets() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));
    when(tenantMapper.toTenantInfo(any(Tenant.class)))
        .thenReturn(
            new TenantInfo(
                tenantId, "u", "e@e.com", "My Name", null, false, Instant.EPOCH, Instant.EPOCH));

    profileService.updateDisplayName(tenantId, new UpdateDisplayNameForm("  My Name  "));

    assertThat(tenant.getDisplayName()).isEqualTo("My Name");
  }

  @Test
  @DisplayName("changePassword throws when user not found")
  void changePassword_throws_whenUserNotFound() {
    UUID tenantId = UUID.randomUUID();
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                profileService.changePassword(
                    tenantId, new ChangePasswordForm("current", "newpass123")))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("userNotFound");
  }

  @Test
  @DisplayName("changePassword throws BadRequestException when current password wrong")
  void changePassword_throws_whenWrongCurrentPassword() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    tenant.setSalt("somesalt");
    tenant.setHash("correctHash");
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

    assertThatThrownBy(
            () ->
                profileService.changePassword(
                    tenantId, new ChangePasswordForm("wrongCurrent", "newpass123")))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("wrongPassword");
  }

  @Test
  @DisplayName("changePassword updates hash and salt on success")
  void changePassword_updatesHashAndSalt_whenSuccess() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    tenant.setSalt("oldSalt");
    tenant.setHash(DigestUtils.sha256Hex("current" + "oldSalt"));
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));

    profileService.changePassword(tenantId, new ChangePasswordForm("current", "newpass123"));

    verify(tenantRepository).save(tenant);
    assertThat(tenant.getSalt()).isNotEqualTo("oldSalt");
    assertThat(tenant.getHash()).isNotEqualTo(DigestUtils.sha256Hex("current" + "oldSalt"));
  }

  @Test
  @DisplayName("deleteAccount throws when user not found")
  void deleteAccount_throws_whenUserNotFound() {
    UUID tenantId = UUID.randomUUID();
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> profileService.deleteAccount(tenantId))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("userNotFound");
  }

  @Test
  @DisplayName("deleteAccount deletes tenant when found")
  void deleteAccount_deletes_whenFound() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = mock(Tenant.class);
    when(tenant.getUsername()).thenReturn("testuser");
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

    profileService.deleteAccount(tenantId);

    verify(tenantRepository).delete(tenant);
    verify(eventPublisher).publishEvent(any(TenantDeletedEvent.class));
  }

  @Test
  @DisplayName("getTenantInfo throws when user not found")
  void getTenantInfo_throws_whenUserNotFound() {
    UUID tenantId = UUID.randomUUID();
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> profileService.getTenantInfo(tenantId))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("userNotFound");
  }

  @Test
  @DisplayName("getTenantInfo returns mapped TenantInfo")
  void getTenantInfo_returnsMappedInfo() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    TenantInfo expected =
        new TenantInfo(
            tenantId, "u", "e@e.com", "Display", null, false, Instant.EPOCH, Instant.EPOCH);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantMapper.toTenantInfo(tenant)).thenReturn(expected);

    TenantInfo result = profileService.getTenantInfo(tenantId);

    assertThat(result).isSameAs(expected);
  }

  @Test
  @DisplayName("getPublicProfile throws when user not found")
  void getPublicProfile_throws_whenUserNotFound() {
    when(tenantRepository.findByUsername("nobody")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> profileService.getPublicProfile("nobody"))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("userNotFound");
  }

  @Test
  @DisplayName("getPublicProfile returns DTO with displayName when set")
  void getPublicProfile_returnsDto_withDisplayName() {
    Tenant tenant = new Tenant();
    tenant.setUsername("alice");
    tenant.setDisplayName("Alice");
    tenant.setAvatarUrl("https://avatar");
    when(tenantRepository.findByUsername("alice")).thenReturn(Optional.of(tenant));

    TenantPublicProfileDto result = profileService.getPublicProfile("alice");

    assertThat(result.getUsername()).isEqualTo("alice");
    assertThat(result.getDisplayName()).isEqualTo("Alice");
    assertThat(result.getAvatarUrl()).isEqualTo("https://avatar");
  }

  @Test
  @DisplayName("getPublicProfile uses username as displayName when displayName null")
  void getPublicProfile_usesUsernameAsDisplayName_whenNull() {
    Tenant tenant = new Tenant();
    tenant.setUsername("bob");
    tenant.setEmail("bob@example.com");
    tenant.setDisplayName(null);
    tenant.setAvatarUrl(null);
    when(tenantRepository.findByUsername("bob")).thenReturn(Optional.of(tenant));

    TenantPublicProfileDto result = profileService.getPublicProfile("bob");

    assertThat(result.getDisplayName()).isEqualTo("bob");
  }
}
