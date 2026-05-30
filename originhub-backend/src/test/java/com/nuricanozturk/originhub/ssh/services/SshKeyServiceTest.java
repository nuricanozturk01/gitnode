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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import com.nuricanozturk.originhub.ssh.dtos.AddSshKeyForm;
import com.nuricanozturk.originhub.ssh.entities.SshKey;
import com.nuricanozturk.originhub.ssh.repositories.SshKeyRepository;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SshKeyService unit tests")
class SshKeyServiceTest {

  @Mock private SshKeyRepository sshKeyRepository;
  @Mock private TenantRepository tenantRepository;

  @InjectMocks private SshKeyService sshKeyService;

  @Test
  @DisplayName("addKey throws ItemNotFoundException when tenant not found")
  void addKey_throws_whenTenantNotFound() {
    UUID tenantId = UUID.randomUUID();
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> sshKeyService.addKey(tenantId, new AddSshKeyForm("title", "ssh-rsa AAAA")))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("User not found");
  }

  @Test
  @DisplayName("addKey throws ErrorOccurredException when title already exists")
  void addKey_throws_whenTitleExists() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(sshKeyRepository.existsByTenantIdAndTitle(tenantId, "mytitle")).thenReturn(true);

    assertThatThrownBy(
            () -> sshKeyService.addKey(tenantId, new AddSshKeyForm("mytitle", "ssh-rsa AAAA")))
        .isInstanceOf(ErrorOccurredException.class)
        .hasMessageContaining("sshKeyTitleAlreadyExists");
  }

  @Test
  @DisplayName("addKey throws ErrorOccurredException when public key format invalid")
  void addKey_throws_whenInvalidKeyFormat() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(sshKeyRepository.existsByTenantIdAndTitle(tenantId, "t")).thenReturn(false);

    assertThatThrownBy(
            () ->
                sshKeyService.addKey(
                    tenantId, new AddSshKeyForm("t", "not-valid-key-only-one-part")))
        .isInstanceOf(ErrorOccurredException.class);
  }

  @Test
  @DisplayName("addKey returns SshKeyInfo and saves key when valid")
  void addKey_returnsInfoAndSaves_whenValid() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    SshKey savedKey = new SshKey();
    savedKey.setId(UUID.randomUUID());
    savedKey.setTitle("mykey");
    savedKey.setFingerprint("SHA256:abc");
    savedKey.setCreatedAt(Instant.EPOCH);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(sshKeyRepository.existsByTenantIdAndTitle(tenantId, "mykey")).thenReturn(false);
    when(sshKeyRepository.findByFingerprint(any())).thenReturn(Optional.empty());
    when(sshKeyRepository.save(any(SshKey.class))).thenReturn(savedKey);

    var result = sshKeyService.addKey(tenantId, new AddSshKeyForm("mykey", "ssh-rsa AAAA"));

    assertThat(result.id()).isEqualTo(savedKey.getId());
    assertThat(result.title()).isEqualTo("mykey");
    assertThat(result.fingerprint()).isEqualTo("SHA256:abc");
    verify(sshKeyRepository).save(any(SshKey.class));
  }

  @Test
  @DisplayName("deleteKey throws ItemNotFoundException when key does not exist")
  void deleteKey_throws_whenKeyNotFound() {
    UUID keyId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    when(sshKeyRepository.existsById(keyId)).thenReturn(false);

    assertThatThrownBy(() -> sshKeyService.deleteKey(keyId, tenantId))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("SSH key not found");
  }

  @Test
  @DisplayName("deleteKey calls deleteByIdAndTenantId when key exists")
  void deleteKey_callsDelete_whenKeyExists() {
    UUID keyId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    when(sshKeyRepository.existsById(keyId)).thenReturn(true);

    sshKeyService.deleteKey(keyId, tenantId);

    verify(sshKeyRepository).deleteByIdAndTenantId(eq(keyId), eq(tenantId));
  }

  @Test
  @DisplayName("listKeys returns empty list when no keys")
  void listKeys_returnsEmpty_whenNoKeys() {
    UUID tenantId = UUID.randomUUID();
    when(sshKeyRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of());

    var result = sshKeyService.listKeys(tenantId);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("listKeys returns mapped SshKeyInfo list")
  void listKeys_returnsMappedList() {
    UUID tenantId = UUID.randomUUID();
    SshKey key = new SshKey();
    key.setId(UUID.randomUUID());
    key.setTitle("k");
    key.setFingerprint("SHA256:x");
    key.setCreatedAt(Instant.EPOCH);
    key.setLastUsedAt(null);
    when(sshKeyRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(key));

    var result = sshKeyService.listKeys(tenantId);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo(key.getId());
    assertThat(result.get(0).title()).isEqualTo("k");
    assertThat(result.get(0).fingerprint()).isEqualTo("SHA256:x");
  }

  @Test
  @DisplayName("findTenantByFingerprint throws when key not found")
  void findTenantByFingerprint_throws_whenNotFound() {
    when(sshKeyRepository.findByFingerprintWithTenant("unknown")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sshKeyService.findTenantByFingerprint("unknown"))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("SSH key not found");
  }

  @Test
  @DisplayName("findTenantByFingerprint returns tenant and updates lastUsedAt")
  void findTenantByFingerprint_returnsTenantAndUpdatesLastUsed() {
    Tenant tenant = new Tenant();
    tenant.setId(UUID.randomUUID());
    tenant.setUsername("alice");
    SshKey sshKey = new SshKey();
    sshKey.setTenant(tenant);
    when(sshKeyRepository.findByFingerprintWithTenant("fp")).thenReturn(Optional.of(sshKey));
    when(sshKeyRepository.save(any(SshKey.class))).thenAnswer(i -> i.getArgument(0));

    Tenant result = sshKeyService.findTenantByFingerprint("fp");

    assertThat(result).isSameAs(tenant);
    verify(sshKeyRepository).save(sshKey);
    assertThat(sshKey.getLastUsedAt()).isNotNull();
  }

  @Test
  @DisplayName("fingerprintFromWireBytes returns SHA256 prefix and base64 without padding")
  void fingerprintFromWireBytes_returnsSha256Base64Format() throws NoSuchAlgorithmException {
    byte[] input = new byte[] {1, 2, 3};

    String result = SshKeyService.fingerprintFromWireBytes(input);

    assertThat(result).startsWith("SHA256:");
    assertThat(result.substring(7)).doesNotContain("=");
  }

  @Test
  @DisplayName("fingerprintFromWireBytes is deterministic for same input")
  void fingerprintFromWireBytes_isDeterministic() throws NoSuchAlgorithmException {
    byte[] input = "hello".getBytes();

    String a = SshKeyService.fingerprintFromWireBytes(input);
    String b = SshKeyService.fingerprintFromWireBytes(input);

    assertThat(a).isEqualTo(b);
  }
}
