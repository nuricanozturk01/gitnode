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

import com.nuricanozturk.originhub.actions.dtos.request.RunnerRegistrationRequest;
import com.nuricanozturk.originhub.actions.dtos.response.RegistrationTokenResponse;
import com.nuricanozturk.originhub.actions.dtos.response.RunnerRegistrationResponse;
import com.nuricanozturk.originhub.actions.dtos.response.RunnerResponse;
import com.nuricanozturk.originhub.actions.entities.ExecutorType;
import com.nuricanozturk.originhub.actions.entities.Runner;
import com.nuricanozturk.originhub.actions.entities.RunnerRegistrationToken;
import com.nuricanozturk.originhub.actions.entities.RunnerStatus;
import com.nuricanozturk.originhub.actions.repositories.RunnerRegistrationTokenRepository;
import com.nuricanozturk.originhub.actions.repositories.RunnerRepository;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.AccessNotAllowedException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class RunnerRegistryService {

  private static final String TOKEN_PREFIX = "ghrt_";
  private static final int TOKEN_BYTES = 32;
  private static final int REGISTRATION_TOKEN_TTL_HOURS = 1;

  private final RunnerRepository runnerRepository;
  private final RunnerRegistrationTokenRepository registrationTokenRepository;
  private final RunnerTokenService runnerTokenService;

  private final SecureRandom secureRandom = new SecureRandom();

  @Transactional
  public RegistrationTokenResponse generateRegistrationToken(
      final UUID repoId, final UUID createdBy) {

    final var raw = this.generateSecureToken();
    final var hash = DigestUtils.sha256Hex(raw);
    final var expiresAt = Instant.now().plus(REGISTRATION_TOKEN_TTL_HOURS, ChronoUnit.HOURS);

    final var entity = new RunnerRegistrationToken();
    entity.setRepoId(repoId);
    entity.setCreatedBy(createdBy);
    entity.setTokenHash(hash);
    entity.setExpiresAt(expiresAt);

    this.registrationTokenRepository.save(entity);

    return new RegistrationTokenResponse(raw, expiresAt);
  }

  @Transactional
  public RunnerRegistrationResponse register(final RunnerRegistrationRequest request) {

    final var hash = DigestUtils.sha256Hex(request.token());
    final var regToken =
        this.registrationTokenRepository
            .findByTokenHash(hash)
            .orElseThrow(() -> new ErrorOccurredException("Invalid registration token"));

    if (regToken.isUsed()) {
      throw new ErrorOccurredException("Registration token already used");
    }

    if (regToken.getExpiresAt().isBefore(Instant.now())) {
      throw new ErrorOccurredException("Registration token expired");
    }

    regToken.setUsed(true);
    this.registrationTokenRepository.save(regToken);

    final var runnerTokenHash = DigestUtils.sha256Hex(UUID.randomUUID().toString());

    final var runner = new Runner();
    runner.setRepoId(regToken.getRepoId());
    runner.setName(request.name());
    runner.setLabels(request.labels());
    runner.setOs(request.os());
    runner.setArch(request.arch());
    runner.setVersion(request.version());
    runner.setExecutorType(
        ExecutorType.valueOf(request.executorType().toUpperCase(java.util.Locale.ROOT)));
    runner.setStatus(RunnerStatus.ONLINE);
    runner.setLastHeartbeat(Instant.now());
    runner.setTokenHash(runnerTokenHash);
    runner.setCreatedBy(regToken.getCreatedBy());

    final var saved = this.runnerRepository.save(runner);
    final var jwt = this.runnerTokenService.generateRunnerToken(saved.getId());

    log.info("Runner registered: id={}, name={}", saved.getId(), saved.getName());

    return new RunnerRegistrationResponse(saved.getId(), jwt, saved.getLabels());
  }

  @Transactional
  public void heartbeat(final UUID runnerId, final String runnerToken, final String status) {

    this.runnerTokenService.validate(runnerToken);

    final var runner =
        this.runnerRepository
            .findById(runnerId)
            .orElseThrow(() -> new ItemNotFoundException("Runner not found"));

    final var extractedId = this.runnerTokenService.extractRunnerId(runnerToken);

    if (!extractedId.equals(runnerId)) {
      throw new AccessNotAllowedException("Token does not match runner id");
    }

    runner.setLastHeartbeat(Instant.now());
    runner.setStatus(RunnerStatus.valueOf(status.toUpperCase(java.util.Locale.ROOT)));
    this.runnerRepository.save(runner);
  }

  @Transactional(readOnly = true)
  public List<RunnerResponse> listByRepo(final UUID repoId) {

    return this.runnerRepository.findAllByRepoId(repoId).stream().map(this::toResponse).toList();
  }

  @Transactional
  public void delete(final UUID runnerId, final UUID requesterId, final UUID repoId) {

    final var runner =
        this.runnerRepository
            .findById(runnerId)
            .orElseThrow(() -> new ItemNotFoundException("Runner not found"));

    if (!repoId.equals(runner.getRepoId())) {
      throw new AccessNotAllowedException("Runner does not belong to this repository");
    }

    this.runnerRepository.delete(runner);
    log.info("Runner deleted: id={} by requester={}", runnerId, requesterId);
  }

  private RunnerResponse toResponse(final Runner r) {

    return new RunnerResponse(
        r.getId(),
        r.getName(),
        r.getLabels(),
        r.getStatus().name().toLowerCase(),
        r.getExecutorType().name().toLowerCase(),
        r.getOs(),
        r.getArch(),
        r.getVersion(),
        r.getLastHeartbeat(),
        r.getCreatedAt());
  }

  private String generateSecureToken() {

    final var bytes = new byte[TOKEN_BYTES];
    this.secureRandom.nextBytes(bytes);
    return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
