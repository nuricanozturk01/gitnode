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
package com.nuricanozturk.originhub.actions.controllers;

import com.nuricanozturk.originhub.actions.dtos.request.StepLogRequest;
import com.nuricanozturk.originhub.actions.repositories.WorkflowLogRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowStepRepository;
import com.nuricanozturk.originhub.actions.services.RunnerTokenService;
import com.nuricanozturk.originhub.actions.services.WorkflowExecutionService;
import com.nuricanozturk.originhub.actions.websocket.SseEmitterRegistry;
import com.nuricanozturk.originhub.shared.auth.services.JwtUtils;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/actions/steps")
@RequiredArgsConstructor
@NullMarked
public class LogStreamController {

  private static final String BEARER = "Bearer ";
  private final WorkflowExecutionService executionService;
  private final WorkflowLogRepository logRepository;
  private final WorkflowStepRepository stepRepository;
  private final SseEmitterRegistry sseEmitterRegistry;
  private final RunnerTokenService runnerTokenService;
  private final JwtUtils jwtUtils;

  @PostMapping("/{stepId}/logs")
  public ResponseEntity<Void> ingestLog(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final UUID stepId,
      @Valid @RequestBody final StepLogRequest request) {

    this.runnerTokenService.validate(authHeader.substring(BEARER.length()));

    this.executionService.ingestLog(
        stepId, request.lineNumber(), request.content(), request.level());

    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{stepId}/logs")
  public SseEmitter streamLogs(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader,
      @PathVariable final UUID stepId) {

    this.jwtUtils.tryExtractUserId(authHeader);

    final var emitter = this.sseEmitterRegistry.subscribe(stepId);

    // Replay existing logs so the client catches up regardless of subscribe timing.
    final var existing = this.logRepository.findAllByStepIdOrderByLineNumberAsc(stepId);
    for (final var log : existing) {
      try {
        emitter.send(
            SseEmitter.event()
                .data(
                    new WorkflowExecutionService.LogLine(
                        log.getLineNumber(), log.getContent(), log.getLevel())));
      } catch (final Exception ignored) {
        break;
      }
    }

    this.stepRepository
        .findById(stepId)
        .ifPresent(
            step -> {
              if ("completed".equals(step.getStatus())) {
                emitter.complete();
              }
            });

    return emitter;
  }
}
