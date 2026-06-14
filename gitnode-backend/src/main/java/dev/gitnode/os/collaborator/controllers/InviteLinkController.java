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
package dev.gitnode.os.collaborator.controllers;

import dev.gitnode.os.collaborator.dtos.CollaboratorInfo;
import dev.gitnode.os.collaborator.dtos.InvitationTokenInfo;
import dev.gitnode.os.collaborator.services.CollaboratorService;
import dev.gitnode.os.shared.auth.services.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
@NullMarked
public class InviteLinkController {

  private final CollaboratorService collaboratorService;
  private final JwtUtils jwtUtils;

  @GetMapping("/{token}")
  public ResponseEntity<InvitationTokenInfo> getByToken(@PathVariable final String token) {
    return ResponseEntity.ok(this.collaboratorService.getInvitationByToken(token));
  }

  @PostMapping("/{token}/accept")
  public ResponseEntity<CollaboratorInfo> acceptByToken(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String token) {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(this.collaboratorService.acceptViaToken(requesterId, token));
  }

  @PostMapping("/{token}/decline")
  public ResponseEntity<CollaboratorInfo> declineByToken(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String token) {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(this.collaboratorService.declineViaToken(requesterId, token));
  }
}
