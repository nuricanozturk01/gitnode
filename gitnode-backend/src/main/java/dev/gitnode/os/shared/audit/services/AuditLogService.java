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
package dev.gitnode.os.shared.audit.services;

import dev.gitnode.os.shared.audit.entities.AuditLog;
import dev.gitnode.os.shared.audit.repositories.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@NullMarked
@RequiredArgsConstructor
public class AuditLogService {

  private final AuditLogRepository auditLogRepository;

  @Async
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void log(
      final @Nullable String actorUsername,
      final String action,
      final @Nullable String entityType,
      final @Nullable String entityId,
      final @Nullable String details,
      final @Nullable String ipAddress) {
    try {
      final var entry = new AuditLog();
      entry.setActorUsername(actorUsername);
      entry.setAction(action);
      entry.setEntityType(entityType);
      entry.setEntityId(entityId);
      entry.setDetails(details);
      entry.setIpAddress(ipAddress);
      this.auditLogRepository.save(entry);
    } catch (final Exception ex) {
      log.error(
          "Failed to persist audit log action={} actor={}: {}",
          action,
          actorUsername,
          ex.getMessage());
    }
  }
}
