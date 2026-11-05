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
package dev.gitnode.os.actions.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.actions.services.RunnerTokenService;
import dev.gitnode.os.actions.services.WorkflowExecutionService;
import dev.gitnode.os.actions.services.WorkflowRunQueryService;
import dev.gitnode.os.actions.websocket.SseEmitterRegistry;
import dev.gitnode.os.identity.services.JwtUtils;
import dev.gitnode.os.repo.api.RepoAccessPort;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
@DisplayName("LogStreamController")
class LogStreamControllerTest {

  private static final UUID STEP_ID = UUID.randomUUID();
  private static final UUID REPO_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final String AUTH = "Bearer token";

  @Mock private WorkflowExecutionService executionService;
  @Mock private WorkflowRunQueryService runQueries;
  @Mock private SseEmitterRegistry sseEmitterRegistry;
  @Mock private RunnerTokenService runnerTokenService;
  @Mock private JwtUtils jwtUtils;
  @Mock private RepoLookupPort repoLookup;
  @Mock private RepoAccessPort repoAccess;

  private LogStreamController controller;

  @BeforeEach
  void setUp() {
    this.controller =
        new LogStreamController(
            this.executionService,
            this.runQueries,
            this.sseEmitterRegistry,
            this.runnerTokenService,
            this.jwtUtils,
            this.repoLookup,
            this.repoAccess);
    when(this.runQueries.repoIdOfStep(STEP_ID)).thenReturn(REPO_ID);
    when(this.repoLookup.findById(REPO_ID))
        .thenReturn(
            Optional.of(new RepoRef(REPO_ID, UUID.randomUUID(), "alice", "app", true, "main")));
  }

  @Test
  @DisplayName("streamLogs refuses a user who cannot read the repository, without subscribing")
  void streamLogs_refusesUserWithoutAccess() {
    when(this.jwtUtils.tryExtractUserId(AUTH)).thenReturn(USER_ID);
    doThrow(new AccessNotAllowedException("no access"))
        .when(this.repoAccess)
        .assertUserCanAccessRepo(USER_ID, "alice", "app");

    assertThatThrownBy(() -> this.controller.streamLogs(AUTH, STEP_ID))
        .isInstanceOf(AccessNotAllowedException.class);

    verify(this.sseEmitterRegistry, never()).subscribe(any());
    verify(this.runQueries, never()).logLines(any());
  }

  @Test
  @DisplayName("streamLogs checks access as an anonymous user when there is no token")
  void streamLogs_checksAnonymousUser() {
    when(this.jwtUtils.tryExtractUserId(null)).thenReturn(null);
    doThrow(new AccessNotAllowedException("no access"))
        .when(this.repoAccess)
        .assertUserCanAccessRepo(null, "alice", "app");

    assertThatThrownBy(() -> this.controller.streamLogs(null, STEP_ID))
        .isInstanceOf(AccessNotAllowedException.class);

    verify(this.sseEmitterRegistry, never()).subscribe(any());
  }

  @Test
  @DisplayName("streamLogs streams the stored lines when the user can read the repository")
  void streamLogs_streamsWhenAllowed() {
    when(this.jwtUtils.tryExtractUserId(AUTH)).thenReturn(USER_ID);
    when(this.sseEmitterRegistry.subscribe(STEP_ID)).thenReturn(new SseEmitter());
    when(this.runQueries.logLines(STEP_ID)).thenReturn(List.of());
    when(this.runQueries.isStepCompleted(STEP_ID)).thenReturn(false);

    final var emitter = this.controller.streamLogs(AUTH, STEP_ID);

    assertThat(emitter).isNotNull();
    verify(this.repoAccess).assertUserCanAccessRepo(USER_ID, "alice", "app");
  }
}
