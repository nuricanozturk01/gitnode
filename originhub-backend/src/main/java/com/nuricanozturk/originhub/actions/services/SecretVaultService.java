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
package com.nuricanozturk.originhub.actions.services;

import com.nuricanozturk.originhub.actions.entities.WorkflowSecret;
import com.nuricanozturk.originhub.actions.repositories.WorkflowSecretRepository;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores and retrieves AES-256-GCM encrypted workflow secrets. */
@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class SecretVaultService {

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH = 12;
  private static final int GCM_TAG_LENGTH_BITS = 128;

  @Value("${originhub.actions.secrets.encryption-key:}")
  private String encryptionKeyBase64;

  private final WorkflowSecretRepository secretRepository;
  private final SecureRandom secureRandom = new SecureRandom();

  private SecretKey aesKey;

  @PostConstruct
  void init() {
    if (encryptionKeyBase64 == null || encryptionKeyBase64.isBlank()) {
      aesKey = null;
      log.warn(
          "originhub.actions.secrets.encryption-key not set — secrets vault is disabled."
              + " Set ACTIONS_ENCRYPTION_KEY to a 32-byte base64 value.");
      return;
    }
    final byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
    if (keyBytes.length != 32) {
      throw new IllegalStateException(
          "actions.secrets.encryption-key must be 32 bytes (256-bit AES) base64-encoded");
    }
    aesKey = new SecretKeySpec(keyBytes, "AES");
  }

  @Transactional
  public void createOrUpdate(final UUID repoId, final String name, final String plaintext) {
    requireKey();
    final byte[] iv = new byte[GCM_IV_LENGTH];
    secureRandom.nextBytes(iv);

    final String encryptedValue = encrypt(plaintext, iv);
    final String ivBase64 = Base64.getEncoder().encodeToString(iv);

    final var existing = secretRepository.findByRepoIdAndName(repoId, name);
    final WorkflowSecret entity = existing.orElseGet(WorkflowSecret::new);
    entity.setRepoId(repoId);
    entity.setName(name);
    entity.setEncryptedValue(encryptedValue);
    entity.setIv(ivBase64);
    secretRepository.save(entity);
    log.info("Secret saved: repo={} name={}", repoId, name);
  }

  @Transactional
  public void delete(final UUID repoId, final String name) {
    final var secret =
        secretRepository
            .findByRepoIdAndName(repoId, name)
            .orElseThrow(() -> new ItemNotFoundException("Secret not found: " + name));
    secretRepository.delete(secret);
    log.info("Secret deleted: repo={} name={}", repoId, name);
  }

  @Transactional(readOnly = true)
  public List<String> listNames(final UUID repoId) {
    return secretRepository.findAllByRepoId(repoId).stream().map(WorkflowSecret::getName).toList();
  }

  /**
   * Returns all decrypted secrets for the given repo. Used by {@code JobDispatcher} when building
   * the job payload sent to the runner.
   */
  @Transactional(readOnly = true)
  public Map<String, String> resolveSecrets(final UUID repoId) {
    if (aesKey == null) {
      return Map.of();
    }
    return secretRepository.findAllByRepoId(repoId).stream()
        .collect(
            Collectors.toMap(
                WorkflowSecret::getName, s -> decrypt(s.getEncryptedValue(), s.getIv())));
  }

  // ── crypto helpers ────────────────────────────────────────────────────────

  private String encrypt(final String plaintext, final byte[] iv) {
    try {
      final Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
      final byte[] ciphertext =
          cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(ciphertext);
    } catch (final Exception ex) {
      throw new ErrorOccurredException("Secret encryption failed: " + ex.getMessage());
    }
  }

  private String decrypt(final String encryptedBase64, final String ivBase64) {
    try {
      final byte[] iv = Base64.getDecoder().decode(ivBase64);
      final byte[] ciphertext = Base64.getDecoder().decode(encryptedBase64);
      final Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
      return new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8);
    } catch (final Exception ex) {
      throw new ErrorOccurredException("Secret decryption failed: " + ex.getMessage());
    }
  }

  private void requireKey() {
    if (aesKey == null) {
      throw new ErrorOccurredException(
          "Secrets vault not configured — set originhub.actions.secrets.encryption-key");
    }
  }
}
