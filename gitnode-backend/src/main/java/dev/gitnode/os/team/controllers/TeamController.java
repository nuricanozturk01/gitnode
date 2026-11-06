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

import dev.gitnode.os.identity.api.TokenUserResolver;
import dev.gitnode.os.repo.dtos.PageResponse;
import dev.gitnode.os.shared.ratelimit.RateLimit;
import dev.gitnode.os.team.dtos.ChangeTeamRoleForm;
import dev.gitnode.os.team.dtos.InviteTeamMemberForm;
import dev.gitnode.os.team.dtos.TeamMeInfo;
import dev.gitnode.os.team.dtos.TeamMemberInfo;
import dev.gitnode.os.team.entities.TeamMemberStatus;
import dev.gitnode.os.team.services.TeamService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/team")
@RequiredArgsConstructor
@NullMarked
public class TeamController {

  private final TeamService teamService;
  private final TokenUserResolver jwtUtils;

  @GetMapping("/me")
  public ResponseEntity<TeamMeInfo> me(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {

    return ResponseEntity.ok(this.teamService.me(this.jwtUtils.extractUserId(authHeader)));
  }

  @GetMapping("/members")
  public ResponseEntity<PageResponse<TeamMemberInfo>> members(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @RequestParam(defaultValue = "ACCEPTED") final TeamMemberStatus status,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size) {

    return ResponseEntity.ok(
        this.teamService.members(this.jwtUtils.extractUserId(authHeader), status, page, size));
  }

  @PostMapping("/invitations")
  @RateLimit(limit = 30, windowSeconds = 600, key = "team.invite")
  public ResponseEntity<TeamMemberInfo> invite(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @Valid @RequestBody final InviteTeamMemberForm form) {

    final var info =
        this.teamService.invite(
            this.jwtUtils.extractUserId(authHeader), form.username(), form.role());
    return ResponseEntity.status(HttpStatus.CREATED).body(info);
  }

  @DeleteMapping("/invitations/{id}")
  public ResponseEntity<Void> cancelInvitation(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final UUID id) {

    this.teamService.cancelInvitation(this.jwtUtils.extractUserId(authHeader), id);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/members/{id}/role")
  public ResponseEntity<TeamMemberInfo> changeRole(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final UUID id,
      @Valid @RequestBody final ChangeTeamRoleForm form) {

    return ResponseEntity.ok(
        this.teamService.changeRole(this.jwtUtils.extractUserId(authHeader), id, form.role()));
  }

  @DeleteMapping("/members/{id}")
  public ResponseEntity<Void> remove(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final UUID id) {

    this.teamService.remove(this.jwtUtils.extractUserId(authHeader), id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/memberships")
  public ResponseEntity<PageResponse<TeamMemberInfo>> memberships(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size) {
    return ResponseEntity.ok(
        this.teamService.memberships(this.jwtUtils.extractUserId(authHeader), page, size));
  }

  @DeleteMapping("/memberships/{id}")
  public ResponseEntity<Void> leave(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final UUID id) {
    this.teamService.leave(this.jwtUtils.extractUserId(authHeader), id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/invitations/mine")
  public ResponseEntity<PageResponse<TeamMemberInfo>> myInvitations(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size) {

    return ResponseEntity.ok(
        this.teamService.myInvitations(this.jwtUtils.extractUserId(authHeader), page, size));
  }

  @PostMapping("/invitations/{id}/accept")
  public ResponseEntity<TeamMemberInfo> accept(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final UUID id) {

    return ResponseEntity.ok(this.teamService.accept(this.jwtUtils.extractUserId(authHeader), id));
  }

  @PostMapping("/invitations/{id}/decline")
  public ResponseEntity<Void> decline(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final UUID id) {

    this.teamService.decline(this.jwtUtils.extractUserId(authHeader), id);
    return ResponseEntity.noContent().build();
  }
}
