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
package dev.gitnode.os.ssh.controllers;

import dev.gitnode.os.shared.auth.services.JwtUtils;
import dev.gitnode.os.shared.ratelimit.RateLimit;
import dev.gitnode.os.ssh.dtos.AddSshKeyForm;
import dev.gitnode.os.ssh.dtos.SshKeyInfo;
import dev.gitnode.os.ssh.services.SshKeyService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/ssh-keys")
@RequiredArgsConstructor
@NullMarked
public class SshKeyController {

  private final SshKeyService sshKeyService;
  private final JwtUtils tokenService;

  @GetMapping
  public ResponseEntity<List<SshKeyInfo>> listKeys(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {

    final var tenantId = this.tokenService.extractUserId(authHeader);

    final var keys = this.sshKeyService.listKeys(tenantId);

    return ResponseEntity.ok(keys);
  }

  @PostMapping
  @RateLimit(limit = 15, windowSeconds = 3600, key = "ssh.add-key")
  public ResponseEntity<SshKeyInfo> addKey(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @Valid @RequestBody final AddSshKeyForm form) {

    final var tenantId = this.tokenService.extractUserId(authHeader);

    final var key = this.sshKeyService.addKey(tenantId, form);

    return ResponseEntity.status(HttpStatus.CREATED).body(key);
  }

  @DeleteMapping("/{keyId}")
  public ResponseEntity<Void> deleteKey(
      @PathVariable final UUID keyId,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {

    final var tenantId = this.tokenService.extractUserId(authHeader);

    this.sshKeyService.deleteKey(keyId, tenantId);

    return ResponseEntity.noContent().build();
  }
}
