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
package com.nuricanozturk.originhub.collaborator.controllers;

import com.nuricanozturk.originhub.collaborator.dtos.CollaboratorInfo;
import com.nuricanozturk.originhub.collaborator.dtos.InviteCollaboratorForm;
import com.nuricanozturk.originhub.collaborator.dtos.InviteLinkResponse;
import com.nuricanozturk.originhub.collaborator.dtos.UpdateCollaboratorPermissionsForm;
import com.nuricanozturk.originhub.collaborator.services.CollaboratorService;
import com.nuricanozturk.originhub.shared.auth.services.JwtUtils;
import com.nuricanozturk.originhub.shared.ratelimit.RateLimit;
import com.nuricanozturk.originhub.shared.repo.dtos.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repos/{owner}/{repo}/collaborators")
@RequiredArgsConstructor
@NullMarked
public class CollaboratorController {

  private final CollaboratorService collaboratorService;
  private final JwtUtils jwtUtils;

  @PostMapping
  @RateLimit(limit = 50, windowSeconds = 3600, key = "collaborator.invite")
  public ResponseEntity<CollaboratorInfo> invite(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @Valid @RequestBody final InviteCollaboratorForm form) {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    final var info = this.collaboratorService.invite(requesterId, owner, repo, form);
    return ResponseEntity.status(HttpStatus.CREATED).body(info);
  }

  @GetMapping
  public ResponseEntity<PageResponse<CollaboratorInfo>> list(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size) {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(this.collaboratorService.list(requesterId, owner, repo, page, size));
  }

  @DeleteMapping("/{username}")
  public ResponseEntity<Void> remove(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final String username) {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    this.collaboratorService.remove(requesterId, owner, repo, username);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{username}/permissions")
  public ResponseEntity<CollaboratorInfo> updatePermissions(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final String username,
      @Valid @RequestBody final UpdateCollaboratorPermissionsForm form) {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    final var info =
        this.collaboratorService.updatePermissions(requesterId, owner, repo, username, form);
    return ResponseEntity.ok(info);
  }

  @PostMapping("/invitation/accept")
  public ResponseEntity<CollaboratorInfo> acceptInvitation(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo) {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(
        this.collaboratorService.respondToInvitation(requesterId, owner, repo, true));
  }

  @PostMapping("/invitation/decline")
  public ResponseEntity<CollaboratorInfo> declineInvitation(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo) {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(
        this.collaboratorService.respondToInvitation(requesterId, owner, repo, false));
  }

  @GetMapping("/invitation")
  public ResponseEntity<CollaboratorInfo> getMyInvitation(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo) {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(this.collaboratorService.getMyInvitation(requesterId, owner, repo));
  }

  @PostMapping("/{username}/invite-link")
  @RateLimit(limit = 30, windowSeconds = 3600, key = "collaborator.invite-link")
  public ResponseEntity<InviteLinkResponse> generateInviteLink(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final String username) {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(
        this.collaboratorService.generateInviteLink(requesterId, owner, repo, username));
  }

  @SuppressWarnings("unused")
  @PostMapping("/{username}/send-invitation")
  @RateLimit(limit = 30, windowSeconds = 3600, key = "collaborator.send-invitation")
  public ResponseEntity<Void> sendInvitationEmail(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final String username) {

    throw new UnsupportedOperationException("Send invitation email is not yet implemented");
  }
}
