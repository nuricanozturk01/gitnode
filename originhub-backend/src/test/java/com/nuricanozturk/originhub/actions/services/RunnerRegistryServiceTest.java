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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.actions.dtos.request.RunnerRegistrationRequest;
import com.nuricanozturk.originhub.actions.entities.ExecutorType;
import com.nuricanozturk.originhub.actions.entities.Runner;
import com.nuricanozturk.originhub.actions.entities.RunnerRegistrationToken;
import com.nuricanozturk.originhub.actions.entities.RunnerStatus;
import com.nuricanozturk.originhub.actions.repositories.RunnerRegistrationTokenRepository;
import com.nuricanozturk.originhub.actions.repositories.RunnerRepository;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.AccessNotAllowedException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RunnerRegistryService unit tests")
class RunnerRegistryServiceTest {

  @Mock private RunnerRepository runnerRepository;
  @Mock private RunnerRegistrationTokenRepository registrationTokenRepository;
  @Mock private RunnerTokenService runnerTokenService;

  @InjectMocks private RunnerRegistryService service;

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID RUNNER_ID = UUID.randomUUID();

  @Test
  @DisplayName("generateRegistrationToken persists token and returns raw value with ghrt_ prefix")
  void generateRegistrationToken_savesAndReturnsToken() {
    when(registrationTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    final var result = service.generateRegistrationToken(TENANT_ID, USER_ID);

    assertThat(result.token()).startsWith("ghrt_");
    assertThat(result.expiresAt()).isAfter(Instant.now());
    verify(registrationTokenRepository).save(any());
  }

  @Test
  @DisplayName("generateRegistrationToken stores tenantId in token entity")
  void generateRegistrationToken_storesTenantId() {
    when(registrationTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.generateRegistrationToken(TENANT_ID, USER_ID);

    verify(registrationTokenRepository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                t -> TENANT_ID.equals(((RunnerRegistrationToken) t).getTenantId())));
  }

  @Test
  @DisplayName("register throws when token not found")
  void register_throws_whenTokenUnknown() {
    when(registrationTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

    final var req = regRequest("ghrt_bad");

    assertThatThrownBy(() -> service.register(req))
        .isInstanceOf(ErrorOccurredException.class)
        .hasMessageContaining("Invalid registration token");
  }

  @Test
  @DisplayName("register throws when token already used and no runnerId")
  void register_throws_whenTokenUsed() {
    final var token = buildToken(false, false);
    token.setUsed(true);
    when(registrationTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> service.register(regRequest("ghrt_used")))
        .isInstanceOf(ErrorOccurredException.class)
        .hasMessageContaining("already used");
  }

  @Test
  @DisplayName("register throws when token expired")
  void register_throws_whenTokenExpired() {
    final var token = buildToken(false, true);
    when(registrationTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> service.register(regRequest("ghrt_expired")))
        .isInstanceOf(ErrorOccurredException.class)
        .hasMessageContaining("expired");
  }

  @Test
  @DisplayName("register creates runner scoped to tenant and returns JWT on valid token")
  void register_succeeds_onValidToken() {
    final var regToken = buildToken(false, false);
    when(registrationTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(regToken));
    when(registrationTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(runnerRepository.save(any()))
        .thenAnswer(
            inv -> {
              final Runner r = inv.getArgument(0);
              r.setId(RUNNER_ID);
              return r;
            });
    when(runnerTokenService.generateRunnerToken(RUNNER_ID)).thenReturn("runner.jwt.token");

    final var result = service.register(regRequest("ghrt_valid"));

    assertThat(result.runnerId()).isEqualTo(RUNNER_ID);
    assertThat(result.token()).isEqualTo("runner.jwt.token");
    assertThat(result.labels()).containsExactly("self-hosted", "linux");
    verify(registrationTokenRepository).save(any());
    verify(runnerRepository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                r -> TENANT_ID.equals(((Runner) r).getTenantId())));
  }

  @Test
  @DisplayName("heartbeat updates last_heartbeat and status")
  void heartbeat_updatesRunner() {
    final var runner = new Runner();
    runner.setId(RUNNER_ID);
    runner.setStatus(RunnerStatus.ONLINE);
    when(runnerRepository.findById(RUNNER_ID)).thenReturn(Optional.of(runner));
    when(runnerTokenService.extractRunnerId("runner.jwt")).thenReturn(RUNNER_ID);
    when(runnerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.heartbeat(RUNNER_ID, "runner.jwt", "online");

    verify(runnerTokenService).validate("runner.jwt");
    assertThat(runner.getLastHeartbeat()).isNotNull();
    assertThat(runner.getStatus()).isEqualTo(RunnerStatus.ONLINE);
  }

  @Test
  @DisplayName("heartbeat throws when runner not found")
  void heartbeat_throws_whenRunnerMissing() {
    when(runnerRepository.findById(RUNNER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.heartbeat(RUNNER_ID, "tok", "online"))
        .isInstanceOf(ItemNotFoundException.class);
  }

  @Test
  @DisplayName("heartbeat throws when token belongs to different runner")
  void heartbeat_throws_whenTokenMismatch() {
    final var runner = new Runner();
    runner.setId(RUNNER_ID);
    when(runnerRepository.findById(RUNNER_ID)).thenReturn(Optional.of(runner));
    when(runnerTokenService.extractRunnerId("other.jwt")).thenReturn(UUID.randomUUID());

    assertThatThrownBy(() -> service.heartbeat(RUNNER_ID, "other.jwt", "online"))
        .isInstanceOf(AccessNotAllowedException.class);
  }

  @Test
  @DisplayName("delete throws when runner belongs to different tenant")
  void delete_throws_whenTenantMismatch() {
    final var runner = new Runner();
    runner.setId(RUNNER_ID);
    runner.setTenantId(UUID.randomUUID());
    when(runnerRepository.findById(RUNNER_ID)).thenReturn(Optional.of(runner));

    assertThatThrownBy(() -> service.delete(RUNNER_ID, USER_ID, TENANT_ID))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining("does not belong");
  }

  @Test
  @DisplayName("delete removes runner when scoped to correct tenant")
  void delete_removesRunner_whenTenantMatches() {
    final var runner = new Runner();
    runner.setId(RUNNER_ID);
    runner.setTenantId(TENANT_ID);
    when(runnerRepository.findById(RUNNER_ID)).thenReturn(Optional.of(runner));

    service.delete(RUNNER_ID, USER_ID, TENANT_ID);

    verify(runnerRepository).delete(runner);
  }

  @Test
  @DisplayName("listByTenant returns runners for that tenant only")
  void listByTenant_returnsRunnersForTenant() {
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));

    final var runner = new Runner();
    runner.setId(RUNNER_ID);
    runner.setTenantId(TENANT_ID);
    runner.setName("my-runner");
    runner.setStatus(RunnerStatus.ONLINE);
    runner.setExecutorType(ExecutorType.SHELL);
    runner.setCreatedAt(Instant.now());
    when(runnerRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(runner));

    final var result = service.listByTenant(TENANT_ID);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().status()).isEqualTo("online");
    assertThat(result.getFirst().executorType()).isEqualTo("shell");
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static RunnerRegistrationToken buildToken(final boolean used, final boolean expired) {
    final var t = new RunnerRegistrationToken();
    t.setId(UUID.randomUUID());
    t.setTenantId(TENANT_ID);
    t.setCreatedBy(USER_ID);
    t.setUsed(used);
    t.setTokenHash(DigestUtils.sha256Hex("ghrt_some"));
    t.setExpiresAt(
        expired
            ? Instant.now().minus(1, ChronoUnit.HOURS)
            : Instant.now().plus(1, ChronoUnit.HOURS));
    return t;
  }

  private static RunnerRegistrationRequest regRequest(final String token) {
    return new RunnerRegistrationRequest(
        token, "my-runner", List.of("self-hosted", "linux"), "linux", "amd64", "1.0.0", "shell");
  }
}
