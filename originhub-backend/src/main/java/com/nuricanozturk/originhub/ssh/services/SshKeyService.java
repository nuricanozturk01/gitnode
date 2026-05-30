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
package com.nuricanozturk.originhub.ssh.services;

import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import com.nuricanozturk.originhub.ssh.dtos.AddSshKeyForm;
import com.nuricanozturk.originhub.ssh.dtos.SshKeyInfo;
import com.nuricanozturk.originhub.ssh.entities.SshKey;
import com.nuricanozturk.originhub.ssh.repositories.SshKeyRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class SshKeyService {

  private final @NonNull SshKeyRepository sshKeyRepository;
  private final @NonNull TenantRepository tenantRepository;

  @Transactional
  public @NonNull SshKeyInfo addKey(
      final @NonNull UUID tenantId, final @NonNull AddSshKeyForm form) {

    final var tenant =
        this.tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new ItemNotFoundException("User not found"));

    if (this.sshKeyRepository.existsByTenantIdAndTitle(tenantId, form.getTitle())) {
      throw new ErrorOccurredException("sshKeyTitleAlreadyExists");
    }

    final var fingerprint = this.resolveFingerprint(form.getPublicKey().trim());

    if (this.sshKeyRepository.findByFingerprint(fingerprint).isPresent()) {
      throw new ErrorOccurredException("sshKeyAlreadyExists");
    }

    final var sshKey = new SshKey();
    sshKey.setTenant(tenant);
    sshKey.setTitle(form.getTitle());
    sshKey.setPublicKey(form.getPublicKey().trim());
    sshKey.setFingerprint(fingerprint);

    final var saved = this.sshKeyRepository.save(sshKey);

    return this.toInfo(saved);
  }

  @Transactional
  public void deleteKey(final @NonNull UUID keyId, final @NonNull UUID tenantId) {
    if (!this.sshKeyRepository.existsById(keyId)) {
      throw new ItemNotFoundException("SSH key not found");
    }

    this.sshKeyRepository.deleteByIdAndTenantId(keyId, tenantId);
  }

  public @NonNull List<SshKeyInfo> listKeys(final @NonNull UUID tenantId) {

    return this.sshKeyRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
        .map(this::toInfo)
        .toList();
  }

  @Transactional
  public @NonNull Tenant findTenantByFingerprint(final @NonNull String fingerprint) {

    final var sshKey =
        this.sshKeyRepository
            .findByFingerprintWithTenant(fingerprint)
            .orElseThrow(() -> new ItemNotFoundException("SSH key not found"));

    sshKey.setLastUsedAt(Instant.now());
    this.sshKeyRepository.save(sshKey);

    final var tenant = sshKey.getTenant();
    Hibernate.initialize(tenant);
    return tenant;
  }

  static @NonNull String fingerprintFromWireBytes(final byte @NonNull [] wireBytes)
      throws NoSuchAlgorithmException {

    final var hash = MessageDigest.getInstance("SHA-256").digest(wireBytes);

    return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(hash);
  }

  private @NonNull String resolveFingerprint(final @NonNull String publicKey) {

    try {
      final var normalized = publicKey.trim().replaceAll("[\\r\\n\\t]+", " ").replaceAll(" +", " ");
      final var parts = normalized.split(" ");

      if (parts.length < 2) {
        throw new ErrorOccurredException("Invalid SSH public key format");
      }

      final var wireBytes = Base64.getDecoder().decode(parts[1]);
      return fingerprintFromWireBytes(wireBytes);

    } catch (final ErrorOccurredException ex) {
      throw ex;
    } catch (final Exception ex) {
      log.error("Failed to resolve fingerprint", ex);
      throw new ErrorOccurredException("Invalid SSH public key format");
    }
  }

  private @NonNull SshKeyInfo toInfo(final @NonNull SshKey key) {

    return new SshKeyInfo(
        key.getId(), key.getTitle(), key.getFingerprint(), key.getLastUsedAt(), key.getCreatedAt());
  }
}
