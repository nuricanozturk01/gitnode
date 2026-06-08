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
package dev.gitnode.os.profile.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.events.profile.TenantDeletedEvent;
import dev.gitnode.os.events.profile.UsernameChangedEvent;
import dev.gitnode.os.profile.dtos.ChangePasswordForm;
import dev.gitnode.os.profile.dtos.TenantPublicProfileDto;
import dev.gitnode.os.profile.dtos.UpdateDisplayNameForm;
import dev.gitnode.os.profile.dtos.UpdateProfileForm;
import dev.gitnode.os.profile.dtos.UpdateUsernameForm;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemAlreadyExistsException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.tenant.dtos.TenantInfo;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.shared.tenant.mappers.TenantMapper;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
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
            tenantId,
            "newuser",
            "e@e.com",
            "newuser",
            null,
            null,
            null,
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH);
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
        new TenantInfo(
            tenantId,
            "u",
            "e@e.com",
            "u",
            null,
            null,
            null,
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH);
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
                tenantId,
                "u",
                "e@e.com",
                "My Name",
                null,
                null,
                null,
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH));

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
  @DisplayName("deleteAccount is idempotent when user not found")
  void deleteAccount_noop_whenUserNotFound() {
    UUID tenantId = UUID.randomUUID();
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

    profileService.deleteAccount(tenantId);

    verify(tenantRepository, never()).delete(any());
    verify(eventPublisher, never()).publishEvent(any());
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
            tenantId,
            "u",
            "e@e.com",
            "Display",
            null,
            null,
            null,
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH);
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

  @Test
  @DisplayName("getPublicProfile includes bio, website, location, profileReadme")
  void getPublicProfile_includesProfileFields() {
    Tenant tenant = new Tenant();
    tenant.setUsername("alice");
    tenant.setEmail("alice@example.com");
    tenant.setDisplayName("Alice");
    tenant.setAvatarUrl("https://avatar");
    tenant.setBio("Hello world");
    tenant.setWebsite("https://alice.dev");
    tenant.setLocation("Istanbul");
    tenant.setProfileReadme("# Alice\nI love code.");
    when(tenantRepository.findByUsername("alice")).thenReturn(Optional.of(tenant));

    TenantPublicProfileDto result = profileService.getPublicProfile("alice");

    assertThat(result.getBio()).isEqualTo("Hello world");
    assertThat(result.getWebsite()).isEqualTo("https://alice.dev");
    assertThat(result.getLocation()).isEqualTo("Istanbul");
    assertThat(result.getProfileReadme()).isEqualTo("# Alice\nI love code.");
  }

  @Test
  @DisplayName("updateProfile throws when user not found")
  void updateProfile_throws_whenUserNotFound() {
    UUID tenantId = UUID.randomUUID();
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                profileService.updateProfile(
                    tenantId, new UpdateProfileForm("bio", "https://site.com", "Istanbul", "# Me")))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("userNotFound");
  }

  @Test
  @DisplayName("updateProfile saves trimmed values")
  void updateProfile_savesTrimmedValues() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    TenantInfo expected =
        new TenantInfo(
            tenantId,
            "u",
            "e@e.com",
            "u",
            null,
            "My bio",
            "https://site.com",
            "Istanbul",
            "# Me",
            Instant.EPOCH,
            Instant.EPOCH);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));
    when(tenantMapper.toTenantInfo(any(Tenant.class))).thenReturn(expected);

    TenantInfo result =
        profileService.updateProfile(
            tenantId,
            new UpdateProfileForm("  My bio  ", "  https://site.com  ", "  Istanbul  ", "# Me"));

    assertThat(result).isSameAs(expected);
    assertThat(tenant.getBio()).isEqualTo("My bio");
    assertThat(tenant.getWebsite()).isEqualTo("https://site.com");
    assertThat(tenant.getLocation()).isEqualTo("Istanbul");
    assertThat(tenant.getProfileReadme()).isEqualTo("# Me");
  }

  @Test
  @DisplayName("updateProfile sets fields to null when blank")
  void updateProfile_setsNull_whenBlank() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    tenant.setBio("Old bio");
    tenant.setWebsite("https://old.com");
    tenant.setLocation("Old city");
    tenant.setProfileReadme("Old readme");
    TenantInfo expected =
        new TenantInfo(
            tenantId,
            "u",
            "e@e.com",
            "u",
            null,
            null,
            null,
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));
    when(tenantMapper.toTenantInfo(any(Tenant.class))).thenReturn(expected);

    profileService.updateProfile(tenantId, new UpdateProfileForm("  ", "  ", "  ", "  "));

    assertThat(tenant.getBio()).isNull();
    assertThat(tenant.getWebsite()).isNull();
    assertThat(tenant.getLocation()).isNull();
    assertThat(tenant.getProfileReadme()).isNull();
  }
}
