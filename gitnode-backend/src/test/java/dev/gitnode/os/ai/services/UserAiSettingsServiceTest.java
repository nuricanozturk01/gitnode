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
package dev.gitnode.os.ai.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.ai.dtos.TestAiConnectionRequest;
import dev.gitnode.os.ai.dtos.UpdateAiSettingsRequest;
import dev.gitnode.os.ai.entities.AiProvider;
import dev.gitnode.os.ai.entities.UserAiSettings;
import dev.gitnode.os.ai.repositories.UserAiSettingsRepository;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserAiSettingsService unit tests")
class UserAiSettingsServiceTest {

  @Mock private UserAiSettingsRepository settingsRepository;
  @Mock private AiProviderService openAiProvider;

  @InjectMocks private UserAiSettingsService service;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "encryptionKeyBase64", "");
    ReflectionTestUtils.setField(service, "providerServices", List.of());
    service.init();
  }

  private void stubOpenAiProvider() {
    when(openAiProvider.provider()).thenReturn(AiProvider.OPENAI);
    when(openAiProvider.complete(any(), any(), any(), anyInt())).thenReturn("OK");
    ReflectionTestUtils.setField(service, "providerServices", List.of(openAiProvider));
    service.init();
  }

  @Test
  @DisplayName("getSettings returns default when no settings exist")
  void getSettings_returnsDefault_whenNotFound() {
    final var tenantId = UUID.randomUUID();
    when(settingsRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());

    final var result = service.getSettings(tenantId);

    assertThat(result).isNotNull();
    assertThat(result.provider()).isEqualTo(AiProvider.OPENAI);
    assertThat(result.enabled()).isFalse();
    assertThat(result.hasApiKey()).isFalse();
  }

  @Test
  @DisplayName("updateSettings saves new entity when none exists")
  void updateSettings_createsNew_whenNotFound() {
    final var tenantId = UUID.randomUUID();
    final var request =
        new UpdateAiSettingsRequest(
            AiProvider.ANTHROPIC, "sk-test-key", null, "claude-haiku-4-5-20251001", true);

    when(settingsRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
    when(settingsRepository.save(any(UserAiSettings.class))).thenAnswer(inv -> inv.getArgument(0));

    final var result = service.updateSettings(tenantId, request);

    assertThat(result.provider()).isEqualTo(AiProvider.ANTHROPIC);
    assertThat(result.enabled()).isTrue();
    assertThat(result.hasApiKey()).isTrue();
    verify(settingsRepository).save(any(UserAiSettings.class));
  }

  @Test
  @DisplayName("updateSettings updates existing entity")
  void updateSettings_updatesExisting() {
    final var tenantId = UUID.randomUUID();
    final var existing = new UserAiSettings();
    existing.setTenantId(tenantId);
    existing.setProvider(AiProvider.OPENAI);
    existing.setEnabled(false);

    final var request =
        new UpdateAiSettingsRequest(AiProvider.GEMINI, null, null, "gemini-2.0-flash", true);

    when(settingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));
    when(settingsRepository.save(any(UserAiSettings.class))).thenAnswer(inv -> inv.getArgument(0));

    final var result = service.updateSettings(tenantId, request);

    assertThat(result.provider()).isEqualTo(AiProvider.GEMINI);
    assertThat(result.enabled()).isTrue();
    assertThat(result.model()).isEqualTo("gemini-2.0-flash");
  }

  @Test
  @DisplayName("findEnabledSettings returns empty when AI disabled")
  void findEnabledSettings_returnsEmpty_whenDisabled() {
    final var tenantId = UUID.randomUUID();
    final var settings = new UserAiSettings();
    settings.setEnabled(false);
    settings.setApiKey("sk-key");

    when(settingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));

    assertThat(service.findEnabledSettings(tenantId)).isEmpty();
  }

  @Test
  @DisplayName("findEnabledSettings returns empty when no API key")
  void findEnabledSettings_returnsEmpty_whenNoApiKey() {
    final var tenantId = UUID.randomUUID();
    final var settings = new UserAiSettings();
    settings.setEnabled(true);
    settings.setApiKey(null);

    when(settingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));

    assertThat(service.findEnabledSettings(tenantId)).isEmpty();
  }

  @Test
  @DisplayName("findEnabledSettings returns LOCAL settings without API key")
  void findEnabledSettings_returnsLocal_whenEnabledWithoutApiKey() {
    final var tenantId = UUID.randomUUID();
    final var settings = new UserAiSettings();
    settings.setProvider(AiProvider.LOCAL);
    settings.setEnabled(true);
    settings.setApiKey(null);

    when(settingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));

    assertThat(service.findEnabledSettings(tenantId)).contains(settings);
  }

  @Test
  @DisplayName("testConnection throws when provider and API key are not configured")
  void testConnection_throws_whenNotConfigured() {
    final var tenantId = UUID.randomUUID();
    when(settingsRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.testConnection(tenantId, null))
        .isInstanceOf(ErrorOccurredException.class)
        .hasMessageContaining("Configure provider and API key");
  }

  @Test
  @DisplayName("testConnection uses request API key even when AI is disabled in storage")
  void testConnection_usesRequestApiKey_whenAiDisabledInStorage() {
    stubOpenAiProvider();
    final var tenantId = UUID.randomUUID();
    final var stored = new UserAiSettings();
    stored.setTenantId(tenantId);
    stored.setProvider(AiProvider.OPENAI);
    stored.setEnabled(false);

    when(settingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(stored));

    final var result =
        service.testConnection(
            tenantId, new TestAiConnectionRequest(AiProvider.OPENAI, "sk-test", null, null));

    assertThat(result).isEqualTo("Connection successful");
    verify(openAiProvider).complete(any(), any(), any(), anyInt());
  }

  @Test
  @DisplayName("deleteApiKey clears key from existing settings")
  void deleteApiKey_clearsKey() {
    final var tenantId = UUID.randomUUID();
    final var settings = new UserAiSettings();
    settings.setApiKey("sk-key");
    settings.setApiKeyIv("iv");

    when(settingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));
    when(settingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.deleteApiKey(tenantId);

    verify(settingsRepository).save(any(UserAiSettings.class));
    assertThat(settings.getApiKey()).isNull();
    assertThat(settings.getApiKeyIv()).isNull();
  }
}
