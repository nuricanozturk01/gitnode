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
package dev.gitnode.os.actions.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.actions.entities.WorkflowSecret;
import dev.gitnode.os.actions.repositories.WorkflowSecretRepository;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecretVaultService unit tests")
class SecretVaultServiceTest {

  @Mock private WorkflowSecretRepository secretRepository;

  @InjectMocks private SecretVaultService service;

  private static final UUID REPO_ID = UUID.randomUUID();

  /** 32 random bytes encoded as base64 — valid AES-256 key for tests. */
  private static final String TEST_KEY_B64 = Base64.getEncoder().encodeToString(new byte[32]);

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "encryptionKeyBase64", TEST_KEY_B64);
    service.init();
  }

  @Test
  @DisplayName("createOrUpdate encrypts plaintext and persists")
  void createOrUpdate_encryptsAndPersists() {
    when(secretRepository.findByRepoIdAndName(REPO_ID, "MY_SECRET")).thenReturn(Optional.empty());
    when(secretRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.createOrUpdate(REPO_ID, "MY_SECRET", "super-secret-value");

    final var captor = ArgumentCaptor.forClass(WorkflowSecret.class);
    verify(secretRepository).save(captor.capture());
    final var saved = captor.getValue();

    assertThat(saved.getName()).isEqualTo("MY_SECRET");
    assertThat(saved.getEncryptedValue()).isNotBlank();
    assertThat(saved.getIv()).isNotBlank();
    assertThat(saved.getEncryptedValue()).doesNotContain("super-secret-value");
  }

  @Test
  @DisplayName("resolveSecrets decrypts stored values")
  void resolveSecrets_decrypts() {
    // Store, then resolve
    when(secretRepository.findByRepoIdAndName(REPO_ID, "DB_PASS")).thenReturn(Optional.empty());
    final var captorStore = ArgumentCaptor.forClass(WorkflowSecret.class);
    when(secretRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.createOrUpdate(REPO_ID, "DB_PASS", "hunter2");

    verify(secretRepository).save(captorStore.capture());
    final var stored = captorStore.getValue();

    when(secretRepository.findAllByRepoId(REPO_ID)).thenReturn(List.of(stored));

    final var resolved = service.resolveSecrets(REPO_ID);
    assertThat(resolved).containsEntry("DB_PASS", "hunter2");
  }

  @Test
  @DisplayName("listNames returns secret names without values")
  void listNames_returnsNames() {
    final var s = new WorkflowSecret();
    s.setRepoId(REPO_ID);
    s.setName("TOKEN");
    s.setEncryptedValue("enc");
    s.setIv("iv");
    when(secretRepository.findAllByRepoId(REPO_ID)).thenReturn(List.of(s));

    final var names = service.listNames(REPO_ID);
    assertThat(names).containsExactly("TOKEN");
  }

  @Test
  @DisplayName("delete removes existing secret")
  void delete_removesExisting() {
    final var s = new WorkflowSecret();
    s.setName("OLD_SECRET");
    when(secretRepository.findByRepoIdAndName(REPO_ID, "OLD_SECRET")).thenReturn(Optional.of(s));

    service.delete(REPO_ID, "OLD_SECRET");

    verify(secretRepository).delete(s);
  }

  @Test
  @DisplayName("delete throws when secret not found")
  void delete_throwsIfMissing() {
    when(secretRepository.findByRepoIdAndName(REPO_ID, "GHOST")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(REPO_ID, "GHOST"))
        .isInstanceOf(ItemNotFoundException.class);
  }

  @Test
  @DisplayName("init throws when key is not 32 bytes")
  void init_throwsOnShortKey() {
    ReflectionTestUtils.setField(
        service, "encryptionKeyBase64", Base64.getEncoder().encodeToString(new byte[16]));
    assertThatThrownBy(service::init).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("createOrUpdate throws when key not configured")
  void createOrUpdate_throwsWhenNoKey() {
    ReflectionTestUtils.setField(service, "encryptionKeyBase64", "");
    service.init();
    assertThatThrownBy(() -> service.createOrUpdate(REPO_ID, "X", "v"))
        .isInstanceOf(ErrorOccurredException.class);
  }
}
