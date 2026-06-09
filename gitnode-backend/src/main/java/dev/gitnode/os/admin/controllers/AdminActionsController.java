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
package dev.gitnode.os.admin.controllers;

import dev.gitnode.os.actions.api.ActionsAdminPort;
import dev.gitnode.os.admin.dtos.AdminActionsStatsResponse;
import dev.gitnode.os.admin.dtos.AdminRunnerSummary;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/actions")
@RequiredArgsConstructor
@NullMarked
@PreAuthorize("@platformAdminService.isCurrentUserPlatformAdmin()")
public class AdminActionsController {

  private final ActionsAdminPort actionsAdminPort;

  @GetMapping("/runners")
  public ResponseEntity<List<AdminRunnerSummary>> runners() {
    final var data =
        this.actionsAdminPort.listAllRunners().stream()
            .map(
                r ->
                    new AdminRunnerSummary(
                        r.id(),
                        r.tenantId(),
                        r.name(),
                        r.labels(),
                        r.status(),
                        r.os(),
                        r.arch(),
                        r.version(),
                        r.lastHeartbeat(),
                        r.createdAt()))
            .toList();
    return ResponseEntity.ok(data);
  }

  @GetMapping("/stats")
  public ResponseEntity<AdminActionsStatsResponse> stats() {
    final var s = this.actionsAdminPort.getRunStats();
    return ResponseEntity.ok(
        new AdminActionsStatsResponse(
            s.totalRuns(),
            s.successRuns(),
            s.failureRuns(),
            s.cancelledRuns(),
            s.inProgressRuns(),
            s.totalRunners(),
            s.onlineRunners(),
            s.busyRunners()));
  }
}
