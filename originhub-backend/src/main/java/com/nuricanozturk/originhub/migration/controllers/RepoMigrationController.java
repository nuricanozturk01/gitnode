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
package com.nuricanozturk.originhub.migration.controllers;

import com.nuricanozturk.originhub.migration.dtos.MigrationForm;
import com.nuricanozturk.originhub.migration.dtos.MigrationJobResponse;
import com.nuricanozturk.originhub.migration.dtos.MigrationStatus;
import com.nuricanozturk.originhub.migration.entities.MigrationJob;
import com.nuricanozturk.originhub.migration.service.GitMigrationService;
import com.nuricanozturk.originhub.shared.auth.services.JwtUtils;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/migration")
@RequiredArgsConstructor
@NullMarked
public class RepoMigrationController {

  private final GitMigrationService migrationService;
  private final JwtUtils tokenService;

  @PostMapping
  public ResponseEntity<MigrationJobResponse> create(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @RequestBody @Valid final MigrationForm form) {

    final var tenantId = this.tokenService.extractUserId(authHeader);

    final var jobId = UUID.randomUUID();
    this.migrationService.migrate(form, tenantId, jobId);

    return ResponseEntity.accepted().body(new MigrationJobResponse(jobId, MigrationStatus.PENDING));
  }

  @GetMapping("/{jobId}")
  public ResponseEntity<MigrationJob> getStatus(@PathVariable final UUID jobId) {

    final var jobOpt = this.migrationService.findJob(jobId);

    return jobOpt.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
  }
}
