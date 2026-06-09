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

import dev.gitnode.os.shared.audit.annotations.Audited;
import dev.gitnode.os.shared.repo.dtos.PageResponse;
import dev.gitnode.os.webhook.api.WebhookDlqAdminPort;
import dev.gitnode.os.webhook.api.WebhookDlqEntry;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/webhooks/dlq")
@RequiredArgsConstructor
@NullMarked
@PreAuthorize("@platformAdminService.isCurrentUserPlatformAdmin()")
public class AdminWebhookDlqController {

  private static final int MAX_PAGE_SIZE = 100;

  private final WebhookDlqAdminPort webhookDlqAdminPort;

  @GetMapping
  public ResponseEntity<PageResponse<WebhookDlqEntry>> list(
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size) {

    final var pageable =
        PageRequest.of(
            page, Math.min(size, MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "failedAt"));
    return ResponseEntity.ok(PageResponse.from(this.webhookDlqAdminPort.list(pageable)));
  }

  @GetMapping("/summary")
  public ResponseEntity<Map<String, Long>> summary() {

    return ResponseEntity.ok(Map.of("pending", this.webhookDlqAdminPort.countPending()));
  }

  @Audited(
      action = "WEBHOOK_DLQ_RETRY",
      entityType = "WEBHOOK_DLQ",
      entityIdSpEL = "#id.toString()")
  @PostMapping("/{id}/retry")
  public ResponseEntity<Void> retry(@PathVariable final UUID id) {

    this.webhookDlqAdminPort.retry(id);
    return ResponseEntity.noContent().build();
  }

  @Audited(
      action = "WEBHOOK_DLQ_DISMISS",
      entityType = "WEBHOOK_DLQ",
      entityIdSpEL = "#id.toString()")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> dismiss(@PathVariable final UUID id) {

    this.webhookDlqAdminPort.dismiss(id);
    return ResponseEntity.noContent().build();
  }
}
