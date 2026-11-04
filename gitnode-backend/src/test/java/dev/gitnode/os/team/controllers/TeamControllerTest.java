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
package dev.gitnode.os.team.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.gitnode.os.identity.services.JwtUtils;
import dev.gitnode.os.repo.dtos.PageResponse;
import dev.gitnode.os.shared.errorhandling.exceptions.TokenExpiredException;
import dev.gitnode.os.team.api.TeamRole;
import dev.gitnode.os.team.dtos.InviteTeamMemberForm;
import dev.gitnode.os.team.dtos.TeamMeInfo;
import dev.gitnode.os.team.dtos.TeamMemberInfo;
import dev.gitnode.os.team.entities.TeamMemberStatus;
import dev.gitnode.os.team.services.TeamService;
import jakarta.validation.Validation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeamController unit tests")
class TeamControllerTest {

  private static final String AUTH = "Bearer token";

  @Mock private TeamService teamService;
  @Mock private JwtUtils jwtUtils;

  @InjectMocks private TeamController controller;

  @Test
  @DisplayName("me returns the caller's pending invitation count")
  void me_returnsPendingInvitationCount() {
    final var userId = UUID.randomUUID();
    when(jwtUtils.extractUserId(AUTH)).thenReturn(userId);
    when(teamService.me(userId)).thenReturn(new TeamMeInfo(2));

    final var response = controller.me(AUTH);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().pendingInvitations()).isEqualTo(2);
  }

  @Test
  @DisplayName("leave passes the caller and membership id and returns 204")
  void leave_passesCallerAndMembershipId() {
    final var userId = UUID.randomUUID();
    final var membershipId = UUID.randomUUID();
    when(jwtUtils.extractUserId(AUTH)).thenReturn(userId);

    final var response = controller.leave(AUTH, membershipId);

    verify(teamService).leave(userId, membershipId);
    assertThat(response.getStatusCode().value()).isEqualTo(204);
  }

  @Test
  @DisplayName("memberships lists the teams the caller belongs to")
  void memberships_listsCallersTeams() {
    final var userId = UUID.randomUUID();
    final var page = new PageResponse<TeamMemberInfo>(List.of(), 0, 20, 0, 0);
    when(jwtUtils.extractUserId(AUTH)).thenReturn(userId);
    when(teamService.memberships(userId, 0, 20)).thenReturn(page);

    assertThat(controller.memberships(AUTH, 0, 20).getBody()).isSameAs(page);
  }

  @Test
  @DisplayName("invite form validates the body")
  void invite_validatesBody() {
    try (final var factory = Validation.buildDefaultValidatorFactory()) {
      final var validator = factory.getValidator();

      assertThat(validator.validate(new InviteTeamMemberForm(" ", TeamRole.VIEWER))).hasSize(1);
      assertThat(validator.validate(new InviteTeamMemberForm("x".repeat(81), TeamRole.VIEWER)))
          .hasSize(1);
      assertThat(validator.validate(new InviteTeamMemberForm("bob", null))).hasSize(1);
      assertThat(validator.validate(new InviteTeamMemberForm("bob", TeamRole.VIEWER))).isEmpty();
    }
  }

  @Test
  @DisplayName("list passes raw page and size to the service which clamps")
  void list_passesRawPageAndSizeToServiceWhichClamps() {
    final var userId = UUID.randomUUID();
    final var page = new PageResponse<TeamMemberInfo>(List.of(), 0, 50, 0, 0);
    when(jwtUtils.extractUserId(AUTH)).thenReturn(userId);
    when(teamService.members(userId, TeamMemberStatus.PENDING, -1, 100000)).thenReturn(page);

    final var response = controller.members(AUTH, TeamMemberStatus.PENDING, -1, 100000);

    verify(teamService).members(userId, TeamMemberStatus.PENDING, -1, 100000);
    assertThat(response.getBody()).isSameAs(page);
  }

  @Test
  @DisplayName("an invalid token is rejected before reaching the service")
  void unauthenticated_isRejected() {
    when(jwtUtils.extractUserId("bad")).thenThrow(new TokenExpiredException("invalidToken"));

    assertThatThrownBy(() -> controller.me("bad")).isInstanceOf(TokenExpiredException.class);
    verifyNoInteractions(teamService);
  }
}
