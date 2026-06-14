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

import dev.gitnode.os.ai.dtos.TestAiConnectionRequest;
import dev.gitnode.os.ai.dtos.UpdateAiSettingsRequest;
import dev.gitnode.os.ai.dtos.UserAiSettingsDto;
import dev.gitnode.os.ai.entities.AiProvider;
import dev.gitnode.os.ai.entities.UserAiSettings;
import dev.gitnode.os.ai.repositories.UserAiSettingsRepository;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class UserAiSettingsService {

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH = 12;
  private static final int GCM_TAG_LENGTH_BITS = 128;
  private static final int AES_KEY_LENGTH = 32;

  @Value("${gitnode.ai.encryption-key:}")
  private String encryptionKeyBase64;

  private final UserAiSettingsRepository settingsRepository;
  private final List<AiProviderService> providerServices;

  private @Nullable SecretKey aesKey;
  private @Nullable Map<AiProvider, AiProviderService> providerMap;
  private final SecureRandom secureRandom = new SecureRandom();

  @PostConstruct
  void init() {
    this.providerMap =
        this.providerServices.stream()
            .collect(Collectors.toMap(AiProviderService::provider, Function.identity()));

    if (this.encryptionKeyBase64.isBlank()) {
      this.aesKey = null;
      log.warn(
          "gitnode.ai.encryption-key not set — AI API keys will be stored unencrypted."
              + " Set GITNODE_AI_ENCRYPTION_KEY in production.");
      return;
    }
    final byte[] keyBytes = Base64.getDecoder().decode(this.encryptionKeyBase64);
    if (keyBytes.length != AES_KEY_LENGTH) {
      throw new IllegalStateException(
          "gitnode.ai.encryption-key must be 32 bytes (256-bit AES) base64-encoded");
    }
    this.aesKey = new SecretKeySpec(keyBytes, "AES");
  }

  @Transactional(readOnly = true)
  public UserAiSettingsDto getSettings(final UUID tenantId) {
    final var settings =
        this.settingsRepository
            .findByTenantId(tenantId)
            .orElseGet(() -> this.defaultSettings(tenantId));
    return this.toDto(settings);
  }

  @Transactional
  public UserAiSettingsDto updateSettings(
      final UUID tenantId, final UpdateAiSettingsRequest request) {
    final var settings =
        this.settingsRepository
            .findByTenantId(tenantId)
            .orElseGet(() -> this.newSettings(tenantId));

    settings.setProvider(request.provider());
    settings.setEnabled(request.enabled());

    if (request.baseUrl() != null) {
      settings.setBaseUrl(request.baseUrl().isBlank() ? null : request.baseUrl());
    }
    if (request.model() != null) {
      settings.setModel(request.model().isBlank() ? null : request.model());
    }

    if (request.apiKey() != null && !request.apiKey().isBlank()) {
      final var encrypted = this.encryptApiKey(request.apiKey());
      settings.setApiKey(encrypted.encryptedKey());
      settings.setApiKeyIv(encrypted.iv());
    }

    return this.toDto(this.settingsRepository.save(settings));
  }

  @Transactional
  public void deleteApiKey(final UUID tenantId) {
    this.settingsRepository
        .findByTenantId(tenantId)
        .ifPresent(
            s -> {
              s.setApiKey(null);
              s.setApiKeyIv(null);
              this.settingsRepository.save(s);
            });
  }

  public Optional<UserAiSettings> findEnabledSettings(final UUID tenantId) {
    return this.settingsRepository
        .findByTenantId(tenantId)
        .filter(UserAiSettings::isEnabled)
        .filter(this::isConfiguredForProvider);
  }

  public Optional<UserAiSettings> findFirstEnabledSettings(final @Nullable UUID... tenantIds) {
    for (final var tenantId : tenantIds) {
      if (tenantId == null) {
        continue;
      }
      final var settings = this.findEnabledSettings(tenantId);
      if (settings.isPresent()) {
        return settings;
      }
    }
    return Optional.empty();
  }

  public UserAiSettings decryptSettings(final UserAiSettings settings) {
    if (settings.getApiKey() == null) {
      return settings;
    }
    final var iv = settings.getApiKeyIv();
    if (this.aesKey == null || iv == null || iv.isBlank()) {
      return this.copyWithApiKey(settings, settings.getApiKey());
    }
    try {
      final var decrypted = this.decrypt(settings.getApiKey(), iv);
      return this.copyWithApiKey(settings, decrypted);
    } catch (final Exception e) {
      log.error("Failed to decrypt AI API key for tenant {}", settings.getTenantId());
      throw new ErrorOccurredException("Failed to decrypt AI API key");
    }
  }

  private boolean isConfiguredForProvider(final UserAiSettings settings) {
    if (settings.getProvider() == AiProvider.LOCAL) {
      return true;
    }
    final var apiKey = settings.getApiKey();
    return apiKey != null && !apiKey.isBlank();
  }

  private UserAiSettings copyWithApiKey(final UserAiSettings settings, final String apiKey) {
    final var copy = new UserAiSettings();
    copy.setId(settings.getId());
    copy.setTenantId(settings.getTenantId());
    copy.setProvider(settings.getProvider());
    copy.setApiKey(apiKey);
    copy.setBaseUrl(settings.getBaseUrl());
    copy.setModel(settings.getModel());
    copy.setEnabled(settings.isEnabled());
    return copy;
  }

  public AiProviderService resolveProvider(final UserAiSettings settings) {

    final var service = this.providerMap.get(settings.getProvider());

    if (service == null) {
      throw new ErrorOccurredException("Unsupported AI provider: " + settings.getProvider());
    }
    return service;
  }

  public String testConnection(
      final UUID tenantId, final @Nullable TestAiConnectionRequest request) {
    final var settings = this.resolveSettingsForTest(tenantId, request);
    final var providerService = this.resolveProvider(settings);
    final var response =
        providerService.complete(
            settings,
            AiPrompts.TEST_CONNECTION,
            "Reply with: OK",
            AiInputBounds.TEST_CONNECTION_MAX_COMPLETION_TOKENS);
    if (response.isBlank()) {
      throw new ErrorOccurredException("Empty response from AI provider");
    }
    return "Connection successful";
  }

  private UserAiSettings resolveSettingsForTest(
      final UUID tenantId, final @Nullable TestAiConnectionRequest request) {
    final var stored =
        this.settingsRepository
            .findByTenantId(tenantId)
            .orElseGet(() -> this.defaultSettings(tenantId));
    final var draft = buildTestDraft(tenantId, stored, request);
    if (request != null && hasInlineApiKey(request)) {
      return this.resolveTestDraftWithInlineKey(draft, request.apiKey());
    }
    draft.setApiKey(stored.getApiKey());
    draft.setApiKeyIv(stored.getApiKeyIv());
    if (!this.isConfiguredForProvider(draft)) {
      throw new ErrorOccurredException("Configure provider and API key before testing.");
    }
    return this.decryptSettings(draft);
  }

  private static boolean hasInlineApiKey(final TestAiConnectionRequest request) {
    return request.apiKey() != null && !request.apiKey().isBlank();
  }

  private UserAiSettings resolveTestDraftWithInlineKey(
      final UserAiSettings draft, final String apiKey) {

    final var withKey = this.copyWithApiKey(draft, apiKey);

    if (!this.isConfiguredForProvider(withKey)) {
      throw new ErrorOccurredException("Configure provider and API key before testing.");
    }

    return withKey;
  }

  private static UserAiSettings buildTestDraft(
      final UUID tenantId,
      final UserAiSettings stored,
      final @Nullable TestAiConnectionRequest request) {
    var provider = stored.getProvider();
    var model = stored.getModel();
    var baseUrl = stored.getBaseUrl();
    if (request != null) {
      provider = applyIfNotNull(request.provider(), provider);
      model =
          request.model() != null ? (request.model().isBlank() ? null : request.model()) : model;
      baseUrl =
          request.baseUrl() != null
              ? (request.baseUrl().isBlank() ? null : request.baseUrl())
              : baseUrl;
    }
    final var draft = new UserAiSettings();
    draft.setTenantId(tenantId);
    draft.setProvider(provider);
    draft.setModel(model);
    draft.setBaseUrl(baseUrl);
    draft.setEnabled(true);
    return draft;
  }

  private static <T> T applyIfNotNull(final @Nullable T override, final T fallback) {
    return override != null ? override : fallback;
  }

  private UserAiSettingsDto toDto(final UserAiSettings s) {
    return new UserAiSettingsDto(
        s.getId(),
        s.getProvider(),
        s.getApiKey() != null,
        s.getBaseUrl(),
        s.getModel(),
        s.isEnabled());
  }

  private UserAiSettings defaultSettings(final UUID tenantId) {
    final var s = new UserAiSettings();
    s.setTenantId(tenantId);
    return s;
  }

  private UserAiSettings newSettings(final UUID tenantId) {
    final var s = new UserAiSettings();
    s.setTenantId(tenantId);
    return s;
  }

  private record EncryptedKey(String encryptedKey, String iv) {}

  private EncryptedKey encryptApiKey(final String plainKey) {
    if (this.aesKey == null) {
      return new EncryptedKey(plainKey, "");
    }
    try {
      final byte[] iv = new byte[GCM_IV_LENGTH];
      this.secureRandom.nextBytes(iv);
      final var cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, this.aesKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
      final byte[] encrypted =
          cipher.doFinal(plainKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return new EncryptedKey(
          Base64.getEncoder().encodeToString(encrypted), Base64.getEncoder().encodeToString(iv));
    } catch (final Exception e) {
      throw new ErrorOccurredException("Failed to encrypt API key");
    }
  }

  private String decrypt(final String encryptedBase64, final String ivBase64) {
    if (this.aesKey == null) {
      return encryptedBase64;
    }
    try {
      final byte[] iv = Base64.getDecoder().decode(ivBase64);
      final byte[] encrypted = Base64.getDecoder().decode(encryptedBase64);
      final var cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, this.aesKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
      return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
    } catch (final Exception e) {
      throw new ErrorOccurredException("Failed to decrypt API key");
    }
  }
}
